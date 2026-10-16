package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity

enum class SshObservationCode {
    INVALID_TIMEOUT, BUSY, QUARANTINED, CANCELLED, TIMED_OUT, CONNECT_FAILED,
    ALGORITHM_REJECTED, KEY_REJECTED, NO_SIGNED_KEY, CLEANUP_UNCERTAIN,
}

/** Observation metadata only: neither authentication nor permission to accept a trust pin. */
sealed interface SshObservationResult {
    class SignedKeyObserved internal constructor(val peer: SshPeerIdentity, val key: SshHostKeyIdentity) : SshObservationResult {
        override fun toString(): String = "SignedKeyObserved(redacted)"
    }
    class Failure internal constructor(val code: SshObservationCode) : SshObservationResult {
        override fun toString(): String = "SshObservationFailure(${code.name})"
    }
}
