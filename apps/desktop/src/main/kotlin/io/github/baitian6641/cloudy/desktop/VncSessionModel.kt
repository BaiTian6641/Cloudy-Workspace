package io.github.baitian6641.cloudy.desktop

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.InetAddresses
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.baitian6641.cloudy.desktop.vnc.VncEngine
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Referential identities never cross saved state, intents, logs or process restoration. */
internal class VncAttempt
internal class VncPresentedFrame(val attempt: VncAttempt, val frame: VncEngine.Frame) {
    val identity = Any()
}
internal enum class VncIssue {
    NONE, WRONG_APP, BUSY, PROFILES_UNAVAILABLE, STALE_PROFILE, NUMERIC_ENDPOINT_REQUIRED,
    PERMISSION_REQUIRED, INVALID_PIN_OR_DURATION, CONNECT_REJECTED, ENGINE_FAILED,
    RETIRING, CLEANUP_UNCERTAIN, INTERRUPTED,
}
internal data class VncUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val loading: Boolean = false,
    val issue: VncIssue = VncIssue.NONE,
    val engineState: VncEngine.State? = null,
    val releaseOutcome: VncEngine.ReleaseOutcome? = null,
    val attempt: VncAttempt? = null,
    val presented: Boolean = false,
    val canRetryAbandonedCleanup: Boolean = false,
    val inputEnabled: Boolean = false,
    val operationFailure: VncOperationFailure? = null,
) {
    override fun toString() = "VncUiState(redacted)"
}

/** One original engine remains reachable after ViewModel loss if native cleanup is uncertain.
 * Main-thread confined. It holds application-context engine state only, never an Activity/View.
 * There is no service, automatic retry, reconnect or permanently scheduled registry poller.
 */
private object DesktopVncOwner {
    class Record(val window: Any, val attempt: VncAttempt, val engine: VncEngine,
        val operationOutcome: VncOperationOutcome) {
        var stopped = false
        var abandoned = false
        val presentation = VncPresentationReceipt()
    }
    var record: Record? = null
    fun reap() {
        val old = record ?: return
        // Public FAILED is emitted by finishWithoutHandle; native cleanup uncertainty has its own state.
        val snapshot = old.engine.snapshot()
        if (snapshot.state in setOf(VncEngine.State.RETIRED, VncEngine.State.FAILED)) {
            // Any window may reap, but only the original model owns this retained result holder.
            // Capture before unlinking: its next poll may otherwise see only an empty registry.
            old.operationOutcome.observe(old.attempt, snapshot.nativeError,
                snapshot.state == VncEngine.State.FAILED)
            record = null
        }
    }
}

/** Desktop-owned session composition over the real public JNI owner.
 * All methods except the bounded catalog worker run on main. Submit transfers arrays to this model;
 * cancellation wipes them only while model-owned. Entering real Engine.connect relinquishes secret
 * ownership, including rejection; only the engine may then consume/wipe those asynchronous buffers.
 * UI must clear its native editable/composition/undo state immediately on submit/cancel/pause.
 */
internal class VncSessionModel(private val app: Application) : ViewModel() {
    private val main = Handler(Looper.getMainLooper())
    private val window = Any()
    private var attachment: Any? = null
    private var detachedAttachment: Any? = null
    private var foreground = false
    private var cleared = false
    private var epoch = 0L
    private var catalog: ConnectionProfileCatalog? = null
    private var pending: VncAdmissionBuffers? = null
    private var retainedFrame: VncPresentedFrame? = null
    private var inputAttempt: VncAttempt? = null
    private val operationOutcome = VncOperationOutcome()
    private val worker = ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
        ArrayBlockingQueue(1), { task -> Thread(task, "CloudyVncProfileRead").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }
    var state by mutableStateOf(VncUiState())
        private set

