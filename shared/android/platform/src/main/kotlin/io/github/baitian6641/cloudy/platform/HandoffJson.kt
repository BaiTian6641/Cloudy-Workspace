package io.github.baitian6641.cloudy.platform

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import io.github.baitian6641.cloudy.domain.HandoffValidationException
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** No error contains the incoming document, parser message, or a nested exception. */
enum class HandoffJsonCode {
    TOO_LARGE, INVALID_UTF8, BOM, INVALID_JSON, UNKNOWN_FIELD, DUPLICATE_FIELD,
    MISSING_FIELD, WRONG_TYPE, UNKNOWN_VERSION, INVALID_ENVELOPE,
}

class HandoffJsonException(val code: HandoffJsonCode) : Exception(code.name)

/** Bounded data codec only. Caller identity, registry, expiry and replay policy belong to the receiver. */
object HandoffJson {
    const val MAX_BYTES = 8192
    private val fields = setOf(
        "version", "requestId", "createdAt", "expiresAt", "action", "hostId",
        "environmentId", "workspaceId", "resourceId",
    )

    fun decode(bytes: ByteArray): HandoffEnvelope {
        // Check before decoding or allocating a copy of an untrusted transport payload.
        if (bytes.size > MAX_BYTES) fail(HandoffJsonCode.TOO_LARGE)
        val document = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            fail(HandoffJsonCode.INVALID_UTF8)
        }
        if (document.startsWith('\uFEFF')) fail(HandoffJsonCode.BOM)
        validateStringLexemes(document)
        try {
            JsonReader(StringReader(document)).use { reader ->
                reader.isLenient = false
                if (reader.peek() != JsonToken.BEGIN_OBJECT) fail(HandoffJsonCode.WRONG_TYPE)
                reader.beginObject()
                val values = mutableMapOf<String, String>()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name !in fields) fail(HandoffJsonCode.UNKNOWN_FIELD)
                    if (values.containsKey(name)) fail(HandoffJsonCode.DUPLICATE_FIELD)
                    val expected = if (name == "version") JsonToken.NUMBER else JsonToken.STRING
                    // nextString accepts numbers; the explicit token gate is mandatory.
                    if (reader.peek() != expected) fail(HandoffJsonCode.WRONG_TYPE)
                    val value = reader.nextString()
                    if (name == "version") {
                        if (value.length > 64) fail(HandoffJsonCode.UNKNOWN_VERSION)
                        val number = try {
                            BigDecimal(value)
                        } catch (_: NumberFormatException) {
                            fail(HandoffJsonCode.INVALID_JSON)
                        }
                        if (number.compareTo(BigDecimal.ONE) != 0) fail(HandoffJsonCode.UNKNOWN_VERSION)
                    }
                    values[name] = value
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) fail(HandoffJsonCode.INVALID_JSON)
                if (values.keys != fields) fail(HandoffJsonCode.MISSING_FIELD)
                return HandoffEnvelope.create(
                    version = 1,
                    requestId = values.getValue("requestId"),
                    createdAt = values.getValue("createdAt"),
                    expiresAt = values.getValue("expiresAt"),
                    action = values.getValue("action"),
                    hostId = values.getValue("hostId"),
                    environmentId = values.getValue("environmentId"),
                    workspaceId = values.getValue("workspaceId"),
                    resourceId = values.getValue("resourceId"),
                )
            }
        } catch (error: HandoffJsonException) {
            throw error
        } catch (_: HandoffValidationException) {
            fail(HandoffJsonCode.INVALID_ENVELOPE)
        } catch (_: IOException) {
            fail(HandoffJsonCode.INVALID_JSON)
        } catch (_: IllegalStateException) {
            fail(HandoffJsonCode.INVALID_JSON)
        } catch (_: IllegalArgumentException) {
            fail(HandoffJsonCode.INVALID_JSON)
        }
    }

    fun encode(envelope: HandoffEnvelope): ByteArray {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.beginObject()
            writer.name("version").value(envelope.version.toLong())
            writer.name("requestId").value(envelope.requestId)
            writer.name("createdAt").value(envelope.createdAt.toString())
            writer.name("expiresAt").value(envelope.expiresAt.toString())
            writer.name("action").value(envelope.action.wireId)
            writer.name("hostId").value(envelope.hostId)
            writer.name("environmentId").value(envelope.environmentId)
            writer.name("workspaceId").value(envelope.workspaceId)
            writer.name("resourceId").value(envelope.resourceId)
            writer.endObject()
        }
        return output.toString().toByteArray(Charsets.UTF_8)
    }

    private fun fail(code: HandoffJsonCode): Nothing = throw HandoffJsonException(code)

    /**
     * Android's reader accepts some non-RFC string contents even in strict mode. This bounded
     * compatibility guard enforces RFC 8259 section 7 only; JsonReader owns all JSON structure,
     * member decoding and numeric token recognition.
     */
    private fun validateStringLexemes(document: String) {
        var inString = false
        var index = 0
        while (index < document.length) {
            val character = document[index++]
            if (!inString) {
                if (character == '"') inString = true
                continue
            }
            when {
                character == '"' -> inString = false
                character.code < 0x20 -> fail(HandoffJsonCode.INVALID_JSON)
                character == '\\' -> {
                    if (index >= document.length) fail(HandoffJsonCode.INVALID_JSON)
                    when (document[index++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> {
                            if (document.length - index < 4) fail(HandoffJsonCode.INVALID_JSON)
                            repeat(4) {
                                val digit = document[index++]
                                if (digit !in '0'..'9' && digit !in 'a'..'f' && digit !in 'A'..'F') {
                                    fail(HandoffJsonCode.INVALID_JSON)
                                }
                            }
                        }
                        else -> fail(HandoffJsonCode.INVALID_JSON)
                    }
                }
            }
        }
        if (inString) fail(HandoffJsonCode.INVALID_JSON)
    }
}
