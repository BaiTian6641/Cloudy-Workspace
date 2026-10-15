package dev.cloudy.qualification.baseline

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Private ordinary-shell endpoint settings, independent of finite-fixture consent/history. */
internal data class TerminalConfigSpec(
    val host: String,
    val port: Int,
    val username: String,
    val expectedHostKeySha256: String,
) {
    init {
        val octets = host.split('.')
        require(host.length in 7..15 && octets.size == 4 && octets.all {
            it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255
        } && octets.first().toInt() in 1..223) { "TerminalConfigIPv4" }
        require(port in 1..65535) { "TerminalConfigPort" }
        require(username.matches(Regex("[a-z_][a-z0-9_-]{0,31}"))) { "TerminalConfigUsername" }
        require(SshConfigSpec.validFingerprint(expectedHostKeySha256)) { "TerminalConfigFingerprint" }
    }

    // Trust is endpoint-scoped; identity also fences a changed login or reference fingerprint.
    val endpointId: String = MessageDigest.getInstance("SHA-256")
        .digest("$host:$port".toByteArray(Charsets.US_ASCII)).hex()
    val configIdentity: String = MessageDigest.getInstance("SHA-256").run {
        listOf("cloudy-terminal-config-v1", "1", host, port.toString(), username, expectedHostKeySha256)
            .forEach { field ->
                val bytes = field.toByteArray(Charsets.UTF_8)
                update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                update(bytes)
            }
        digest().hex()
    }

    override fun toString(): String = "TerminalConfigSpec(endpointId=$endpointId, configIdentity=$configIdentity)"

    companion object {
        fun validate(schemaVersion: Int, host: String, port: Int, username: String,
            expectedHostKeySha256: String): TerminalConfigSpec {
            require(schemaVersion == 1) { "TerminalConfigVersion" }
            return TerminalConfigSpec(host, port, username, expectedHostKeySha256)
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

internal object TerminalConfig {
    const val CONFIG_BYTE_LIMIT = 16 * 1024
    private val fields = setOf("schemaVersion", "host", "port", "username", "expectedHostKeySha256")

    fun read(file: File): TerminalConfigSpec {
        // Reject special files before opening; bounded reading also catches regular-file growth.
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS)
        require(attributes.isRegularFile && attributes.size() <= CONFIG_BYTE_LIMIT) { "TerminalConfigFileTypeOrSize" }
        val bytes = file.inputStream().use { it.readNBytes(CONFIG_BYTE_LIMIT + 1) }
        require(bytes.size <= CONFIG_BYTE_LIMIT) { "TerminalConfigByteLimit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val values = FlatObject(text).read()
        require(values.keys == fields) { "TerminalConfigFields" }
        fun string(name: String) = values[name] as? String ?: throw IllegalArgumentException("TerminalConfigStringType")
        fun integer(name: String) = values[name] as? Int ?: throw IllegalArgumentException("TerminalConfigIntegerType")
        return TerminalConfigSpec.validate(integer("schemaVersion"), string("host"), integer("port"),
            string("username"), string("expectedHostKeySha256"))
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
                require(key in fields && key !in result) { "TerminalConfigFields" }
                take(':')
                whitespace()
                result[key] = if (peek('"')) string() else integer()
                whitespace()
                if (!peek(',')) break
                take(',')
            } while (true)
            take('}')
            whitespace()
            require(position == text.length) { "TerminalConfigTrailingData" }
            return result
        }

        private fun string(): String {
            take('"')
            val result = StringBuilder()
            while (position < text.length) {
                val value = text[position++]
                if (value == '"') return result.toString()
                require(value.code >= 32) { "TerminalConfigStringSyntax" }
                if (value != '\\') result.append(value) else {
                    require(position < text.length) { "TerminalConfigStringSyntax" }
                    result.append(when (val escape = text[position++]) {
                        '"', '\\', '/' -> escape
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            require(position + 4 <= text.length) { "TerminalConfigStringSyntax" }
                            val hex = text.substring(position, position + 4)
                            require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "TerminalConfigStringSyntax" }
                            position += 4
                            hex.toInt(16).toChar()
                        }
                        else -> throw IllegalArgumentException("TerminalConfigStringSyntax")
                    })
                }
            }
            throw IllegalArgumentException("TerminalConfigStringSyntax")
        }

        private fun integer(): Int {
            val begin = position
            if (peek('-')) position++
            require(position < text.length && text[position] in '0'..'9') { "TerminalConfigIntegerSyntax" }
            if (text[position] == '0') position++
            else while (position < text.length && text[position] in '0'..'9') position++
            return text.substring(begin, position).toIntOrNull() ?: throw IllegalArgumentException("TerminalConfigIntegerRange")
        }

        private fun take(value: Char) {
            whitespace()
            require(peek(value)) { "TerminalConfigSyntax" }
            position++
        }
        private fun peek(value: Char) = position < text.length && text[position] == value
        private fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
    }
}
