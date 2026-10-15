package dev.cloudy.qualification.rdp

import java.security.cert.CertificateFactory
import java.time.Instant
import java.util.Date
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generated public certificate fixture only: no real endpoint or server qualification.
 * OpenSSL 3.5.5 req -x509, RSA2048/SHA256, CN=Cloudy generated test fixture only,
 * SAN=DNS:fixture.invalid, 3650 days. Generated 2026-10-15; private key was created
 * only in /tmp and deleted immediately. Fixed test times keep validity deterministic.
 */
class RdpTrustPolicyTest {
    private val certificate = checkNotNull(javaClass.getResourceAsStream("/rdp-trust-test-certificate.pem"))
        .use { it.readBytes() }
    private val validTime = Date.from(Instant.parse("2030-01-01T00:00:00Z"))
    private val fingerprint = "fee81e926b1e71a164b9a0098b2b87ae3a25f8717d6816ad8ca8c18d3b3bff17"

    private fun accepts(
        pem: ByteArray = certificate,
        host: String = "fixture.invalid",
        port: Long = 3389,
        flags: Long = 0,
        pin: String = fingerprint,
        now: Date = validTime,
        expectedHost: String = "fixture.invalid",
        expectedPort: Int = 3389,
    ) = RdpTrustPolicy.accepts(pem, host, port, flags, expectedHost, expectedPort, pin, now)

    @Test
    fun validExplicitPinAcceptsEitherHexCaseAndEndpointCase() {
        assertTrue(accepts())
        assertTrue(accepts(host = "FIXTURE.INVALID", pin = fingerprint.uppercase()))
    }

    @Test
    fun wrongOrNonCanonicalPinFails() {
        for (pin in listOf("0".repeat(64), fingerprint.dropLast(1), fingerprint + "0",
            " " + fingerprint, "g" + fingerprint.drop(1), fingerprint.chunked(2).joinToString(":"))) {
            assertFalse("Unexpected pin acceptance", accepts(pin = pin))
        }
    }

    @Test
    fun endpointComparisonDoesNotResolveAliasesOrTrimInput() {
        assertFalse(accepts(host = "other.invalid"))
        assertFalse(accepts(host = "fixture.invalid."))
        assertFalse(accepts(host = " fixture.invalid"))
        assertFalse(accepts(port = 3390))
        assertFalse(accepts(port = 3389L + (1L shl 32)))
        assertFalse(accepts(host = "", expectedHost = ""))
        assertFalse(accepts(port = 0, expectedPort = 0))
    }

    @Test
    fun everyNonzeroFlagFailsClosed() {
        for (flags in longArrayOf(0x02, 0x10, 0x20, 0x01, 0x04, 0x40, -1, 1L shl 40)) {
            assertFalse("Unexpected flag acceptance: $flags", accepts(flags = flags))
        }
    }

    @Test
    fun expiredAndNotYetValidCertificatesFailAtInjectedTimes() {
        assertFalse(accepts(now = Date.from(Instant.parse("2040-01-01T00:00:00Z"))))
        assertFalse(accepts(now = Date.from(Instant.parse("2020-01-01T00:00:00Z"))))
    }

    @Test
    fun malformedOversizedAndDerInputsFail() {
        assertFalse(accepts(pem = byteArrayOf()))
        assertFalse(accepts(pem = "-----BEGIN CERTIFICATE-----\ninvalid\n-----END CERTIFICATE-----".toByteArray()))
        assertFalse(accepts(pem = ByteArray(128 * 1024 + 1)))
        val der = CertificateFactory.getInstance("X.509").generateCertificate(certificate.inputStream()).encoded
        assertFalse(accepts(pem = der))
    }

    @Test
    fun suppliedChainCannotSubstituteForTheFirstLeaf() {
        val invalidFirst = "-----BEGIN CERTIFICATE-----\ninvalid\n-----END CERTIFICATE-----\n".toByteArray()
        assertFalse(accepts(pem = invalidFirst + certificate))
        assertTrue(accepts(pem = certificate + certificate))
    }
}
