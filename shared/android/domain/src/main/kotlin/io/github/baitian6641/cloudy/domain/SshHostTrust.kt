package io.github.baitian6641.cloudy.domain

import java.util.Base64

enum class SshTrustRoute(val wireId: String) { Direct("direct") }

enum class SshTrustValidationCode {
    INVALID_HOST, INVALID_PORT, UNSUPPORTED_KEY_FORMAT, INVALID_FINGERPRINT,
    INVALID_GENERATION, INVALID_REVISION, INVALID_ATTEMPT_ID, INVALID_PRESENTATION_GENERATION,
    WRONG_OWNER, WRONG_PEER, STALE_GENERATION, INVALID_CHALLENGE,
}

class SshTrustValidationException(val code: SshTrustValidationCode) : IllegalArgumentException(code.name)

/** Logical peer only: canonicalization never resolves a transport address or network interface. */
class SshPeerIdentity private constructor(val host: String, val port: Int, val route: SshTrustRoute) {
    override fun equals(other: Any?): Boolean = other is SshPeerIdentity &&
        host == other.host && port == other.port && route == other.route
    override fun hashCode(): Int = listOf(host, port, route).hashCode()
    override fun toString(): String = "SshPeerIdentity(redacted)"

    companion object {
        fun create(host: String, port: Int, route: SshTrustRoute = SshTrustRoute.Direct): SshPeerIdentity {
            if (port !in 1..65535) invalid(SshTrustValidationCode.INVALID_PORT)
            val canonical = try { EndpointHost.canonicalize(host) } catch (_: IllegalArgumentException) {
                invalid(SshTrustValidationCode.INVALID_HOST)
            }
            return SshPeerIdentity(canonical, port, route)
        }
    }
}

/** Exact public-key format and public digest, not a signature-algorithm authorization policy. */
class SshHostKeyIdentity private constructor(val keyFormat: String, val fingerprint: String) {
    override fun equals(other: Any?): Boolean = other is SshHostKeyIdentity &&
        keyFormat == other.keyFormat && fingerprint == other.fingerprint
    override fun hashCode(): Int = listOf(keyFormat, fingerprint).hashCode()
    override fun toString(): String = "SshHostKeyIdentity(redacted)"

    companion object {
        private val formats = setOf("ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384",
            "ecdsa-sha2-nistp521", "ssh-rsa")
        private val digestText = Regex("[A-Za-z0-9+/]{43}")

        fun create(keyFormat: String, fingerprint: String): SshHostKeyIdentity {
            if (keyFormat.length !in 1..64 || keyFormat !in formats) invalid(SshTrustValidationCode.UNSUPPORTED_KEY_FORMAT)
            // Bound before decoding, and reject aliases including padding and nonzero unused bits.
            if (fingerprint.length != 50 || !fingerprint.startsWith("SHA256:")) {
                invalid(SshTrustValidationCode.INVALID_FINGERPRINT)
            }
            val body = fingerprint.substring(7)
            if (!digestText.matches(body)) invalid(SshTrustValidationCode.INVALID_FINGERPRINT)
            val bytes = try { Base64.getDecoder().decode(body) } catch (_: IllegalArgumentException) {
                invalid(SshTrustValidationCode.INVALID_FINGERPRINT)
            }
            if (bytes.size != 32 || Base64.getEncoder().withoutPadding().encodeToString(bytes) != body) {
                invalid(SshTrustValidationCode.INVALID_FINGERPRINT)
            }
            return SshHostKeyIdentity(keyFormat, fingerprint)
        }
    }
}

sealed class SshTrustStatus private constructor() {
    class Accepted private constructor(val key: SshHostKeyIdentity) : SshTrustStatus() {
        override fun equals(other: Any?): Boolean = other is Accepted && key == other.key
        override fun hashCode(): Int = 31 + key.hashCode()
        companion object { internal fun create(key: SshHostKeyIdentity): Accepted = Accepted(key) }
    }
    class Revoked private constructor(val lastKey: SshHostKeyIdentity) : SshTrustStatus() {
        override fun equals(other: Any?): Boolean = other is Revoked && lastKey == other.lastKey
        override fun hashCode(): Int = 37 + lastKey.hashCode()
        companion object { internal fun create(lastKey: SshHostKeyIdentity): Revoked = Revoked(lastKey) }
    }
    final override fun toString(): String = "SshTrustStatus(redacted)"
    companion object {
        fun accepted(key: SshHostKeyIdentity): Accepted = Accepted.create(key)
        fun revoked(lastKey: SshHostKeyIdentity): Revoked = Revoked.create(lastKey)
    }
}

