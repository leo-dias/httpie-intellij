package io.github.leodias.httpie

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import java.nio.file.InvalidPathException
import java.nio.file.Path

class HttpieGotoDeclaration : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        if (source == null || source.language != HttpieLanguage) return null
        val key = RequestEnvironment.variable.findAll(source.text)
            .firstOrNull { offset - source.textOffset in it.range }?.groupValues?.get(1) ?: return null
        val file = source.containingFile.virtualFile?.takeIf { it.fileSystem is LocalFileSystem } ?: return null
        val project = source.project
        val directory = file.toNioPath().parent
        val name = RequestEnvironment.envName(editor.document.charsSequence,
            project.getService(HttpieSession::class.java).selectedEnvironment) ?: return null
        val path = try {
            RequestEnvironment.findEnv(name, directory, project.basePath?.let(Path::of) ?: directory)
        } catch (_: InvalidPathException) { null } ?: return null
        val env = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return null
        val document = FileDocumentManager.getInstance().getDocument(env) ?: return null
        val psi = PsiManager.getInstance(project).findFile(env) ?: return null
        // Later assignments overwrite earlier ones when HTTPie loads the environment.
        for (line in document.lineCount - 1 downTo 0) {
            val start = document.getLineStartOffset(line)
            val text = document.charsSequence.subSequence(start, document.getLineEndOffset(line))
            val group = RequestEnvironment.assignment.matchEntire(text)?.groups?.get(1) ?: continue
            if (group.value != key) continue
            val range = TextRange.from(start + group.range.first, key.length)
            return arrayOf(object : FakePsiElement() {
                override fun getParent() = psi
                override fun getName() = key
                override fun getTextOffset() = range.startOffset
                override fun getTextRange() = range
            })
        }
        return null
    }
}
