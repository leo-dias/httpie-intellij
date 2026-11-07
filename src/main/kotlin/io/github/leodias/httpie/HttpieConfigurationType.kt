package io.github.leodias.httpie

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.executeState
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import javax.swing.Icon

class HttpieConfigurationType : ConfigurationType, DumbAware {
    override fun getId(): String = "HTTPie"
    override fun getDisplayName(): String = "HTTPie"
    override fun getConfigurationTypeDescription(): String = "Run a specific request from an HTTPie file"
    override fun getIcon(): Icon = AllIcons.Actions.Execute
    private val factory = object : ConfigurationFactory(this) {
        override fun getId(): String = "HTTPieRequest"
        override fun createTemplateConfiguration(project: Project): RunConfiguration = HttpieRunConfiguration(project, this, "HTTPie")
    }
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

class HttpieProgramRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "HTTPieRunner"
    override fun canRun(executorId: String, profile: RunProfile): Boolean = executorId == DefaultRunExecutor.EXECUTOR_ID && profile is HttpieRunConfiguration
    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        val key = (environment.runProfile as HttpieRunConfiguration).requestKey()
        environment.contentToReuse = HttpieRunTabs.findReusable(RunContentManager.getInstance(environment.project).allDescriptors, key)
        return resolvedPromise(executeState(state, environment, this)?.also(HttpieRunTabs::protect))
    }
}
