package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalJsonTest {
    private fun rejects(json: String) {
        assertThrows(IllegalArgumentException::class.java) { TerminalJson.parse(json) }
    }

    @Test fun parsesExactIntegerBooleanNullAndEscapedKeys() {
        assertEquals(mapOf("schemaVersion" to 1L, "authenticated" to false, "reason" to null,
            "nested" to listOf(mapOf("value" to "a\n\"b"))),
            TerminalJson.parse("""{"schema\u0056ersion":1,"authenticated":false,"reason":null,"nested":[{"value":"a\n\"b"}]}"""))
        assertEquals(Long.MAX_VALUE, TerminalJson.parse("""{"n":9223372036854775807}""")["n"])
    }

    @Test fun rejectsDuplicateFieldsIncludingEscapedAliasAndRelaxedJson() {
        listOf("""{"x":1,"x":2}""", """{"x":1,"\u0078":2}""", """{"x":{"a":1,"a":2}}""",
            """{'x':1}""", """{x:1}""", """{"x":1,}""", """{"x":[1,]}""", """{"x":true}ignored""",
            """{"x":NaN}""", """{"x":1.0}""", """{"x":1e0}""", """{"x":01}""",
            """{"x":9223372036854775808}""", "[]", "null", """{"x":"\q"}""").forEach(::rejects)
    }

    @Test fun boundsDepthStringsAndTotalInputBeforeAcceptingEvidence() {
        rejects("""{"x":"""" + "a".repeat(4097) + "\"}")
        rejects("{\"x\":" + "[".repeat(13) + "0" + "]".repeat(13) + "}")
        rejects(" ".repeat(128 * 1024 + 1) + "{}")
        rejects("{\"x\":\"raw\nline\"}")
        rejects("{\"x\":\"unterminated}")
    }
}
