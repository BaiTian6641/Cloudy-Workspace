package io.github.baitian6641.cloudy.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Parser fixtures only; no fake product/session result and no private storage or launch. */
@RunWith(AndroidJUnit4::class)
class FilesHandoffReceiverCheckpointCodecTest {
    @Test fun exactIntegersAndEscapedStringsRemainTyped() {
        val value = RetainedCheckpointJson.decode("""{"schema":2,"text":"a\\b\u0041","rows":[[1,"x"]]}""".toByteArray())
        assertEquals(2L, value["schema"])
        assertEquals("a\\bA", value["text"])
        assertEquals(listOf(listOf(1L, "x")), value["rows"])
    }
    @Test fun duplicateKeysCoercionTrailingAndMalformedUtf8AreRejected() {
        for (text in listOf("""{"a":1,"a":2}""", """{"a":1.0}""", """{"a":01}""",
                """{"a":9223372036854775808}""", """{"a":1}{}""", """{"a":true}""",
                """{"a":"\uD800"}""", """{"a":"\x20"}""", """{"a":[1,]}""")) {
            assertThrows(Exception::class.java) { RetainedCheckpointJson.decode(text.toByteArray()) }
        }
        assertThrows(Exception::class.java) { RetainedCheckpointJson.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
    }
    @Test fun fixedPreparationMetadataAcceptsCompletedSameBootSnapshot() {
        validateMetadata(metadata())
    }
    @Test fun preparationMetadataRejectsWrongIdentityCaseAndImpossibleTime() {
        val valid = metadata()
        for ((key, value) in listOf("case" to "registered-location", "run" to "other-run", "direction" to "console-to-desktop",
                "owner" to "desktop", "package" to "other.package", "uid" to 10124L, "version" to "0.1.99", "code" to 999L,
                "preparedAtElapsedMillis" to 0L, "preparedAtElapsedMillis" to -1L,
                "preparedAtElapsedMillis" to 1001L, "preparedAtElapsedMillis" to "500", "preparedAtElapsedMillis" to 500.0)) {
            assertThrows(Exception::class.java) { validateMetadata(valid + (key to value)) }
        }
        assertThrows(Exception::class.java) { validateMetadata(valid - "case") }
        assertThrows(Exception::class.java) { validateMetadata(valid - "preparedAtElapsedMillis") }
        assertThrows(Exception::class.java) { validateMetadata(valid + ("unrecognized" to 1L)) }
    }
    private fun metadata(): Map<String, Any?> = mapOf("schema" to 2L, "case" to "handoff-receiver",
        "preparedAtElapsedMillis" to 500L, "run" to "fixture-run", "direction" to "desktop-to-console",
        "owner" to "console", "package" to "io.github.baitian6641.cloudy.console", "uid" to 10123L,
        "version" to "0.1.13", "code" to 14L, "baseline" to emptyList<Any>(), "retained" to emptyList<Any>())
    private fun validateMetadata(value: Map<String, Any?>) = RetainedCheckpointMetadata.validate(value,
        "fixture-run", "desktop-to-console", "console", "io.github.baitian6641.cloudy.console", 10123L, "0.1.13", 14L, 1000L)

    @Test fun depthAndFieldLengthAreBounded() {
        assertThrows(Exception::class.java) { RetainedCheckpointJson.decode(("{\"a\":" + "[".repeat(18) + "0" + "]".repeat(18) + "}").toByteArray()) }
        assertThrows(Exception::class.java) { RetainedCheckpointJson.decode(("{\"a\":\"" + "x".repeat(4097) + "\"}").toByteArray()) }
    }
}
