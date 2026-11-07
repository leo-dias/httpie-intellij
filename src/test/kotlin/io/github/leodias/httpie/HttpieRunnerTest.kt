package io.github.leodias.httpie

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

class HttpieRunnerTest {
    @TempDir lateinit var root: Path
    private lateinit var server: HttpServer
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private lateinit var url: String

    @BeforeEach fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = workers
        server.createContext("/") { exchange ->
            val requestBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val response = if (exchange.requestMethod == "POST") requestBody else "{\"message\":\"hello\"}"
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("X-Repeat", "one")
            exchange.responseHeaders.add("X-Repeat", "two")
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach fun stopServer() { server.stop(0); workers.close() }

    private fun requireTool(tool: String) {
        val available = runCatching { ProcessBuilder(tool, "--version").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue(available, "$tool is required for this integration test")
    }

    private fun prepare(text: String): PreparedRequest = RequestEnvironment.prepare(
        text, 0, root.resolve("requests.httpie"), root, null, emptyMap(), emptyMap()
    )!!

    @Test fun `real GET captures response without blocking on stdin`() {
        requireTool("http")
        val result = HttpieRunner().run(prepare("GET $url"), emptyMap()) { false }
        assertEquals(0, result.exitCode)
        assertEquals(200, result.response.status)
        assertTrue(result.response.body.contains("hello"))
        assertEquals(2, result.response.headers["x-repeat"]!!.size)
        assertTrue(result.durationMillis >= 0)
    }

    @Test fun `real POST sends stdin and saves only response body`() {
        requireTool("http")
        val result = HttpieRunner().run(prepare("POST $url\nContent-Type: application/json\n\n{\"name\":\"Ada\"}\n>>! body.json"), emptyMap()) { false }
        assertEquals(0, result.exitCode)
        val saved = Files.readString(root.resolve("body.json"))
        assertTrue(saved.contains("Ada"))
        assertFalse(saved.contains("HTTP/"))
        assertEquals(result.response.body, saved)
    }

    @Test fun `response parser preserves empty bodies repeated headers and header-like body text`() {
        val parsed = HttpieRunner.parseResponse("HTTP/1.1 200 OK\r\nX-Test: one\r\nX-Test: two\r\n\r\nhello: world\r\n\r\nlast\r\n")
        assertEquals(200, parsed.status)
        assertEquals(listOf("one", "two"), parsed.headers["x-test"])
        assertEquals("hello: world\n\nlast\n", parsed.body)
        assertEquals("", HttpieRunner.parseResponse("HTTP/1.1 204 No Content\n\n").body)
        assertEquals("raw body\n", HttpieRunner.parseResponse("raw body\n").body)
    }

    @Test fun `nonzero process does not overwrite existing response`() {
        Files.writeString(root.resolve("keep.txt"), "keep")
        val req = PreparedRequest(listOf("/bin/sh", "-c", "printf broken; printf diagnostic >&2; exit 7"), root, emptyMap(), save = root.resolve("keep.txt"))
        val result = HttpieRunner().run(req, emptyMap()) { false }
        assertEquals(7, result.exitCode)
        assertEquals("diagnostic", result.stderr)
        assertEquals("broken", result.response.body)
        assertEquals("keep", Files.readString(root.resolve("keep.txt")))
    }

    @Test fun `missing executable is actionable`() {
        val error = assertThrows(IllegalStateException::class.java) {
            HttpieRunner().run(PreparedRequest(listOf("httpie-test-nonexistent"), root, emptyMap()), emptyMap()) { false }
        }
        assertTrue(error.message!!.contains("httpie-test-nonexistent"))
    }

    @Test fun `timeout terminates sleeping subprocess`() {
        val started = System.nanoTime()
        assertThrows(IllegalStateException::class.java) {
            HttpieRunner(150).run(PreparedRequest(listOf("/bin/sh", "-c", "sleep 30"), root, emptyMap()), emptyMap()) { false }
        }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5000)
    }

    @Test fun `cancellation stops process and its child`() {
        val pidFile = root.resolve("pid")
        val req = PreparedRequest(listOf("/bin/sh", "-c", "sleep 30 & echo \$! > pid; wait"), root, emptyMap())
        assertThrows(CancellationException::class.java) {
            HttpieRunner().run(req, emptyMap()) { Files.exists(pidFile) && Files.readString(pidFile).isNotBlank() }
        }
        val child = Files.readString(pidFile).trim().toLong()
        assertFalse(ProcessHandle.of(child).map { it.isAlive }.orElse(false))
    }

    @Test fun `node handler logs asserts and updates globals`() {
        requireTool("http"); requireTool("node")
        val request = prepare("""
            GET $url

            > {%
            client.test('status', () => client.assert(response.status === 200));
            client.test('body', () => client.assert(response.body.message === 'hello'));
            client.test('headers', () => client.assert(response.headers.valuesOf('X-Repeat').length === 2));
            client.test('failure', () => client.assert(false, 'expected failure'));
            client.global.set('token', 'new');
            client.global.clear('old');
            client.log('hello');
            %}
        """.trimIndent())
        val result = HttpieRunner().run(request, mapOf("old" to "value")) { false }
        assertEquals("new", result.globalUpdates["token"])
        assertTrue(result.globalUpdates.containsKey("old"))
        assertNull(result.globalUpdates["old"])
        assertTrue(result.handlerReport.contains("hello"))
        assertTrue(result.handlerReport.contains("expected failure"))
        assertEquals(1, result.failedTests)
    }

    @Test fun `node handler errors are reported without losing HTTP response`() {
        requireTool("http"); requireTool("node")
        val result = HttpieRunner().run(prepare("GET $url\n\n> {% throw new Error('handler broke'); %}"), emptyMap()) { false }
        assertEquals(200, result.response.status)
        assertTrue(result.handlerReport.contains("handler broke"))
        assertEquals(1, result.failedTests)
    }

    @Test fun `cancellation during pipe drain is prompt and kills reparented child`() {
        val pidFile = root.resolve("child")
        val started = System.nanoTime()
        try {
            val request = PreparedRequest(listOf("/bin/sh", "-c", "sleep 30 & echo \$! > child; sleep 0.2; exit 0"), root, emptyMap())
            assertThrows(CancellationException::class.java) {
                HttpieRunner(2500).run(request, emptyMap()) { (System.nanoTime() - started) / 1_000_000 > 450 }
            }
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1500, "Cancellation must not wait for the request timeout")
            assertChildStopped(pidFile)
        } finally { stopTestChild(pidFile) }
    }

    @Test fun `timeout kills child observed before shell parent exits`() {
        val pidFile = root.resolve("child")
        try {
            val request = PreparedRequest(listOf("/bin/sh", "-c", "sleep 30 & echo \$! > child; sleep 0.2; exit 0"), root, emptyMap())
            assertThrows(IllegalStateException::class.java) { HttpieRunner(500).run(request, emptyMap()) { false } }
            assertChildStopped(pidFile)
        } finally { stopTestChild(pidFile) }
    }

    private fun assertChildStopped(pidFile: Path) {
        val child = ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElse(null)
        val deadline = System.nanoTime() + 500_000_000
        while (child?.isAlive == true && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(child?.isAlive ?: false, "Child must not survive runner cleanup")
    }

    private fun stopTestChild(pidFile: Path) {
        if (Files.exists(pidFile)) ProcessHandle.of(Files.readString(pidFile).trim().toLong()).ifPresent { it.destroyForcibly() }
    }
}