    private val poll = object : Runnable {
        override fun run() {
            if (cleared || attachment == null) return
            updateSnapshot()
            main.postDelayed(this, 100)
        }
    }
    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }
    private fun allowed(token: Any) = !cleared && attachment === token && foreground
    private fun cancelAdmission() {
        epoch++; pending?.cancel(); pending = null
        state = state.copy(loading = false)
    }

    fun attach(token: Any): Boolean {
        mainThread()
        if (cleared || (attachment != null && attachment !== token)) return false
        attachment = token
        detachedAttachment = null
        main.removeCallbacks(poll)
        updateSnapshot()
        main.post(poll)
        return true
    }
    fun foreground(token: Any, value: Boolean) {
        mainThread()
        if (attachment !== token || cleared) return
        foreground = value
        if (!value) {
            revokeInput()
            cancelAdmission()
            DesktopVncOwner.record?.takeIf { it.window === window }?.presentation?.revoke()
            state = state.copy(presented = false)
        }
    }
    fun detach(token: Any) {
        mainThread()
        if (attachment !== token) return
        revokeInput()
        foreground = false
        cancelAdmission()
        attachment = null
        detachedAttachment = token
        DesktopVncOwner.record?.takeIf { it.window === window }?.presentation?.revoke()
        state = state.copy(presented = false)
        main.removeCallbacks(poll)
    }
    /** Only the current attachment (or its not-yet-replaced detach) may report onStop.
     * A late old Activity cannot stop an already attached replacement's original engine.
     */
    fun windowStopped(token: Any, changingConfigurations: Boolean) {
        mainThread()
        if (attachment !== token && detachedAttachment !== token) return
        if (!changingConfigurations) retireOriginal()
    }

    fun refresh(token: Any) {
        mainThread()
        if (!allowed(token)) return
        cancelAdmission()
        val generation = epoch
        state = state.copy(loading = true, issue = VncIssue.NONE)
        try {
            worker.execute {
                val result = readProfiles()
                main.post {
                    if (cleared || generation != epoch || !allowed(token)) return@post
                    if (result is ConnectionProfilesResult.Success) {
                        catalog = result.value
                        state = state.copy(profiles = result.value.profiles.filter {
                            it.owner == WindowOwner.DESKTOP && it.protocol == ConnectionProtocol.VNC
                        }, loading = false, issue = VncIssue.NONE)
                    } else state = state.copy(loading = false, issue = VncIssue.PROFILES_UNAVAILABLE)
                }
            }
        } catch (_: RejectedExecutionException) {
            state = state.copy(loading = false, issue = VncIssue.BUSY)
        }
    }

    /** Pin is a32-byte independently compared exact-leaf SHA256 value, not observed-first-use trust.
     * finiteSeconds is the retained engine's10..600s PRIVATE QUALIFICATION limit, not product policy.
     * No raw field, secret, pin or connection authority is restored by this model.
     */
    fun connect(token: Any, profileId: String, revision: Long, pin: ByteArray,
        username: ByteArray, password: ByteArray, finiteSeconds: Int) =
        connectWithPolicy(token, profileId, revision, pin, username, password, finiteSeconds, false)

    /** Ordinary foreground-owned session: no overall run limit, never background/reconnect authority. */
    fun connectUserEnded(token: Any, profileId: String, revision: Long, pin: ByteArray,
        username: ByteArray, password: ByteArray) =
        connectWithPolicy(token, profileId, revision, pin, username, password, 0, true)

    private fun connectWithPolicy(token: Any, profileId: String, revision: Long, pin: ByteArray,
        username: ByteArray, password: ByteArray, finiteSeconds: Int, userEnded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            pin.fill(0); username.fill(0); password.fill(0)
            throw IllegalStateException("VNC_MAIN_THREAD")
        }
        val admission = VncAdmissionBuffers(pin, username, password)
        if (!allowed(token)) { admission.cancel(); return }
        captureOperationFailure()
        DesktopVncOwner.reap()
        if (DesktopVncOwner.record != null || pending != null) {
            admission.cancel(); state = state.copy(issue = VncIssue.BUSY); return
        }
        val previous = catalog
        val selected = previous?.profiles?.singleOrNull {
            it.id == profileId && it.revision == revision && it.owner == WindowOwner.DESKTOP &&
                it.protocol == ConnectionProtocol.VNC
        }
        if (previous == null || selected == null) { admission.cancel(); state = state.copy(issue = VncIssue.STALE_PROFILE); return }
        if (app.packageName != "io.github.baitian6641.cloudy.desktop") {
            admission.cancel(); state = state.copy(issue = VncIssue.WRONG_APP); return
        }
        if (pin.size != 32 || (if (userEnded) finiteSeconds != 0 else finiteSeconds !in 10..600)) {
            admission.cancel(); state = state.copy(issue = VncIssue.INVALID_PIN_OR_DURATION); return
        }
        try { InetAddresses.parseNumericAddress(selected.host); require(!selected.host.contains('%')) }
        catch (_: IllegalArgumentException) {
            admission.cancel(); state = state.copy(issue = VncIssue.NUMERIC_ENDPOINT_REQUIRED); return
        }
        if (!permissionGranted()) {
            admission.cancel(); state = state.copy(issue = VncIssue.PERMISSION_REQUIRED); return
        }
        pending = admission
        val generation = ++epoch
        state = state.copy(loading = true, issue = VncIssue.NONE)
        try {
            worker.execute {
                val result = readProfiles()
                // The worker never reads submitted buffers. Cancellation wipes them on main;
                // a late result can neither reconnect nor resurrect credential ownership.
                if (!main.post {
                    try {
                        if (pending !== admission || admission.cancelled || generation != epoch || !allowed(token)) return@post
                        val current = when (result) {
                            is ConnectionProfilesResult.Success -> result.value
                            is ConnectionProfilesResult.Failure -> null
                        }
                        val exact = current?.profiles?.singleOrNull {
                            it.id == selected.id && it.revision == selected.revision &&
                                it.owner == WindowOwner.DESKTOP && it.protocol == ConnectionProtocol.VNC &&
                                it.host == selected.host && it.port == selected.port
                        }
                        if (current == null || current.generation != previous.generation || exact == null) {
                            state = state.copy(issue = VncIssue.STALE_PROFILE); return@post
                        }
                        if (!permissionGranted()) { state = state.copy(issue = VncIssue.PERMISSION_REQUIRED); return@post }
                        DesktopVncOwner.reap()
                        if (DesktopVncOwner.record != null) { state = state.copy(issue = VncIssue.BUSY); return@post }
                        val config = if (userEnded) VncEngine.Config.userEnded(exact.id, exact.host, exact.port, admission.pin)
                        else VncEngine.Config(exact.id, exact.host, exact.port, admission.pin, finiteSeconds)
                        val engine = VncEngine(app)
                        val record = DesktopVncOwner.Record(window, VncAttempt(), engine, operationOutcome)
                        retainedFrame = null
                        DesktopVncOwner.record = record
                        operationOutcome.admit(record.attempt)
                        state = state.copy(operationFailure = null)
                        // Config cloned the pin. Relinquish both secret arrays immediately before
                        // entering the public asynchronous owner; even rejection belongs to engine.
                        val credentials = admission.transferToEngine()
                        if (!engine.connect(config, credentials.username, credentials.password)) {
                            captureOperationFailure(forceFailure = true)
                            state = state.copy(issue = VncIssue.CONNECT_REJECTED)
                        }
                        updateSnapshot()
                    } catch (_: RuntimeException) {
                        captureOperationFailure(forceFailure = true)
                        stopOriginal()
                        state = state.copy(issue = VncIssue.CONNECT_REJECTED)
                    } catch (_: LinkageError) {
                        captureOperationFailure(forceFailure = true)
                        stopOriginal()
                        state = state.copy(issue = VncIssue.CONNECT_REJECTED)
                    } finally {
                        admission.wipeOwned()
                        if (pending === admission) { pending = null; state = state.copy(loading = false) }
                    }
                }) admission.wipeOwned()
            }
        } catch (_: RejectedExecutionException) {
            admission.cancel(); pending = null
            state = state.copy(loading = false, issue = VncIssue.BUSY)
        }
    }

    private fun permissionGranted(): Boolean = try {
        Build.VERSION.SDK_INT < 37 || app.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: RuntimeException) { false }
    private fun readProfiles(): ConnectionProfilesResult<ConnectionProfileCatalog> = try {
        ConnectionProfiles(app).load()
    } catch (_: RuntimeException) {
        ConnectionProfilesResult.Failure(io.github.baitian6641.cloudy.platform.ConnectionProfilesCode.STORE_UNAVAILABLE)
    }
    private fun stopOriginal() {
        val record = DesktopVncOwner.record ?: return
        if (record.window !== window || record.stopped) return
        revokeInput()
        record.stopped = true
        retainedFrame = null
        record.engine.stop()
    }
    fun disconnect(token: Any) {
        mainThread()
        if (attachment !== token) return
        retireOriginal()
    }
    private fun retireOriginal() {
        cancelAdmission()
        stopOriginal()
        updateSnapshot()
    }
    /** Only a deliberate recovery control may call this, after displaying current uncertainty.
     * It retries the same original owner, including an abandoned model's record; never new connect.
     */
    fun retryCleanup(token: Any) {
        mainThread()
        if (!allowed(token)) return
        val record = DesktopVncOwner.record ?: return
        if (record.window !== window && !record.abandoned) return
        if (record.engine.snapshot().state != VncEngine.State.CLEANUP_UNCERTAIN) return
        record.engine.retryCleanup()
        updateSnapshot()
    }
    /** Explicit local consent, never restored or inferred from fullscreen/Connect. */
    fun enableInput(token: Any, attempt: VncAttempt): Boolean {
        mainThread()
        val record = DesktopVncOwner.record ?: return false
        if (currentAttempt(token) !== attempt || !record.presentation.presented ||
            record.window !== window || record.stopped) return false
        inputAttempt = attempt
        state = state.copy(inputEnabled = true)
        return true
    }
    fun inputAllowed(token: Any, attempt: VncAttempt): Boolean {
        mainThread()
        val record = DesktopVncOwner.record ?: return false
        return inputAttempt === attempt && currentAttempt(token) === attempt &&
            record.window === window && record.presentation.presented && !record.stopped
    }
    fun pointer(token: Any, attempt: VncAttempt, x: Int, y: Int, mask: Int): Boolean {
        mainThread()
        if (!inputAllowed(token, attempt) || x < 0 || y < 0 || mask !in 0..127) return false
        val record = DesktopVncOwner.record ?: return false
        if (record.engine.pointer(x, y, mask)) return true
        revokeInput()
        return false
    }
    fun key(token: Any, attempt: VncAttempt, symbol: Int, down: Boolean): Boolean {
        mainThread()
        if (!inputAllowed(token, attempt)) return false
        val record = DesktopVncOwner.record ?: return false
        if (record.engine.key(symbol, down)) return true
        revokeInput()
        return false
    }
    /** Release remains callable after local ownership loss; it never enables another attempt. */
    fun releaseInput(token: Any, attempt: VncAttempt? = null) {
        mainThread()
        if (attachment !== token && detachedAttachment !== token) return
        if (attempt != null && DesktopVncOwner.record?.attempt !== attempt) return
        revokeInput()
    }
    private fun revokeInput() {
        val previous = inputAttempt
        inputAttempt = null
        state = state.copy(inputEnabled = false)
        val record = DesktopVncOwner.record ?: return
        if (previous == null || record.window !== window || record.attempt !== previous || record.stopped) return
        // Admission is not a remote ACK. If release cannot even be queued, stop the original
        // owner so its bounded release-before-cancel path is retained; never silently resume.
        if (!record.engine.releaseInputs()) {
            record.stopped = true
            retainedFrame = null
            record.engine.stop()
        }
    }
    fun takeFrame(token: Any): VncPresentedFrame? {
        mainThread()
        if (!allowed(token)) return null
        val record = DesktopVncOwner.record ?: return null
        if (currentAttempt(token) !== record.attempt) { retainedFrame = null; return null }
        val frame = record.engine.takeLatestFrame()
        if (frame != null) retainedFrame = VncPresentedFrame(record.attempt, frame)
        return retainedFrame?.takeIf { it.attempt === record.attempt }
    }
    fun presented(token: Any, attempt: VncAttempt): Boolean {
        mainThread()
        if (!allowed(token)) return false
        val record = DesktopVncOwner.record ?: return false
        if (record.window !== window || record.attempt !== attempt || record.stopped ||
            record.engine.snapshot().state != VncEngine.State.FIRST_FRAME) return false
        if (record.presentation.acknowledge()) updateSnapshot()
        return true
    }
    fun currentAttempt(token: Any): VncAttempt? {
        mainThread()
        val record = DesktopVncOwner.record ?: return null
        return if (VncRenderEligibility.allows(record.engine.snapshot().state, allowed(token),
            record.window === window, record.stopped)) record.attempt else null
    }
    /** Model authority is checked every screen tick; no view-local cached acknowledgment may
     * survive foreground revocation. A real subsequent draw restores it idempotently.
     */
    fun needsPresentation(token: Any, attempt: VncAttempt): Boolean {
        mainThread()
        val record = DesktopVncOwner.record ?: return false
        return currentAttempt(token) === attempt && !record.presentation.presented
    }
    /** Cleanup success answers ownership, not whether the original connection succeeded.
     * Capture before reaping as well as while polling, without borrowing another window's outcome.
     */
    private fun captureOperationFailure(forceFailure: Boolean = false) {
        val record = DesktopVncOwner.record ?: return
        if (record.window !== window) return
        val snapshot = record.engine.snapshot()
        state = state.copy(operationFailure = operationOutcome.observe(record.attempt,
            snapshot.nativeError, forceFailure || snapshot.state == VncEngine.State.FAILED))
    }
    private fun updateSnapshot() {
        mainThread()
        val selected = inputAttempt
        if (selected != null && (attachment == null || currentAttempt(checkNotNull(attachment)) !== selected)) revokeInput()
        val record = DesktopVncOwner.record
        if (record != null && record.window !== window && record.engine.snapshot().state in
            setOf(VncEngine.State.RETIRED, VncEngine.State.FAILED)) {
            DesktopVncOwner.reap()
            updateSnapshot()
            return
        }
        if (record == null) {
            retainedFrame = null
            state = state.copy(engineState = null, releaseOutcome = null, attempt = null,
                operationFailure = operationOutcome.failure,
                presented = false, canRetryAbandonedCleanup = false,
                issue = if (state.engineState != null) VncIssue.NONE else state.issue)
            return
        }
        captureOperationFailure()
        val snapshot = record.engine.snapshot()
        val own = record.window === window
        if (!own || record.stopped || snapshot.state in setOf(VncEngine.State.RETIRED, VncEngine.State.FAILED)) {
            retainedFrame = null
        }
        val issue = when (snapshot.state) {
            VncEngine.State.CLEANUP_UNCERTAIN -> VncIssue.CLEANUP_UNCERTAIN
            VncEngine.State.STOP_REQUESTED -> VncIssue.RETIRING
            VncEngine.State.FAILED -> VncIssue.ENGINE_FAILED
            else -> if (own) VncIssue.NONE else VncIssue.BUSY
        }
        state = state.copy(engineState = snapshot.state, releaseOutcome = snapshot.releaseOutcome,
            operationFailure = operationOutcome.failure,
            attempt = if (own) record.attempt else null, presented = own && record.presentation.presented &&
                snapshot.state == VncEngine.State.FIRST_FRAME && !record.stopped,
            issue = issue, canRetryAbandonedCleanup = record.abandoned &&
                snapshot.state == VncEngine.State.CLEANUP_UNCERTAIN)
    }
    override fun onCleared() {
        mainThread()
        revokeInput()
        cleared = true
        retainedFrame = null
        foreground = false
        cancelAdmission()
        main.removeCallbacks(poll)
        stopOriginal()
        DesktopVncOwner.record?.takeIf { it.window === window }?.abandoned = true
        attachment = null
        detachedAttachment = null
        worker.shutdown()
        super.onCleared()
    }
}

