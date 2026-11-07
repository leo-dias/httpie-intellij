package io.github.leodias.httpie

import java.nio.file.Files
import java.nio.file.Path

data class PreparedRequest(
    val argv: List<String>,
    val directory: Path,
    val environment: Map<String, String>,
    val stdin: String? = null,
    val handler: String? = null,
    val save: Path? = null,
) {
    val display: String get() = argv.joinToString(" ")
}

object RequestEnvironment {
    private val variable = Regex("\\{\\{\\s*([\\w]+)\\s*}}")

    fun prepare(
        text: String, caretLine: Int, file: Path, projectRoot: Path, selectedEnv: String?,
        globals: Map<String, String>, environment: Map<String, String>,
    ): PreparedRequest? {
        val request = RequestParser.parse(text, caretLine) ?: return null
        val directory = file.toAbsolutePath().parent
        val envName = Regex("(?m)^\\s*#\\s*env:\\s*(\\S+)").find(text)?.groupValues?.get(1) ?: selectedEnv
        val fileVars = if (envName == null) emptyMap() else {
            val path = expandedPath(envName)
            val found = listOf(directory.resolve(path), projectRoot.resolve(path)).firstOrNull(Files::isRegularFile)
            require(found != null) { "Env file not found: $envName" }
            readEnv(found)
        }
        val exported = fileVars + globals
        val vars = environment + exported
        fun substitute(value: String): String = variable.replace(value) {
            vars[it.groupValues[1]] ?: throw IllegalArgumentException("Variable {{${it.groupValues[1]}}} not found (env: ${envName ?: "none"})")
        }
        fun resolve(value: String): Path = directory.resolve(expandedPath(substitute(value))).normalize()
        fun read(value: String): String {
            val path = resolve(value)
            require(Files.isRegularFile(path) && Files.isReadable(path)) { "File not found or unreadable: $path" }
            return Files.readString(path)
        }
        val body = request.bodyFile?.let { path ->
            val value = read(path)
            if (request.substituteBodyFile) substitute(value) else value
        } ?: request.body?.let(::substitute)
        val handler = request.handlerFile?.let(::read)?.let(::substitute) ?: request.handler?.let(::substitute)
        val argv = if (request.shell != null) {
            require(!System.getProperty("os.name").startsWith("Windows")) { "Plain shell HTTPie commands require macOS or Linux" }
            listOf("/bin/sh", "-c", substitute(request.shell))
        } else request.argv.map(::substitute)
        return PreparedRequest(argv, directory, vars, body, handler, request.save?.let(::resolve))
    }

    private fun expandedPath(value: String): Path = Path.of(
        if (value.startsWith("~/")) System.getProperty("user.home") + value.substring(1) else value
    )

    private fun readEnv(path: Path): Map<String, String> = buildMap {
        Files.readAllLines(path).forEach { line ->
            val match = Regex("^\\s*(?:export\\s+)?([\\w]+)\\s*=\\s*(.*?)\\s*$").matchEntire(line) ?: return@forEach
            var value = match.groupValues[2]
            if (value.length >= 2 && ((value.first() == '"' && value.last() == '"') || (value.first() == '\'' && value.last() == '\''))) {
                value = value.substring(1, value.length - 1)
            }
            put(match.groupValues[1], value)
        }
    }
}
