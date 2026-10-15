package dev.cloudy.qualification.baseline

import net.schmizz.sshj.DefaultSecurityProviderConfig

/** The existing finite probe and ordinary terminal share this exact narrow SSH policy. */
internal object SshConnectionPolicy {
    const val IO_TIMEOUT_MILLIS = 10_000
    const val KEX = "ecdh-sha2-nistp256"
    const val HOST_SIGNATURE = "ecdsa-sha2-nistp256"
    const val CIPHER = "aes128-gcm@openssh.com"
    const val MAC = "hmac-sha2-256"

    fun create(): DefaultSecurityProviderConfig = DefaultSecurityProviderConfig().apply {
        // Initialize this class before SSHClient. It disables SSHJ auto-registration, not Android providers.
        keyExchangeFactories = keyExchangeFactories.filter { it.name == KEX || it.name == "ext-info-c" }
        keyAlgorithms = keyAlgorithms.filter { it.name == HOST_SIGNATURE }
        cipherFactories = cipherFactories.filter { it.name == CIPHER }
        setMACFactories(getMACFactories().filter { it.name == MAC })
        compressionFactories = compressionFactories.filter { it.name == "none" }
        if (keyExchangeFactories.none { it.name == KEX } || keyAlgorithms.isEmpty() ||
            cipherFactories.isEmpty() || getMACFactories().isEmpty() || compressionFactories.isEmpty()) {
            throw SshProbeFailure("modern-algorithm-unavailable")
        }
        timeoutMs = IO_TIMEOUT_MILLIS
        channelReadTimeoutMs = IO_TIMEOUT_MILLIS
        channelErrorReadTimeoutMs = IO_TIMEOUT_MILLIS
        maxCircularBufferSize = 1_048_576
    }

}
