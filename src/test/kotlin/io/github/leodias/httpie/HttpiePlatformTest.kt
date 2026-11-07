package io.github.leodias.httpie

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.EditorNotificationProvider

class HttpiePlatformTest : LightPlatformTestCase() {
    fun testFileTypeAndEditorActionsAreRegistered() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("request.httpie")
        assertEquals("HTTPie", fileType.name)
        assertNotSame("HTTPie files need their own tree icon", AllIcons.FileTypes.Text, fileType.icon)
        assertNotNull("HTTPie file icon must be packaged", javaClass.getResource("/icons/httpieFile.svg"))
        assertNotNull(ActionManager.getInstance().getAction("HTTPie.Run"))
        assertNotNull(ActionManager.getInstance().getAction("HTTPie.SelectEnvironment"))
        assertNotNull(
            "HTTPie editors need the Environment selector",
            EditorNotificationProvider.EP_NAME.findExtension(HttpieEnvironmentNotificationProvider::class.java, project),
        )
        assertNotNull(javaClass.getResource("/handler.js"))
    }

    fun testProjectServiceKeepsStateInMemoryAndClearsItOnDisposal() {
        val session = HttpieSession(project)
        session.selectedEnvironment = "dev.env"
        session.applyGlobals(mapOf("token" to "secret", "remove" to "old"))
        session.applyGlobals(mapOf("remove" to null))
        assertEquals(mapOf("token" to "secret"), session.globalsSnapshot())
        assertFalse(HttpieSession(project).globalsSnapshot().containsKey("token"))
        Disposer.dispose(session)
        assertTrue(session.isDisposed)
        assertTrue(session.globalsSnapshot().isEmpty())
        assertNull(session.selectedEnvironment)
    }

    fun testDisposedSessionRefusesToStartNewRequests() {
        val session = HttpieSession(project)
        Disposer.dispose(session)
        assertFalse(session.submit("GET http://127.0.0.1", 0, java.nio.file.Path.of("/unused.httpie")))
    }
}
