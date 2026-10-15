package dev.cloudy.qualification.baseline

import org.json.JSONObject
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

internal data class TransferConfigSpec(val url: URI, val expectedLengthBytes: Long, val expectedSha256: String) {
    companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
        const val FIXTURE_ID = "cloudy-uidt-sha256-blocks-v1"
        fun validate(version: Int, fixtureId: String, address: String, length: Long, hash: String): TransferConfigSpec {
            require(version == 1 && fixtureId == FIXTURE_ID) { "ConfigSchema" }
            require(address.length <= 128 && length in 1..MAX_BYTES && hash.matches(Regex("[0-9a-fA-F]{64}"))) { "ConfigBounds" }
            val uri = URI.create(address)
            require(uri.scheme == "https" && uri.rawUserInfo == null && uri.port in 1024..65535 &&
                uri.rawPath == "/fixture.bin" && uri.rawQuery == null && uri.rawFragment == null) { "ConfigEndpoint" }
            val host = requireNotNull(uri.host)
            val octets = host.split('.')
            require(octets.size == 4 && octets.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255 } &&
                octets.first().toInt() in 1..223 && uri.rawAuthority == "$host:${uri.port}") { "ConfigIPv4" }
            return TransferConfigSpec(uri, length, hash.lowercase())
        }
    }
}

internal data class TransferConfig(val spec: TransferConfigSpec, val socketFactory: SSLSocketFactory) {
    companion object {
        const val CONFIG_BYTE_LIMIT = 32 * 1024
        fun read(file: File): TransferConfig {
            val bytes = file.inputStream().use { it.readNBytes(CONFIG_BYTE_LIMIT + 1) }
            require(bytes.size <= CONFIG_BYTE_LIMIT) { "ConfigByteLimit" }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val json = JSONObject(text)
            require(json.keys().asSequence().toSet() == setOf("schemaVersion", "fixtureId", "url", "expectedLengthBytes", "expectedSha256", "caCertificatePem")) { "ConfigFields" }
            require(json.get("schemaVersion") is Int && json.getInt("schemaVersion") == 1) { "ConfigVersion" }
            require(listOf("fixtureId", "url", "expectedSha256", "caCertificatePem").all { json.get(it) is String }) { "ConfigStringTypes" }
            val lengthValue = json.get("expectedLengthBytes")
            require(lengthValue is Int || lengthValue is Long) { "ConfigLengthType" }
            val rawLength = lengthValue.toString()
            require(rawLength.matches(Regex("[1-9][0-9]{0,8}"))) { "ConfigLength" }
            val spec = TransferConfigSpec.validate(1, json.getString("fixtureId"), json.getString("url"),
                rawLength.toLong(), json.getString("expectedSha256"))
            val pem = json.getString("caCertificatePem").trim()
            require(pem.length <= 16 * 1024 && pem.matches(Regex("-----BEGIN CERTIFICATE-----[A-Za-z0-9+/=\\r\\n]+-----END CERTIFICATE-----"))) { "ConfigCertificate" }
            val certificates = CertificateFactory.getInstance("X.509").generateCertificates(pem.byteInputStream(Charsets.US_ASCII))
            require(certificates.size == 1) { "ConfigCertificateCount" }
            val certificate = certificates.single() as X509Certificate
            certificate.checkValidity()
            require(certificate.basicConstraints >= 0 && (certificate.keyUsage?.getOrNull(5) != false)) { "ConfigCARequired" }
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("fixture-ca", certificate)
            }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
            val tls = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
            // Only this connection factory changes trust. The platform's default
            // HTTPS hostname/IP-SAN verifier remains in force, with no global overrides.
            return TransferConfig(spec, tls.socketFactory)
        }
    }
}
