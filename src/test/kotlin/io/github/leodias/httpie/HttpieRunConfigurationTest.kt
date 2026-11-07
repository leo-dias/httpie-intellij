package io.github.leodias.httpie

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpServer
import org.jdom.Element
import org.jdom.output.XMLOutputter
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertThrows
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpieRunConfigurationTest : BasePlatformTestCase() {
    private lateinit var directory: Path

    override fun setUp() {
        super.setUp()
        directory = Files.createTempDirectory("httpie-config").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
    }

    override fun tearDown() {
        try {
            val manager = RunManager.getInstance(project)
            manager.allSettings.filter { (it.configuration as? HttpieRunConfiguration)?.filePath?.startsWith(directory.toString()) == true }
                .forEach(manager::removeConfiguration)
            Files.deleteIfExists(directory.resolve("requests.httpie"))
            Files.deleteIfExists(directory)
        } finally { super.tearDown() }
    }

    private fun file(text: String): VirtualFile {
        val path = directory.resolve("requests.httpie")
        Files.writeString(path, text)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    }

    fun testTypeIsAvailableAndRepeatedRunsReuseConfiguration() {
        val type = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.single { it.id == "HTTPie" }
        assertEquals("HTTPie", type.displayName)
        val text = "### Anything 1\nGET :8081/1\n### Anything 2\nGET :8081/2"
        val file = file(text)
        val first = HttpieRunConfigurations.remember(project, file, text, 3)!!
        val second = HttpieRunConfigurations.remember(project, file, text, 2)!!
        assertSame(first, second)
        assertEquals("Anything 2 (requests.httpie)", first.name)
        assertSame(first, RunManager.getInstance(project).selectedConfiguration)
        assertEquals(1, RunManager.getInstance(project).allSettings.count { (it.configuration as? HttpieRunConfiguration)?.filePath == file.path })
        val editor = first.configuration.configurationEditor
        try { assertNotNull(editor.component) } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
    }

    fun testSavedConfigurationContainsSelectionNotRequestBodyOrCredentials() {
        val text = "### Create\nPOST :8081\nAuthorization: Bearer secret-token\n\n{\"password\":\"secret-password\"}"
        val file = file(text)
        val settings = HttpieRunConfigurations.remember(project, file, text, 5)!!
        val config = settings.configuration as HttpieRunConfiguration
        val xml = Element("configuration")
        config.writeExternal(xml)
        val saved = XMLOutputter().outputString(xml)
        assertFalse(saved.contains("secret-token"))
        assertFalse(saved.contains("secret-password"))
        val clone = config.factory!!.createTemplateConfiguration(project) as HttpieRunConfiguration
        clone.readExternal(xml)
        assertEquals(file.path, clone.filePath)
        assertEquals("Create", clone.requestName)
        assertEquals(0, clone.requestIndex)
        val independentClone = config.clone() as HttpieRunConfiguration
        assertEquals(config.filePath, independentClone.filePath)
        independentClone.requestName = "Different"
        assertEquals("Create", config.requestName)
        clone.checkConfiguration()
        clone.filePath = ""
        assertThrows(RuntimeConfigurationError::class.java) { clone.checkConfiguration() }
    }

    fun testNativeRunExecutesOnlySavedRequestWithoutAnActiveEditor() {
        assumeTrue("HTTPie required", runCatching { ProcessBuilder("http", "--version").start().waitFor() == 0 }.getOrDefault(false))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requested = AtomicReference<String>()
        val count = AtomicInteger()
        server.createContext("/") { exchange ->
            count.incrementAndGet()
            requested.set(exchange.requestURI.path)
            val bytes = "{\"ok\":true}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val port = server.address.port
            val text = "### First\nGET :$port/first\n### Second\nGET :$port/second"
            val file = file(text)
            val settings = HttpieRunConfigurations.remember(project, file, text, 3)!!
            val config = settings.configuration as HttpieRunConfiguration
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            WriteAction.run<RuntimeException> { document.setText("### Second\nGET :$port/second\n### First\nGET :$port/first") }
            FileDocumentManager.getInstance().saveDocument(document)
            val executor = DefaultRunExecutor.getRunExecutorInstance()
            val runner = ProgramRunner.getRunner(executor.id, config)!!
            val environment = ExecutionEnvironmentBuilder.create(executor, settings).build()
            val execution = config.getState(executor, environment)!!.execute(executor, runner)!!
            assertTrue("Run must contain the rich response viewer, not a second HTTPie window", execution.executionConsole is HttpieResponseConsole)
            val process = execution.processHandler!!
            process.startNotify()
            val deadline = System.nanoTime() + 10_000_000_000
            while (!process.isProcessTerminated && System.nanoTime() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
            assertTrue("Native run must complete", process.isProcessTerminated)
            assertEquals(0, process.exitCode)
            assertEquals("/second", requested.get())
            assertEquals("A saved configuration must execute exactly one request", 1, count.get())
            val console = execution.executionConsole as HttpieResponseConsole
            val editor = EditorFactory.getInstance().allEditors.single { it.isViewer && console.component.isAncestorOf(it.component) }
            assertTrue(editor.document.text.startsWith("// Second"))
            assertTrue("JSON body must keep pretty indentation in Run", Regex("\\n\\s+\"ok\"").containsMatchIn(editor.document.text))
            assertTrue("Run console must contain rich JSON coloring", editor.markupModel.allHighlighters.isNotEmpty())
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
            execution.executionConsole?.let(com.intellij.openapi.util.Disposer::dispose)
        } finally { server.stop(0) }
    }

    fun testNativeStopCancelsRunningHttpieProcess() {
        assumeTrue("HTTPie required", runCatching { ProcessBuilder("http", "--version").start().waitFor() == 0 }.getOrDefault(false))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/") { exchange ->
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        var console: com.intellij.execution.ui.ExecutionConsole? = null
        try {
            val text = "### Slow\nGET :${server.address.port}/slow"
            val file = file(text)
            val settings = HttpieRunConfigurations.remember(project, file, text, 1)!!
            val config = settings.configuration as HttpieRunConfiguration
            val executor = DefaultRunExecutor.getRunExecutorInstance()
            val environment = ExecutionEnvironmentBuilder.create(executor, settings).build()
            val result = config.getState(executor, environment).execute(executor, ProgramRunner.getRunner(executor.id, config)!!)!!
            console = result.executionConsole
            val process = result.processHandler!!
            // Headless background tasks can run synchronously; send Stop from a separate thread.
            val stopper = Thread.ofVirtual().start {
                if (entered.await(5, TimeUnit.SECONDS)) process.destroyProcess()
            }
            process.startNotify()
            val waitForStart = System.nanoTime() + 5_000_000_000
            while (entered.count != 0L && System.nanoTime() < waitForStart) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
            assertEquals("HTTPie must have started before Stop", 0L, entered.count)
            val waitForStop = System.nanoTime() + 3_000_000_000
            while (!process.isProcessTerminated && System.nanoTime() < waitForStop) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
            assertTrue("Native Stop must promptly terminate the request", process.isProcessTerminated)
            assertEquals(130, process.exitCode)
            stopper.join(1000)
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
        } finally {
            release.countDown()
            server.stop(0)
            console?.let(com.intellij.openapi.util.Disposer::dispose)
        }
    }

    fun testConfigurationCanResolveAFileNotPreviouslyOpenedInEditor() {
        val path = directory.resolve("requests.httpie")
        Files.writeString(path, "### Unopened\nGET :8081")
        val type = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.single { it.id == "HTTPie" }
        val settings = RunManager.getInstance(project).createConfiguration("Unopened", type.configurationFactories.single())
        val config = settings.configuration as HttpieRunConfiguration
        config.filePath = path.toString()
        config.requestName = "Unopened"
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = ExecutionEnvironmentBuilder.create(executor, settings).build()
        val result = config.getState(executor, environment).execute(executor, ProgramRunner.getRunner(executor.id, config)!!)!!
        assertNotNull(result.processHandler)
        result.executionConsole?.let(com.intellij.openapi.util.Disposer::dispose)
    }
}
