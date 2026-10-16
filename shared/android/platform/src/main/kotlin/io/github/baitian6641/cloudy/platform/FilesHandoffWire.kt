package io.github.baitian6641.cloudy.platform

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import io.github.baitian6641.cloudy.domain.FilesResourceReference
import io.github.baitian6641.cloudy.domain.FilesSharedDescriptor
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Collections

/** Fixed protocol numbers: enum ordinals must never be serialized. */
enum class FilesDiscoveryStatus(val wireId: Int) {
    OK(0), UNSUPPORTED_VERSION(1), DENIED(2), INVALID_INPUT(3), BUSY(4), UNAVAILABLE(5),
}
enum class FilesSubmitStatus(val wireId: Int) {
    DISPATCH_REQUESTED(0), DENIED(1), INVALID_ENVELOPE(2), UNSUPPORTED_ACTION(3),
    RESOURCE_UNAVAILABLE(4), EXPIRED(5), NOT_YET_VALID(6), DUPLICATE(7), CLOCK_ROLLBACK(8),
    CAPACITY(9), BUSY(10), ADMISSION_UNCONFIRMED(11), ADMITTED_NOT_DISPATCHED(12),
    ADMITTED_DELIVERY_UNCONFIRMED(13),
}
enum class FilesConsumption(val wireId: Int) { NONE(0), RESERVED(1), UNKNOWN(2) }
enum class FilesHandoffWireCode {
    TOO_LARGE, INVALID_UTF8, INVALID_JSON, WRONG_TYPE, UNKNOWN_FIELD, DUPLICATE_FIELD,
    MISSING_FIELD, UNKNOWN_VERSION, INVALID_STATUS, INVALID_CONSUMPTION, INVALID_DESCRIPTOR, INVALID_PAGE, INVALID_CURSOR,
}
class FilesHandoffWireException(val code: FilesHandoffWireCode) : IllegalArgumentException(code.name)

/** A fresh observation page, not a stable snapshot, reservation or resource authorization. */
class FilesDiscoveryReply private constructor(
    val status: FilesDiscoveryStatus, locations: List<FilesSharedDescriptor>, val nextAfterResourceId: String,
) {
    val locations: List<FilesSharedDescriptor> = Collections.unmodifiableList(ArrayList(locations))
    override fun toString(): String = "FilesDiscoveryReply(redacted)"
    companion object {
        fun success(locations: List<FilesSharedDescriptor>, nextAfterResourceId: String = ""): FilesDiscoveryReply {
            if (locations.size > FilesHandoffWire.PAGE_SIZE) wireFail(FilesHandoffWireCode.INVALID_PAGE)
            FilesHandoffWire.validateAfterResourceId(nextAfterResourceId)
            if (locations.zipWithNext().any { (left, right) -> left.reference.resourceId >= right.reference.resourceId }) {
                wireFail(FilesHandoffWireCode.INVALID_PAGE)
            }
            if (nextAfterResourceId.isNotEmpty() && (locations.size != FilesHandoffWire.PAGE_SIZE ||
                    nextAfterResourceId != locations.last().reference.resourceId)) wireFail(FilesHandoffWireCode.INVALID_PAGE)
            return FilesDiscoveryReply(FilesDiscoveryStatus.OK, locations, nextAfterResourceId)
        }
        fun failure(status: FilesDiscoveryStatus): FilesDiscoveryReply {
            if (status == FilesDiscoveryStatus.OK) wireFail(FilesHandoffWireCode.INVALID_STATUS)
            return FilesDiscoveryReply(status, emptyList(), "")
        }
    }
}

/** Current-call consumption only: DUPLICATE reports NONE because this call made no reservation. */
class FilesSubmitReply private constructor(val status: FilesSubmitStatus, val consumption: FilesConsumption) {
    override fun toString(): String = "FilesSubmitReply(status=$status, consumption=$consumption)"
    companion object {
        fun create(status: FilesSubmitStatus, consumption: FilesConsumption): FilesSubmitReply {
            val expected = when (status) {
                FilesSubmitStatus.DISPATCH_REQUESTED,
                FilesSubmitStatus.ADMITTED_NOT_DISPATCHED, FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED -> FilesConsumption.RESERVED
                FilesSubmitStatus.ADMISSION_UNCONFIRMED -> FilesConsumption.UNKNOWN
                else -> FilesConsumption.NONE
            }
            if (consumption != expected) wireFail(FilesHandoffWireCode.INVALID_CONSUMPTION)
            return FilesSubmitReply(status, consumption)
        }
    }
}

/** Strict reply codec only. No caller identity, replay reservation or resource lookup occurs here. */
object FilesHandoffWire {
    const val PAGE_SIZE = 32
    const val MAX_DISCOVERY_BYTES = 65536
    const val MAX_SUBMIT_BYTES = 1024
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val descriptorKeys = setOf("hostId", "environmentId", "workspaceId", "resourceId", "label")

