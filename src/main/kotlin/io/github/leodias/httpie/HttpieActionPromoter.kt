package io.github.leodias.httpie

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

class HttpieActionPromoter : ActionPromoter {
    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction>? {
        val project = CommonDataKeys.PROJECT.getData(context) ?: return null
        if (CommonDataKeys.EDITOR.getData(context) == null || CommonDataKeys.VIRTUAL_FILE.getData(context)?.extension != "httpie" ||
            project.isDisposed || !TrustedProjects.isProjectTrusted(project)) return null
        return actions.filter { it is RunHttpieAction || it is SelectHttpieEnvironmentAction }.takeIf { it.isNotEmpty() }
    }
}
