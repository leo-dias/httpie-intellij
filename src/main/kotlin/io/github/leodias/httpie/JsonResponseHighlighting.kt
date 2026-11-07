package io.github.leodias.httpie

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Font

internal object JsonResponseHighlighting {
    private fun color(light: Int, dark: Int): TextAttributes = TextAttributes(JBColor(light, dark), null, null, null, Font.PLAIN)

    private val key = color(0x005CC5, 0x82AAFF)
    private val string = color(0x287A1F, 0xC3E88D)
    private val number = color(0xA34D00, 0xF78C6C)
    private val boolean = color(0x7B2CBF, 0xC792EA)
    private val nil = color(0xC21F4D, 0xFF5370)
    private val brackets = listOf(
        color(0x8A5700, 0xFFCB6B), color(0x006D77, 0x89DDFF),
        color(0x7B2CBF, 0xC792EA), color(0x287A1F, 0xC3E88D),
        color(0xA34D00, 0xF78C6C), color(0x005CC5, 0x82AAFF),
    )

    fun apply(editor: EditorEx): List<RangeHighlighter> = buildList {
        val text = editor.document.immutableCharSequence
        val tokens = editor.highlighter.createIterator(0)
        var depth = 0
        while (!tokens.atEnd()) {
            val start = tokens.start
            var end = tokens.end
            val token = text.subSequence(start, end).toString()
            if (token.startsWith('"')) {
                // Native highlighting splits escape sequences; color the complete quoted string together.
                end = start + 1
                while (end < text.length) {
                    val char = text[end++]
                    if (char == '\\' && end < text.length) end++
                    else if (char == '"') break
                    else if (char == '\n' || char == '\r') { end--; break }
                }
            }
            val attributes = when {
                token == "{" || token == "[" -> brackets[depth++ % brackets.size]
                token == "}" || token == "]" -> { depth = (depth - 1).coerceAtLeast(0); brackets[depth % brackets.size] }
                token.startsWith('"') -> {
                    var next = end
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') key else string
                }
                token == "true" || token == "false" -> boolean
                token == "null" -> nil
                token.firstOrNull()?.let { it == '-' || it.isDigit() } == true -> number
                else -> null
            }
            if (attributes != null) add(editor.markupModel.addRangeHighlighter(
                start, end, HighlighterLayer.SYNTAX + 1, attributes, HighlighterTargetArea.EXACT_RANGE
            ))
            do { tokens.advance() } while (!tokens.atEnd() && tokens.start < end)
        }
    }
}
