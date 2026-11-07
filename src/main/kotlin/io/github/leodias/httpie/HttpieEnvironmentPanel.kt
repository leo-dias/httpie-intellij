package io.github.leodias.httpie

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.JBColor
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function
import javax.swing.JComponent

internal const val NO_ENVIRONMENT = "(none)"

/**
 * Single source of truth for environment files and the selected environment, shared by the
 * editor combobox and [SelectHttpieEnvironmentAction] so both stay in sync.
 */
internal object HttpieEnvironments {
    fun discover(project: Project, file: VirtualFile): List<String> = runCatching {
        val directories = listOfNotNull(Path.of(file.path).parent, project.basePath?.let(Path::of)).distinct()
        directories.flatMap { directory ->
            if (!Files.isDirectory(directory)) emptyList() else Files.list(directory).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".env") }
                    .map { it.fileName.toString() }.toList()
            }
        }.distinct().sorted()
    }.getOrDefault(emptyList())

    fun selected(project: Project, file: VirtualFile): String? =
        project.getService(HttpieSession::class.java).selectedEnvironment?.takeIf { it in discover(project, file) }

    fun select(project: Project, environment: String?) {
        project.getService(HttpieSession::class.java).selectedEnvironment = environment
    }

    fun chooserGroup(project: Project, file: VirtualFile): DefaultActionGroup =
        DefaultActionGroup().apply {
            add(ChooseEnvironmentAction(project, null))
            discover(project, file).forEach { add(ChooseEnvironmentAction(project, it)) }
        }
}

internal class ChooseEnvironmentAction(private val project: Project, private val environment: String?) :
    ToggleAction(environment ?: NO_ENVIRONMENT) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
        project.getService(HttpieSession::class.java).selectedEnvironment == environment

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        // Radio semantics: clicking the current environment keeps it, clicking (none) clears.
        if (state) HttpieEnvironments.select(project, environment)
    }
}

class HttpieEnvironmentNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<FileEditor, JComponent>? =
        if (file.extension == "httpie" && file.isInLocalFileSystem) Function { HttpieEnvironmentPanel(project, file) } else null
}

internal class HttpieEnvironmentPanel(project: Project, file: VirtualFile) : EditorNotificationPanel() {
    private val chooser = EnvironmentChooserAction(project, file)
    private val toolbar = ActionManager.getInstance()
        .createActionToolbar("HTTPie.NotificationPanel", DefaultActionGroup(chooser), true)

    init {
        removeAll()
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.CENTER)
        putClientProperty(FileEditorManager.SEPARATOR_DISABLED, true)
        background = PANEL_BACKGROUND
        toolbar.component.background = PANEL_BACKGROUND
        border = CustomLineBorder(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 0, 1, 0)
    }

    internal fun environments(): List<String> = chooser.environments()
    internal fun selectedEnvironment(): String? = chooser.selectedName()
    internal fun selectEnvironment(name: String?) = chooser.choose(name)
    internal fun label(): String = chooser.label()

    private companion object {
        val PANEL_BACKGROUND: JBColor = JBColor.namedColor("Editor.SearchField.background", JBColor.background())
    }
}

internal class EnvironmentChooserAction(private val project: Project, private val file: VirtualFile) : ComboBoxAction() {
    init {
        setSmallVariant(true)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = label()
    }

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup =
        HttpieEnvironments.chooserGroup(project, file)

    internal fun label(): String = "Environment: ${selectedName() ?: NO_ENVIRONMENT}"

    internal fun environments(): List<String> = HttpieEnvironments.discover(project, file)

    internal fun selectedName(): String? = HttpieEnvironments.selected(project, file)

    internal fun choose(name: String?) = HttpieEnvironments.select(project, name)
}
