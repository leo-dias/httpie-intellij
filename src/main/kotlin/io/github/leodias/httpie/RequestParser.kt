package io.github.leodias.httpie

data class Request(
    val argv: List<String> = emptyList(),
    val shell: String? = null,
    val body: String? = null,
    val bodyFile: String? = null,
    val substituteBodyFile: Boolean = false,
    val handler: String? = null,
    val handlerFile: String? = null,
    val save: String? = null,
)

object RequestParser {
    private val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    fun parse(text: String, caretLine: Int): Request? {
        val lines = text.replace("\r\n", "\n").split('\n')
        val row = caretLine.coerceIn(0, lines.lastIndex)
        val first = lines.indices.lastOrNull { it <= row && lines[it].startsWith("###") }?.plus(1) ?: 0
        val last = lines.indices.firstOrNull { it > row && lines[it].startsWith("###") } ?: lines.size
        val block = lines.subList(first, last)
        var i = block.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") && !it.trimStart().startsWith("//") }
        if (i == -1) return null
        val words = block[i].trim().split(Regex("\\s+"))
        val isRest = words[0] in methods || words[0].matches(Regex("https?://.*"))
        if (!isRest) return shell(block)
        val method = if (words[0] in methods) words[0] else "GET"
        val url = if (words[0] in methods) words.getOrNull(1) else words[0]
        require(!url.isNullOrBlank()) { "Missing request URL" }
        val argv = mutableListOf("http", "--pretty=format", "--print=hb", method, url)
        i++
        while (i < block.size && block[i].isNotBlank() && !block[i].startsWith(">")) {
            val line = block[i++]
            if (line.trimStart().startsWith("#") || line.trimStart().startsWith("//")) continue
            val header = Regex("^\\s*([^:\\s]+):\\s*(.*?)\\s*$").matchEntire(line)
            require(header != null) { "Invalid header: $line (separate the body with a blank line)" }
            argv += "${header.groupValues[1]}:${header.groupValues[2]}"
        }
        if (i < block.size && block[i].isBlank()) i++
        val body = mutableListOf<String>()
        var handler: String? = null
        var handlerFile: String? = null
        var save: String? = null
        while (i < block.size) {
            val line = block[i++]
            when {
                line.startsWith(">>") -> {
                    save = line.removePrefix(">>").removePrefix("!").trim()
                    require(save.isNotEmpty()) { "Missing response output path" }
                }
                Regex("^>\\s*\\{%.*").matches(line) -> {
                    var part = line.substringAfter("{%")
                    val script = mutableListOf<String>()
                    while (!Regex("^(.*?)%}\\s*$").matches(part)) {
                        script += part
                        require(i < block.size) { "Unterminated response handler; expected %}" }
                        part = block[i++]
                    }
                    script += Regex("^(.*?)%}\\s*$").matchEntire(part)!!.groupValues[1]
                    handler = script.joinToString("\n").trimEnd('\n')
                }
                line.startsWith(">") -> {
                    handlerFile = line.removePrefix(">").trim()
                    require(handlerFile.isNotEmpty()) { "Missing response handler path" }
                }
                handler == null && handlerFile == null && save == null -> body += line
            }
        }
        val bodyText = body.dropWhile(String::isBlank).dropLastWhile(String::isBlank).joinToString("\n").ifEmpty { null }
        val fileMatch = bodyText?.let { Regex("^<(@?)\\s+(.+)$").matchEntire(it) }
        val bodyFile = fileMatch?.groupValues?.get(2)?.trim()
        if (bodyText == null) argv.add(1, "--ignore-stdin")
        return Request(argv, body = if (bodyFile == null) bodyText else null, bodyFile = bodyFile,
            substituteBodyFile = fileMatch?.groupValues?.get(1) == "@", handler = handler, handlerFile = handlerFile, save = save)
    }

    private fun shell(block: List<String>): Request {
        var command = block.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .joinToString(" ") { it.replace(Regex("\\s*\\\\\\s*$"), "") }.trim()
        if (!Regex("^https?(?:\\s|$)").containsMatchIn(command)) command = "http $command"
        val flags = mutableListOf("--ignore-stdin")
        if (!command.contains("--pretty")) flags += "--pretty=format"
        if (!Regex("(?:^|\\s)(?:--(?:print|verbose|headers|body)(?:[=\\s]|$)|-[pvhb])").containsMatchIn(command)) flags += "--print=hb"
        val executable = command.substringBefore(' ')
        command = "$executable ${flags.joinToString(" ")}${command.removePrefix(executable)}"
        return Request(shell = command)
    }
}
