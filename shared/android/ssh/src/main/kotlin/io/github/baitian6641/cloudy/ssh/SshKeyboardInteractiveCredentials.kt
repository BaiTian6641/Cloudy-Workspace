package io.github.baitian6641.cloudy.ssh

/**
 * One keyboard-interactive identity for exactly one attempt. A fixed response answers a single
 * prompt; prompted credentials leave each answer to the supplied UI prompt handler. No
 * serialization, logging or persistence.
 */
class SshKeyboardInteractiveCredentials private constructor(
    private var material: Material?,
) : SshSessionCredentials() {
    override val isClosed: Boolean @Synchronized get() = material == null

    @Synchronized override fun accountName(): String? = material?.username?.concatToString()

    @Synchronized override fun take(): Material? = material.also { material = null }
    @Synchronized override fun close() { material?.close(); material = null }
    override fun toString(): String = "SshKeyboardInteractiveCredentials(redacted)"

    internal class Material(
        override val username: CharArray,
        val response: CharArray?,
    ) : SshCredentialMaterial {
        override fun close() {
            username.fill('\u0000')
            response?.fill('\u0000')
        }
        override fun toString(): String = "SshKeyboardInteractiveMaterial(redacted)"
    }

    companion object {
        const val MAX_USERNAME_UNITS = 256
        const val MAX_RESPONSE_UNITS = 4096

        fun create(username: CharArray, response: CharArray): SshKeyboardInteractiveCredentials {
            if (username.size !in 1..MAX_USERNAME_UNITS || response.size !in 1..MAX_RESPONSE_UNITS) invalid()
            val user = username.copyOf()
            val secret = response.copyOf()
            if (!valid(user) || !valid(secret)) {
                user.fill('\u0000'); secret.fill('\u0000'); invalid()
            }
            return SshKeyboardInteractiveCredentials(Material(user, secret))
        }

        /** Dynamic prompts supply each answer through [SshInteractivePromptHandler]. */
        fun createPrompted(username: CharArray): SshKeyboardInteractiveCredentials {
            if (username.size !in 1..MAX_USERNAME_UNITS) invalid()
            val user = username.copyOf()
            if (!valid(user)) { user.fill('\u0000'); invalid() }
            return SshKeyboardInteractiveCredentials(Material(user, null))
        }

        private fun valid(value: CharArray): Boolean {
            var index = 0
            while (index < value.size) {
                val char = value[index++]
                if (Character.isLowSurrogate(char)) return false
                if (Character.isHighSurrogate(char) && (index >= value.size || !Character.isLowSurrogate(value[index++]))) return false
            }
            return true
        }

        private fun invalid(): Nothing = throw IllegalArgumentException("INVALID_KEYBOARD_INTERACTIVE_CREDENTIALS")
    }
}
