package dev.cloudy.qualification.baseline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal data class ProbeUiState(
    val collecting: Boolean,
    val saved: Boolean,
    val saveError: String?,
    val hasCollectorError: Boolean,
    val summary: String,
    val windowTracking: String,
    val aliveWindowCount: Int,
    val windowCapacity: Int,
)

/** Process-scoped recorder: activity recreation does not erase the current event ring. */
internal class ProbeRecorder(context: Context) {
    fun interface Listener {
        fun onProbeState(state: ProbeUiState)
    }

    private data class Subscription(val identity: WindowIdentity, val listener: Listener)

    private val appContext = context.applicationContext
    private val lock = Any()
    // A single worker serializes codec queries and AtomicFile writes. Refresh and
    // save requests are coalesced so repeated input cannot grow its task queue.
    private val worker = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ListenerRegistry<Subscription>()
    private val ledger = WindowLedger()
    private val processStartedAt = Instant.now().toString()
    private var savedRevision = -1L
    private var collecting = false
    private var requestedInitial = false
    private var saveScheduled = false
    private var saveError: String? = null
    private var uiPublishQueued = false
    private val engagementCleanupFailures = BoundedHistory<Pair<WindowIdentity, EngagementEvent>>(8)
    private var engagementCleanupFailureCount = 0L
    private var build = JSONObject().put("status", "not-collected")
    private var inputs = JSONObject().put("status", "not-collected")
    private var decoders = JSONObject().put("status", "not-collected")
    private var lastCollectionAt: String? = null

    fun addListener(identity: WindowIdentity, listener: Listener) {
        listeners.add(Subscription(identity, listener))
        publishUi()
    }

    fun removeListener(listener: Listener) {
        listeners.removeMatching { it.listener == listener }
    }

    fun register(identity: WindowIdentity, restored: RestoredWindow) {
        synchronized(lock) {
            ledger.register(identity, restored, SystemClock.elapsedRealtime())
            scheduleSaveLocked()
        }
        publishUi()
    }

    fun tracks(identity: WindowIdentity): Boolean = synchronized(lock) { ledger.attribution(identity) == "current" }

    fun engagement(identity: WindowIdentity, update: EngagementUpdate) {
        synchronized(lock) {
            val changed = ledger.engagement(identity, update.snapshot, SystemClock.elapsedRealtime())
            update.newCleanupFailures.forEach { failure ->
                engagementCleanupFailures.add(identity to failure)
                engagementCleanupFailureCount++
                ledger.globalEvent("engagement-cleanup-failed",
                    "generation=${identity.generationId};epoch=${failure.epoch};error=${failure.errorType}",
                    failure.time.elapsedRealtimeMs)
            }
            if (!changed && update.newCleanupFailures.isEmpty()) return
            scheduleSaveLocked()
        }
        publishUi()
    }

    fun ensureInitialCollection() {
        synchronized(lock) {
            if (requestedInitial) return
            requestedInitial = true
        }
        refresh()
    }

    fun refresh(requester: WindowIdentity? = null) {
        synchronized(lock) {
            if (collecting) return
            collecting = true
            requester?.let { ledger.event(it, "refresh-requested", "Device metadata is shared by the process.", SystemClock.elapsedRealtime()) }
            ledger.globalEvent("refresh-started", "", SystemClock.elapsedRealtime())
        }
        publishUi()
        worker.execute {
            val newBuild = FrameworkCollectors.build()
            val newInputs = FrameworkCollectors.inputs(appContext)
            val newDecoders = FrameworkCollectors.decoders()
            synchronized(lock) {
                build = newBuild
                inputs = newInputs
                decoders = newDecoders
                collecting = false
                lastCollectionAt = Instant.now().toString()
                ledger.globalEvent("refresh-finished", "Inspect section errors; completion is not qualification.", SystemClock.elapsedRealtime())
                scheduleSaveLocked()
            }
            publishUi()
        }
    }

    fun event(identity: WindowIdentity, kind: String, detail: String = "", close: Boolean = false) {
        synchronized(lock) {
            ledger.event(identity, kind, detail, SystemClock.elapsedRealtime(), close)
            scheduleSaveLocked()
        }
        publishUi()
    }

    fun changeState(identity: WindowIdentity, state: WindowLocalState) {
        synchronized(lock) {
            ledger.changeState(identity, state, SystemClock.elapsedRealtime())
            scheduleSaveLocked()
        }
        publishUi()
    }

    fun window(identity: WindowIdentity, value: JSONObject) {
        val raw = value.toString()
        val bounded = if (raw.toByteArray(Charsets.UTF_8).size <= 32 * 1024) raw else {
            JSONObject().put("status", "error").put("errorType", "WindowSnapshotExceeded32KiB").toString()
        }
        synchronized(lock) {
            // Identical geometry does not generate events, avoiding a UI-summary
            // update -> layout -> observation -> summary feedback cycle.
            if (!ledger.observe(identity, bounded, SystemClock.elapsedRealtime())) return
            scheduleSaveLocked()
        }
        publishUi()
    }

