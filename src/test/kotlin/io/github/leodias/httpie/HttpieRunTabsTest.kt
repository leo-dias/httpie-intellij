package io.github.leodias.httpie

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HttpieRunTabsTest : BasePlatformTestCase() {
    private fun descriptor(key: HttpieRequestKey, name: String): RunContentDescriptor {
        val console = HttpieResponseConsole(project, key)
        val process = HttpieRunProcessHandler()
        process.startNotify()
        process.finish(RunResult(HttpResponse(200, emptyMap(), "{}"), 0, "", 1), null)
        val descriptor = RunContentDescriptor(console, process, console.component, name)
        Disposer.register(testRootDisposable, descriptor)
        HttpieRunTabs.protect(descriptor)
        return descriptor
    }

    fun testDifferentRequestsKeepSeparateTabsAndRerunsChooseTheirOwnTab() {
        val firstKey = HttpieRequestKey("/test.httpie", "Anything 1", -1)
        val secondKey = HttpieRequestKey("/test.httpie", "Anything 2", -1)
        val first = descriptor(firstKey, "Anything 1")
        val second = descriptor(secondKey, "Anything 2")
        assertFalse(first.reusePolicy.canBeReusedBy(second))
        assertFalse(second.reusePolicy.canBeReusedBy(first))
        val rerun = descriptor(firstKey, "Renamed run configuration")
        assertTrue(first.reusePolicy.canBeReusedBy(rerun))
        assertSame(first, HttpieRunTabs.findReusable(listOf(second, first), firstKey))
        assertSame(second, HttpieRunTabs.findReusable(listOf(first, second), secondKey))
    }

    fun testSameNamesInDifferentFilesAndDuplicateNamesByPositionStaySeparate() {
        val first = descriptor(HttpieRequestKey("/one.httpie", "Anything", -1), "Anything")
        val otherFile = descriptor(HttpieRequestKey("/two.httpie", "Anything", -1), "Anything")
        assertFalse(first.reusePolicy.canBeReusedBy(otherFile))
        val duplicate1 = descriptor(HttpieRequestKey("/one.httpie", "", 0), "Duplicate")
        val duplicate2 = descriptor(HttpieRequestKey("/one.httpie", "", 1), "Duplicate")
        assertFalse(duplicate1.reusePolicy.canBeReusedBy(duplicate2))
    }

    fun testClosingATabAllowsItToBeCreatedAgainWithoutStaleReuse() {
        val key = HttpieRequestKey("/test.httpie", "Anything", -1)
        val closed = descriptor(key, "Anything")
        Disposer.dispose(closed)
        assertNull(HttpieRunTabs.findReusable(listOf(closed), key))
    }
}
