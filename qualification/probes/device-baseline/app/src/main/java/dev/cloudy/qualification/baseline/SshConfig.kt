package dev.cloudy.qualification.baseline

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Base64

internal data class SshConfigSpec(
    val host: String,
    val port: Int,
    val username: String,
    val remoteDirectory: String,
    val expectedHostKeySha256: String,
    val sentinelLength: Int,
    val sentinelSha256: String,
) {
    init { validateFields(host, port, username, remoteDirectory, expectedHostKeySha256, sentinelLength, sentinelSha256) }

    // Host trust belongs to an endpoint, independently of the login name. A
    // changed login still changes configIdentity and invalidates queued consent.
    val endpointId: String = sha256("$host:$port".toByteArray(Charsets.US_ASCII))
    val configIdentity: String = MessageDigest.getInstance("SHA-256").run {
        listOf("cloudy-ssh-probe-config-v1", "1", host, port.toString(), username, remoteDirectory,
            expectedHostKeySha256, sentinelLength.toString(), sentinelSha256).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            update(bytes)
        }
        digest().hex()
    }

    override fun toString(): String = "SshConfigSpec(endpointId=$endpointId, configIdentity=$configIdentity)"

    companion object {
        fun validate(schemaVersion: Int, host: String, port: Int, username: String, remoteDirectory: String,
            expectedHostKeySha256: String, sentinelLength: Int, sentinelSha256: String): SshConfigSpec {
            require(schemaVersion == 1) { "SshConfigVersion" }
            return SshConfigSpec(host, port, username, remoteDirectory, expectedHostKeySha256,
                sentinelLength, sentinelSha256.lowercase())
        }

        private fun validateFields(host: String, port: Int, username: String, directory: String,
            fingerprint: String, length: Int, hash: String) {
            val octets = host.split('.')
            require(host.length in 7..15 && octets.size == 4 && octets.all {
                it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255
            } && octets.first().toInt() in 1..223) { "SshConfigIPv4" }
            require(port in 1024..65535) { "SshConfigPort" }
            require(username.matches(Regex("[a-z_][a-z0-9_-]{0,31}"))) { "SshConfigUsername" }
            require(directory.length in 1..256 && directory.startsWith('/') &&
                directory.all { it.code in 32..126 } &&
                (directory == "/" || directory.substring(1).split('/').all { it.isNotEmpty() && it != "." && it != ".." })) {
                "SshConfigDirectory"
            }
            require(validFingerprint(fingerprint)) { "SshConfigFingerprint" }
            require(length in 1..1024 && hash.matches(Regex("[0-9a-f]{64}"))) { "SshConfigSentinel" }
        }

        fun validFingerprint(value: String): Boolean {
            if (!value.matches(Regex("SHA256:[A-Za-z0-9+/]{43}"))) return false
            val encoded = value.substring(7)
            return try {
                val bytes = Base64.getDecoder().decode(encoded)
                bytes.size == 32 && Base64.getEncoder().withoutPadding().encodeToString(bytes) == encoded
            } catch (_: IllegalArgumentException) { false }
        }

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** Private finite-fixture settings only. The separately bounded key file is
 * owned by the runtime and is never part of this value or its printable form.
 */
internal object SshConfig {
    const val CONFIG_BYTE_LIMIT = 16 * 1024
    private val fields = setOf("schemaVersion", "host", "port", "username", "remoteDirectory",
        "expectedHostKeySha256", "sentinelLength", "sentinelSha256")

    fun read(file: File): SshConfigSpec {
        // Admission precedes the network watchdog. Reject special files that
        // could block open/read; the bounded read still guards ordinary growth.
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS)
        require(attributes.isRegularFile && attributes.size() <= CONFIG_BYTE_LIMIT) { "SshConfigFileTypeOrSize" }
        val bytes = file.inputStream().use { it.readNBytes(CONFIG_BYTE_LIMIT + 1) }
        require(bytes.size <= CONFIG_BYTE_LIMIT) { "SshConfigByteLimit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val values = FlatObject(text).read()
        require(values.keys == fields) { "SshConfigFields" }
        fun string(name: String) = values[name] as? String ?: throw IllegalArgumentException("SshConfigStringType")
        fun integer(name: String) = values[name] as? Int ?: throw IllegalArgumentException("SshConfigIntegerType")
        return SshConfigSpec.validate(integer("schemaVersion"), string("host"), integer("port"),
            string("username"), string("remoteDirectory"), string("expectedHostKeySha256"),
            integer("sentinelLength"), string("sentinelSha256"))
    }

    // This schema is a flat object of strings and integers. Decode only that
    // grammar: Android JSONObject otherwise accepts duplicate keys and relaxed
    // syntax. Byte limits bound parsing; no general JSON engine is introduced.
    private class FlatObject(private val text: String) {
        private var position = 0
        fun read(): Map<String, Any> {
            val result = linkedMapOf<String, Any>()
            take('{')
            whitespace()
            if (!peek('}')) do {
                val key = string()
                require(key in fields && key !in result) { "SshConfigFields" }
                take(':')
                whitespace()
                result[key] = if (peek('"')) string() else integer()
                whitespace()
                if (!peek(',')) break
                take(',')
            } while (true)
            take('}')
            whitespace()
            require(position == text.length) { "SshConfigTrailingData" }
            return result
        }

        private fun string(): String {
            take('"')
            val result = StringBuilder()
            while (position < text.length) {
                val value = text[position++]
                if (value == '"') return result.toString()
                require(value.code >= 32) { "SshConfigStringSyntax" }
                if (value != '\\') result.append(value) else {
                    require(position < text.length) { "SshConfigStringSyntax" }
                    result.append(when (val escape = text[position++]) {
                        '"', '\\', '/' -> escape
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            require(position + 4 <= text.length) { "SshConfigStringSyntax" }
                            val hex = text.substring(position, position + 4)
                            require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "SshConfigStringSyntax" }
                            position += 4
                            hex.toInt(16).toChar()
                        }
                        else -> throw IllegalArgumentException("SshConfigStringSyntax")
                    })
                }
            }
            throw IllegalArgumentException("SshConfigStringSyntax")
        }

        private fun integer(): Int {
            val begin = position
            if (peek('-')) position++
            require(position < text.length && text[position] in '0'..'9') { "SshConfigIntegerSyntax" }
            if (text[position] == '0') position++
            else while (position < text.length && text[position] in '0'..'9') position++
            return text.substring(begin, position).toIntOrNull() ?: throw IllegalArgumentException("SshConfigIntegerRange")
        }

        private fun take(value: Char) {
            whitespace()
            require(peek(value)) { "SshConfigSyntax" }
            position++
        }
        private fun peek(value: Char) = position < text.length && text[position] == value
        private fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
    }
}
