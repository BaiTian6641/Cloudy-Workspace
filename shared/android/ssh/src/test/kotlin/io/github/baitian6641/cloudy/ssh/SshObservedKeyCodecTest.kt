package io.github.baitian6641.cloudy.ssh

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

class SshObservedKeyCodecTest {
    @Test fun standardP256GeneratorPointUsesSshWireFingerprintRatherThanDerEncoding() {
        val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(
            BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
            BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
        )
        val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, parameters))
        val identity = SshObservedKeyCodec.encode(key)
        assertEquals("ecdsa-sha2-nistp256", identity.keyFormat)
        // Independent fixture: SHA256 of SSH strings(format, curve, uncompressed fixed point).
        assertEquals("SHA256:SFMVDOrhOSBssOVH9hIcK/Z2XVf1oSVMjNCL8u2aICY", identity.fingerprint)
        assertEquals(identity, SshObservedKeyCodec.encode(key))
        assertEquals(50, identity.fingerprint.length)
        assertFalse(identity.toString().contains(identity.fingerprint))
    }

    @Test fun unsupportedKeyCannotBecomeAnObservedIdentity() {
        val unsupported = object : PublicKey {
            override fun getAlgorithm(): String = "unsupported-cloudy-test-key"
            override fun getFormat(): String = "test"
            override fun getEncoded(): ByteArray = error("Cloudy must use SSHJ serialization")
        }
        assertThrows(Exception::class.java) { SshObservedKeyCodec.encode(unsupported) }
    }
}
