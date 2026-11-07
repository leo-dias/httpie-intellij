package io.github.leodias.httpie

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.FormBuilder
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

class HttpieRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {
    var filePath = ""
    var requestIndex = 0
    var requestName = ""

    override fun getConfigurationEditor(): SettingsEditor<HttpieRunConfiguration> = HttpieRunConfigurationEditor(project)

    private fun path(): Path {
        val path = Path.of(filePath)
        return (if (path.isAbsolute) path else Path.of(project.basePath ?: ".").resolve(path)).toAbsolutePath().normalize()
    }

    internal fun requestKey(): HttpieRequestKey = HttpieRequestKey(path().toString(), requestName, if (requestName.isBlank()) requestIndex else -1)

    override fun checkConfiguration() {
        if (filePath.isBlank() || !filePath.endsWith(".httpie") || !runCatching { Files.isRegularFile(path()) && Files.isReadable(path()) }.getOrDefault(false))
            throw RuntimeConfigurationError("Choose an existing readable .httpie file")
        if (requestIndex < 0) throw RuntimeConfigurationError("Request number must be at least 1")
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = RunProfileState { _, _ ->
        if (!TrustedProjects.isProjectTrusted(project)) throw ExecutionException("Trust this project before running HTTPie requests")
        checkConfiguration()
        val sourcePath = path()
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath)
            ?: throw ExecutionException("HTTPie file not found")
        val selected = try {
            ReadAction.computeBlocking<Pair<String, RequestLocation>, RuntimeException> {
                val text = FileDocumentManager.getInstance().getDocument(file)?.text
                    ?: throw IllegalArgumentException("Could not read HTTPie file")
                text to RequestLocations.resolve(text, requestIndex, requestName)
            }
        } catch (e: IllegalArgumentException) { throw ExecutionException(e.message ?: "Invalid request selection", e) }
        val handler = HttpieRunProcessHandler()
        val console = HttpieResponseConsole(project, requestKey())
        val title = selected.second.title(sourcePath.fileName.toString())
        console.display(project, "// Running $title…", "txt")
        val completed: (RunResult?, Throwable?) -> Unit = { result, error ->
            try { console.showResult(title, result, error) } finally { handler.finish(result, error) }
        }
        handler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                val submitted = project.getService(HttpieSession::class.java).submit(selected.first, selected.second.line, sourcePath,
                    { handler.cancelled }, completed)
                if (!submitted) completed(null, IllegalStateException("Another HTTPie request is running, or the project is closed/untrusted"))
            }
        })
        DefaultExecutionResult(console, handler)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        filePath = element.getAttributeValue("httpieFile").orEmpty()
        requestIndex = element.getAttributeValue("httpieRequestIndex")?.toIntOrNull() ?: 0
        requestName = element.getAttributeValue("httpieRequestName").orEmpty()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("httpieFile", filePath)
        element.setAttribute("httpieRequestIndex", requestIndex.toString())
        element.setAttribute("httpieRequestName", requestName)
    }
}

private class HttpieRunConfigurationEditor(project: Project) : SettingsEditor<HttpieRunConfiguration>() {
    private val file = TextFieldWithBrowseButton()
    private val number = JSpinner(SpinnerNumberModel(1, 1, Int.MAX_VALUE, 1))
    private val name = JTextField()
    init { file.addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("httpie"), project)) }
    override fun createEditor(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("HTTPie file:", file)
        .addLabeledComponent("Request number (1-based):", number)
        .addLabeledComponent("Request name (optional, overrides number):", name)
        .addComponentFillVertically(JPanel(), 0).panel
    override fun resetEditorFrom(settings: HttpieRunConfiguration) {
        file.text = settings.filePath
        number.value = settings.requestIndex + 1
        name.text = settings.requestName
    }
    override fun applyEditorTo(settings: HttpieRunConfiguration) {
        settings.filePath = file.text.trim()
        settings.requestIndex = (number.value as Number).toInt() - 1
        settings.requestName = name.text.trim().removePrefix("###").trim()
    }
    override fun disposeEditor() { file.dispose() }
}
