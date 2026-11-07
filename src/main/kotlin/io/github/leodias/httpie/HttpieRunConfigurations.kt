package io.github.leodias.httpie

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object HttpieRunConfigurations {
    fun remember(project: Project, file: VirtualFile, text: String, caretLine: Int): RunnerAndConfigurationSettings? {
        val location = RequestLocations.at(text, caretLine) ?: return null
        val label = location.name?.takeIf { name -> RequestLocations.list(text).count { it.name == name } == 1 }.orEmpty()
        val manager = RunManager.getInstance(project)
        val existing = manager.allSettings.firstOrNull {
            val config = it.configuration as? HttpieRunConfiguration
            config != null && config.filePath == file.path && config.requestName == label && (label.isNotEmpty() || config.requestIndex == location.index)
        }
        val settings = existing ?: run {
            val type = ConfigurationTypeUtil.findConfigurationType(HttpieConfigurationType::class.java)
            manager.createConfiguration(manager.suggestUniqueName("${location.title(file.name)} (${file.name})", type), type.configurationFactories.single())
        }
        (settings.configuration as HttpieRunConfiguration).apply {
            filePath = file.path
            requestIndex = location.index
            requestName = label
        }
        if (existing == null) manager.addConfiguration(settings)
        manager.selectedConfiguration = settings
        return settings
    }
}
