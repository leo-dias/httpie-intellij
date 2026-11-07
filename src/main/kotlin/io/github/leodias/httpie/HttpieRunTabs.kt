package io.github.leodias.httpie

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentDescriptorReusePolicy

internal data class HttpieRequestKey(val filePath: String, val name: String, val index: Int)

internal object HttpieRunTabs {
    fun findReusable(descriptors: Collection<RunContentDescriptor>, key: HttpieRequestKey): RunContentDescriptor? = descriptors.firstOrNull {
        val console = it.executionConsole as? HttpieResponseConsole
        console != null && !console.isDisposed && console.requestKey == key && it.processHandler?.isProcessTerminated != false
    }

    fun protect(descriptor: RunContentDescriptor) {
        val key = (descriptor.executionConsole as? HttpieResponseConsole)?.requestKey ?: return
        descriptor.reusePolicy = object : RunContentDescriptorReusePolicy() {
            override fun canBeReusedBy(newDescriptor: RunContentDescriptor): Boolean =
                (newDescriptor.executionConsole as? HttpieResponseConsole)?.requestKey == key
        }
    }
}
