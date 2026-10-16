package io.github.baitian6641.cloudy.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Executes the platform JSON implementation, not a JVM imitation of Android's parser. */
@RunWith(AndroidJUnit4::class)
class HandoffJsonTest {
    private val members = listOf(
        "\"version\":1",
        "\"requestId\":\"13571113-2468-4ace-8bad-001122334455\"",
        "\"createdAt\":\"2026-10-14T18:00:00Z\"",
        "\"expiresAt\":\"2026-10-14T18:02:00Z\"",
        "\"action\":\"open-console\"",
        "\"hostId\":\"demo-host\"",
        "\"environmentId\":\"demo-linux\"",
        "\"workspaceId\":\"demo-fpga\"",
        "\"resourceId\":\"registered-project-root\"",
    )
    private val valid get() = members.joinToString(",", "{", "}")

    private fun rejected(bytes: ByteArray, code: HandoffJsonCode? = null) {
        try {
            HandoffJson.decode(bytes)
            fail("Expected sanitized rejection")
        } catch (error: HandoffJsonException) {
            if (code != null) assertEquals(code, error.code)
            assertEquals(error.code.name, error.message)
            assertNull(error.cause)
            assertEquals(0, error.suppressed.size)
        }
    }

    private fun rejected(document: String, code: HandoffJsonCode? = null) =
        rejected(document.toByteArray(Charsets.UTF_8), code)

    @Test fun canonicalRoundTripIsDeterministic() {
        val envelope = HandoffJson.decode(valid.toByteArray())
        val bytes = HandoffJson.encode(envelope)
        assertEquals(valid, bytes.toString(Charsets.UTF_8))
        assertArrayEquals(bytes, HandoffJson.encode(HandoffJson.decode(bytes)))
    }

    @Test fun acceptsWhitespaceFieldOrderAndEscapedNames() {
        val alternate = members.reversed().joinToString(" ,\n\t", " {\n", "\n} ")
            .replace("hostId", "host\\u0049d")
        assertArrayEquals(valid.toByteArray(), HandoffJson.encode(HandoffJson.decode(alternate.toByteArray())))
    }

    @Test fun normalizesUuidAndTimestampThroughValidatedDomain() {
        val alternate = valid.replace("4ace-8bad", "4ACE-8BAD")
            .replace("18:00:00Z", "18:00:00+00:00")
        assertArrayEquals(valid.toByteArray(), HandoffJson.encode(HandoffJson.decode(alternate.toByteArray())))
    }

    @Test fun rejectsDuplicateIncludingEscapedMemberName() {
        rejected(valid.dropLast(1) + ",\"host\\u0049d\":\"other\"}", HandoffJsonCode.DUPLICATE_FIELD)
        rejected(valid.dropLast(1) + ",\"version\":1}", HandoffJsonCode.DUPLICATE_FIELD)
    }

    @Test fun rejectsUnknownMemberBeforeConsumingItsValue() {
        rejected(valid.dropLast(1) + ",\"private-command\":{}}", HandoffJsonCode.UNKNOWN_FIELD)
    }

    @Test fun rejectsEveryMissingRequiredMember() {
        members.indices.forEach { omitted ->
            rejected(members.filterIndexed { index, _ -> index != omitted }.joinToString(",", "{", "}"),
                HandoffJsonCode.MISSING_FIELD)
        }
    }

    @Test fun rejectsNullCoercionAndNestedValues() {
        listOf("null", "3", "true", "[]", "{}").forEach { replacement ->
            rejected(valid.replace("\"demo-host\"", replacement), HandoffJsonCode.WRONG_TYPE)
        }
        listOf("null", "\"1\"", "true", "[]", "{}").forEach { replacement ->
            rejected(valid.replace("\"version\":1", "\"version\":$replacement"), HandoffJsonCode.WRONG_TYPE)
        }
        rejected("[]", HandoffJsonCode.WRONG_TYPE)
    }