/** Persisted metadata, including a retained tombstone; this value releases no credentials. */
class SshTrustSnapshot private constructor(
    val owner: WindowOwner, val generation: String, val peer: SshPeerIdentity,
    val revision: Long, val status: SshTrustStatus,
) {
    override fun equals(other: Any?): Boolean = other is SshTrustSnapshot && owner == other.owner &&
        generation == other.generation && peer == other.peer && revision == other.revision && status == other.status
    override fun hashCode(): Int = listOf(owner, generation, peer, revision, status).hashCode()
    override fun toString(): String = "SshTrustSnapshot(redacted)"
    companion object {
        fun create(owner: WindowOwner, generation: String, peer: SshPeerIdentity,
                   revision: Long, status: SshTrustStatus): SshTrustSnapshot {
            requireUuid(generation, SshTrustValidationCode.INVALID_GENERATION)
            if (revision < 1) invalid(SshTrustValidationCode.INVALID_REVISION)
            return SshTrustSnapshot(owner, generation, peer, revision, status)
        }
    }
}

/** Zero means observed absence, not unconditional enrollment. Storage must compare atomically. */
class SshTrustEditToken private constructor(
    val owner: WindowOwner, val generation: String, val peer: SshPeerIdentity, val expectedRevision: Long,
) {
    override fun equals(other: Any?): Boolean = other is SshTrustEditToken && owner == other.owner &&
        generation == other.generation && peer == other.peer && expectedRevision == other.expectedRevision
    override fun hashCode(): Int = listOf(owner, generation, peer, expectedRevision).hashCode()
    override fun toString(): String = "SshTrustEditToken(redacted)"
    companion object {
        fun create(owner: WindowOwner, generation: String, peer: SshPeerIdentity, expectedRevision: Long): SshTrustEditToken {
            requireUuid(generation, SshTrustValidationCode.INVALID_GENERATION)
            if (expectedRevision < 0) invalid(SshTrustValidationCode.INVALID_REVISION)
            return SshTrustEditToken(owner, generation, peer, expectedRevision)
        }
    }
}

sealed class SshTrustEvaluation private constructor(
    val token: SshTrustEditToken, val observedKey: SshHostKeyIdentity,
) {
    abstract val current: SshTrustSnapshot?
    class Unknown private constructor(token: SshTrustEditToken, observedKey: SshHostKeyIdentity) :
        SshTrustEvaluation(token, observedKey) {
        override val current: SshTrustSnapshot? get() = null
        companion object {
            internal fun create(token: SshTrustEditToken, observedKey: SshHostKeyIdentity): Unknown = Unknown(token, observedKey)
        }
    }
    class Match private constructor(token: SshTrustEditToken, observedKey: SshHostKeyIdentity,
                                    override val current: SshTrustSnapshot) : SshTrustEvaluation(token, observedKey) {
        companion object {
            internal fun create(token: SshTrustEditToken, observedKey: SshHostKeyIdentity, current: SshTrustSnapshot): Match =
                Match(token, observedKey, current)
        }
    }
    class Changed private constructor(token: SshTrustEditToken, observedKey: SshHostKeyIdentity,
                                      override val current: SshTrustSnapshot) : SshTrustEvaluation(token, observedKey) {
        companion object {
            internal fun create(token: SshTrustEditToken, observedKey: SshHostKeyIdentity, current: SshTrustSnapshot): Changed =
                Changed(token, observedKey, current)
        }
    }
    class Revoked private constructor(token: SshTrustEditToken, observedKey: SshHostKeyIdentity,
                                      override val current: SshTrustSnapshot) : SshTrustEvaluation(token, observedKey) {
        companion object {
            internal fun create(token: SshTrustEditToken, observedKey: SshHostKeyIdentity, current: SshTrustSnapshot): Revoked =
                Revoked(token, observedKey, current)
        }
    }
    final override fun equals(other: Any?): Boolean = other is SshTrustEvaluation && javaClass == other.javaClass &&
        token == other.token && observedKey == other.observedKey && current == other.current
    final override fun hashCode(): Int = listOf(javaClass, token, observedKey, current).hashCode()
    final override fun toString(): String = "SshTrustEvaluation(redacted)"
}