    fun validateAfterResourceId(value: String) {
        if (value.isNotEmpty() && !uuid.matches(value)) wireFail(FilesHandoffWireCode.INVALID_CURSOR)
    }

    fun encodeDiscovery(reply: FilesDiscoveryReply): ByteArray = write(MAX_DISCOVERY_BYTES) { writer ->
        writer.name("status").value(reply.status.wireId.toLong())
        if (reply.status == FilesDiscoveryStatus.OK) {
            writer.name("locations").beginArray()
            reply.locations.forEach { descriptor ->
                writer.beginObject()
                writer.name("hostId").value(descriptor.reference.hostId)
                writer.name("environmentId").value(descriptor.reference.environmentId)
                writer.name("workspaceId").value(descriptor.reference.workspaceId)
                writer.name("resourceId").value(descriptor.reference.resourceId)
                writer.name("label").value(descriptor.label)
                writer.endObject()
            }
            writer.endArray()
            writer.name("nextAfterResourceId").value(reply.nextAfterResourceId)
        }
    }

    fun decodeDiscovery(bytes: ByteArray, afterResourceId: String = ""): FilesDiscoveryReply {
        validateAfterResourceId(afterResourceId)
        return read(bytes, MAX_DISCOVERY_BYTES) { reader ->
            val seen = mutableSetOf<String>()
            var status: FilesDiscoveryStatus? = null
            var locations: List<FilesSharedDescriptor>? = null
            var next: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (field(reader, seen, setOf("version", "status", "locations", "nextAfterResourceId"))) {
                    "version" -> version(reader)
                    "status" -> {
                        val value = integer(reader)
                        status = FilesDiscoveryStatus.entries.firstOrNull { it.wireId == value }
                            ?: wireFail(FilesHandoffWireCode.INVALID_STATUS)
                    }
                    "locations" -> {
                        requireToken(reader, JsonToken.BEGIN_ARRAY)
                        reader.beginArray()
                        val items = ArrayList<FilesSharedDescriptor>()
                        while (reader.hasNext()) {
                            if (items.size >= PAGE_SIZE) wireFail(FilesHandoffWireCode.INVALID_PAGE)
                            items += descriptor(reader)
                        }
                        reader.endArray()
                        locations = items
                    }
                    "nextAfterResourceId" -> next = string(reader)
                    else -> wireFail(FilesHandoffWireCode.UNKNOWN_FIELD)
                }
            }
            reader.endObject()
            if (!seen.containsAll(setOf("version", "status"))) wireFail(FilesHandoffWireCode.MISSING_FIELD)
            val actualStatus = status ?: wireFail(FilesHandoffWireCode.MISSING_FIELD)
            if (actualStatus == FilesDiscoveryStatus.OK) {
                if (seen != setOf("version", "status", "locations", "nextAfterResourceId")) wireFail(FilesHandoffWireCode.MISSING_FIELD)
                val reply = FilesDiscoveryReply.success(locations ?: wireFail(FilesHandoffWireCode.MISSING_FIELD), next ?: wireFail(FilesHandoffWireCode.MISSING_FIELD))
                if (reply.locations.any { it.reference.resourceId <= afterResourceId }) wireFail(FilesHandoffWireCode.INVALID_PAGE)
                reply
            } else {
                if (seen != setOf("version", "status")) wireFail(FilesHandoffWireCode.INVALID_PAGE)
                FilesDiscoveryReply.failure(actualStatus)
            }
        }
    }

    fun encodeSubmit(reply: FilesSubmitReply): ByteArray = write(MAX_SUBMIT_BYTES) { writer ->
        writer.name("status").value(reply.status.wireId.toLong())
        writer.name("consumption").value(reply.consumption.wireId.toLong())
    }

    fun decodeSubmit(bytes: ByteArray): FilesSubmitReply = read(bytes, MAX_SUBMIT_BYTES) { reader ->
        val seen = mutableSetOf<String>()
        var status: Int? = null
        var consumption: Int? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (field(reader, seen, setOf("version", "status", "consumption"))) {
                "version" -> version(reader)
                "status" -> status = integer(reader)
                "consumption" -> consumption = integer(reader)
            }
        }
        reader.endObject()
        if (seen != setOf("version", "status", "consumption")) wireFail(FilesHandoffWireCode.MISSING_FIELD)
        FilesSubmitReply.create(FilesSubmitStatus.entries.firstOrNull { it.wireId == status } ?: wireFail(FilesHandoffWireCode.INVALID_STATUS),
            FilesConsumption.entries.firstOrNull { it.wireId == consumption } ?: wireFail(FilesHandoffWireCode.INVALID_CONSUMPTION))
    }

    private fun descriptor(reader: JsonReader): FilesSharedDescriptor {
        requireToken(reader, JsonToken.BEGIN_OBJECT)
        reader.beginObject()
        val seen = mutableSetOf<String>()
        val values = mutableMapOf<String, String>()
        while (reader.hasNext()) {
            val key = field(reader, seen, descriptorKeys)
            values[key] = string(reader)
        }
        reader.endObject()
        if (seen != descriptorKeys) wireFail(FilesHandoffWireCode.MISSING_FIELD)
        return try {
            FilesSharedDescriptor.create(FilesResourceReference.restore(values.getValue("hostId"), values.getValue("environmentId"),
                values.getValue("workspaceId"), values.getValue("resourceId")), values.getValue("label"))
        } catch (_: IllegalArgumentException) { wireFail(FilesHandoffWireCode.INVALID_DESCRIPTOR) }
    }

    private fun field(reader: JsonReader, seen: MutableSet<String>, allowed: Set<String>): String {
        val key = reader.nextName()
        if (key !in allowed) wireFail(FilesHandoffWireCode.UNKNOWN_FIELD)
        if (!seen.add(key)) wireFail(FilesHandoffWireCode.DUPLICATE_FIELD)
        return key
    }
    private fun string(reader: JsonReader): String { requireToken(reader, JsonToken.STRING); return reader.nextString() }
    private fun integer(reader: JsonReader): Int {
        requireToken(reader, JsonToken.NUMBER)
        val value = reader.nextString()
        // Fixed integers use canonical decimal tokens: no exponent, fraction, sign or coercion.
        if (value.length > 2 || value.isEmpty() || value.any { it !in '0'..'9' } || (value.length > 1 && value[0] == '0')) {
            wireFail(FilesHandoffWireCode.WRONG_TYPE)
        }
        return value.toInt()
    }
    private fun version(reader: JsonReader) { if (integer(reader) != 1) wireFail(FilesHandoffWireCode.UNKNOWN_VERSION) }
    private fun requireToken(reader: JsonReader, token: JsonToken) { if (reader.peek() != token) wireFail(FilesHandoffWireCode.WRONG_TYPE) }

    private fun write(maximum: Int, block: (JsonWriter) -> Unit): ByteArray {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.beginObject(); writer.name("version").value(1L); block(writer); writer.endObject()
        }
        val bytes = output.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > maximum) wireFail(FilesHandoffWireCode.TOO_LARGE)
        return bytes
    }
    private fun <T> read(bytes: ByteArray, maximum: Int, block: (JsonReader) -> T): T {
        if (bytes.size > maximum) wireFail(FilesHandoffWireCode.TOO_LARGE)
        val document = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { wireFail(FilesHandoffWireCode.INVALID_UTF8) }
        validateStringLexemes(document)
        try {
            JsonReader(StringReader(document)).use { reader ->
                reader.isLenient = false
                requireToken(reader, JsonToken.BEGIN_OBJECT)
                val result = block(reader)
                if (reader.peek() != JsonToken.END_DOCUMENT) wireFail(FilesHandoffWireCode.INVALID_JSON)
                return result
            }
        } catch (error: FilesHandoffWireException) { throw error }
        catch (_: IOException) { wireFail(FilesHandoffWireCode.INVALID_JSON) }
        catch (_: IllegalStateException) { wireFail(FilesHandoffWireCode.INVALID_JSON) }
        catch (_: IllegalArgumentException) { wireFail(FilesHandoffWireCode.INVALID_JSON) }
    }

    // JsonReader owns structure; this guard rejects Android reader's non-RFC string escapes/control acceptance.
    private fun validateStringLexemes(document: String) {
        if (document.startsWith('\uFEFF')) wireFail(FilesHandoffWireCode.INVALID_JSON)
        var quoted = false
        var index = 0
        while (index < document.length) {
            val char = document[index++]
            if (!quoted) { if (char == '"') quoted = true; continue }
            when {
                char == '"' -> quoted = false
                char.code < 0x20 -> wireFail(FilesHandoffWireCode.INVALID_JSON)
                char == '\\' -> {
                    if (index >= document.length) wireFail(FilesHandoffWireCode.INVALID_JSON)
                    when (document[index++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> {
                            if (document.length - index < 4) wireFail(FilesHandoffWireCode.INVALID_JSON)
                            repeat(4) { if (document[index++] !in "0123456789abcdefABCDEF") wireFail(FilesHandoffWireCode.INVALID_JSON) }
                        }
                        else -> wireFail(FilesHandoffWireCode.INVALID_JSON)
                    }
                }
            }
        }
        if (quoted) wireFail(FilesHandoffWireCode.INVALID_JSON)
    }
}

private fun wireFail(code: FilesHandoffWireCode): Nothing = throw FilesHandoffWireException(code)
