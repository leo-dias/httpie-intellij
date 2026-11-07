package io.github.leodias.httpie

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType

class HttpieLineMarkerProvider : LineMarkerProvider, DumbAware {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.node.elementType != HttpieTokens.METHOD && element.node.elementType != HttpieTokens.URL) return null
        val project = element.project
        if (!TrustedProjects.isProjectTrusted(project)) return null
        var previous = element.prevSibling
        while (previous != null) {
            when (previous.node.elementType) {
                HttpieTokens.COMMENT -> if (previous.text.trimStart().startsWith("###")) break
                TokenType.WHITE_SPACE -> Unit
                else -> return null
            }
            previous = previous.prevSibling
        }
        return LineMarkerInfo(element, element.textRange, AllIcons.Actions.Execute, { "Run HTTPie request" },
            { _, target ->
                val file = target.containingFile
                val document = PsiDocumentManager.getInstance(project).getDocument(file)
                if (document != null) RunHttpieAction.run(project, document, document.getLineNumber(target.textRange.startOffset), file.virtualFile)
            }, GutterIconRenderer.Alignment.RIGHT, { "Run HTTPie request" })
    }
}
