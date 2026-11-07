package io.github.leodias.httpie

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color

class JsonResponseHighlightingTest : BasePlatformTestCase() {
    private fun withResponse(text: String, test: (HttpieResponseConsole, EditorEx) -> Unit) {
        val view = HttpieResponseConsole(project, HttpieRequestKey("/test.httpie", "Test", -1))
        try {
            view.display(project, text, "jsonc")
            val editor = EditorFactory.getInstance().allEditors.single { it.isViewer && it.document.text == text } as EditorEx
            test(view, editor)
        } finally { Disposer.dispose(view) }
    }

    private fun colorAt(editor: EditorEx, offset: Int): Color? = editor.markupModel.allHighlighters
        .firstOrNull { it.startOffset <= offset && offset < it.endOffset }?.textAttributes?.foregroundColor

    fun testKeysStringsNumbersBooleansAndNullUseDistinctColors() {
        val text = "// content-type: application/json\n\n{\"name\":\"Ada\",\"count\":12,\"ok\":true,\"missing\":null}"
        withResponse(text) { _, editor ->
            val colors = listOf("\"name\"", "\"Ada\"", "12", "true", "null").map { token ->
                val color = colorAt(editor, text.indexOf(token))
                assertNotNull("Distinct response color required for $token", color)
                color
            }
            assertEquals(5, colors.toSet().size)
            assertEquals("Response text must remain unchanged", text, editor.document.text)
        }
    }

    fun testNestedBracketPairsShareColorsButAdjacentDepthsDiffer() {
        val text = "// header: { [ braces here must not affect depth\n\n{\"items\":[{\"text\":\"escaped \\\" } [\",\"ok\":false}]}"
        withResponse(text) { _, editor ->
            val root = text.indexOf('{', text.indexOf("\n\n"))
            val array = text.indexOf('[', root)
            val objectStart = text.indexOf('{', array)
            val colors = listOf(root, array, objectStart).map { colorAt(editor, it) }
            colors.forEach { assertNotNull(it) }
            assertEquals(3, colors.toSet().size)
            assertEquals(colors[0], colorAt(editor, text.lastIndexOf('}')))
            assertEquals(colors[1], colorAt(editor, text.lastIndexOf(']')))
            assertEquals(colors[2], colorAt(editor, text.lastIndexOf("}]}")))
            assertTrue(editor.markupModel.allHighlighters.none { it.startOffset < root })
        }
    }

    fun testRerenderClearsOldJsonColorsAndLeavesPlainTextUntouched() {
        val text = "{\"key\":true}"
        withResponse(text) { view, editor ->
            assertTrue(editor.markupModel.allHighlighters.isNotEmpty())
            view.display(project, text, "jsonc")
            assertEquals(4, editor.markupModel.allHighlighters.size)
            view.display(project, "plain response", "txt")
            assertTrue(editor.markupModel.allHighlighters.isEmpty())
            assertEquals("plain response", editor.document.text)
        }
    }

    fun testLargeEscapedStringDoesNotAffectBracketDepthOrOverflow() {
        val text = "{\"text\":\"${"data \\\" { } [ ] ".repeat(5000)}\",\"value\":-1.5e+2}"
        withResponse(text) { _, editor ->
            assertNotNull(colorAt(editor, text.indexOf("\"data")))
            assertNotNull(colorAt(editor, text.indexOf("-1.5e+2")))
            assertEquals(colorAt(editor, 0), colorAt(editor, text.lastIndex))
            assertEquals(text, editor.document.text)
        }
    }
}
