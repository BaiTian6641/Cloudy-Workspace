package dev.cloudy.qualification.baseline

import java.util.UUID

/** One process-local, one-use admission. Nothing here restores a socket or an authorization. */
internal class SshRetentionPolicy {
    companion object { const val START_WINDOW_MILLIS = 10_000L }
    private var identity: String? = null
    private var reservedAt = 0L
    private var expires = 0L
    private var consumed = false
    private var revoked = false
    private var quarantine = false

    @Synchronized fun reserve(id: String, now: Long): Boolean {
        val canonical = try { UUID.fromString(id).toString() == id } catch (_: IllegalArgumentException) { false }
        if (!canonical || now < 0 || now > Long.MAX_VALUE - START_WINDOW_MILLIS || identity != null || quarantine) return false
        identity = id; reservedAt = now; expires = now + START_WINDOW_MILLIS; consumed = false; revoked = false
        return true
    }
    @Synchronized fun pending(id: String, now: Long): Boolean =
        identity == id && !consumed && !revoked && now >= reservedAt && now < expires

    @Synchronized fun consume(id: String, now: Long): Boolean {
        if (!pending(id, now)) return false
        consumed = true
        return true
    }
    @Synchronized fun revoke(id: String): Boolean {
        if (identity != id) return false
        revoked = true
        return true
    }
    @Synchronized fun abandonPending(id: String): Boolean {
        if (identity != id || consumed) return false
        identity = null; revoked = false
        return true
    }
    @Synchronized fun retained(id: String): Boolean = identity == id && consumed && !revoked && !quarantine
    @Synchronized fun current(id: String): Boolean = identity == id
    @Synchronized fun shouldCancelLastWindow(visibleWindows: Int, changingConfiguration: Boolean): Boolean =
        visibleWindows == 0 && !changingConfiguration && !(identity != null && consumed && !revoked && !quarantine)

    /** Actual worker return is separate from certainty of cleanup and durable final evidence. */
    @Synchronized fun completed(id: String, cleanupCertain: Boolean, terminalPersisted: Boolean): Boolean {
        if (identity != id || !consumed) return false
        quarantine = quarantine || !cleanupCertain || !terminalPersisted
        identity = null; consumed = false; revoked = false
        return true
    }
    @Synchronized fun quarantined(): Boolean = quarantine
    @Synchronized fun hasRequest(): Boolean = identity != null
}
