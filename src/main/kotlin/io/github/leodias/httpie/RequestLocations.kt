package io.github.leodias.httpie

data class RequestLocation(val index: Int, val line: Int, val name: String?, val startLine: Int, val endLine: Int) {
    fun title(fileName: String): String = name ?: "$fileName (request ${index + 1})"
}

object RequestLocations {
    fun list(text: String): List<RequestLocation> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val separators = listOf(-1) + lines.indices.filter { lines[it].startsWith("###") }
        val requests = separators.mapIndexedNotNull { index, separator ->
            val end = separators.getOrNull(index + 1) ?: lines.size
            val line = (separator + 1 until end).firstOrNull {
                val value = lines[it].trimStart()
                value.isNotBlank() && !value.startsWith("#") && !value.startsWith("//")
            } ?: return@mapIndexedNotNull null
            val name = if (separator < 0) null else lines[separator].removePrefix("###").trim().ifEmpty { null }
            RequestLocation(0, line, name, separator.coerceAtLeast(0), end)
        }
        return requests.mapIndexed { index, request -> request.copy(index = index) }
    }

    fun at(text: String, caretLine: Int): RequestLocation? {
        val row = caretLine.coerceIn(0, text.count { it == '\n' })
        return list(text).firstOrNull { row >= it.startLine && row < it.endLine }
    }

    fun resolve(text: String, index: Int, name: String): RequestLocation {
        val requests = list(text)
        if (name.isNotBlank()) {
            val matching = requests.filter { it.name == name }
            require(matching.size == 1) {
                "Request '$name' is ${if (matching.isEmpty()) "missing" else "ambiguous"}. Choose a unique ### name or use a request number."
            }
            return matching.single()
        }
        return requireNotNull(requests.getOrNull(index)) { "Request number ${index + 1} does not exist" }
    }
}
