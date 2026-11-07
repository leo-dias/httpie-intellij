package io.github.leodias.httpie

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RequestLocationsTest {
    @Test fun `caret resolves block name including separator and body lines`() {
        val text = "# env: local.env\r\n### Anything 1\r\nGET :8081/1\r\n### Anything 2\r\nPOST :8081/2\r\n\r\n{}"
        assertEquals("Anything 1", RequestLocations.at(text, 1)!!.name)
        val second = RequestLocations.at(text, 6)!!
        assertEquals("Anything 2", second.name)
        assertEquals(1, second.index)
        assertEquals(4, second.line)
        assertEquals("Anything 2", second.title("test.httpie"))
    }

    @Test fun `named selection survives moving blocks and fails if missing or ambiguous`() {
        val text = "### Other\nGET :8081/other\n### Anything\nGET :8081/anything"
        assertEquals(3, RequestLocations.resolve(text, 0, "Anything").line)
        assertThrows(IllegalArgumentException::class.java) { RequestLocations.resolve(text, 0, "Deleted") }
        assertThrows(IllegalArgumentException::class.java) { RequestLocations.resolve(text + "\n### Anything\nGET :8081/duplicate", 0, "Anything") }
    }

    @Test fun `unnamed requests use position and empty blocks do not become requests`() {
        val text = "# comment\n###\nGET :8081/1\n### empty\n# only comment\n###\nGET :8081/2"
        assertEquals(2, RequestLocations.list(text).size)
        assertNull(RequestLocations.at(text, 4))
        assertEquals("test.httpie (request 2)", RequestLocations.resolve(text, 1, "").title("test.httpie"))
        assertEquals(1, RequestLocations.at(text, 999)!!.index)
        assertThrows(IllegalArgumentException::class.java) { RequestLocations.resolve(text, 2, "") }
    }
}
