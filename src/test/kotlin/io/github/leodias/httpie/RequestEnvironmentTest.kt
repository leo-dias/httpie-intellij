package io.github.leodias.httpie

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RequestEnvironmentTest {
    @TempDir lateinit var root: Path

    private fun prepare(text: String, selected: String? = null, globals: Map<String, String> = emptyMap(), environment: Map<String, String> = emptyMap()): PreparedRequest? =
        RequestEnvironment.prepare(text, 0, root.resolve("requests.httpie"), root, selected, globals, environment)

    @Test fun `directive overrides selected env and values follow precedence`() {
        Files.writeString(root.resolve("dev.env"), "export url=\"http://localhost\"\ntoken='file'\nother=from-file")
        val req = prepare("# env: dev.env\nGET {{url}}/{{token}}/{{other}}/{{fallback}}", "missing.env",
            mapOf("token" to "global"), mapOf("token" to "process", "fallback" to "process"))!!
        assertEquals("http://localhost/global/from-file/process", req.argv.last())
        assertEquals("global", req.environment["token"])
        assertEquals("from-file", req.environment["other"])
        assertEquals(setOf("dev.env"), Files.list(root).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() })
    }

    @Test fun `missing variable file and env are errors before execution`() {
        for (text in listOf("GET {{missing}}", "POST http://localhost\n\n< absent.json", "GET http://localhost\n\n> absent.js")) {
            assertThrows(IllegalArgumentException::class.java) { prepare(text) }
        }
        assertThrows(IllegalArgumentException::class.java) { prepare("GET http://localhost", "absent.env") }
    }

    @Test fun `literal file body preserves braces while templated file substitutes`() {
        Files.writeString(root.resolve("body.json"), "{\"value\":\"{{missing}}\"}")
        assertEquals("{\"value\":\"{{missing}}\"}", prepare("POST http://localhost\n\n< body.json")!!.stdin)
        assertThrows(IllegalArgumentException::class.java) { prepare("POST http://localhost\n\n<@ body.json") }
        assertEquals("{\"value\":\"Ada\"}", prepare("POST http://localhost\n\n<@ body.json", globals = mapOf("missing" to "Ada"))!!.stdin)
    }

    @Test fun `paths resolve beside request file and env falls back to project root`() {
        val sub = Files.createDirectory(root.resolve("requests"))
        Files.writeString(root.resolve("dev.env"), "name=Ada")
        Files.writeString(sub.resolve("body.txt"), "hello")
        Files.writeString(sub.resolve("check.js"), "client.log('{{name}}');")
        val req = RequestEnvironment.prepare("POST http://localhost\n\n< body.txt\n> check.js\n>>! result.txt", 0,
            sub.resolve("test.httpie"), root, "dev.env", emptyMap(), emptyMap())!!
        assertEquals(sub, req.directory)
        assertEquals("hello", req.stdin)
        assertEquals("client.log('Ada');", req.handler)
        assertEquals(sub.resolve("result.txt"), req.save)
    }

    @Test fun `only current block variables need to resolve`() {
        assertNotNull(prepare("GET http://localhost\n### later\nGET {{not_selected}}"))
        assertNull(prepare("# comments only"))
    }

    @Test fun `inline handler and body substitutions preserve empty values`() {
        val req = prepare("POST http://localhost\n\n{{empty}}suffix\n> {% client.log('{{name}}'); %}", globals = mapOf("empty" to "", "name" to "Ada"))!!
        assertEquals("suffix", req.stdin)
        assertEquals(" client.log('Ada'); ", req.handler)
    }
}