    private fun scheduleSaveLocked() {
        if (saveScheduled) return
        saveScheduled = true
        worker.schedule({ saveReport() }, 150, TimeUnit.MILLISECONDS)
    }

    private fun snapshotLocked(): JSONObject = JSONObject()
        .put("schemaVersion", 3)
        .put("probeVersion", BuildConfig.VERSION_NAME)
        .put("scope", "P0 diagnostic metadata and task/window observations; no product or performance qualification")
        .put("processStartedAtDeviceUtc", processStartedAt)
        .put("snapshotAtDeviceUtc", Instant.now().toString())
        .put("revision", ledger.revision)
        .put("collectionInProgress", collecting)
        .put("lastCollectionAtDeviceUtc", lastCollectionAt ?: JSONObject.NULL)
        .put("build", build).put("inputDevices", inputs)
        .put("videoDecoders", decoders)
        .put("windows", JSONArray(ledger.records().map(::windowJson)))
        .put("events", JSONArray(ledger.events().map(::eventJson)))
        .put("engagementCleanup", engagementCleanupJson())
        .put("retention", retentionJson())

    private fun saveReport() {
        val report: JSONObject
        val reportRevision: Long
        synchronized(lock) {
            saveScheduled = false
            report = snapshotLocked()
            reportRevision = ledger.revision
        }
        try {
            val bytes = report.toString(2).toByteArray(Charsets.UTF_8)
            if (bytes.size > 512 * 1024) throw IOException("Report exceeded its byte limit.")
            val file = AtomicFile(File(appContext.filesDir, "baseline.json"))
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
            // AtomicFile may only log a commit failure. Check the bounded file
            // contents before reporting this revision as saved; this is not a
            // guarantee of persistence through power loss.
            val persisted = file.openRead().use { it.readNBytes(bytes.size + 1) }
            if (!persisted.contentEquals(bytes)) throw IOException("Report readback did not match.")
            synchronized(lock) {
                savedRevision = reportRevision
                saveError = null
            }
        } catch (failure: IOException) {
            synchronized(lock) { saveError = failure.javaClass.simpleName }
        } catch (failure: RuntimeException) {
            synchronized(lock) { saveError = failure.javaClass.simpleName }
        }
        publishUi()
    }

    private fun publishUi() {
        synchronized(lock) {
            if (uiPublishQueued) return
            uiPublishQueued = true
        }
        // Read at delivery time: a worker must not enqueue an obsolete saved state
        // after the main thread has already published a newer pending revision.
        main.post {
            synchronized(lock) { uiPublishQueued = false }
            // Subscriptions contain observation identities, never state to restore.
            // Removed Activities are not captured by already queued deliveries.
            listeners.snapshot().forEach { subscription ->
                val state = synchronized(lock) { uiStateLocked(subscription.identity) }
                subscription.listener.onProbeState(state)
            }
        }
    }

    private fun uiStateLocked(identity: WindowIdentity): ProbeUiState {
        val record = ledger.record(identity)
        val window = record?.let(::windowJson) ?: JSONObject().put("status", "not-retained")
        val summary = JSONObject()
            .put("revision", ledger.revision).put("savedRevision", savedRevision)
            .put("thisWindow", window).put("retention", retentionJson())
            .put("engagementCleanup", engagementCleanupJson())
            .put("build", build).put("inputDevices", inputs)
            .put("videoDecoderSummary", JSONObject()
                .put("status", decoders.optString("status"))
                .put("reportedCount", decoders.opt("reportedVideoDecoderCount") ?: JSONObject.NULL)
                .put("recordedCount", decoders.opt("recordedDecoderCount") ?: JSONObject.NULL)
                .put("omittedCount", decoders.opt("omittedDecoderCount") ?: JSONObject.NULL))
            .put("recentEvents", JSONArray(ledger.events().filter {
                it.identity == null || it.identity.logicalWindowId == identity.logicalWindowId
            }.takeLast(10).map(::eventJson)))
        return ProbeUiState(collecting, savedRevision == ledger.revision, saveError,
            containsError(build) || containsError(inputs) || containsError(decoders) || containsError(window) ||
                engagementCleanupFailureCount > 0,
            summary.toString(2), ledger.attribution(identity), ledger.aliveCount, ledger.capacity)
    }

    private fun identityJson(identity: WindowIdentity): JSONObject = JSONObject()
        .put("logicalWindowId", identity.logicalWindowId).put("taskIdObserved", identity.taskId)
        .put("activityGenerationId", identity.generationId)

    private fun stateJson(state: WindowLocalState): JSONObject = JSONObject()
        .put("marker", state.marker).put("markerIndex", state.markerIndex).put("changeCount", state.changeCount)

    private fun windowJson(record: WindowRecord): JSONObject = JSONObject()
        .put("identity", identityJson(record.identity)).put("localState", stateJson(record.localState))
        .put("restorationSource", record.restorationSource).put("activityAliveObserved", record.activityAlive)
        .put("observation", record.observationJson?.let(::JSONObject)
            ?: JSONObject().put("status", "not-yet-observed"))
        .put("engagement", record.engagement?.let(::engagementJson)
            ?: JSONObject().put("status", "not-started"))

