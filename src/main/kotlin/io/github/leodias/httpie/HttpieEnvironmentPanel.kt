package io.github.leodias.httpie

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.components.JBLabel
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

internal const val NO_ENVIRONMENT = "(none)"

class HttpieEnvironmentNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<FileEditor, JComponent>? =
        if (file.extension == "httpie" && file.isInLocalFileSystem) Function { HttpieEnvironmentPanel(project, file) } else null
}

internal class HttpieEnvironmentPanel(private val project: Project, private val file: VirtualFile) : EditorNotificationPanel() {
    private val session = project.getService(HttpieSession::class.java)
    private val model = DefaultComboBoxModel<String>()
    internal val environmentCombo = JComboBox(model)
    private var loading = false

    init {
        myLinksPanel.add(JBLabel("Environment:"))
        myLinksPanel.add(environmentCombo)
        environmentCombo.toolTipText =
            "Environment file for requests in this file. A # env: directive in the request file takes precedence."
        environmentCombo.addActionListener {
            if (!loading) session.selectedEnvironment =
                environmentCombo.selectedItem?.toString()?.takeUnless { it == NO_ENVIRONMENT }
        }
        environmentCombo.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = refresh()
            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = Unit
            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
        })
        refresh()
    }

    private fun refresh() {
        loading = true
        try {
            val environments = environments()
            model.removeAllElements()
            model.addElement(NO_ENVIRONMENT)
            environments.forEach(model::addElement)
            model.selectedItem = session.selectedEnvironment?.takeIf { it in environments } ?: NO_ENVIRONMENT
        } finally {
            loading = false
        }
    }

    private fun environments(): List<String> = runCatching {
        val directories = listOfNotNull(Path.of(file.path).parent, project.basePath?.let(Path::of)).distinct()
        directories.flatMap { directory ->
            if (!Files.isDirectory(directory)) emptyList() else Files.list(directory).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".env") }
                    .map { it.fileName.toString() }.toList()
            }
        }.distinct().sorted()
    }.getOrDefault(emptyList())
}
