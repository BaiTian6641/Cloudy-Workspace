package io.github.baitian6641.cloudy.domain

import java.util.UUID

enum class SshObservationCode {
    INVALID_PROFILE, WRONG_OWNER, INVALID_LIFETIME, INVALID_TIME, NOT_FOREGROUND,
    NO_SELECTION, STALE_ATTEMPT, STALE_PRESENTATION, PROFILE_CHANGED, EXPIRED, WRONG_PEER, CLOSED,
}

class SshObservationException(val code: SshObservationCode) : IllegalArgumentException(code.name)

sealed interface SshObservationResult<out T> {
    data class Success<T>(val value: T) : SshObservationResult<T> {
        override fun toString(): String = "SshObservationResult.Success(redacted)"
    }
    data class Failure(val code: SshObservationCode) : SshObservationResult<Nothing>
}

/** Exact committed profile identity, including its independent profile-store generation. */
class SshObservationProfile private constructor(val generation: String, val profile: ConnectionProfile) {
    val peer: SshPeerIdentity = SshPeerIdentity.create(profile.host, profile.port)
    override fun equals(other: Any?): Boolean = other is SshObservationProfile && generation == other.generation &&
        profile.id == other.profile.id && profile.revision == other.profile.revision && profile.owner == other.profile.owner &&
        profile.label == other.profile.label && profile.protocol == other.profile.protocol && peer == other.peer
    override fun hashCode(): Int = listOf(generation, profile.id, profile.revision, profile.owner,
        profile.label, profile.protocol, peer).hashCode()
    override fun toString(): String = "SshObservationProfile(redacted)"

    companion object {
        fun create(profileGeneration: String, profile: ConnectionProfile): SshObservationProfile {
            if (!observationUuid.matches(profileGeneration) || profile.protocol != ConnectionProtocol.SSH) {
                throw SshObservationException(SshObservationCode.INVALID_PROFILE)
            }
            return SshObservationProfile(profileGeneration, profile)
        }
    }
}

/** In-memory ticket, issued only by begin; UUID text is never sufficient to reconstruct it. */
class SshObservationAttempt internal constructor(
    val attemptId: String, val profile: SshObservationProfile, val expiresAtMillis: Long,
) {
    override fun toString(): String = "SshObservationAttempt(redacted)"
}

class SshObservationChallenge internal constructor(
    val challenge: SshTrustChallenge, val profile: SshObservationProfile, val expiresAtMillis: Long,
) {
    override fun toString(): String = "SshObservationChallenge(redacted)"
}

sealed interface SshObservationCompletion {
    class Matched internal constructor(val evaluation: SshTrustEvaluation.Match) : SshObservationCompletion {
        override fun toString(): String = "SshObservationCompletion.Matched(redacted)"
    }
    class Challenge internal constructor(val presentation: SshObservationChallenge) : SshObservationCompletion {
        override fun toString(): String = "SshObservationCompletion.Challenge(redacted)"
    }
}

/** One admitted persistence proposal, not a storage result, connection continuation or credential grant. */
class SshObservationApproval internal constructor(
    val challenge: SshTrustChallenge, val profile: SshObservationProfile,
) {
    override fun toString(): String = "SshObservationApproval(redacted)"
}

/**
 * Platform-independent, in-memory admission policy. The owning platform must supply its trusted
 * monotonic millisecond clock, lifecycle/selection updates and a freshly loaded profile at approval.
 * No restore API, transport, storage, authentication or implicit retry exists here. Synchronization
 * orders pause/cancel against approval admission; it cannot roll back a previously admitted CAS.
 */
