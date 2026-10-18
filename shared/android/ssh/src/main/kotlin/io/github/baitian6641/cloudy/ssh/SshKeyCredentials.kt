package io.github.baitian6641.cloudy.ssh

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * App-owned private-key material for exactly one authentication attempt. The key is validated as
 * bounded UTF-8 text on creation, transferred once to the engine, parsed only after the current
 * host pin has been admitted, and wiped on closure. No key text, passphrase or path enters logs,
 * profile serialization or the terminal renderer.
 */
class SshKeyCredentials private constructor(private var material: Material?) : SshSessionCredentials() {
    override val isClosed: Boolean @Synchronized get() = material == null

    /** Exact account metadata from the still-owned copy; never key material. */
    @Synchronized override fun accountName(): String? = material?.username?.concatToString()

    @Synchronized override fun take(): Material? = material.also { material = null }
    @Synchronized override fun close() { material?.close(); material = null }
    override fun toString(): String = "SshKeyCredentials(redacted)"

    internal class Material(
        override val username: CharArray,
        val key: ByteArray,
        val passphrase: CharArray?,
    ) : SshCredentialMaterial {
        override fun close() {
            username.fill('\u0000')
            key.fill(0)
            passphrase?.fill('\u0000')
        }
        override fun toString(): String = "SshKeyMaterial(redacted)"
    }

    companion object {
        const val MAX_USERNAME_UNITS = 256
        const val MAX_KEY_BYTES = 64 * 1024
        const val MAX_PASSPHRASE_UNITS = 4096

        fun create(username: CharArray, key: ByteArray, passphrase: CharArray?): SshKeyCredentials {
            if (username.size !in 1..MAX_USERNAME_UNITS || key.size !in 1..MAX_KEY_BYTES ||
                (passphrase != null && passphrase.size > MAX_PASSPHRASE_UNITS)
            ) invalid()
            val user = username.copyOf()
            val body = key.copyOf()
            val secret = passphrase?.copyOf()
            try {
                if (!valid(user, true) || !valid(secret, false) || !validKey(body)) invalid()
            } catch (failure: Throwable) {
                user.fill('\u0000'); body.fill(0); secret?.fill('\u0000')
                throw failure
            }
            return SshKeyCredentials(Material(user, body, secret))
        }

        /** Same exact-Unicode policy as account names; controls and NUL are not account content. */
        private fun valid(value: CharArray?, rejectControls: Boolean): Boolean {
            if (value == null) return true
            var index = 0
            while (index < value.size) {
                val char = value[index++]
                if (rejectControls && (Character.isISOControl(char) || char == '\u2028' || char == '\u2029')) return false
                if (Character.isLowSurrogate(char)) return false
                if (Character.isHighSurrogate(char) && (index >= value.size || !Character.isLowSurrogate(value[index++]))) return false
            }
            return true
        }

        /** Keys are armor text; reject malformed UTF-8/NUL before any upstream parser sees them. */
        private fun validKey(value: ByteArray): Boolean {
            return try {
                val decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                var index = 0
                while (index < decoded.length) if (decoded.get(index++) == '\u0000') return false
                true
            } catch (_: CharacterCodingException) {
                false
            }
        }

        private fun invalid(): Nothing = throw IllegalArgumentException("INVALID_KEY_CREDENTIALS")
    }
}
