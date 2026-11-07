package io.github.leodias.httpie

import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JPanel

internal class HttpieResponseConsole(private val project: Project, val requestKey: HttpieRequestKey) : JPanel(BorderLayout()), ExecutionConsole {
    private val editor = EditorFactory.getInstance().createViewer(EditorFactory.getInstance().createDocument(""), project)
    private var jsonHighlights = emptyList<RangeHighlighter>()
    @Volatile var isDisposed = false
        private set

    init {
        getAccessibleContext().accessibleName = "HTTPie response"
        editor.contentComponent.accessibleContext.accessibleName = "HTTPie response text"
        editor.settings.isLineNumbersShown = false
        add(editor.component, BorderLayout.CENTER)
    }

    fun display(project: Project, text: String, extension: String) {
        if (isDisposed || project.isDisposed) return
        jsonHighlights.forEach { editor.markupModel.removeHighlighter(it) }
        jsonHighlights = emptyList()
        (editor as? EditorEx)?.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
            project, FileTypeManager.getInstance().getFileTypeByFileName("response.$extension")
        )
        WriteAction.run<RuntimeException> { editor.document.setText(text) }
        if ((extension == "jsonc" || extension == "json") && editor is EditorEx) {
            jsonHighlights = JsonResponseHighlighting.apply(editor)
        }
        editor.caretModel.moveToOffset(0)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun showResult(title: String, result: RunResult?, error: Throwable?) {
        if (isDisposed || project.isDisposed) return
        if (!ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().invokeLater { showResult(title, result, error) }
            return
        }
        if (error != null) {
            display(project, if (error is CancellationException) "Request cancelled" else "HTTPie error: ${error.message}", "txt")
            return
        }
        if (result == null) return
        val response = result.response
        val text = buildString {
            appendLine("// $title (${result.durationMillis} ms, exit ${result.exitCode})")
            if (result.command.isNotEmpty()) appendLine("// $ ${result.command}")
            if (result.handlerReport.isNotEmpty()) result.handlerReport.lineSequence().forEach { appendLine("// $it") }
            if (response.head.isNotEmpty()) response.head.lineSequence().forEach { appendLine("// $it") }
            appendLine()
            append(response.body)
            if (result.stderr.isNotBlank()) append("\n${result.stderr}")
            if (result.exitCode != 0) append("\nHTTPie exited with ${result.exitCode}\n")
            if (result.failedTests > 0) append("\n// ${result.failedTests} handler check(s) failed\n")
            result.savedFile?.let { append("\n// Saved response to $it\n") }
        }
        val body = response.body.trimStart()
        val extension = when { body.startsWith("{") || body.startsWith("[") -> "jsonc"; body.startsWith("<") -> "xml"; else -> "txt" }
        display(project, text, extension)
    }

    override fun getComponent(): JComponent = this
    override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent
    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
