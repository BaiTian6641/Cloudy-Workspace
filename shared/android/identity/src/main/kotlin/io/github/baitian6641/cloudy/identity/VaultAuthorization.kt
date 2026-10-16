package io.github.baitian6641.cloudy.identity

import java.util.UUID

enum class VaultOperation { SAVE, USE, DELETE }

/** Deliberately carries no endpoint, account, secret, or platform exception text. */
class VaultAuthorizationException internal constructor() : IllegalStateException("VAULT_AUTH_REQUIRED")

/** A grant cannot be constructed, persisted, or refreshed by a vault caller. */
class VaultGrant internal constructor(
    private val authority: VaultAuthorization,
    private val attempt: VaultAttempt,
) {
    internal fun consume(operation: VaultOperation, id: CredentialId, binding: VaultBinding): VaultLease =
        authority.consume(attempt, operation, id, binding)

    override fun toString(): String = "VaultGrant(redacted)"
}

internal class VaultAttempt(
    val generation: UUID,
    val operation: VaultOperation,
    val id: CredentialId,
    val binding: VaultBinding,
    val startedAt: Long,
) {
    var authorizedAt: Long? = null
    var consumed = false
    override fun toString(): String = "VaultAttempt(redacted)"
}

/** The store rechecks this revocable lease before crypto and before scoped consumption. */
internal class VaultLease(
    private val authority: VaultAuthorization,
    private val attempt: VaultAttempt,
) : AutoCloseable {
    fun check() = authority.check(attempt)
    override fun close() = authority.finish(attempt)
}

/**
 * App admission is independent of Android's timed key authorization. A recent device
 * unlock never mints a grant. Only the current prompt controller invokes success.
 * Synchronization joins main-thread lifecycle revocation with off-main vault work.
 */
internal class VaultAuthorization(
    private val ownerPackage: String,
    private val windowId: UUID,
    private val elapsedRealtime: () -> Long,
) {
    private var visible = false
    private var current: VaultAttempt? = null

    @Synchronized
    fun setVisible(value: Boolean) {
        visible = value
        if (!value) current = null
    }

    @Synchronized
    fun begin(operation: VaultOperation, id: CredentialId, binding: VaultBinding): VaultAttempt {
        current = null
        if (!visible || binding.ownerPackage != ownerPackage) throw VaultAuthorizationException()
        return VaultAttempt(UUID.randomUUID(), operation, id, binding, elapsedRealtime()).also { current = it }
    }

    @Synchronized
    fun succeeded(attempt: VaultAttempt): VaultGrant {
        val now = elapsedRealtime()
        if (!visible || current !== attempt || attempt.authorizedAt != null ||
            !within(now, attempt.startedAt, PROMPT_TIMEOUT_MS)
        ) throw VaultAuthorizationException()
        attempt.authorizedAt = now
        return VaultGrant(this, attempt)
    }

    @Synchronized
    fun cancel() { current = null }

    @Synchronized
    fun consume(
        attempt: VaultAttempt,
        operation: VaultOperation,
        id: CredentialId,
        binding: VaultBinding,
    ): VaultLease {
        check(attempt)
        if (attempt.consumed || attempt.operation != operation || attempt.id != id ||
            attempt.binding != binding || binding.ownerPackage != ownerPackage
        ) throw VaultAuthorizationException()
        attempt.consumed = true
        return VaultLease(this, attempt)
    }

    @Synchronized
    fun check(attempt: VaultAttempt) {
        val authorizedAt = attempt.authorizedAt
        if (!visible || current !== attempt || authorizedAt == null ||
            !within(elapsedRealtime(), authorizedAt, USE_TIMEOUT_MS)
        ) throw VaultAuthorizationException()
    }

    @Synchronized
    fun finish(attempt: VaultAttempt) {
        // A late completion must never cancel a newer prompt in the same window.
        if (current === attempt) current = null
    }

    override fun toString(): String = "VaultAuthorization(window=$windowId)"

    private fun within(now: Long, start: Long, duration: Long): Boolean =
        now >= start && now - start < duration

    companion object {
        internal const val PROMPT_TIMEOUT_MS = 120_000L
        internal const val USE_TIMEOUT_MS = 30_000L
    }
}
