package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.FilesResourceState
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import io.github.baitian6641.cloudy.domain.HandoffFreshness
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Private one-use navigation only. Nothing here restores a connection or grants authentication. */
internal object FilesLocationTickets {
    const val EXTRA = "io.github.baitian6641.cloudy.FILES_LOCATION_TICKET"
    private const val CAPACITY = 16
    private const val LIFETIME_MILLIS = 30_000L
    private class Origin(val caller: VerifiedHandoffCaller, val envelope: HandoffEnvelope)
    private class Ticket(val packageName: String, val registration: FilesResourceRegistration,
                         val expiresAt: Long, val origin: Origin?)
    private val tickets = mutableMapOf<String, Ticket>()

    /** Off main, after an explicit own-location Open action; resolve again before minting. */
    fun issue(context: Context, registration: FilesResourceRegistration): FilesResourceResult<String> =
        issueChecked(context, registration, null)

    /** A sibling receives no token. Its captured identity and envelope remain process-private. */
    fun issueHandoff(context: Context, registration: FilesResourceRegistration,
                     caller: VerifiedHandoffCaller, envelope: HandoffEnvelope): FilesResourceResult<String> {
        val origin = Origin(caller, envelope)
        val result = issueChecked(context, registration, origin)
        // PM/crypto stays outside the shared storage coordination lock. A concurrent resource
        // change is independently denied at consume and again at fresh credential admission.
        if (result is FilesResourceResult.Success && !validOrigin(context, registration, origin)) {
            discard(result.value)
            return FilesResourceResult.Failure(FilesResourceCode.WRONG_OWNER)
        }
        return result
    }

    private fun issueChecked(context: Context, registration: FilesResourceRegistration,
                             origin: Origin?): FilesResourceResult<String> {
        eligibility(context)?.let { return FilesResourceResult.Failure(it) }
        if (registration.state == FilesResourceState.REVOKED) return FilesResourceResult.Failure(FilesResourceCode.INVALID_STATE)
        if (!validOrigin(context, registration, origin)) return FilesResourceResult.Failure(FilesResourceCode.WRONG_OWNER)
        if (!SshAuthenticationRegistry.lock.tryLock()) return FilesResourceResult.Failure(FilesResourceCode.BUSY)
        try {
            val resolved = FilesResourceStore(context).resolve(registration.reference, registration.state)
            if (resolved is FilesResourceResult.Failure) return resolved
            if ((resolved as FilesResourceResult.Success).value != registration) {
                return FilesResourceResult.Failure(FilesResourceCode.CONFLICT)
            }
            val now = SystemClock.elapsedRealtime()
            val lifetime = origin?.let { Duration.between(Instant.now(), it.envelope.expiresAt).toMillis()
                .coerceAtMost(LIFETIME_MILLIS) } ?: LIFETIME_MILLIS
            if (lifetime <= 0) return FilesResourceResult.Failure(FilesResourceCode.NOT_FOUND)
            if (now < 0 || now > Long.MAX_VALUE - LIFETIME_MILLIS) {
                return FilesResourceResult.Failure(FilesResourceCode.STORE_UNAVAILABLE)
            }
            synchronized(tickets) {
                tickets.entries.removeAll { now >= it.value.expiresAt }
                if (tickets.size >= CAPACITY) return FilesResourceResult.Failure(FilesResourceCode.CAPACITY)
                val token = UUID.randomUUID().toString()
                check(!tickets.containsKey(token))
                tickets[token] = Ticket(context.packageName, registration, now + lifetime, origin)
                return FilesResourceResult.Success(token)
            }
        } catch (_: Exception) { return FilesResourceResult.Failure(FilesResourceCode.STORE_UNAVAILABLE) }
        finally { SshAuthenticationRegistry.lock.unlock() }
    }

    /** Removal precedes all delayed validation: a failed consume never restores ticket authority. */
    fun consume(context: Context, token: String): FilesResourceResult<FilesResourceRegistration> {
        eligibility(context)?.let { return FilesResourceResult.Failure(it) }
        if (!uuid.matches(token)) return FilesResourceResult.Failure(FilesResourceCode.INVALID_INPUT)
        val ticket = synchronized(tickets) { tickets.remove(token) }
            ?: return FilesResourceResult.Failure(FilesResourceCode.NOT_FOUND)
        if (ticket.packageName != context.packageName) return FilesResourceResult.Failure(FilesResourceCode.WRONG_OWNER)
        val now = SystemClock.elapsedRealtime()
        if (now < 0 || now >= ticket.expiresAt) return FilesResourceResult.Failure(FilesResourceCode.NOT_FOUND)
        if (!validOrigin(context, ticket.registration, ticket.origin)) return FilesResourceResult.Failure(FilesResourceCode.WRONG_OWNER)
        if (!SshAuthenticationRegistry.lock.tryLock()) return FilesResourceResult.Failure(FilesResourceCode.BUSY)
        val result = try {
            val registration = ticket.registration
            when (val resolved = FilesResourceStore(context).resolve(registration.reference, registration.state)) {
                is FilesResourceResult.Failure -> resolved
                is FilesResourceResult.Success -> when {
                    SystemClock.elapsedRealtime() >= ticket.expiresAt -> FilesResourceResult.Failure(FilesResourceCode.NOT_FOUND)
                    resolved.value == registration -> resolved
                    else -> FilesResourceResult.Failure(FilesResourceCode.CONFLICT)
                }
            }
        } catch (_: Exception) { return FilesResourceResult.Failure(FilesResourceCode.STORE_UNAVAILABLE) }
        finally { SshAuthenticationRegistry.lock.unlock() }
        if (result is FilesResourceResult.Success &&
            (!validOrigin(context, ticket.registration, ticket.origin) || SystemClock.elapsedRealtime() >= ticket.expiresAt)) {
            return FilesResourceResult.Failure(FilesResourceCode.WRONG_OWNER)
        }
        return result
    }

    /** Cancellation removes only the opaque ticket; durable replay reservations are never undone. */
    fun discard(token: String) { synchronized(tickets) { tickets.remove(token) } }

    private fun validOrigin(context: Context, registration: FilesResourceRegistration, origin: Origin?): Boolean {
        if (origin == null) return true
        val envelope = origin.envelope
        val reference = registration.reference
        return registration.state == FilesResourceState.SHARED &&
            envelope.action == io.github.baitian6641.cloudy.domain.HandoffAction.OPEN_FILES &&
            envelope.hostId == reference.hostId && envelope.environmentId == reference.environmentId &&
            envelope.workspaceId == reference.workspaceId && envelope.resourceId == reference.resourceId &&
            envelope.freshAt(Instant.now()) == HandoffFreshness.CURRENT &&
            HandoffCaller.recheck(context, origin.caller) == HandoffCallerCode.VERIFIED
    }

    private fun eligibility(context: Context): FilesResourceCode? {
        if (Looper.myLooper() == Looper.getMainLooper()) return FilesResourceCode.WRONG_THREAD
        if (context.isDeviceProtectedStorage) return FilesResourceCode.STORE_UNAVAILABLE
        val app = context.applicationContext
        if (app.isDeviceProtectedStorage) return FilesResourceCode.STORE_UNAVAILABLE
        return if (context.packageName != app.packageName || app.packageName !in setOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop") ||
            app.applicationInfo.uid != Process.myUid() || Application.getProcessName() != app.packageName)
            FilesResourceCode.WRONG_OWNER else null
    }
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
}
