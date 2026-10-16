package dev.cloudy.qualification.baseline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.security.Security
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class SshUiState(val status: String, val summary: String, val canRun: Boolean,
    val canTrust: Boolean, val canCancel: Boolean, val trustSummary: String = "",
    val activeAttemptId: String? = null, val ptyAction: SshPtyAction? = null)

/** A finite process-owned worker outlives recreation; screens never own sockets or credentials. */
internal class SshRuntime(private val context: Context) {
    fun interface Listener { fun update(state: SshUiState) }
    internal data class RetentionCompletion(val attemptId: String, val workerReturned: Boolean,
        val cleanupCertain: Boolean, val terminalPersisted: Boolean, val quarantined: Boolean, val workerStarted: Boolean = true)
    internal fun interface RetentionObserver { fun update(state: SshUiState, completion: RetentionCompletion?) }
    private val retention = SshRetentionPolicy()
    private val attemptIdentity = AtomicReference<String?>()
    private var retentionObserver: Pair<String, RetentionObserver>? = null // Main only; NOT a window.
    @Volatile private var retentionCompletion: RetentionCompletion? = null
    @Volatile private var retentionFailure: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val listeners = mutableSetOf<Listener>() // Main-thread only, bounded by native windows.
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val publicationQueued = AtomicBoolean(false)
    private data class ActiveEngine(val attemptId: String, val engine: SshEngine)
    private val engine = AtomicReference<ActiveEngine?>()
    private val buildIdentity = SshBuildIdentity(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        BuildConfig.QUALIFICATION_BUILD_REF)
    private val process = UUID.randomUUID().toString()
    private val originalProviders = Security.getProviders().map { it.name }
    private val journal = SshJournal(context.filesDir)
    private var state: SshState? = null // All fields below are owned by the IO executor.
    private val trust = mutableMapOf<String, String>()
    private var config: SshConfigSpec? = null
    private var revision = 0L
    private var error: String? = null
    private var ready = false
    @Volatile private var latest = SshUiState("initializing", "", false, false, false)

    init { refresh() }

