package io.github.leodias.httpie

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RequestParserTest {
    @Test fun `GET is argv not shell and does not read stdin`() {
        val req = RequestParser.parse("GET http://localhost:8080/users?q=a&b=c\nX-Test: yes", 0)!!
        assertEquals(listOf("http", "--ignore-stdin", "--pretty=format", "--print=hb", "GET", "http://localhost:8080/users?q=a&b=c", "X-Test:yes"), req.argv)
        assertNull(req.shell)
        assertNull(req.body)
    }

    @Test fun `POST preserves body and does not ignore stdin`() {
        val req = RequestParser.parse("POST http://localhost\nContent-Type: application/json\n\n{\"name\": \"Ada\"}", 0)!!
        assertEquals("{\"name\": \"Ada\"}", req.body)
        assertFalse(req.argv.contains("--ignore-stdin"))
    }

    @Test fun `URL alone is a GET`() {
        assertTrue(RequestParser.parse("http://localhost/test", 0)!!.argv.contains("GET"))
    }

    @Test fun `caret separator starts next block and CRLF is accepted`() {
        val text = "### first\r\nGET http://localhost/first\r\n### second\r\nGET http://localhost/second"
        assertEquals("http://localhost/first", RequestParser.parse(text, 1)!!.argv.last())
        assertEquals("http://localhost/second", RequestParser.parse(text, 2)!!.argv.last())
        assertNull(RequestParser.parse("# comment\n// another\n\n### empty", 3))
    }

    @Test fun `shell command continuations keep user flags`() {
        val req = RequestParser.parse("# comment\nhttp --body GET localhost \\\n  q==hello", 1)!!
        assertTrue(req.shell!!.contains("--ignore-stdin"))
        assertTrue(req.shell.contains("--pretty=format"))
        assertFalse(req.shell.contains("--print=hb"))
        assertFalse(req.shell.contains("\\"))
    }

    @Test fun `inline handler and response save are not body`() {
        val req = RequestParser.parse("POST http://localhost\n\n{}\n> {%\nclient.log(response.status);\n%}\n>>! response.json", 0)!!
        assertEquals("{}", req.body)
        assertEquals("\nclient.log(response.status);", req.handler)
        assertEquals("response.json", req.save)
    }

    @Test fun `file bodies and external handlers`() {
        val req = RequestParser.parse("POST http://localhost\n\n<@ ./body.json\n> ./check.js", 0)!!
        assertNull(req.body)
        assertEquals("./body.json", req.bodyFile)
        assertTrue(req.substituteBodyFile)
        assertEquals("./check.js", req.handlerFile)
        assertFalse(req.argv.contains("--ignore-stdin"))
        assertFalse(RequestParser.parse("POST http://localhost\n\n< body.json", 0)!!.substituteBodyFile)
    }

    @Test fun `one line handler and save directly after headers`() {
        val req = RequestParser.parse("GET http://localhost\n> {% client.log('ok'); %}\n>> result.json", 0)!!
        assertEquals(" client.log('ok'); ", req.handler)
        assertEquals("result.json", req.save)
    }

    @Test fun `unterminated handler fails clearly`() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestParser.parse("GET http://localhost\n\n> {% client.log('oops');", 0)
        }
    }

    @Test fun `delimiter inside JavaScript string does not end handler`() {
        val req = RequestParser.parse("GET http://localhost\n\n> {%\nclient.log('literal %} text');\nclient.assert(true);\n%}", 0)!!
        assertEquals("\nclient.log('literal %} text');\nclient.assert(true);", req.handler)
    }
}
