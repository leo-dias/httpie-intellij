package io.github.leodias.httpie

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class HttpieSession(private val project: Project) : Disposable {
    @Volatile var selectedEnvironment: String? = null
    @Volatile var isDisposed: Boolean = false
        private set
    @Volatile private var active: ProgressIndicator? = null
    private val running = AtomicBoolean(false)
    private val globals = linkedMapOf<String, String>()

    @Synchronized fun globalsSnapshot(): Map<String, String> = globals.toMap()

    @Synchronized fun applyGlobals(updates: Map<String, String?>) {
        if (isDisposed) return
        updates.forEach { (key, value) -> if (value == null) globals.remove(key) else globals[key] = value }
    }

    fun submit(text: String, caretLine: Int, file: Path, cancelledExternally: () -> Boolean = { false },
               completed: (RunResult?, Throwable?) -> Unit = { _, _ -> }): Boolean {
        if (isDisposed || project.isDisposed || !TrustedProjects.isProjectTrusted(project) || !running.compareAndSet(false, true)) return false
        val selected = selectedEnvironment
        val snapshot = globalsSnapshot()
        val root = project.basePath?.let(Path::of) ?: file.toAbsolutePath().parent
        object : Task.Backgroundable(project, "HTTPie request", true) {
            private var result: RunResult? = null
            private var cancelled = false

            override fun run(indicator: ProgressIndicator) {
                active = indicator
                try {
                    if (isDisposed || project.isDisposed || !TrustedProjects.isProjectTrusted(project)) {
                        cancelled = true
                        return
                    }
                    val request = RequestEnvironment.prepare(text, caretLine, file, root, selected, snapshot, EnvironmentUtil.getEnvironmentMap())
                        ?: throw IllegalArgumentException("No request found under the caret")
                    result = HttpieRunner().run(request, snapshot) { isDisposed || project.isDisposed || indicator.isCanceled || cancelledExternally() }
                    applyGlobals(result!!.globalUpdates)
                } catch (_: CancellationException) {
                    cancelled = true
                    indicator.cancel()
                } finally {
                    active = null
                }
            }

            override fun onSuccess() {
                running.set(false)
                completed(result, if (cancelled) CancellationException("Request cancelled") else null)
            }

            override fun onCancel() {
                running.set(false)
                completed(null, CancellationException("Request cancelled"))
            }

            override fun onThrowable(error: Throwable) {
                running.set(false)
                completed(null, error)
            }
        }.queue()
        return true
    }

    @Synchronized override fun dispose() {
        isDisposed = true
        active?.cancel()
        globals.clear()
        selectedEnvironment = null
    }
}
