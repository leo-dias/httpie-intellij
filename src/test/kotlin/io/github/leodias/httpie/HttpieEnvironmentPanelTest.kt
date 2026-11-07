package io.github.leodias.httpie

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class HttpieEnvironmentPanelTest : BasePlatformTestCase() {
    private lateinit var directory: Path
    private lateinit var request: VirtualFile

    override fun setUp() {
        super.setUp()
        directory = Files.createTempDirectory("httpie-env").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
        Files.writeString(directory.resolve("test.httpie"), "GET {{url}}")
        Files.writeString(directory.resolve("dev.env"), "url=http://localhost")
        Files.writeString(directory.resolve("prod.env"), "url=http://example.com")
        request = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory.resolve("test.httpie"))!!
    }

    override fun tearDown() {
        try {
            directory.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testProviderOnlyHandlesHttpieFiles() {
        val provider = HttpieEnvironmentNotificationProvider()
        assertNotNull(provider.collectNotificationData(project, request))
        assertNull(provider.collectNotificationData(project, myFixture.configureByText("notes.txt", "plain").virtualFile))
    }

    fun testChooserDefaultsToNoneListsEnvironmentsAndControlsSession() {
        val panel = panel()
        assertEquals("Environment: $NO_ENVIRONMENT", panel.label())
        assertEquals(listOf("dev.env", "prod.env"), panel.environments())
        assertNull(panel.selectedEnvironment())

        panel.selectEnvironment("dev.env")
        assertEquals("dev.env", project.getService(HttpieSession::class.java).selectedEnvironment)
        assertEquals("Environment: dev.env", panel.label())

        panel.selectEnvironment(null)
        assertNull(project.getService(HttpieSession::class.java).selectedEnvironment)
        assertEquals("Environment: $NO_ENVIRONMENT", panel.label())
    }

    fun testChooserRestoresSelectionAndFallsBackToNoneWhenMissing() {
        val session = project.getService(HttpieSession::class.java)
        session.selectedEnvironment = "prod.env"
        assertEquals("prod.env", panel().selectedEnvironment())

        session.selectedEnvironment = "missing.env"
        assertNull(panel().selectedEnvironment())
        assertEquals("Environment: $NO_ENVIRONMENT", panel().label())
    }

    private fun panel(): HttpieEnvironmentPanel = HttpieEnvironmentPanel(project, request)
}