    fun addListener(listener: Listener) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (listener !in listeners && listeners.size >= 4) {
            listener.update(SshUiState("observer-window-limit", "At most four active SSH observer windows", false, false, false))
            return
        }
        listeners.add(listener)
        listener.update(currentView())
    }

    fun canOpenWindow(): Boolean = listeners.size < 4

    fun removeListener(listener: Listener, changingConfiguration: Boolean) {
        listeners.remove(listener)
        // A service observer is deliberately separate. Only a promoted, matching lease
        // changes last-window cancellation; default operations stay visible-only.
        if (retention.shouldCancelLastWindow(listeners.size, changingConfiguration)) cancel()
    }

    fun refresh() {
        if (retention.quarantined()) return
        if (!busy.compareAndSet(false, true)) return
        io.execute {
            try {
                ready = false
                if (state == null) {
                    val restored = journal.read()
                    state = SshState(restored.attempts)
                    trust.putAll(restored.trust)
                    state!!.reconcileInterrupted(time())
                }
                persist() // A prior failed write must be retried before admission is possible.
                config = SshConfig.read(File(context.filesDir, "ssh-config.json"))
                ready = true
                error = null
            } catch (failure: Exception) { error = safe(failure); ready = false }
            finally { busy.set(false); publish() }
        }
    }

    fun acceptTrust() {
        if (!latest.canTrust || !busy.compareAndSet(false, true)) return
        io.execute {
            try {
                val spec = requireNotNull(config)
                val current = SshConfig.read(File(context.filesDir, "ssh-config.json"))
                require(current.configIdentity == spec.configIdentity) { "SshConfigChanged" }
                val attempt = requireNotNull(state).snapshot().last()
                require(attempt.configIdentity == spec.configIdentity && attempt.status == "trust-required")
                val observed = requireNotNull(attempt.observedHostKey)
                require(SshTrust(spec.expectedHostKeySha256, observed, trust[spec.endpointId]).canAcceptUnknown)
                require(trust.size < SshState.ATTEMPT_LIMIT) { "SshTrustLimit" }
                trust[spec.endpointId] = observed
                persist() // No authentication is triggered here or before this exact write succeeds.
                error = null
            } catch (failure: Exception) { error = safe(failure); ready = false }
            finally { busy.set(false); publish() }
        }
    }

    /** Main-thread reservation precedes service launch; it cannot itself open a connection. */
    fun reserveRetention(isStillVisible: () -> Boolean): String? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!isStillVisible() || !hasLanAccess() || !currentView().canRun || retentionObserver != null ||
            !busy.compareAndSet(false, true)) return null
        val id = UUID.randomUUID().toString()
        if (!retention.reserve(id, SystemClock.elapsedRealtime())) { busy.set(false); return null }
        attemptIdentity.set(id); cancelled.set(false); retentionFailure = null; retentionCompletion = null
        notifyObservers()
        main.postDelayed({ failRetentionStart(id, "retention-start-expired") }, SshRetentionPolicy.START_WINDOW_MILLIS)
        return id
    }

    fun retentionPending(id: String): Boolean = retention.pending(id, SystemClock.elapsedRealtime()) &&
        attemptIdentity.get() == id && !cancelled.get()

    /** Called only after actual startForeground succeeds. Duplicate or expired starts do nothing. */
    fun consumeRetention(id: String, observer: RetentionObserver): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!hasLanAccess() || !retentionPending(id) || !retention.consume(id, SystemClock.elapsedRealtime())) return false
        retentionObserver = id to observer
        execute(id, SshOperation.RETAINED_EXEC_PTY) { retention.retained(id) }
        return true
    }

    fun failRetentionStart(id: String, reason: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!retention.abandonPending(id)) return
        cancelled.set(true); attemptIdentity.compareAndSet(id, null); busy.set(false)
        retentionFailure = reason.take(80)
        notifyObservers()
    }

    fun reportRetentionAdmissionFailure(reason: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!busy.get()) { retentionFailure = reason.take(80); notifyObservers() }
    }

    fun detachRetentionObserver(id: String, observer: RetentionObserver) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (retentionObserver == (id to observer)) retentionObserver = null
        notifyObservers()
    }

    fun checkRetentionAccess(id: String) {
        if (attemptIdentity.get() == id && (!hasLanAccess() || !retention.retained(id))) cancelAttempt(id)
    }

    fun cancelAttempt(id: String) {
        if (attemptIdentity.get() != id) return
        if (Looper.myLooper() == Looper.getMainLooper() && retention.pending(id, SystemClock.elapsedRealtime())) {
            failRetentionStart(id, "retention-start-cancelled"); return
        }
        retention.revoke(id)
        cancel()
    }

    fun run(kind: SshOperation, isStillVisible: () -> Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (kind == SshOperation.RETAINED_EXEC_PTY || !currentView().canRun || !busy.compareAndSet(false, true)) return
        val id = UUID.randomUUID().toString()
        attemptIdentity.set(id); cancelled.set(false); retentionFailure = null
        execute(id, kind, isStillVisible)
    }

    private fun execute(stableId: String, kind: SshOperation, isAuthorized: () -> Boolean) {
        try { io.execute {
            var id: String? = null
            var deadline: java.util.concurrent.ScheduledFuture<*>? = null
            var cleanupUncertain = false
            var terminalPersisted = false
            var journalUncertain = false
            fun commit() {
                try { persist() } catch (failure: Exception) { journalUncertain = true; throw failure }
            }
            // A late watchdog belongs only to this attempt, never to the next explicit run.
            val timedOut = AtomicBoolean(false)
            try {
                require(ready && isAuthorized() && hasLanAccess()) { "SshAdmissionUnavailable" }
                val spec = SshConfig.read(File(context.filesDir, "ssh-config.json"))
                require(spec.configIdentity == config?.configIdentity) { "SshConfigChanged" }
                val owner = requireNotNull(state)
                error = null
                id = stableId
                requireNotNull(owner.begin(id, process, spec.configIdentity, kind, buildIdentity, time())) { "SshAttemptUnavailable" }
                commit()
                publish()
                val operation = SshEngine()
                engine.set(ActiveEngine(id, operation))
                if (cancelled.get() || !isAuthorized() || !hasLanAccess()) operation.cancel()
                deadline = timer.schedule({ timedOut.set(true); operation.cancel() }, kind.totalTimeoutSeconds, TimeUnit.SECONDS)
                val result = operation.run(spec, File(context.filesDir, "ssh-client-key"), id,
                    trust[spec.endpointId], kind) { observation ->
                    if (!hasLanAccess()) { operation.cancel(); throw IllegalStateException("SshLanRevoked") }
                    require(Security.getProviders().map { it.name } == originalProviders) { "SshProviderOrderChanged" }
                    owner.update(id, "running", observation.status, observation.observedHostKey,
                        observation.authenticated, observation.details, time())
                    commit() // Failure aborts before the engine proceeds to its next protocol operation.
                    publish()
                }
                cleanupUncertain = result.reason == "helper-cleanup-timeout" ||
                    (kind == SshOperation.RETAINED_EXEC_PTY && result.reason?.startsWith("cleanup-") == true)
                val status = if (timedOut.get()) "failed" else if (cancelled.get()) "cancelled" else result.status
                val reason = if (timedOut.get()) "deadline" else result.reason
                owner.update(id, status, reason, result.observedHostKey, result.authenticated, result.details, time())
                commit()
                terminalPersisted = true
            } catch (failure: Exception) {
                id?.let { state?.update(it, if (cancelled.get()) "cancelled" else "failed",
                    safe(failure), time = time()) }
                try { if (state != null) { commit(); terminalPersisted = id != null } } catch (writeFailure: Exception) {
                    ready = false; error = safe(writeFailure)
                }
                error = error ?: safe(failure)
            } finally {
                deadline?.cancel(false)
                engine.getAndSet(null)?.engine?.cancel()
                // A cancel request does not release the slot. If a helper survives the bounded
                // join, quarantine this process until restart; another run cannot overlap it.
                if (cleanupUncertain) {
                    ready = false
                    error = "ssh-cleanup-uncertain"
                }
                val retained = kind == SshOperation.RETAINED_EXEC_PTY
                if (retained) {
                    val durable = terminalPersisted && !journalUncertain
                    // No journal row exists when admission failed before begin; that is not a failed write.
                    retention.completed(stableId, !cleanupUncertain, durable || (id == null && !journalUncertain))
                    retentionCompletion = RetentionCompletion(stableId, true, !cleanupUncertain,
                        terminalPersisted, retention.quarantined())
                    if (retention.quarantined()) { ready = false; error = error ?: "retention-cleanup-or-evidence-uncertain" }
                }
                attemptIdentity.compareAndSet(stableId, null)
                busy.set(cleanupUncertain || retention.quarantined())
                publish()
            }
        }
        } catch (failure: java.util.concurrent.RejectedExecutionException) {
            // No worker was admitted. Fail closed rather than reusing an uncertain executor.
            if (kind == SshOperation.RETAINED_EXEC_PTY) {
                retention.completed(stableId, false, false)
                retentionCompletion = RetentionCompletion(stableId, false, false, false, true, workerStarted = false)
            }
            attemptIdentity.compareAndSet(stableId, null)
            cancelled.set(true)
            busy.set(kind == SshOperation.RETAINED_EXEC_PTY)
            retentionFailure = "ssh-worker-unavailable"
            notifyObservers()
        }
    }

    /** Admission stays on the UI thread; bounded engine controls never queue behind a blocked read. */
    fun control(attemptId: String, action: SshPtyAction, isStillVisible: () -> Boolean): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val view = latest
        if (!isStillVisible() || !hasLanAccess() || !busy.get() || cancelled.get() ||
            view.activeAttemptId != attemptId || view.ptyAction != action) return false
        val active = engine.get() ?: return false
        // Fence against a late click from a previous attempt, even if a new engine is already installed.
        if (active.attemptId != attemptId) return false
        return active.engine.control(action)
    }

    fun cancel() {
        if (!busy.get()) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val pending = attemptIdentity.get()
            if (pending != null && retention.pending(pending, SystemClock.elapsedRealtime())) {
                failRetentionStart(pending, "retention-start-cancelled"); return
            }
        }
        cancelled.set(true)
        engine.get()?.engine?.cancel()
        // Immediate UI acknowledgement is only a request. The durable terminal row follows
        // actual worker/control cleanup; the occupied slot is deliberately retained until then.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyObservers()
        }
    }

    private fun persist() {
        val owner = requireNotNull(state)
        revision++
        journal.write(snapshot(owner))
    }

    private fun snapshot(owner: SshState) = journal.encode(process, revision, owner, trust)
        .put("providerNamesAtRuntimeCreation", JSONArray(originalProviders))
        .put("providerNamesNow", JSONArray(Security.getProviders().map { it.name }))
        .put("providerOrderUnchanged", Security.getProviders().map { it.name } == originalProviders)

    private fun publish() {
        val owner = state
        val attempt = owner?.snapshot()?.lastOrNull()
        val spec = config
        val canTrust = ready && !busy.get() && spec != null && attempt?.status == "trust-required" &&
            attempt.configIdentity == spec.configIdentity && attempt.observedHostKey?.let {
                SshTrust(spec.expectedHostKeySha256, it, trust[spec.endpointId]).canAcceptUnknown
            } == true
        val summary = owner?.let { snapshot(it).toString(2) } ?: "No readable SSH journal"
        val keySummary = "Observed: ${attempt?.observedHostKey ?: "none"}\nIndependent fixture reference: ${spec?.expectedHostKeySha256 ?: "not loaded"}\nTrusted for endpoint: ${spec?.let { trust[it.endpointId] } ?: "none"}"
        val ptyAction = if (busy.get() && !cancelled.get() && attempt?.status == "running" &&
            attempt.operation.isPty && engine.get()?.attemptId == attempt.id) {
            when (attempt.reason) {
                "pty-awaiting-resize" -> SshPtyAction.RESIZE
                "pty-awaiting-finish" -> SshPtyAction.FINISH
                else -> null
            }
        } else null
        val visibleStatus = error ?: "cancellation-requested".takeIf { busy.get() && cancelled.get() }
            ?: attempt?.reason?.takeIf { attempt.status == "running" }
            ?: attempt?.status ?: "ready"
        val view = SshUiState(visibleStatus, summary,
            ready && !busy.get() && (owner?.snapshot()?.size ?: 8) < SshState.ATTEMPT_LIMIT,
            canTrust, busy.get() && !cancelled.get() && engine.get() != null, keySummary,
            attempt?.id?.takeIf { busy.get() }, ptyAction)
        latest = view
        if (publicationQueued.compareAndSet(false, true)) main.post {
            publicationQueued.set(false)
            val current = currentView()
            notifyObservers(current)
        }
    }

    private fun notifyObservers(view: SshUiState = currentView()) {
        check(Looper.myLooper() == Looper.getMainLooper())
        listeners.toList().forEach { it.update(view) }
        retentionObserver?.let { (id, observer) ->
            val completion = retentionCompletion?.takeIf { it.attemptId == id }
            if (retention.current(id) || completion != null) observer.update(view, completion)
        }
    }

    private fun currentView(): SshUiState {
        var view = latest
        retentionFailure?.let { view = view.copy(status = it) }
        if (retention.hasRequest() && engine.get() == null) view = view.copy(status = "retention-start-pending",
            canRun = false, canTrust = false, canCancel = true, activeAttemptId = attemptIdentity.get(), ptyAction = null)
        if (retentionObserver != null || retention.quarantined() || busy.get()) view = view.copy(canRun = false, canTrust = false)
        // Do not overwrite the IO owner's latest view from the main thread: a concurrent
        // terminal publication must never be replaced by an older cancellation request.
        return if (busy.get() && cancelled.get() && engine.get() != null) view.copy(status = "cancellation-requested",
            canCancel = false, ptyAction = null) else view
    }

    private fun hasLanAccess(): Boolean = Build.VERSION.SDK_INT < 37 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    private fun time() = TransferTime(Instant.now().toString(), SystemClock.elapsedRealtime())
    private fun safe(failure: Exception) = failure.javaClass.simpleName.replace(Regex("[^A-Za-z0-9_-]"), "").take(80)
}
