package io.github.baitian6641.cloudy.identity

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Bounded wire framing only. Encryption and authentication belong to the Keystore adapter. */
object VaultCodec {
    private const val MAGIC = 0x43575631 // CWV1
    private const val PAYLOAD_MAGIC = 0x43575031 // CWP1
    private const val SCHEMA = 1
    private const val AES_GCM = 1
    private const val MAX_PAYLOAD_BYTES = 4 + 12 + (256 + 256 + 4096) * 3
    private const val HEADER_BYTES = 4 + 1 + 1 + 16 + 4 + 1 + 12 + 4

    fun encodeEnvelope(envelope: VaultEnvelope): ByteArray {
        val uuid = UUID.fromString(envelope.id.value)
        val cipher = envelope.copyCiphertext()
        val iv = envelope.copyIv()
        return try {
            ByteBuffer.allocate(HEADER_BYTES + cipher.size).putInt(MAGIC).put(SCHEMA.toByte())
                .put(AES_GCM.toByte()).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
                .putInt(envelope.keyVersion).put(12.toByte()).put(iv).putInt(cipher.size).put(cipher).array()
        } finally { cipher.fill(0); iv.fill(0) }
    }

    fun decodeEnvelope(bytes: ByteArray): VaultEnvelope {
        require(bytes.size in HEADER_BYTES + 16..HEADER_BYTES + VaultEnvelope.MAX_CIPHERTEXT_BYTES) {
            "Invalid vault envelope"
        }
        val input = ByteBuffer.wrap(bytes)
        require(input.int == MAGIC && input.get().toInt() == SCHEMA && input.get().toInt() == AES_GCM) {
            "Unknown vault format"
        }
        val id = CredentialId.parse(UUID(input.long, input.long).toString())
        val version = input.int
        require(version == 1 && input.get().toInt() == 12) { "Invalid vault envelope" }
        val iv = ByteArray(12).also(input::get)
        var ciphertext: ByteArray? = null
        return try {
            val size = input.int
            require(size in 16..VaultEnvelope.MAX_CIPHERTEXT_BYTES && input.remaining() == size) {
                "Invalid vault envelope length"
            }
            val cipher = ByteArray(size).also(input::get)
            ciphertext = cipher
            VaultEnvelope.create(id, version, iv, cipher)
        } finally { iv.fill(0); ciphertext?.fill(0) }
    }

    fun aad(binding: VaultBinding, id: CredentialId, keyVersion: Int = 1): ByteArray {
        require(keyVersion == 1) { "Unknown key version" }
        val fields = listOf(binding.ownerPackage, binding.protocol.name, binding.host,
            binding.role.name, binding.accountBindingId, id.value)
        val encoded = fields.map { it.toByteArray(StandardCharsets.UTF_8) }
        val output = ByteBuffer.allocate(12 + encoded.sumOf { 4 + it.size })
        output.putInt(SCHEMA).putInt(keyVersion).putInt(binding.port)
        for (field in encoded) output.putInt(field.size).put(field)
        return output.array()
    }

    /** Returned mutable bytes are owned by the caller and must be wiped after crypto use. */
    fun encodePayload(payload: SensitivePayload): ByteArray {
        val chars = mutableListOf<CharArray>()
        val fields = mutableListOf<ByteArray>()
        try {
            chars += payload.copyUsername(); chars += payload.copyDomain(); chars += payload.copyPassword()
            for (field in chars) fields += utf8(field)
            val size = 16 + fields.sumOf { it.size }
            require(size <= MAX_PAYLOAD_BYTES) { "Credential payload too large" }
            val output = ByteBuffer.allocate(size).putInt(PAYLOAD_MAGIC)
            for (field in fields) output.putInt(field.size).put(field)
            return output.array()
        } finally { chars.forEach { it.fill('\u0000') }; fields.forEach { it.fill(0) } }
    }

    fun decodePayload(bytes: ByteArray): SensitivePayload {
        require(bytes.size in 16..MAX_PAYLOAD_BYTES) { "Invalid credential payload size" }
        val input = ByteBuffer.wrap(bytes)
        require(input.int == PAYLOAD_MAGIC) { "Unknown credential payload" }
        val fields = mutableListOf<CharArray>()
        try {
            for (limit in listOf(SensitivePayload.MAX_IDENTITY_CHARS,
                    SensitivePayload.MAX_IDENTITY_CHARS, SensitivePayload.MAX_PASSWORD_CHARS)) {
                require(input.remaining() >= 4) { "Invalid credential field length" }
                val size = input.int
                require(size in 0..limit * 3 && input.remaining() >= size) { "Invalid credential field length" }
                val raw = ByteArray(size).also(input::get)
                try {
                    val decoded = utf8(raw)
                    fields += decoded // Register ownership before any subsequent rejection.
                    require(decoded.size <= limit) { "Credential field too large" }
                }
                finally { raw.fill(0) }
            }
            require(!input.hasRemaining()) { "Trailing credential payload" }
            return SensitivePayload.create(fields[0], fields[1], fields[2])
        } finally { fields.forEach { it.fill('\u0000') } }
    }

    private fun utf8(chars: CharArray): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer = ByteBuffer.allocate(chars.size * 3)
        try {
            encoder.encode(CharBuffer.wrap(chars), buffer, true).throwExceptionIfError()
            encoder.flush(buffer).throwExceptionIfError()
            buffer.flip()
            return ByteArray(buffer.remaining()).also(buffer::get)
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Invalid credential Unicode")
        } finally { buffer.array().fill(0) }
    }

    private fun utf8(bytes: ByteArray): CharArray {
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer = CharBuffer.allocate(bytes.size)
        try {
            decoder.decode(ByteBuffer.wrap(bytes), buffer, true).throwExceptionIfError()
            decoder.flush(buffer).throwExceptionIfError()
            buffer.flip()
            return CharArray(buffer.remaining()).also(buffer::get)
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Invalid credential encoding")
        } finally { buffer.array().fill('\u0000') }
    }

    private fun java.nio.charset.CoderResult.throwExceptionIfError() {
        if (isError) throwException()
        check(isUnderflow) { "Credential codec capacity invariant" }
    }
}