    @Test fun comparesVersionExactlyWithoutFloatingPointRounding() {
        listOf("0.9999999999", "1.00000000000001", "2", "1e999999999").forEach { version ->
            rejected(valid.replace("\"version\":1", "\"version\":$version"), HandoffJsonCode.UNKNOWN_VERSION)
        }
        listOf("1", "1.0", "1e0").forEach { version ->
            val bytes = valid.replace("\"version\":1", "\"version\":$version").toByteArray()
            assertArrayEquals(valid.toByteArray(), HandoffJson.encode(HandoffJson.decode(bytes)))
        }
        rejected(valid.replace("\"version\":1", "\"version\":" + "1".repeat(65)),
            HandoffJsonCode.UNKNOWN_VERSION)
    }

    @Test fun rejectsInvalidUtf8BomAndOversizeBeforeParsing() {
        rejected(byteArrayOf(0xc3.toByte(), 0x28), HandoffJsonCode.INVALID_UTF8)
        rejected(byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()), HandoffJsonCode.INVALID_UTF8)
        rejected("\uFEFF$valid", HandoffJsonCode.BOM)
        rejected(ByteArray(HandoffJson.MAX_BYTES + 1) { 0xff.toByte() }, HandoffJsonCode.TOO_LARGE)
        rejected(" ".repeat(HandoffJson.MAX_BYTES - valid.length) + valid + " ", HandoffJsonCode.TOO_LARGE)
        assertEquals(1, HandoffJson.decode((" ".repeat(HandoffJson.MAX_BYTES - valid.length) + valid).toByteArray()).version)
    }

    @Test fun rejectsMalformedSyntaxAndTrailingDocuments() {
        listOf(
            "", "{", valid.dropLast(1), valid + "{}", valid + " true",
            valid.dropLast(1) + ",}", valid.replace("\"version\"", "version"),
            valid.replace("\"version\":1", "\"version\":/*comment*/1"),
            valid.replace("\"demo-host\"", "\"bad\\uZZZZ\""),
            valid.replace("\"version\":1", "\"version\":01"),
        ).forEach { rejected(it) }
    }

    @Test fun domainRejectionsNeverExposeSubmittedValues() {
        listOf("secret/raw-command", "", "x".repeat(129)).forEach { value ->
            rejected(valid.replace("demo-host", value), HandoffJsonCode.INVALID_ENVELOPE)
        }
        rejected(valid.replace("open-console", "unrecognized"), HandoffJsonCode.INVALID_ENVELOPE)
        rejected(valid.replace("2026-10-14T18:00:00Z", "invalid-secret-date"), HandoffJsonCode.INVALID_ENVELOPE)
    }

    @Test fun rejectsRawControlsAndUnknownOrIncompleteEscapes() {
        (0..31).forEach { control ->
            rejected(valid.replace("demo-host", "bad${control.toChar()}value"), HandoffJsonCode.INVALID_JSON)
        }
        listOf("bad\\q", "bad\\'", "bad\\u123x", "bad\\u12").forEach { value ->
            rejected(valid.replace("demo-host", value), HandoffJsonCode.INVALID_JSON)
        }
        rejected("{\"hostId\":\"unfinished\\", HandoffJsonCode.INVALID_JSON)
    }

    @Test fun legalEscapesStayWithTheReaderAndDomainValidator() {
        // A JSON-legal escaped control still fails the resource-ID domain constraint.
        listOf("\\\"", "\\\\", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t").forEach { escape ->
            rejected(valid.replace("demo-host", "left${escape}right"), HandoffJsonCode.INVALID_ENVELOPE)
        }
        // Quotes/backslashes inside names must not move the guard's string boundary.
        listOf("unknown\\\"name", "unknown\\\\name").forEach { name ->
            rejected(valid.dropLast(1) + ",\"$name\":\"value\"}", HandoffJsonCode.UNKNOWN_FIELD)
        }
    }
}
