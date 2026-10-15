package dev.cloudy.qualification.baseline

internal data class WindowRecord(
    val identity: WindowIdentity,
    val localState: WindowLocalState,
    val restorationSource: String,
    val activityAlive: Boolean = true,
    val observationJson: String? = null,
    val engagement: EngagementSnapshot? = null,
)

internal data class ProbeEvent(
    val sequence: Long,
    val elapsedRealtimeMs: Long,
    val identity: WindowIdentity?,
    val kind: String,
    val detail: String,
    val attribution: String,
    val localState: WindowLocalState?,
)

/** Caller owns synchronization. This records observations, never restores UI state.
 * Closed records may be evicted; an observed live generation is never silently evicted.
 */
internal class WindowLedger(val capacity: Int = 8, eventCapacity: Int = 128) {
    init { require(capacity > 0) }

    private val records = linkedMapOf<String, WindowRecord>()
    private val history = BoundedHistory<ProbeEvent>(eventCapacity)
    var revision: Long = 0
        private set
    var evictedWindowCount: Long = 0
        private set
    var rejectedRegistrationCount: Long = 0
        private set
    var rejectedCallbackCount: Long = 0
        private set
    var supersededGenerationCount: Long = 0
        private set
    val aliveCount: Int get() = records.values.count { it.activityAlive }
    val droppedEventCount: Long get() = history.droppedCount

    fun records(): List<WindowRecord> = records.values.toList()
    fun events(): List<ProbeEvent> = history.snapshot()
    fun record(identity: WindowIdentity): WindowRecord? = records[identity.logicalWindowId]?.takeIf { it.identity == identity }

    fun register(identity: WindowIdentity, restored: RestoredWindow, elapsedMs: Long): String {
        val existing = records[identity.logicalWindowId]
        val rejection = when {
            restored.state.logicalWindowId != identity.logicalWindowId -> "state-identity-mismatch"
            existing?.identity == identity -> "duplicate-generation"
            existing?.activityAlive == true && existing.identity.taskId != identity.taskId -> "live-task-identity-conflict"
            existing == null && records.size == capacity && records.values.all { it.activityAlive } -> "registry-capacity"
            else -> null
        }
        if (rejection != null) {
            rejectedRegistrationCount++
            append(identity, "activity-created", restored.source, rejection, restored.state, elapsedMs)
            return rejection
        }
        if (existing == null && records.size == capacity) {
            val oldestClosed = records.entries.first { !it.value.activityAlive }.key
            records.remove(oldestClosed)
            evictedWindowCount++
        }
        if (existing != null) supersededGenerationCount++
        // Reset the observation when a generation changes. Old geometry must not
        // be presented as a measurement from the new Activity.
        records[identity.logicalWindowId] = WindowRecord(identity, restored.state, restored.source)
        append(identity, "activity-created", restored.source, "current", restored.state, elapsedMs)
        return "current"
    }

    fun attribution(identity: WindowIdentity): String {
        val current = records[identity.logicalWindowId] ?: return "not-retained"
        if (current.identity != identity) return "stale-generation-or-task"
        return if (current.activityAlive) "current" else "closed-generation"
    }

    fun event(identity: WindowIdentity, kind: String, detail: String, elapsedMs: Long, close: Boolean = false) {
        val attribution = attribution(identity)
        val current = record(identity)
        if (attribution != "current") rejectedCallbackCount++
        if (attribution == "current" && close) {
            records[identity.logicalWindowId] = requireNotNull(current).copy(activityAlive = false)
        }
        append(identity, kind, detail, attribution, current?.localState, elapsedMs)
    }

    fun changeState(identity: WindowIdentity, state: WindowLocalState, elapsedMs: Long) {
        val attribution = if (state.logicalWindowId == identity.logicalWindowId) attribution(identity) else "state-identity-mismatch"
        if (attribution == "current") {
            records[identity.logicalWindowId] = requireNotNull(record(identity)).copy(localState = state)
        } else {
            rejectedCallbackCount++
        }
        append(identity, "marker-changed", "", attribution, state, elapsedMs)
    }

    fun observe(identity: WindowIdentity, observationJson: String, elapsedMs: Long): Boolean {
        val attribution = attribution(identity)
        val current = record(identity)
        if (attribution == "current" && current?.observationJson == observationJson) return false
        if (attribution == "current") {
            records[identity.logicalWindowId] = requireNotNull(current).copy(observationJson = observationJson)
        } else {
            rejectedCallbackCount++
        }
        append(identity, "window-observed", "", attribution, current?.localState, elapsedMs)
        return true
    }

    fun globalEvent(kind: String, detail: String, elapsedMs: Long) {
        append(null, kind, detail, "process", null, elapsedMs)
    }

    fun engagement(identity: WindowIdentity, snapshot: EngagementSnapshot, elapsedMs: Long): Boolean {
        val current = record(identity)
        // Cleanup may finish after onDestroy. Retain that historical result only
        // for the exact generation, never revive it or overwrite its successor.
        val accepted = current != null && (current.activityAlive || !snapshot.active) &&
            snapshot.epoch >= (current.engagement?.epoch ?: 0)
        if (accepted && current?.engagement == snapshot) return false
        if (accepted) {
            records[identity.logicalWindowId] = requireNotNull(current).copy(engagement = snapshot)
        } else {
            rejectedCallbackCount++
        }
        append(identity, "engagement-state", "epoch=${snapshot.epoch};status=${snapshot.status};registration=${snapshot.registrationState}",
            if (accepted) attribution(identity) else "rejected-engagement-generation-or-epoch", current?.localState, elapsedMs)
        return true
    }

    private fun append(identity: WindowIdentity?, kind: String, detail: String, attribution: String,
        state: WindowLocalState?, elapsedMs: Long) {
        revision++
        history.add(ProbeEvent(revision, elapsedMs, identity, kind.take(80), detail.take(160), attribution, state))
    }
}
