package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.util.concurrent.atomic.AtomicBoolean

/** Engine-issued only after actual authentication. It is authority by object identity, not text. */
class SshAuthenticatedOwnership internal constructor(
    val peer: SshPeerIdentity,
    val key: SshHostKeyIdentity,
    val cancellation: SshObservationCancellation,
) {
    private val retired = AtomicBoolean(false)
    val isRetired: Boolean get() = retired.get()
    internal fun markRetired() { retired.set(true) }
    override fun toString() = "SshAuthenticatedOwnership(redacted)"
}