object SshHostTrust {
    fun evaluate(owner: WindowOwner, generation: String, peer: SshPeerIdentity,
                 observedKey: SshHostKeyIdentity, current: SshTrustSnapshot?): SshTrustEvaluation {
        val token = SshTrustEditToken.create(owner, generation, peer, current?.revision ?: 0)
        if (current == null) return SshTrustEvaluation.Unknown.create(token, observedKey)
        if (current.owner != owner) invalid(SshTrustValidationCode.WRONG_OWNER)
        if (current.generation != generation) invalid(SshTrustValidationCode.STALE_GENERATION)
        if (current.peer != peer) invalid(SshTrustValidationCode.WRONG_PEER)
        return when (val status = current.status) {
            is SshTrustStatus.Revoked -> SshTrustEvaluation.Revoked.create(token, observedKey, current)
            is SshTrustStatus.Accepted -> if (status.key == observedKey) SshTrustEvaluation.Match.create(token, observedKey, current)
                else SshTrustEvaluation.Changed.create(token, observedKey, current)
        }
    }
}

enum class SshTrustChallengeReason { FIRST_USE, REPLACEMENT, REENROLLMENT }

/**
 * Proposal metadata, never approval. A controller must check the current attempt/presentation,
 * foreground ownership and cancellation before explicitly asking storage to compare-and-set.
 */
class SshTrustChallenge private constructor(
    val attemptId: String, val presentationGeneration: String, val token: SshTrustEditToken,
    val observedKey: SshHostKeyIdentity, val reason: SshTrustChallengeReason, val previousKey: SshHostKeyIdentity?,
) {
    override fun equals(other: Any?): Boolean = other is SshTrustChallenge && attemptId == other.attemptId &&
        presentationGeneration == other.presentationGeneration && token == other.token &&
        observedKey == other.observedKey && reason == other.reason && previousKey == other.previousKey
    override fun hashCode(): Int = listOf(attemptId, presentationGeneration, token, observedKey, reason, previousKey).hashCode()
    override fun toString(): String = "SshTrustChallenge(redacted)"

    companion object {
        fun create(attemptId: String, presentationGeneration: String, evaluation: SshTrustEvaluation): SshTrustChallenge {
            requireUuid(attemptId, SshTrustValidationCode.INVALID_ATTEMPT_ID)
            requireUuid(presentationGeneration, SshTrustValidationCode.INVALID_PRESENTATION_GENERATION)
            val token = evaluation.token
            // Recompute structural consistency rather than confer authority on a proposal object.
            if (evaluation != SshHostTrust.evaluate(token.owner, token.generation, token.peer,
                    evaluation.observedKey, evaluation.current)) invalid(SshTrustValidationCode.INVALID_CHALLENGE)
            val (reason, previous) = when (evaluation) {
                is SshTrustEvaluation.Unknown -> SshTrustChallengeReason.FIRST_USE to null
                is SshTrustEvaluation.Changed -> SshTrustChallengeReason.REPLACEMENT to
                    (evaluation.current.status as SshTrustStatus.Accepted).key
                is SshTrustEvaluation.Revoked -> SshTrustChallengeReason.REENROLLMENT to
                    (evaluation.current.status as SshTrustStatus.Revoked).lastKey
                is SshTrustEvaluation.Match -> invalid(SshTrustValidationCode.INVALID_CHALLENGE)
            }
            return SshTrustChallenge(attemptId, presentationGeneration, token, evaluation.observedKey, reason, previous)
        }
    }
}

private val canonicalTrustUuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
private fun requireUuid(value: String, code: SshTrustValidationCode) {
    if (value.length != 36 || !canonicalTrustUuid.matches(value)) invalid(code)
}
private fun invalid(code: SshTrustValidationCode): Nothing = throw SshTrustValidationException(code)
