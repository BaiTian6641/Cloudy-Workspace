package dev.cloudy.qualification.rdp

import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/** Explicit operator-approved leaf pin scoped to one endpoint, not a CA or TOFU store. */
object RdpTrustPolicy {
    private const val MAX_PEM_BYTES = 128 * 1024
    private val fingerprintPattern = Regex("[0-9a-fA-F]{64}")

    fun accepts(
        pem: ByteArray,
        host: String,
        port: Long,
        flags: Long,
        expectedHost: String,
        expectedPort: Int,
        sha256: String,
        now: Date = Date(),
    ): Boolean {
        // First probe admits only the ordinary direct PEM path. Gateway, redirect,
        // changed, legacy and every future/unknown flag must fail closed.
        if (flags != 0L || pem.isEmpty() || pem.size > MAX_PEM_BYTES) return false
        if (expectedHost.isEmpty() || !host.equals(expectedHost, ignoreCase = true)) return false
        if (expectedPort !in 1..65535 || port != expectedPort.toLong()) return false
        if (!fingerprintPattern.matches(sha256)) return false
        val pemText = pem.toString(Charsets.US_ASCII).trimStart()
        if (!pemText.startsWith("-----BEGIN CERTIFICATE-----")) return false
        return try {
            // generateCertificate consumes the first certificate, the peer leaf;
            // never search a supplied chain for another certificate matching the pin.
            val leaf = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(pem)) as X509Certificate
            leaf.checkValidity(now)
            val expected = ByteArray(32) { index ->
                sha256.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(leaf.encoded))
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
