package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.FilesResourceState
import java.util.UUID

/** Private one-use navigation only. Nothing here restores a connection or grants authentication. */
internal object FilesLocationTickets {
    const val EXTRA = "io.github.baitian6641.cloudy.FILES_LOCATION_TICKET"
    private const val CAPACITY = 16
    private const val LIFETIME_MILLIS = 30_000L
    private class Ticket(val packageName: String, val registration: FilesResourceRegistration, val expiresAt: Long)
    private val tickets = mutableMapOf<String, Ticket>()

    /** Off main, after an explicit own-location Open action; resolve again before minting. */
    fun issue(context: Context, registration: FilesResourceRegistration): FilesResourceResult<String> {
        eligibility(context)?.let { return FilesResourceResult.Failure(it) }
        if (registration.state == FilesResourceState.REVOKED) return FilesResourceResult.Failure(FilesResourceCode.INVALID_STATE)
        if (!SshAuthenticationRegistry.lock.tryLock()) return FilesResourceResult.Failure(FilesResourceCode.BUSY)
        try {
            val resolved = FilesResourceStore(context).resolve(registration.reference, registration.state)
            if (resolved is FilesResourceResult.Failure) return resolved
            if ((resolved as FilesResourceResult.Success).value != registration) {
                return FilesResourceResult.Failure(FilesResourceCode.CONFLICT)
            }
            val now = SystemClock.elapsedRealtime()
            if (now < 0 || now > Long.MAX_VALUE - LIFETIME_MILLIS) {
                return FilesResourceResult.Failure(FilesResourceCode.STORE_UNAVAILABLE)
            }
            synchronized(tickets) {
                tickets.entries.removeAll { now >= it.value.expiresAt }
                if (tickets.size >= CAPACITY) return FilesResourceResult.Failure(FilesResourceCode.CAPACITY)
                val token = UUID.randomUUID().toString()
                check(!tickets.containsKey(token))
                tickets[token] = Ticket(context.packageName, registration, now + LIFETIME_MILLIS)
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
        if (!SshAuthenticationRegistry.lock.tryLock()) return FilesResourceResult.Failure(FilesResourceCode.BUSY)
        try {
            val registration = ticket.registration
            return when (val resolved = FilesResourceStore(context).resolve(registration.reference, registration.state)) {
                is FilesResourceResult.Failure -> resolved
                is FilesResourceResult.Success -> when {
                    SystemClock.elapsedRealtime() >= ticket.expiresAt -> FilesResourceResult.Failure(FilesResourceCode.NOT_FOUND)
                    resolved.value == registration -> resolved
                    else -> FilesResourceResult.Failure(FilesResourceCode.CONFLICT)
                }
            }
        } catch (_: Exception) { return FilesResourceResult.Failure(FilesResourceCode.STORE_UNAVAILABLE) }
        finally { SshAuthenticationRegistry.lock.unlock() }
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
