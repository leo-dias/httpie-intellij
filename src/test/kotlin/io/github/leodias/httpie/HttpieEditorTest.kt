package io.github.leodias.httpie

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpServer
import org.junit.Assume.assumeTrue
import java.net.InetSocketAddress
import java.nio.file.Files
import javax.swing.KeyStroke

class HttpieEditorTest : BasePlatformTestCase() {
    fun testScreenshotRequestIsHighlighted() {
        val file = myFixture.configureByText("test.httpie", "###\nGET :8081")
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.fileType, project, file.virtualFile)
        assertNotNull("HTTPie must register a syntax highlighter", highlighter)
        val lexer = highlighter!!.highlightingLexer
        lexer.start(file.text)
        var methodHighlighted = false
        while (lexer.tokenType != null) {
            if (lexer.tokenText == "GET") {
                methodHighlighted = highlighter.getTokenHighlights(lexer.tokenType).contains(DefaultLanguageHighlighterColors.KEYWORD)
            }
            lexer.advance()
        }
        assertTrue("GET must have keyword highlighting", methodHighlighted)
    }

    fun testRunActionIsAvailableForScreenshotRequest() {
        myFixture.configureByText("test.httpie", "###\nGET :8081")
        val action = ActionManager.getInstance().getAction("HTTPie.Run")
        val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor).add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile).build()
        val event = AnActionEvent.createFromAnAction(action, null, "EditorPopup", context)
        action.update(event)
        assertTrue("Run action must be enabled in the request editor", event.presentation.isEnabledAndVisible)
    }

    fun testGutterRunButtonsAppearOnlyAtRequestStarts() {
        myFixture.configureByText("test.httpie", "###\nGET :8081\nX-Test: yes\n\n### second\nPOST :8081\n\n{\"name\":\"Ada\"}")
        val gutters = myFixture.findAllGutters().filter { it.tooltipText == "Run HTTPie request" }
        assertEquals("Each request needs an accessible gutter run button", 2, gutters.size)
    }

    fun testHeaderVariableAndBodyStringsHaveHighlighting() {
        val file = myFixture.configureByText("test.httpie", "POST :8081\nAuthorization: Bearer {{token}}\n\n{\"name\":\"Ada\"}")
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.fileType, project, file.virtualFile)
        assertNotNull("HTTPie must register a syntax highlighter", highlighter)
        val lexer = highlighter!!.highlightingLexer
        lexer.start(file.text)
        val colored = mutableSetOf<String>()
        while (lexer.tokenType != null) {
            if (highlighter.getTokenHighlights(lexer.tokenType).isNotEmpty()) colored += lexer.tokenText
            lexer.advance()
        }
        assertTrue("Header names must be colored", colored.contains("Authorization"))
        assertTrue("Variables must be colored", colored.contains("{{token}}"))
        assertTrue("Body strings must be colored", colored.contains("\"Ada\""))
    }

    fun testLargeBodyStringsDoNotOverflowLexerAndEveryTokenAdvances() {
        val text = "POST :8081\n\n{\"value\":\"${"a".repeat(50_000)}\",\"other\":'${"b".repeat(50_000)}'}"
        val lexer = HttpieLexer()
        lexer.start(text)
        var end = 0
        var strings = 0
        while (lexer.tokenType != null) {
            assertEquals(end, lexer.tokenStart)
            assertTrue("Lexer must advance on every token", lexer.tokenEnd > lexer.tokenStart)
            if (lexer.tokenType == HttpieTokens.STRING) strings++
            end = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals(text.length, end)
        assertEquals(4, strings)
    }

    fun testResponseViewCanBeCreatedAndDisplayOutput() {
        val view = HttpieResponseConsole(project, HttpieRequestKey("/test.httpie", "Test", -1))
        try {
            view.display(project, "HTTPie response test", "txt")
            assertTrue(EditorFactory.getInstance().allEditors.any { it.isViewer && it.document.text == "HTTPie response test" })
        } finally { Disposer.dispose(view) }
    }

    fun testRunResponsesKeepIndependentNamedContentAndHandleCancellation() {
        val first = HttpieResponseConsole(project, HttpieRequestKey("/test.httpie", "Anything 1", -1))
        val second = HttpieResponseConsole(project, HttpieRequestKey("/test.httpie", "Anything 2", -1))
        try {
            first.showResult("Anything 1", RunResult(HttpResponse(200, emptyMap(), "first"), 0, "", 1), null)
            second.showResult("Anything 2", RunResult(HttpResponse(200, emptyMap(), "second"), 0, "", 2), null)
            assertTrue(EditorFactory.getInstance().allEditors.any { it.isViewer && it.document.text.startsWith("// Anything 1") && it.document.text.endsWith("first") })
            assertTrue(EditorFactory.getInstance().allEditors.any { it.isViewer && it.document.text.startsWith("// Anything 2") && it.document.text.endsWith("second") })
            first.showResult("Anything 1", null, java.util.concurrent.CancellationException())
            assertTrue(EditorFactory.getInstance().allEditors.any { it.isViewer && it.document.text == "Request cancelled" })
            assertTrue(EditorFactory.getInstance().allEditors.any { it.isViewer && it.document.text.endsWith("second") })
        } finally { Disposer.dispose(first); Disposer.dispose(second) }
    }

    fun testKeyboardShortcutRunsCaretBlockAndGutterUsesClickedBlock() {
        for (tool in listOf("http", "node")) {
            assumeTrue("$tool required for editor execution integration", runCatching {
                ProcessBuilder(tool, "--version").start().waitFor() == 0
            }.getOrDefault(false))
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "{\"ok\":true}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val directory = Files.createTempDirectory("httpie-editor").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
        val file = directory.resolve("test.httpie")
        val session = project.getService(HttpieSession::class.java)
        session.applyGlobals(mapOf("editorRun" to null))
        try {
            val text = "###\nGET :${server.address.port}\n\n> {% client.global.set('editorRun', response.body.ok); %}" +
                "\n### second\nGET :${server.address.port}/second\n\n> {% client.global.set('editorRun', 'second'); %}"
            Files.writeString(file, text)
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
            myFixture.configureFromExistingVirtualFile(virtualFile)
            myFixture.editor.caretModel.moveToOffset(text.lastIndexOf("GET") + 4)
            val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke("ctrl alt R"), null)
            val keymap = KeymapManager.getInstance().getKeymap("Eclipse (Mac OS X)")!!
            val candidates = keymap.getActionIds(shortcut).mapNotNull(ActionManager.getInstance()::getAction)
            val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, myFixture.editor).add(CommonDataKeys.VIRTUAL_FILE, virtualFile).build()
            val promoter = ActionPromoter.EP_NAME.extensionList.filterIsInstance<HttpieActionPromoter>().single()
            val action = promoter.promote(candidates, context)!!.first()
            assertSame(ActionManager.getInstance().getAction("HTTPie.Run"), action)
            val presentation = myFixture.testAction(action)
            assertTrue(presentation.isEnabledAndVisible)
            val saved = RunManager.getInstance(project).selectedConfiguration!!.configuration as HttpieRunConfiguration
            assertEquals("second", saved.requestName)
            assertEquals(virtualFile.path, saved.filePath)
            val deadline = System.nanoTime() + 10_000_000_000
            while (session.globalsSnapshot()["editorRun"] != "second" && System.nanoTime() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
            assertEquals("Keyboard action must execute the request at the caret, not the first request", "second", session.globalsSnapshot()["editorRun"])
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
            assertEquals(5, myFixture.editor.caretModel.logicalPosition.line)
            val first = myFixture.file.findElementAt(text.indexOf("GET"))!!
            @Suppress("UNCHECKED_CAST")
            val marker = HttpieLineMarkerProvider().getLineMarkerInfo(first) as com.intellij.codeInsight.daemon.LineMarkerInfo<com.intellij.psi.PsiElement>
            marker.navigationHandler!!.navigate(null, first)
            val gutterDeadline = System.nanoTime() + 10_000_000_000
            while (session.globalsSnapshot()["editorRun"] != "true" && System.nanoTime() < gutterDeadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
            assertEquals("Gutter must run its own request, not the caret's request", "true", session.globalsSnapshot()["editorRun"])
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
        } finally {
            session.applyGlobals(mapOf("editorRun" to null))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            // Run contents are not registered with RunContentManager in unit-test mode, so find their editors by component tree.
            EditorFactory.getInstance().allEditors.mapNotNull {
                javax.swing.SwingUtilities.getAncestorOfClass(HttpieResponseConsole::class.java, it.component) as? HttpieResponseConsole
            }.distinct().forEach(Disposer::dispose)
            val manager = RunManager.getInstance(project)
            manager.allSettings.filter { (it.configuration as? HttpieRunConfiguration)?.filePath == file.toString() }.forEach(manager::removeConfiguration)
            server.stop(0)
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
