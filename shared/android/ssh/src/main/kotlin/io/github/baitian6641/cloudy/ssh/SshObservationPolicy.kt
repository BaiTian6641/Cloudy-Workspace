package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.common.LoggerFactory

/** Same finite P0 policy; domain support for other key formats does not enable their negotiation. */
internal object SshObservationPolicy {
    const val KEX = "ecdh-sha2-nistp256"
    const val SIGNATURE = "ecdsa-sha2-nistp256"
    const val CIPHER = "aes128-gcm@openssh.com"
    const val MAC = "hmac-sha2-256"

    fun create(logging: LoggerFactory): DefaultSecurityProviderConfig = DefaultSecurityProviderConfig().apply {
        // This class disables SSHJ's BC auto-registration before SSHClient is constructed.
        loggerFactory = logging
        keyExchangeFactories = keyExchangeFactories.filter { it.name == KEX || it.name == "ext-info-c" }
        keyAlgorithms = keyAlgorithms.filter { it.name == SIGNATURE }
        cipherFactories = cipherFactories.filter { it.name == CIPHER }
        setMACFactories(getMACFactories().filter { it.name == MAC })
        compressionFactories = compressionFactories.filter { it.name == "none" }
        check(keyExchangeFactories.any { it.name == KEX } && keyAlgorithms.isNotEmpty() &&
            cipherFactories.isNotEmpty() && getMACFactories().isNotEmpty() && compressionFactories.isNotEmpty())
        timeoutMs = 10_000
        channelReadTimeoutMs = 10_000
        channelErrorReadTimeoutMs = 10_000
        maxCircularBufferSize = 1_048_576
    }
}
