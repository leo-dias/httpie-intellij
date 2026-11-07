package io.github.leodias.httpie

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import java.io.OutputStream
import java.util.concurrent.CancellationException

class HttpieRunProcessHandler : ProcessHandler() {
    @Volatile var cancelled = false
        private set
    override fun destroyProcessImpl() { cancelled = true }
    override fun detachProcessImpl() { notifyProcessDetached() }
    override fun detachIsDefault(): Boolean = false
    override fun getProcessInput(): OutputStream? = null

    fun finish(result: RunResult?, error: Throwable?) {
        if (isProcessTerminated) return
        if (error != null) notifyTextAvailable("${error.message ?: "Request failed"}\n", ProcessOutputTypes.STDERR)
        if (result != null) {
            notifyTextAvailable("${result.response.head}\n\n${result.response.body}\n", ProcessOutputTypes.STDOUT)
            if (result.handlerReport.isNotEmpty()) notifyTextAvailable("${result.handlerReport}\n", ProcessOutputTypes.STDOUT)
            if (result.stderr.isNotEmpty()) notifyTextAvailable(result.stderr, ProcessOutputTypes.STDERR)
        }
        notifyProcessTerminated(when {
            cancelled || error is CancellationException -> 130
            error != null || result == null || result.failedTests > 0 -> 1
            else -> result.exitCode
        })
    }
}
