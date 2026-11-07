package io.github.leodias.httpie

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class HttpieParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = HttpieLexer()
    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        // Flat PSI is sufficient for request-line gutter actions; request semantics live in RequestParser.
        val file = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        file.done(root)
        builder.treeBuilt
    }
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = TokenSet.create(HttpieTokens.COMMENT)
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(HttpieTokens.STRING)
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = object : PsiFileBase(viewProvider, HttpieLanguage) {
        override fun getFileType(): FileType = HttpieFileType
    }

    companion object { private val FILE = IFileElementType(HttpieLanguage) }
}
