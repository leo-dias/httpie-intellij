package io.github.leodias.httpie

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class HttpResponse(val status: Int?, val headers: Map<String, List<String>>, val body: String, val head: String = "")
data class RunResult(
    val response: HttpResponse,
    val exitCode: Int,
    val stderr: String,
    val durationMillis: Long,
    val handlerReport: String = "",
    val globalUpdates: Map<String, String?> = emptyMap(),
    val failedTests: Int = 0,
    val command: String = "",
    val savedFile: Path? = null,
)

class HttpieRunner(private val timeoutMillis: Long = 30_000) {
    fun run(request: PreparedRequest, globals: Map<String, String>, cancelled: () -> Boolean): RunResult {
        val start = System.nanoTime()
        val output = execute(request, cancelled)
        val response = parseResponse(output.stdout)
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        if (cancelled()) throw CancellationException("Request cancelled")
        if (request.save != null && output.code == 0) Files.writeString(request.save, response.body)
        val result = RunResult(response, output.code, output.stderr, elapsed, command = request.display, savedFile = request.save.takeIf { output.code == 0 })
        if (request.handler == null || response.status == null) return result
        val script = javaClass.getResourceAsStream("/handler.js")!!.bufferedReader().use { it.readText() }
        val input = Gson().toJson(mapOf("script" to request.handler, "status" to response.status,
            "headers" to response.headers, "body" to response.body, "globals" to globals))
        try {
            val handler = execute(request.copy(argv = listOf("node", "-e", script), stdin = input), cancelled)
            require(handler.code == 0) { "Node.js handler exited with ${handler.code}: ${handler.stderr}" }
            val report = JsonParser.parseString(handler.stdout).asJsonObject
            val updates = report.getAsJsonObject("globals").entrySet().associate { (k, v) -> k to if (v.isJsonNull) null else v.asString }
            val lines = mutableListOf<String>()
            var failed = 0
            report.getAsJsonArray("tests").forEach { entry ->
                val test = entry.asJsonObject
                val ok = test["ok"].asBoolean
                if (!ok) failed++
                lines += "${if (ok) "✓" else "✗"} ${test["name"].asString}${if (ok) "" else ": ${test["msg"].asString}"}"
            }
            report.getAsJsonArray("logs").forEach { lines += "log: ${it.asString}" }
            updates.forEach { (k, v) -> lines += "set {{$k}} = ${v ?: "(cleared)"}" }
            report["error"]?.let { lines += "error: ${it.asString}"; failed++ }
            return result.copy(handlerReport = lines.joinToString("\n"), globalUpdates = updates, failedTests = failed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return result.copy(handlerReport = "Handler error: ${e.message}", failedTests = 1)
        }
    }

    private data class Output(val stdout: String, val stderr: String, val code: Int)

    private fun execute(request: PreparedRequest, cancelled: () -> Boolean): Output {
        if (cancelled()) throw CancellationException("Request cancelled")
        val process = try {
            ProcessBuilder(request.argv).directory(request.directory.toFile()).apply {
                environment().putAll(request.environment)
            }.start()
        } catch (e: IOException) {
            throw IllegalStateException("Could not start ${request.argv.first()}. Ensure it is installed and on the IDE's PATH.", e)
        }
        val io = Executors.newVirtualThreadPerTaskExecutor()
        val stdout = io.submit<String> { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        val stderr = io.submit<String> { process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        val stdin = io.submit {
            process.outputStream.use { out -> request.stdin?.let { out.write(it.toByteArray(Charsets.UTF_8)) } }
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        // shortcut: tracks observable descendants; use OS process groups if daemonizing commands must be contained.
        val descendants = linkedSetOf<ProcessHandle>()
        fun checkRunning() {
            descendants.addAll(process.descendants().toList())
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Request cancelled")
            check(System.nanoTime() < deadline) { "Request timed out after $timeoutMillis ms" }
        }
        fun <T> await(future: Future<T>): T {
            while (true) {
                checkRunning()
                try {
                    return future.get(25, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    // Keep observing cancellation while readers drain inherited subprocess pipes.
                }
            }
        }
        try {
            while (true) {
                checkRunning()
                if (process.waitFor(25, TimeUnit.MILLISECONDS)) break
            }
            val out = await(stdout)
            val err = await(stderr)
            // A failed command may close its input early; preserve its exit code and diagnostics.
            if (process.exitValue() == 0) await(stdin)
            return Output(out, err, process.exitValue())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Request cancelled")
        } finally {
            descendants.addAll(process.descendants().toList())
            val children = descendants.toList()
            children.reversed().forEach { it.destroyForcibly() }
            if (children.isNotEmpty()) {
                // Let a waiting shell reap terminated children before terminating the shell itself.
                runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
            }
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
            stdin.cancel(true); stdout.cancel(true); stderr.cancel(true)
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            io.shutdownNow()
        }
    }

    companion object {
        fun parseResponse(stdout: String): HttpResponse {
            val text = stdout.replace("\r\n", "\n")
            val status = Regex("^HTTP/\\S+\\s+(\\d{3})").find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: return HttpResponse(null, emptyMap(), text)
            val boundary = text.indexOf("\n\n")
            val head = if (boundary == -1) text.trimEnd('\n') else text.substring(0, boundary)
            val headers = linkedMapOf<String, MutableList<String>>()
            head.lineSequence().drop(1).forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) headers.getOrPut(line.substring(0, colon).lowercase()) { mutableListOf() }.add(line.substring(colon + 1).trim())
            }
            return HttpResponse(status, headers, if (boundary == -1) "" else text.substring(boundary + 2), head)
        }
    }
}
