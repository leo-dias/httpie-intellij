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

    fun testComboboxDefaultsToNoneListsEnvironmentsAndControlsSession() {
        val combo = panel().environmentCombo
        assertEquals(3, combo.itemCount)
        assertEquals(NO_ENVIRONMENT, combo.getItemAt(0))
        assertEquals(NO_ENVIRONMENT, combo.selectedItem)
        assertEquals(listOf("dev.env", "prod.env"), (1 until combo.itemCount).map { combo.getItemAt(it) })

        combo.selectedItem = "dev.env"
        assertEquals("dev.env", project.getService(HttpieSession::class.java).selectedEnvironment)

        combo.selectedItem = NO_ENVIRONMENT
        assertNull(project.getService(HttpieSession::class.java).selectedEnvironment)
    }

    fun testComboboxRestoresSelectionAndFallsBackToNoneWhenMissing() {
        val session = project.getService(HttpieSession::class.java)
        session.selectedEnvironment = "prod.env"
        assertEquals("prod.env", panel().environmentCombo.selectedItem)

        session.selectedEnvironment = "missing.env"
        assertEquals(NO_ENVIRONMENT, panel().environmentCombo.selectedItem)
    }

    private fun panel(): HttpieEnvironmentPanel = HttpieEnvironmentPanel(project, request)
}
