package dev.cloudy.qualification.rdp

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import com.freerdp.freerdpcore.services.LibFreeRDP
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class RdpSnapshot(
    val state: String = "loading", val detail: String = "Loading pinned native libraries",
    val nativeVersion: String = "unavailable", val width: Int = 0, val height: Int = 0,
    val frameUpdates: Long = 0, val inputEvents: Long = 0,
    val busy: Boolean = true, val connected: Boolean = false, val cursorMode: String = "default",
)

/** One visible diagnostic owner. No credentials, callback objects or executor retain an Activity.
 * All native owner operations run on one executor. Native callbacks may copy pixels synchronously,
 * but never free/join or wait for UI. A terminal notification is not native thread completion. */
class RdpSession(context: Context) : LibFreeRDP.Sink {
    private val app = context.applicationContext
    private val executor = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val main = Handler(Looper.getMainLooper())
    private val uiPending = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val stopping = AtomicBoolean()
    private val pendingInput = AtomicInteger()
    private val frameCount = AtomicLong()
    private val inputCount = AtomicLong()
    private val certAccepted = AtomicLong()
    private val certRejected = AtomicLong()
    private val authCallbacks = AtomicLong()
    private val frame = RdpFrameBuffer()
    private val callbackFailures = RdpFailureTracker()
    private var failureToken: RdpFailureTracker.Token? = null
    private var persistedFailure: RdpCallbackFailure? = null
    private val stateLock = Any()
    @Volatile private var state = RdpSnapshot()
    @Volatile private var nativeAvailable = false
    @Volatile private var handle = 0L
    @Volatile private var config: RdpConfig? = null
    @Volatile private var liveConnected = false
    @Volatile var listener: ((RdpSnapshot) -> Unit)? = null
    private var poll: ScheduledFuture<*>? = null
    private var nativeThreadStarted = false
    private var startedAt = 0L
    private var stopAt = 0L
    private var lastPersist = 0L
    @Volatile private var terminalEvent = ""
    private var stopReason = ""
    private val heldKeys = linkedSetOf<Int>()
    private val heldButtons = linkedSetOf<Int>()
    private var pointerX = 0
    private var pointerY = 0
    private val journalFile = AtomicFile(File(app.filesDir, "rdp-journal.json"))
    private var attempts = JSONArray()
    private var attempt: JSONObject? = null
    private var journalReady = false

    init {
        owner { initializeOnOwner() }
    }

    private fun initializeOnOwner() {
        if (closed.get()) { finishOwnerIfClosed(); return }
        // Lease covers the journal as well as JNI: a recreated Activity cannot rewrite
        // an attempt that the previous visible owner is still joining.
        if (!lease.compareAndSet(null, this) && lease.get() !== this) {
            change { it.copy(state = "waiting", detail = "Previous native owner is cleaning up", busy = true) }
            executor.schedule({ guarded { initializeOnOwner() } }, 100, TimeUnit.MILLISECONDS)
            return
        }
        readJournal()
        LibFreeRDP.load()
        val version = LibFreeRDP.freerdp_get_version()
        check(version.startsWith("3.32.2")) { "native-version-mismatch" }
        check(LibFreeRDP.freerdp_has_h264()) { "native-h264-unavailable" }
        nativeAvailable = true
        change { it.copy(state = "ready", detail = "Native library loaded; import a private fixture configuration", nativeVersion = version, busy = false) }
    }

    fun snapshot(): RdpSnapshot = state
    fun drawFrame(canvas: Canvas, destination: RectF) = frame.draw(canvas, destination)

    private fun change(transform: (RdpSnapshot) -> RdpSnapshot) {
        synchronized(stateLock) { state = transform(state).copy(frameUpdates = frameCount.get(), inputEvents = inputCount.get()) }
        if (uiPending.compareAndSet(false, true)) main.post {
            uiPending.set(false)
            listener?.invoke(state)
        }
    }

