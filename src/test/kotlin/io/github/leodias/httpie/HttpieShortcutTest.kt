package io.github.leodias.httpie

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

class HttpieShortcutTest : BasePlatformTestCase() {
    private val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke("ctrl alt R"), null)

    fun testControlOptionRIsBoundInMacAndEclipseKeymaps() {
        for (name in listOf("Mac OS X", "Mac OS X 10.5+", "Eclipse (Mac OS X)")) {
            val keymap = KeymapManager.getInstance().getKeymap(name)!!
            assertTrue("Ctrl+Option+R must bind HTTPie in $name", keymap.getActionIds(shortcut).contains("HTTPie.Run"))
        }
    }

    fun testHttpieWinsOverConfigurationPopupOnlyInRequestEditor() {
        val keymap = KeymapManager.getInstance().getKeymap("Eclipse (Mac OS X)")!!
        val manager = ActionManager.getInstance()
        val candidates = keymap.getActionIds(shortcut).mapNotNull(manager::getAction)
        assertTrue(candidates.contains(manager.getAction("ChooseRunConfiguration")))
        myFixture.configureByText("test.httpie", "### first\nGET :8081\n### second\nGET :8082")
        val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor).add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile).build()
        val promoter = ActionPromoter.EP_NAME.extensionList.filterIsInstance<HttpieActionPromoter>().single()
        assertSame("The registered promoter must prioritize HTTPie's direct action over the chooser",
            manager.getAction("HTTPie.Run"), promoter.promote(candidates, context)?.firstOrNull())

        myFixture.configureByText("Main.java", "class Main {}")
        val javaContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor).add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile).build()
        assertNull("HTTPie must not take over the shortcut in other languages", promoter.promote(candidates, javaContext))
    }
}
