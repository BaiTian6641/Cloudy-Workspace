package io.github.baitian6641.cloudy.identity

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VaultCodecTest {
    private val id = CredentialId.parse("12345678-1234-4234-8234-123456789abc")
    private val binding = VaultBinding.create("io.cloudy.console", VaultProtocol.RDP,
        "example.org", 3389, VaultRole.TARGET, id.value)

    @Test fun envelopeRoundTripAndStrictFraming() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(32) { (it + 10).toByte() }
        val envelope = VaultEnvelope.create(id, 1, iv, ciphertext)
        iv.fill(0); ciphertext.fill(0)
        val encoded = VaultCodec.encodeEnvelope(envelope)
        val restored = VaultCodec.decodeEnvelope(encoded)
        assertEquals(id, restored.id)
        assertArrayEquals(envelope.copyIv(), restored.copyIv())
        assertArrayEquals(envelope.copyCiphertext(), restored.copyCiphertext())
        for (bad in listOf(encoded.copyOf(8), encoded + 0.toByte(),
                encoded.copyOf().also { it[0] = 0 }, encoded.copyOf().also { it[4] = 2 },
                encoded.copyOf().also { it[5] = 2 }, encoded.copyOf().also { it[26] = 2 },
                encoded.copyOf().also { ByteBuffer.wrap(it).putInt(39, Int.MAX_VALUE) })) {
            assertThrows(IllegalArgumentException::class.java) { VaultCodec.decodeEnvelope(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { VaultEnvelope.create(id, 2, ByteArray(12), ByteArray(16)) }
    }

    @Test fun aadIsDeterministicAndSeparatesEveryBindingDimension() {
        val aad = VaultCodec.aad(binding, id)
        assertArrayEquals(aad, VaultCodec.aad(binding, id))
        val variants = listOf(
            VaultBinding.create("io.cloudy.desktop", binding.protocol, binding.host, binding.port, binding.role, id.value),
            VaultBinding.create(binding.ownerPackage, VaultProtocol.VNC, binding.host, binding.port, binding.role, id.value),
            VaultBinding.create(binding.ownerPackage, binding.protocol, "other.org", binding.port, binding.role, id.value),
            VaultBinding.create(binding.ownerPackage, binding.protocol, binding.host, 3390, binding.role, id.value),
            VaultBinding.create(binding.ownerPackage, binding.protocol, binding.host, binding.port, VaultRole.GATEWAY, id.value),
            VaultBinding.create(binding.ownerPackage, binding.protocol, binding.host, binding.port, binding.role, CredentialId.create().value),
        )
        for (variant in variants) assertFalse(aad.contentEquals(VaultCodec.aad(variant, id)))
        assertFalse(aad.contentEquals(VaultCodec.aad(binding, CredentialId.create())))
    }

    @Test fun strictUtf8PayloadRoundTripAndMalformedLengthsReject() {
        val payload = SensitivePayload.create("用户😀".toCharArray(), "域".toCharArray(), "sëcret🔒".toCharArray())
        val bytes = VaultCodec.encodePayload(payload)
        val restored = VaultCodec.decodePayload(bytes)
        try {
            assertArrayEquals(payload.copyUsername(), restored.copyUsername())
            assertArrayEquals(payload.copyDomain(), restored.copyDomain())
            assertArrayEquals(payload.copyPassword(), restored.copyPassword())
            for (bad in listOf(bytes + 0.toByte(), bytes.copyOf(9),
                    bytes.copyOf().also { ByteBuffer.wrap(it).putInt(4, -1) },
                    bytes.copyOf().also { it[8] = 0xff.toByte() })) {
                assertThrows(IllegalArgumentException::class.java) { VaultCodec.decodePayload(bad) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                SensitivePayload.create(charArrayOf('\uD800'), charArrayOf(), charArrayOf('x'))
            }
            assertThrows(IllegalArgumentException::class.java) {
                SensitivePayload.create(charArrayOf(), charArrayOf(), CharArray(4097) { 'x' })
            }
        } finally { payload.close(); restored.close(); bytes.fill(0) }
    }
}
