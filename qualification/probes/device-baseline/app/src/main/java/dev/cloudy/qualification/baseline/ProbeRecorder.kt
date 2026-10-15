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
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal data class ProbeUiState(
    val collecting: Boolean,
    val saved: Boolean,
    val saveError: String?,
    val hasCollectorError: Boolean,
    val summary: String,
)

/** Process-scoped recorder: activity recreation does not erase the current event ring. */
internal class ProbeRecorder(context: Context) {
    fun interface Listener {
        fun onProbeState(state: ProbeUiState)
    }

    private val appContext = context.applicationContext
    private val lock = Any()
    // A single worker serializes codec queries and AtomicFile writes. Refresh and
    // save requests are coalesced so repeated input cannot grow its task queue.
    private val worker = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val history = BoundedHistory<JSONObject>(128)
    private val processStartedAt = Instant.now().toString()
    private var revision = 0L
    private var savedRevision = -1L
    private var collecting = false
    private var requestedInitial = false
    private var saveScheduled = false
    private var saveError: String? = null
    private var build = JSONObject().put("status", "not-collected")
    private var inputs = JSONObject().put("status", "not-collected")
    private var decoders = JSONObject().put("status", "not-collected")
    private var window = JSONObject().put("status", "not-yet-attached")
    private var lastCollectionAt: String? = null

    fun addListener(listener: Listener) {
        listeners.add(listener)
        publishUi()
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun ensureInitialCollection() {
        synchronized(lock) {
            if (requestedInitial) return
            requestedInitial = true
        }
        refresh()
    }

    fun refresh() {
        synchronized(lock) {
            if (collecting) return
            collecting = true
            appendEventLocked("refresh-started", "")
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
                appendEventLocked("refresh-finished", "Inspect section errors; completion is not qualification.")
                scheduleSaveLocked()
            }
            publishUi()
        }
    }

    fun event(kind: String, detail: String = "") {
        synchronized(lock) {
            appendEventLocked(kind, detail)
            scheduleSaveLocked()
        }
        publishUi()
    }

    fun window(value: JSONObject) {
        synchronized(lock) {
            // Only retain changed snapshots; resize/inset callbacks can repeat.
            if (window.toString() == value.toString()) return
            window = value
            appendEventLocked("window-observed", "")
            scheduleSaveLocked()
        }
        publishUi()
    }

    private fun appendEventLocked(kind: String, detail: String) {
        revision++
        history.add(JSONObject().put("sequence", revision)
            .put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
            .put("kind", kind.take(80)).put("detail", detail.take(160)))
    }

    private fun scheduleSaveLocked() {
        if (saveScheduled) return
        saveScheduled = true
        worker.schedule({ saveReport() }, 150, TimeUnit.MILLISECONDS)
    }

    private fun snapshotLocked(): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("probeVersion", "0.1.0")
        .put("scope", "P0-S1 reported Android metadata; no feature or performance qualification")
        .put("processStartedAtDeviceUtc", processStartedAt)
        .put("snapshotAtDeviceUtc", Instant.now().toString())
        .put("revision", revision)
        .put("collectionInProgress", collecting)
        .put("lastCollectionAtDeviceUtc", lastCollectionAt ?: JSONObject.NULL)
        .put("build", build).put("inputDevices", inputs)
        .put("videoDecoders", decoders).put("window", window)
        .put("events", JSONArray(history.snapshot()))
        .put("droppedEventCount", history.droppedCount)
        .put("retention", "Current process; latest 128 events. Process death can prevent final callbacks or pending writes.")

    private fun saveReport() {
        val report: JSONObject
        val reportRevision: Long
        synchronized(lock) {
            saveScheduled = false
            report = snapshotLocked()
            reportRevision = revision
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
        // Read at delivery time: a worker must not enqueue an obsolete saved state
        // after the main thread has already published a newer pending revision.
        main.post {
            val state = synchronized(lock) {
                val summary = JSONObject()
                    .put("revision", revision).put("savedRevision", savedRevision)
                    .put("build", build).put("inputDevices", inputs).put("window", window)
                    .put("videoDecoderSummary", JSONObject()
                        .put("status", decoders.optString("status"))
                        .put("reportedCount", decoders.opt("reportedVideoDecoderCount") ?: JSONObject.NULL)
                        .put("recordedCount", decoders.opt("recordedDecoderCount") ?: JSONObject.NULL)
                        .put("omittedCount", decoders.opt("omittedDecoderCount") ?: JSONObject.NULL))
                    .put("recentEvents", JSONArray(history.snapshot().takeLast(10)))
                ProbeUiState(collecting, savedRevision == revision, saveError,
                    containsError(build) || containsError(inputs) || containsError(decoders) || containsError(window),
                    summary.toString(2))
            }
            // Consult current listeners so stopped Activities are not retained.
            listeners.forEach { it.onProbeState(state) }
        }
    }

    private fun containsError(value: Any?): Boolean = when (value) {
        is JSONObject -> value.optString("status") == "error" ||
            value.keys().asSequence().any { containsError(value.opt(it)) }
        is JSONArray -> (0 until value.length()).any { containsError(value.opt(it)) }
        else -> false
    }
}