    private fun owner(action: () -> Unit): Boolean {
        return try {
            executor.execute { guarded(action) }
            true
        } catch (_: RejectedExecutionException) { false }
    }

    private fun guarded(action: () -> Unit) {
        try { action() }
        catch (error: Exception) { ownerFailure(error.javaClass.simpleName) }
        catch (error: LinkageError) { ownerFailure(error.javaClass.simpleName) }
    }

    private fun finishOwnerIfClosed() {
        if (closed.get() && handle == 0L) {
            lease.compareAndSet(this, null)
            executor.shutdown()
        }
    }

    private fun ensureObserver() {
        if (poll == null) poll = executor.scheduleWithFixedDelay({
            guarded { observeNative() }
        }, 0, 100, TimeUnit.MILLISECONDS)
    }

    private fun ownerFailure(failureClass: String) {
        callbackFailures.captureOwner(failureToken, failureClass, SystemClock.elapsedRealtime() - startedAt) {
            stopping.set(true)
        }
        failedOnOwner("adapter-failure")
    }

    private fun failureDetail(fallback: String): String = callbackFailures.snapshot(failureToken)?.summary() ?: fallback

    private fun persistFirstFailure() {
        val cause = callbackFailures.snapshot(failureToken) ?: return
        if (cause === persistedFailure) return
        attempt?.put("firstFailure", JSONObject(cause.journalFields()))
        // Failure disables new connections and queues stop without throwing/re-entering.
        if (persist()) persistedFailure = cause
    }

    private fun failedOnOwner(kind: String) {
        stopping.set(true)
        persistFirstFailure()
        change { it.copy(state = "failed", detail = failureDetail(kind), busy = handle != 0L, connected = false) }
        if (handle != 0L) {
            // Reap must survive diagnostics or stop-request errors. Never free an unjoined thread.
            ensureObserver()
            try { stopOnOwner("adapter-failure") }
            catch (_: Exception) { change { it.copy(detail = failureDetail("Stop failed; native ownership retained")) } }
            catch (_: LinkageError) { change { it.copy(detail = failureDetail("Native stop unavailable; ownership retained")) } }
        } else {
            config = null
            if (lease.get() === this) LibFreeRDP.setSink(null)
            attempt?.put("closed", true)?.put("outcome", "failed-before-native-allocation")
            record("adapter-failure")
            callbackFailures.retire(failureToken)
            attempt = null
            failureToken = null; persistedFailure = null
            finishOwnerIfClosed()
        }
    }

    fun connect() {
        owner {
            if (closed.get() || handle != 0L) return@owner
            check(nativeAvailable && journalReady) { "native-or-journal-unavailable" }
            check(attempts.length() < 16) { "preserve-journal-before-more-attempts" }
            val file = File(app.filesDir, "rdp-config.json")
            check(file.isFile && file.length() in 1..16384) { "private-configuration-required" }
            val selected = RdpConfig.parse(file.readText())
            check(lease.get() === this) { "previous-native-owner-still-cleaning-up" }
            config = selected
            failureToken = null; persistedFailure = null
            stopping.set(false); liveConnected = false; terminalEvent = ""; stopReason = ""
            frameCount.set(0); inputCount.set(0); certAccepted.set(0); certRejected.set(0); authCallbacks.set(0)
            heldKeys.clear(); heldButtons.clear(); frame.clear()
            change { it.copy(width = 0, height = 0, cursorMode = "default") }
            startedAt = SystemClock.elapsedRealtime(); stopAt = 0; nativeThreadStarted = false
            attempt = JSONObject().put("id", UUID.randomUUID().toString())
                .put("buildRef", BuildConfig.QUALIFICATION_BUILD_REF).put("version", BuildConfig.VERSION_NAME)
                .put("startedAtDeviceUtcMillis", System.currentTimeMillis()).put("startedElapsedMillis", startedAt)
                .put("closed", false).put("nativeThreadStarted", false).put("nativeInstanceFreed", false).put("nativeThreadJoined", false).put("events", JSONArray())
            attempts.put(attempt)
            failureToken = callbackFailures.begin()
            check(record("start-requested")) { "journal-unavailable" } // Durable intent precedes native allocation/authentication.
            LibFreeRDP.setSink(this)
            handle = LibFreeRDP.freerdp_new(app)
            check(handle != 0L) { "native-allocation-failed" }
            callbackFailures.bind(checkNotNull(failureToken), handle)
            check(LibFreeRDP.freerdp_parse_arguments(handle, selected.arguments())) { "native-arguments-rejected" }
            check(LibFreeRDP.freerdp_set_external_certificate_management(handle)) { "native-trust-policy-rejected" }
            change { it.copy(state = "connecting", detail = "NLA and an endpoint-bound leaf certificate pin are required", busy = true, connected = false) }
            check(LibFreeRDP.freerdp_connect(handle)) { "native-thread-start-failed" }
            nativeThreadStarted = true
            attempt?.put("nativeThreadStarted", true)
            ensureObserver()
        }
    }

