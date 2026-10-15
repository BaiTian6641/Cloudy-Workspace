package dev.cloudy.qualification.baseline

internal fun interface EngagementQueue { fun execute(work: () -> Unit) }
internal fun interface EngagementClock { fun now(): EngagementTime }
internal data class EngagementTime(val deviceUtc: String, val elapsedRealtimeMs: Long)
internal data class EngagementModes(val names: List<String>, val omittedCount: Int = 0, val truncatedNameCount: Int = 0)

internal interface EngagementRegistration {
    fun register()
    fun unregister()
}

internal fun interface EngagementBackend<Owner> {
    fun prepare(owner: Owner, callback: (EngagementModes) -> Unit): EngagementRegistration
}

/** Reservations include failed cleanup: a suspected leaked listener must still consume capacity. */
internal class EngagementSlots(val capacity: Int = 8) {
    init { require(capacity > 0) }
    private var reserved = 0
    @Synchronized fun acquire(): Boolean {
        if (reserved == capacity) return false
        reserved++
        return true
    }
    @Synchronized fun release() { check(reserved > 0); reserved-- }
    @Synchronized fun count(): Int = reserved
}

internal data class EngagementEvent(
    val epoch: Long,
    val kind: String,
    val time: EngagementTime,
    val modes: EngagementModes? = null,
    val errorType: String? = null,
)

internal data class EngagementSnapshot(
    val epoch: Long,
    val active: Boolean,
    val status: String,
    val registrationState: String,
    val modes: EngagementModes?,
    val lastCallbackTime: EngagementTime?,
    val callbackCount: Long,
    val currentEpochCallbackCount: Long,
    val repeatedCallbackCount: Long,
    val rejectedLateCallbackCount: Long,
    val cleanupFailureCount: Long,
    val errorType: String?,
    val events: List<EngagementEvent>,
    val droppedEventCount: Long,
)

internal data class EngagementUpdate(
    val snapshot: EngagementSnapshot,
    // Drained once per delivery, so an old generation's cleanup failure remains
    // process-visible without overwriting the replacement window's observation.
    val newCleanupFailures: List<EngagementEvent>,
)

/** Lifecycle entry points and report delivery run on the main queue. Backend
 * work runs on one process-owned serial queue. No backend/report call holds lock.
 */
