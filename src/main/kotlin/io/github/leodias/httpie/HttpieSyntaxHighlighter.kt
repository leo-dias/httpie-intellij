package io.github.leodias.httpie

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object HttpieTokens {
    val COMMENT = IElementType("COMMENT", HttpieLanguage)
    val METHOD = IElementType("METHOD", HttpieLanguage)
    val URL = IElementType("URL", HttpieLanguage)
    val HEADER = IElementType("HEADER", HttpieLanguage)
    val VARIABLE = IElementType("VARIABLE", HttpieLanguage)
    val STRING = IElementType("STRING", HttpieLanguage)
    val NUMBER = IElementType("NUMBER", HttpieLanguage)
    val DIRECTIVE = IElementType("DIRECTIVE", HttpieLanguage)
    val TEXT = IElementType("TEXT", HttpieLanguage)
}

class HttpieLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var end = 0
    private var start = 0
    private var next = 0
    private var type: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        end = endOffset
        start = startOffset
        locate()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = type
    override fun getTokenStart(): Int = start
    override fun getTokenEnd(): Int = next
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = end
    override fun advance() { start = next; locate() }

    private fun locate() {
        type = null
        next = end
        if (start >= end) return
        val match = pattern.find(buffer, start)
        if (match == null || match.range.first > start) {
            next = minOf(match?.range?.first ?: end, end)
            type = HttpieTokens.TEXT
            return
        }
        next = minOf(match.range.last + 1, end)
        type = kinds.first { (name, _) -> match.groups[name] != null }.second
    }

    companion object {
        private val kinds = listOf("comment" to HttpieTokens.COMMENT, "method" to HttpieTokens.METHOD,
            "variable" to HttpieTokens.VARIABLE, "url" to HttpieTokens.URL, "header" to HttpieTokens.HEADER,
            "string" to HttpieTokens.STRING, "number" to HttpieTokens.NUMBER,
            "directive" to HttpieTokens.DIRECTIVE, "space" to TokenType.WHITE_SPACE)
        private val pattern = Regex(
            "(?m)(?<comment>^[ \\t]*(?:#|//)[^\\r\\n]*)" +
                "|(?<method>^[ \\t]*(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|http|https)(?=[ \\t]|$))" +
                "|(?<variable>\\{\\{[^}\\r\\n]*}})" +
                "|(?<url>https?://[^\\s{}\"']+|:[0-9]+[^\\s{}\"']*)" +
                "|(?<header>^[ \\t]*[A-Za-z_][\\w-]*(?=[ \\t]*:))" +
                "|(?<string>\"(?:\\\\.|[^\"\\\\\\r\\n])*+\"|'(?:\\\\.|[^'\\\\\\r\\n])*+')" +
                "|(?<number>\\b[0-9]+(?:\\.[0-9]+)?\\b)" +
                "|(?<directive>^[ \\t]*(?:>>!?|>\\s*\\{%|>|<@?)|%})" +
                "|(?<space>\\s)"
        )
    }
}

class HttpieSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = HttpieLexer()
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        HttpieTokens.COMMENT -> arrayOf(DefaultLanguageHighlighterColors.LINE_COMMENT)
        HttpieTokens.METHOD, HttpieTokens.DIRECTIVE -> arrayOf(DefaultLanguageHighlighterColors.KEYWORD)
        HttpieTokens.URL, HttpieTokens.STRING -> arrayOf(DefaultLanguageHighlighterColors.STRING)
        HttpieTokens.HEADER -> arrayOf(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        HttpieTokens.VARIABLE -> arrayOf(DefaultLanguageHighlighterColors.CONSTANT)
        HttpieTokens.NUMBER -> arrayOf(DefaultLanguageHighlighterColors.NUMBER)
        else -> emptyArray()
    }
}

class HttpieSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = HttpieSyntaxHighlighter()
}