    fun disconnect(reason: String) {
        stopping.set(true) // Authentication/trust callbacks see cancellation before the actor runs.
        owner { stopOnOwner(reason) }
    }
    private fun stopOnOwner(reason: String) {
        if (handle == 0L) {
            finishOwnerIfClosed()
            return
        }
        ensureObserver()
        persistFirstFailure()
        if (stopAt == 0L) {
            stopAt = SystemClock.elapsedRealtime(); stopReason = reason
            releaseOnOwner()
            stopping.set(true); liveConnected = false
            val requested = LibFreeRDP.freerdp_disconnect(handle)
            change { it.copy(state = "stopping", detail = failureDetail(if (requested) reason else "Native stop request failed; retaining ownership"), busy = true, connected = false) }
            record(if (requested) "stop-requested" else "stop-request-failed")
        }
    }

    private fun observeNative() {
        val current = handle
        if (current == 0L) return
        persistFirstFailure()
        if (LibFreeRDP.freerdp_join(current, 0)) {
            // A successful join has completed callbacks. Drain the synchronous cause
            // here even if the polling task beat its queued failure handler.
            callbackFailures.retire(failureToken)
            persistFirstFailure()
            liveConnected = false; stopping.set(true)
            val error = LibFreeRDP.freerdp_get_last_error_string(current).take(180)
            LibFreeRDP.freerdp_free(current)
            handle = 0L
            LibFreeRDP.setSink(null)
            config = null; heldKeys.clear(); heldButtons.clear(); frame.clear()
            attempt?.put("closed", true)?.put("nativeThreadJoined", nativeThreadStarted)?.put("nativeInstanceFreed", true)?.put("nativeError", error)
                ?.put("terminalEvent", terminalEvent)?.put("stopReason", stopReason)
            change { it.copy(state = "closed", detail = failureDetail(error), busy = false, connected = false) }
            try { record("native-instance-reaped-and-freed") }
            finally {
                attempt = null
                // The closed journal/UI retain the cause. A later configuration
                // failure before a new attempt must not inherit its summary.
                failureToken = null; persistedFailure = null
                poll?.cancel(false); poll = null
                finishOwnerIfClosed()
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!stopping.get() && now - startedAt > 10 * 60 * 1000) stopOnOwner("finite-probe-deadline")
        if (stopAt != 0L && now - stopAt > 10000) {
            change { it.copy(state = "cleanup-pending", detail = failureDetail("Native thread has not joined; ownership retained"), busy = true, connected = false) }
        }
        change { it }
        if (now - lastPersist > 2000) persist()
    }

    fun key(code: Int, down: Boolean): Boolean {
        val virtualKey = RdpKeyMap.virtualKey(code) ?: return false
        if (!liveConnected || stopping.get()) return false
        input {
            check(LibFreeRDP.freerdp_send_key_event(handle, virtualKey, down))
            if (down) heldKeys.add(virtualKey) else heldKeys.remove(virtualKey)
            inputCount.incrementAndGet()
        }
        return true
    }
    fun pointer(x: Int, y: Int, flags: Int) {
        if (!liveConnected || stopping.get()) return
        val size = state
        if (x !in 0 until size.width || y !in 0 until size.height) return
        input {
            check(LibFreeRDP.freerdp_send_cursor_event(handle, x, y, flags))
            pointerX = x; pointerY = y; frame.move(x, y)
            for (button in intArrayOf(0x1000, 0x2000, 0x4000)) {
                if (flags and button != 0) {
                    if (flags and 0x8000 != 0) heldButtons.add(button) else heldButtons.remove(button)
                }
            }
            inputCount.incrementAndGet()
        }
    }
    fun text(value: String) {
        if (!liveConnected || stopping.get()) return
        if (value.isEmpty() || value.length > 256 || '\u0000' in value) {
            change { it.copy(detail = "Text must contain 1–256 UTF-16 units without NUL") }; return
        }
        input {
            check(LibFreeRDP.freerdp_is_unicode_input_supported(handle)) { "remote-unicode-input-unavailable" }
            // RDP Unicode input carries UTF-16 units, including paired supplementary units.
            for (unit in value) {
                check(LibFreeRDP.freerdp_send_unicodekey_event(handle, unit.code, true))
                check(LibFreeRDP.freerdp_send_unicodekey_event(handle, unit.code, false))
                inputCount.addAndGet(2)
            }
        }
    }
    private fun input(action: () -> Unit) {
        if (pendingInput.incrementAndGet() > 64) {
            pendingInput.decrementAndGet(); disconnect("input-backlog-limit"); return
        }
        if (!owner {
            try { if (handle != 0L && liveConnected && !stopping.get()) action() }
            finally { pendingInput.decrementAndGet() }
        }) pendingInput.decrementAndGet()
    }
    fun releaseInput() { owner { if (!releaseOnOwner()) stopOnOwner("input-release-failed") } }
    private fun releaseOnOwner(): Boolean {
        var released = true
        if (handle != 0L && liveConnected) {
            for (key in heldKeys) released = LibFreeRDP.freerdp_send_key_event(handle, key, false) && released
            for (button in heldButtons) released = LibFreeRDP.freerdp_send_cursor_event(handle, pointerX, pointerY, button) && released
            if (!released) stopping.set(true)
        }
        heldKeys.clear(); heldButtons.clear()
        return released
    }
    fun close() {
        listener = null; closed.set(true); disconnect("activity-destroyed")
    }

    override fun event(instance: Long, name: String, width: Int, height: Int, bpp: Int) {
        if (instance != handle || instance == 0L) return
        when (name) {
            "settings", "resize" -> {
                frame.resize(width, height)
                change { it.copy(width = width, height = height) }
            }
            "connected" -> if (!stopping.get()) liveConnected = true
            "disconnecting", "disconnected", "native-failure" -> {
                liveConnected = false
                if (name != "disconnecting") terminalEvent = name
                change { it.copy(connected = false) }
            }
            "pointer-null", "pointer-default" -> {
                frame.defaultPointer()
                change { it.copy(cursorMode = if (name == "pointer-null") "hidden" else "default") }
                return
            }
        }
        owner {
            if (instance != handle) return@owner
            if (name == "connected" && !stopping.get()) {
                change { it.copy(state = "connected", detail = "Native connection callback received", busy = true, connected = true) }
            }
            record(name)
            if (name.startsWith("unsupported-") || name.contains("certificate") || name.startsWith("unexpected-")) stopOnOwner(name)
        }
    }
    override fun graphics(instance: Long, x: Int, y: Int, width: Int, height: Int) {
        if (instance != handle || stopping.get()) return
        check(frame.update(instance, x, y, width, height)) { "native-frame-copy-failed" }
        frameCount.incrementAndGet()
        change { it }
    }
    override fun pointer(instance: Long, pixels: IntArray, width: Int, height: Int, hotX: Int, hotY: Int) {
        if (instance != handle || stopping.get()) return
        frame.pointer(pixels, width, height, hotX, hotY); change { it.copy(cursorMode = "custom") }
    }
    override fun authenticate(instance: Long, user: StringBuilder, domain: StringBuilder, password: StringBuilder): Boolean {
        val selected = config ?: return false
        if (instance != handle || stopping.get()) return false
        user.setLength(0); user.append(selected.username)
        domain.setLength(0); domain.append(selected.domain)
        password.setLength(0); password.append(selected.password)
        authCallbacks.incrementAndGet()
        return true
    }
    override fun verify(instance: Long, pem: ByteArray, host: String, port: Long, flags: Long): Int {
        val selected = config ?: return 0
        val accepted = instance == handle && !stopping.get() && RdpTrustPolicy.accepts(
            pem, host, port, flags, selected.host, selected.port, selected.sha256)
        if (accepted) certAccepted.incrementAndGet() else certRejected.incrementAndGet()
        return if (accepted) 1 else 0
    }
    override fun callbackFailure(instance: Long, operation: String, failureClass: String, dimensions: IntArray) {
        if (instance == 0L || instance != handle) return
        val captured = callbackFailures.capture(instance, operation, failureClass, dimensions, SystemClock.elapsedRealtime() - startedAt) {
            stopping.set(true)
        } ?: return
        owner {
            // Native addresses may be reused. Token identity rejects a queued failure
            // from an already reaped attempt even when its address matches again.
            if (instance != handle || !callbackFailures.isActive(captured)) return@owner
            persistFirstFailure()
            failedOnOwner("native-callback-failure")
        }
    }

    private fun readJournal() {
        if (journalFile.baseFile.exists()) {
            check(journalFile.baseFile.length() <= 262144)
            val document = JSONObject(journalFile.readFully().toString(Charsets.UTF_8))
            check(document.getInt("schemaVersion") == 1)
            attempts = document.getJSONArray("attempts")
            check(attempts.length() <= 16)
            for (i in 0 until attempts.length()) {
                val previous = attempts.getJSONObject(i)
                if (!previous.getBoolean("closed")) previous.put("closed", true).put("outcome", "process-interrupted")
            }
        }
        journalReady = true
        check(persist()) { "journal-unavailable" }
    }
    private fun record(name: String): Boolean {
        attempt?.getJSONArray("events")?.let { events ->
            if (events.length() < 80) events.put(JSONObject().put("name", name).put("elapsedMillis", SystemClock.elapsedRealtime()))
            else attempt?.put("eventsOmitted", attempt!!.optInt("eventsOmitted") + 1)
        }
        return persist()
    }
    private fun persist(): Boolean {
        if (!journalReady) return false
        return try { persistChecked(); true }
        catch (_: Exception) {
            journalReady = false
            stopping.set(true)
            change { it.copy(detail = failureDetail("Journal write failed; further connections disabled") + "; journal unavailable") }
            // Queue stop after this operation so persistence failure cannot recurse into itself.
            if (handle != 0L) owner { stopOnOwner("journal-write-failed") }
            false
        }
    }
    private fun persistChecked() {
        callbackFailures.snapshot(failureToken)?.let { cause ->
            attempt?.put("firstFailure", JSONObject(cause.journalFields()))
        }
        attempt?.put("frameUpdates", frameCount.get())?.put("inputEvents", inputCount.get())
            ?.put("certificateAccepts", certAccepted.get())?.put("certificateRejects", certRejected.get())
            ?.put("authenticationCallbacks", authCallbacks.get())?.put("width", state.width)?.put("height", state.height)
        val bytes = JSONObject().put("schemaVersion", 1).put("attempts", attempts).toString(2).toByteArray()
        check(bytes.size <= 262144)
        val output = journalFile.startWrite()
        try { output.write(bytes); journalFile.finishWrite(output) }
        catch (error: Exception) { journalFile.failWrite(output); throw error }
        lastPersist = SystemClock.elapsedRealtime()
    }
    companion object { private val lease = AtomicReference<RdpSession?>() }
}