    private fun engagementJson(snapshot: EngagementSnapshot): JSONObject = JSONObject()
        .put("status", snapshot.status).put("subscriptionEpoch", snapshot.epoch)
        .put("activeSubscriptionEpoch", snapshot.active).put("registrationState", snapshot.registrationState)
        .put("library", "androidx.window:window:1.6.0-alpha05")
        .put("modes", snapshot.modes?.let(::engagementModesJson) ?: JSONObject.NULL)
        .put("lastCallbackTime", snapshot.lastCallbackTime?.let(::engagementTimeJson) ?: JSONObject.NULL)
        .put("callbackCount", snapshot.callbackCount).put("currentEpochCallbackCount", snapshot.currentEpochCallbackCount)
        .put("repeatedCallbackCount", snapshot.repeatedCallbackCount)
        .put("rejectedLateCallbackCount", snapshot.rejectedLateCallbackCount)
        .put("cleanupFailureCount", snapshot.cleanupFailureCount)
        .put("errorType", snapshot.errorType ?: JSONObject.NULL)
        .put("events", JSONArray(snapshot.events.map(::engagementEventJson)))
        .put("droppedEventCount", snapshot.droppedEventCount)
        .put("scope", "Reported engagement hints, not physical-input qualification. Null modes means no callback for this epoch. Stopped/closed records are historical. Repeated and rejected callback counters are sampled on transition or Refresh.")

    private fun engagementModesJson(modes: EngagementModes): JSONObject = JSONObject()
        .put("names", JSONArray(modes.names)).put("omittedModeCount", modes.omittedCount)
        .put("truncatedNameCount", modes.truncatedNameCount)

    private fun engagementTimeJson(time: EngagementTime): JSONObject = JSONObject()
        .put("deviceUtc", time.deviceUtc).put("elapsedRealtimeMs", time.elapsedRealtimeMs)

    private fun engagementEventJson(event: EngagementEvent): JSONObject = JSONObject()
        .put("subscriptionEpoch", event.epoch).put("kind", event.kind).put("time", engagementTimeJson(event.time))
        .put("modes", event.modes?.let(::engagementModesJson) ?: JSONObject.NULL)
        .put("errorType", event.errorType ?: JSONObject.NULL)

    private fun engagementCleanupJson(): JSONObject = JSONObject()
        .put("status", if (engagementCleanupFailureCount == 0L) "no-failure-observed" else "error")
        .put("failureCount", engagementCleanupFailureCount)
        .put("failures", JSONArray(engagementCleanupFailures.snapshot().map { (identity, event) ->
            JSONObject().put("identity", identityJson(identity)).put("failure", engagementEventJson(event))
        }))
        .put("droppedFailureCount", engagementCleanupFailures.droppedCount)
        .put("scope", "Failed cleanup may retain library/backend resources until process death. Its admission slot remains reserved; no retry is treated as proven cleanup.")

    private fun eventJson(event: ProbeEvent): JSONObject = JSONObject()
        .put("sequence", event.sequence).put("elapsedRealtimeMs", event.elapsedRealtimeMs)
        .put("identity", event.identity?.let(::identityJson) ?: JSONObject.NULL)
        .put("kind", event.kind).put("detail", event.detail).put("attribution", event.attribution)
        .put("localState", event.localState?.let(::stateJson) ?: JSONObject.NULL)

    private fun retentionJson(): JSONObject = JSONObject()
        .put("windowRecordLimit", ledger.capacity).put("retainedWindowCount", ledger.records().size)
        .put("activityAliveCountObserved", ledger.aliveCount).put("evictedClosedWindowCount", ledger.evictedWindowCount)
        .put("rejectedRegistrationCount", ledger.rejectedRegistrationCount)
        .put("rejectedCallbackCount", ledger.rejectedCallbackCount)
        .put("supersededGenerationCount", ledger.supersededGenerationCount)
        .put("eventLimit", 128).put("droppedEventCount", ledger.droppedEventCount)
        .put("windowSnapshotByteLimit", 32 * 1024)
        .put("engagementEventLimitPerGeneration", 32).put("engagementRegistrationLimit", 8)
        .put("engagementModeLimit", 16).put("engagementModeNameCharacterLimit", 80)
        .put("policy", "Current process only. Evict oldest closed record, never a live generation; reject excess registration. Latest geometry per generation and latest 128 correlated events. Stopped is still alive. This is a diagnostic retention limit, not an OS task limit. Process death may omit final callbacks/writes.")

    private fun containsError(value: Any?): Boolean = when (value) {
        is JSONObject -> value.optString("status") == "error" ||
            value.keys().asSequence().any { containsError(value.opt(it)) }
        is JSONArray -> (0 until value.length()).any { containsError(value.opt(it)) }
        else -> false
    }
}
