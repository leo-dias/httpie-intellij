package io.github.leodias.httpie

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.popup.JBPopupFactory

class RunHttpieAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && e.getData(CommonDataKeys.EDITOR) != null &&
            e.getData(CommonDataKeys.VIRTUAL_FILE)?.extension == "httpie" && TrustedProjects.isProjectTrusted(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!TrustedProjects.isProjectTrusted(project)) return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        run(project, editor.document, editor.caretModel.logicalPosition.line, file)
    }

    companion object {
        fun run(project: Project, document: Document, caretLine: Int, file: VirtualFile) {
            if (project.isDisposed || !TrustedProjects.isProjectTrusted(project) || file.extension != "httpie" || !file.isInLocalFileSystem) return
            val text = document.text
            val settings = HttpieRunConfigurations.remember(project, file, text, caretLine)
            if (settings == null) { notifyHttpie(project, "No request found under the caret"); return }
            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
    }
}

class SelectHttpieEnvironmentAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            e.getData(CommonDataKeys.VIRTUAL_FILE)?.extension == "httpie" && TrustedProjects.isProjectTrusted(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!TrustedProjects.isProjectTrusted(project)) return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Select Environment",
                HttpieEnvironments.chooserGroup(project, file),
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false,
            )
            .showInBestPositionFor(e.dataContext)
    }
}

private fun notifyHttpie(project: Project, message: String) {
    NotificationGroupManager.getInstance().getNotificationGroup("HTTPie").createNotification(message, NotificationType.INFORMATION).notify(project)
}
