package io.github.baitian6641.cloudy.ssh

/** Mutable, single-transfer per-session credentials. No serialization or diagnostic plaintext. */
class SshPasswordCredentials private constructor(private var material: Material?) : AutoCloseable {
    val isClosed: Boolean @Synchronized get() = material == null

    /** Transfers owned arrays once; the engine must close the returned material on every path. */
    @Synchronized internal fun take(): Material? = material.also { material = null }
    @Synchronized override fun close() { material?.close(); material = null }
    override fun toString(): String = "SshPasswordCredentials(redacted)"

    internal class Material(val username: CharArray, val password: CharArray) : AutoCloseable {
        override fun close() { username.fill('\u0000'); password.fill('\u0000') }
        override fun toString(): String = "SshPasswordMaterial(redacted)"
    }

    companion object {
        const val MAX_USERNAME_UNITS = 256
        const val MAX_PASSWORD_UNITS = 4096
        fun create(username: CharArray, password: CharArray): SshPasswordCredentials {
            // Snapshot before validation so caller changes cannot bypass the validated boundary.
            // Bounds precede copies; password controls/NUL are valid length-delimited SSH content.
            if (username.size !in 1..MAX_USERNAME_UNITS || password.size !in 1..MAX_PASSWORD_UNITS) invalid()
            val user = username.copyOf()
            val secret = password.copyOf()
            if (!valid(user, true) || !valid(secret, false)) {
                user.fill('\u0000'); secret.fill('\u0000'); invalid()
            }
            return SshPasswordCredentials(Material(user, secret))
        }
        private fun valid(value: CharArray, rejectControls: Boolean): Boolean {
            var index = 0
            while (index < value.size) {
                val char = value[index++]
                if (rejectControls && (Character.isISOControl(char) || char == '\u2028' || char == '\u2029')) return false
                if (Character.isLowSurrogate(char)) return false
                if (Character.isHighSurrogate(char) && (index >= value.size || !Character.isLowSurrogate(value[index++]))) return false
            }
            return true
        }
        private fun invalid(): Nothing = throw IllegalArgumentException("INVALID_CREDENTIALS")
    }
}