internal class EngagementController<Owner>(
    private val serial: EngagementQueue,
    private val delivery: EngagementQueue,
    private val backend: EngagementBackend<Owner>,
    private val slots: EngagementSlots,
    private val clock: EngagementClock,
    private val report: (EngagementUpdate) -> Unit,
    private val historyCapacity: Int = 32,
) {
    private inner class Session(val epoch: Long, var owner: Owner?) {
        var valid = true
        var registration: EngagementRegistration? = null // serial queue only
        var cleanupQueued = false
        var cleanupAttempted = false // serial queue only
    }

    private val lock = Any()
    private val history = BoundedHistory<EngagementEvent>(historyCapacity)
    private val cleanupFailures = mutableListOf<EngagementEvent>()
    private var current: Session? = null
    private var started = false
    private var destroyed = false
    private var deliveryQueued = false
    private var epoch = 0L
    private var status = "not-started"
    private var registrationState = "none"
    private var modes: EngagementModes? = null
    private var lastCallbackTime: EngagementTime? = null
    private var callbackCount = 0L
    private var epochCallbackCount = 0L
    private var repeatedCallbackCount = 0L
    private var rejectedCallbackCount = 0L
    private var cleanupFailureCount = 0L
    private var errorType: String? = null

    fun start(owner: Owner, tracked: Boolean) {
        val session: Session?
        synchronized(lock) {
            if (destroyed || started) return
            check(epoch < Long.MAX_VALUE) { "Engagement epoch exhausted." }
            epoch++
            started = true
            modes = null
            lastCallbackTime = null
            epochCallbackCount = 0
            errorType = null
            status = when {
                !tracked -> "not-tracked"
                !slots.acquire() -> "capacity-rejected"
                else -> "pending"
            }
            session = if (status == "pending") Session(epoch, owner) else null
            current = session
            registrationState = if (session == null) "not-requested" else "queued"
            addEventLocked(epoch, status)
            publishLocked()
        }
        if (session != null) serial.execute { register(session) }
    }

    fun stop() {
        val cleanup: Session?
        synchronized(lock) {
            if (!started) return
            started = false
            val session = current
            // Invalidate immediately, including an already accepted callback
            // whose coalesced report delivery has not run yet.
            session?.valid = false
            session?.owner = null
            if (status != "error") status = "stopped"
            cleanup = session?.takeUnless { it.cleanupQueued }
            cleanup?.cleanupQueued = true
            if (cleanup != null) registrationState = "cleanup-queued"
            addEventLocked(epoch, "stopped")
            publishLocked()
        }
        cleanup?.let { serial.execute { cleanup(it) } }
    }

    fun destroy() {
        stop()
        synchronized(lock) { destroyed = true }
    }

    /** Repeated/late callback counters are sampled on explicit refresh or the
     * next meaningful transition, rather than causing a save/render feedback loop.
     */
    fun refresh() = synchronized(lock) { publishLocked() }

    private fun register(session: Session) {
        val owner = synchronized(lock) {
            if (!session.valid || current !== session || !started) null else session.owner
        }
        if (owner == null) {
            cleanup(session)
            return
        }
        val failure = dependencyFailure {
            val registration = backend.prepare(owner) { value -> callback(session, value) }
            session.registration = registration
            synchronized(lock) { session.owner = null }
            registration.register()
        }
        synchronized(lock) {
            session.owner = null
            if (failure == null) {
                addEventLocked(session.epoch, "registered")
                if (current === session && started) registrationState = "registered"
            } else {
                session.valid = false
                addEventLocked(session.epoch, "registration-failed", error = failure)
                if (current === session) {
                    status = "error"
                    registrationState = "registration-failed"
                    errorType = failure
                }
            }
            publishLocked()
        }
        // Registration can fail after installing a listener. Reuse its exact
        // tracker/Consumer pair for best-effort cleanup on this serial queue.
        if (failure != null) cleanup(session)
    }

    private fun cleanup(session: Session) {
        if (session.cleanupAttempted) return
        session.cleanupAttempted = true
        synchronized(lock) { session.cleanupQueued = true }
        val registration = session.registration
        val failure = dependencyFailure { registration?.unregister() }
        session.registration = null
        if (failure == null) slots.release()
        synchronized(lock) {
            session.owner = null
            val kind = if (failure == null) "cleanup-complete" else "cleanup-failed"
            val event = addEventLocked(session.epoch, kind, error = failure)
            if (failure != null) {
                cleanupFailureCount = increment(cleanupFailureCount)
                // At most slots.capacity cleanup failures can occur: failed
                // reservations are never released for further registrations.
                cleanupFailures.add(event)
            }
            if (current === session) {
                registrationState = kind
                if (failure != null) { status = "error"; errorType = failure }
            }
            publishLocked()
        }
    }

    private fun callback(session: Session, value: EngagementModes) {
        synchronized(lock) {
            if (!started || !session.valid || current !== session) {
                rejectedCallbackCount = increment(rejectedCallbackCount)
                return
            }
            callbackCount = increment(callbackCount)
            epochCallbackCount = increment(epochCallbackCount)
            lastCallbackTime = clock.now()
            if (modes == value) {
                repeatedCallbackCount = increment(repeatedCallbackCount)
                return
            }
            modes = value.copy(names = value.names.toList())
            status = "reported"
            addEventLocked(session.epoch, "modes-reported", modes)
            publishLocked()
        }
    }

    private fun addEventLocked(epoch: Long, kind: String, modes: EngagementModes? = null, error: String? = null): EngagementEvent {
        val event = EngagementEvent(epoch, kind, clock.now(), modes, error)
        history.add(event)
        return event
    }

    private fun publishLocked() {
        if (deliveryQueued) return
        deliveryQueued = true
        delivery.execute {
            val update = synchronized(lock) {
                deliveryQueued = false
                val snapshot = EngagementSnapshot(epoch, started && current?.valid == true, status,
                    registrationState, modes, lastCallbackTime, callbackCount, epochCallbackCount,
                    repeatedCallbackCount, rejectedCallbackCount, cleanupFailureCount, errorType,
                    history.snapshot(), history.droppedCount)
                EngagementUpdate(snapshot, cleanupFailures.toList()).also { cleanupFailures.clear() }
            }
            // Snapshot at delivery time, not callback time: epoch 1 cannot
            // overwrite epoch 2 when stop/start preceded this main-queue job.
            report(update)
        }
    }

    private fun dependencyFailure(block: () -> Unit): String? = try {
        block()
        null
    } catch (failure: RuntimeException) {
        failure.javaClass.simpleName.take(80)
    } catch (failure: NotImplementedError) {
        // Alpha interface defaults explicitly throw this when unimplemented.
        failure.javaClass.simpleName
    } catch (failure: LinkageError) {
        // A missing/incompatible runtime API is evidence, not a fallback mode.
        failure.javaClass.simpleName.take(80)
    }

    private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1
}