class SshObservationPresentation(
    private val owner: WindowOwner,
    private val lifetimeMillis: Long = MAX_LIFETIME_MILLIS,
) {
    private var foreground = false
    private var closed = false
    private var selected: SshObservationProfile? = null
    private var attempt: SshObservationAttempt? = null
    private var pending: SshObservationChallenge? = null
    private var lastTime: Long? = null

    init {
        if (lifetimeMillis !in 1..MAX_LIFETIME_MILLIS) throw SshObservationException(SshObservationCode.INVALID_LIFETIME)
    }

    val currentAttempt: SshObservationAttempt? @Synchronized get() = attempt
    val presentation: SshObservationChallenge? @Synchronized get() = pending

    @Synchronized fun select(profile: SshObservationProfile?): SshObservationResult<Unit> {
        if (closed) return failure(SshObservationCode.CLOSED)
        if (profile != null && profile.profile.owner != owner) {
            retire()
            selected = null
            return failure(SshObservationCode.WRONG_OWNER)
        }
        if (selected != profile) retire()
        selected = profile
        return SshObservationResult.Success(Unit)
    }

    @Synchronized fun resume(): SshObservationResult<Unit> {
        if (closed) return failure(SshObservationCode.CLOSED)
        foreground = true
        return SshObservationResult.Success(Unit)
    }

    @Synchronized fun pause() { foreground = false; retire() }
    @Synchronized fun cancel() { retire() }
    @Synchronized fun close() { closed = true; foreground = false; selected = null; retire() }

    @Synchronized fun begin(nowMillis: Long): SshObservationResult<SshObservationAttempt> {
        if (closed) return failure(SshObservationCode.CLOSED)
        if (!foreground) return failure(SshObservationCode.NOT_FOREGROUND)
        val profile = selected ?: return failure(SshObservationCode.NO_SELECTION)
        // Every explicit start supersedes an old observation, even if the peer is unchanged.
        retire()
        if (!timeValid(nowMillis)) return failure(SshObservationCode.INVALID_TIME)
        val deadline = deadline(nowMillis) ?: return failure(SshObservationCode.INVALID_TIME)
        return SshObservationResult.Success(SshObservationAttempt(UUID.randomUUID().toString(), profile, deadline)
            .also { attempt = it })
    }

    @Synchronized fun complete(
        ticket: SshObservationAttempt, evaluation: SshTrustEvaluation, nowMillis: Long,
    ): SshObservationResult<SshObservationCompletion> {
        if (closed) return failure(SshObservationCode.CLOSED)
        // A delayed completion cannot retire a newer attempt, even with an older timestamp.
        if (attempt !== ticket || pending != null) return failure(SshObservationCode.STALE_ATTEMPT)
        if (!foreground) { retire(); return failure(SshObservationCode.NOT_FOREGROUND) }
        if (selected != ticket.profile) { retire(); return failure(SshObservationCode.PROFILE_CHANGED) }
        if (!timeValid(nowMillis)) { retire(); return failure(SshObservationCode.INVALID_TIME) }
        if (nowMillis >= ticket.expiresAtMillis) { retire(); return failure(SshObservationCode.EXPIRED) }
        if (evaluation.token.owner != owner || evaluation.token.peer != ticket.profile.peer) {
            retire()
            return failure(if (evaluation.token.owner != owner) SshObservationCode.WRONG_OWNER else SshObservationCode.WRONG_PEER)
        }
        if (evaluation is SshTrustEvaluation.Match) {
            retire()
            return SshObservationResult.Success(SshObservationCompletion.Matched(evaluation))
        }
        val expires = deadline(nowMillis) ?: run { retire(); return failure(SshObservationCode.INVALID_TIME) }
        val proposal = SshTrustChallenge.create(ticket.attemptId, UUID.randomUUID().toString(), evaluation)
        val value = SshObservationChallenge(proposal, ticket.profile, expires)
        pending = value
        return SshObservationResult.Success(SshObservationCompletion.Challenge(value))
    }

    @Synchronized fun approve(
        presentationGeneration: String, currentProfile: SshObservationProfile, nowMillis: Long,
    ): SshObservationResult<SshObservationApproval> {
        if (closed) return failure(SshObservationCode.CLOSED)
        val value = pending ?: return failure(SshObservationCode.STALE_PRESENTATION)
        if (value.challenge.presentationGeneration != presentationGeneration) return failure(SshObservationCode.STALE_PRESENTATION)
        if (!foreground) { retire(); return failure(SshObservationCode.NOT_FOREGROUND) }
        if (selected != value.profile || currentProfile != value.profile) {
            retire(); return failure(SshObservationCode.PROFILE_CHANGED)
        }
        if (!timeValid(nowMillis)) { retire(); return failure(SshObservationCode.INVALID_TIME) }
        if (nowMillis >= value.expiresAtMillis) { retire(); return failure(SshObservationCode.EXPIRED) }
        // Consume before returning: another approve, pause or late completion cannot reuse it.
        retire()
        return SshObservationResult.Success(SshObservationApproval(value.challenge, value.profile))
    }

    private fun retire() { attempt = null; pending = null }
    private fun timeValid(now: Long): Boolean {
        if (now < 0 || lastTime?.let { now < it } == true) return false
        lastTime = now
        return true
    }
    private fun deadline(now: Long): Long? = if (now > Long.MAX_VALUE - lifetimeMillis) null else now + lifetimeMillis
    private fun failure(code: SshObservationCode) = SshObservationResult.Failure(code)

    companion object { const val MAX_LIFETIME_MILLIS = 120_000L }
}

private val observationUuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