/** Single-use model-to-engine secret handoff. After transfer this owner has no secret references.
 * No callback/backend injection: the production consumer is the public real VncEngine.connect.
 */
internal class VncAdmissionBuffers(val pin: ByteArray, username: ByteArray, password: ByteArray) {
    private var usernameOwned: ByteArray? = username
    private var passwordOwned: ByteArray? = password
    var cancelled = false
        private set
    fun cancel() { cancelled = true; wipeOwned() }
    fun wipeOwned() {
        pin.fill(0)
        usernameOwned?.fill(0)
        passwordOwned?.fill(0)
        usernameOwned = null
        passwordOwned = null
    }
    fun transferToEngine(): VncEngineCredentials {
        check(!cancelled) { "VNC_CREDENTIAL_OWNERSHIP" }
        val user = checkNotNull(usernameOwned) { "VNC_CREDENTIAL_OWNERSHIP" }
        val password = checkNotNull(passwordOwned) { "VNC_CREDENTIAL_OWNERSHIP" }
        val value = VncEngineCredentials(user, password)
        usernameOwned = null
        passwordOwned = null
        return value
    }
}
internal class VncEngineCredentials(val username: ByteArray, val password: ByteArray)

/** Render eligibility follows actual engine state, including autonomous retirement. */
internal object VncRenderEligibility {
    fun allows(state: VncEngine.State, foreground: Boolean, originalOwner: Boolean, stopped: Boolean) =
        foreground && originalOwner && !stopped && state == VncEngine.State.FIRST_FRAME
}
/** A draw acknowledgment is local presentation state, not native callback or physical screen proof. */
internal class VncPresentationReceipt {
    var presented = false
        private set
    fun revoke() { presented = false }
    fun acknowledge(): Boolean {
        val changed = !presented
        presented = true
        return changed
    }
}
