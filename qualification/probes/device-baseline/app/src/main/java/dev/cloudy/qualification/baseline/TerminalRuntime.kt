package dev.cloudy.qualification.baseline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.security.Security
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class TerminalUiState(val status: String, val canConnect: Boolean, val canTrust: Boolean,
    val canDisconnect: Boolean, val identity: TerminalIdentity?, val trustSummary: String, val summary: String,
    val trustTicket: TerminalTrustTicket? = null)

/** One process-owned session; a renderer is an explicitly revocable owner, never a credential holder. */
internal class TerminalRuntime(private val context: Context) {
    fun interface Listener { fun update(state: TerminalUiState) }
    private data class Stop(val reason: String, val failure: Boolean)
    private class Active(val identity: TerminalIdentity, val transport: TerminalTransport,
        val abortOutput: () -> Unit) {
        val stop = AtomicReference<Stop?>(null)
    }
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val listeners = mutableSetOf<Listener>()
    private val busy = AtomicBoolean(false)
    private val active = AtomicReference<Active?>(null)
    private val publishQueued = AtomicBoolean(false)
    private val journal = TerminalJournal(context.filesDir)
    private val process = UUID.randomUUID().toString()
    private val providers = Security.getProviders().map { it.name }
    private val build = SshBuildIdentity(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        BuildConfig.QUALIFICATION_BUILD_REF)
    // The following state is confined to io; UI receives only immutable published views.
    private var state: TerminalState? = null
    private val trust = mutableMapOf<String, String>()
    private var config: TerminalConfigSpec? = null
    private var revision = 0L
    private var ready = false
    private var error: String? = null
    @Volatile private var latest = TerminalUiState("loading", false, false, false, null, "", "")

    init { refresh() }

    fun addListener(listener: Listener) {
        checkMain(); listeners.add(listener); listener.update(currentView())
    }
    fun removeListener(listener: Listener) { checkMain(); listeners.remove(listener) }

    fun refresh() {
        if (!busy.compareAndSet(false, true)) return
        io.execute {
            try {
                ready = false
                if (state == null) {
                    val data = journal.read()
                    state = TerminalState(data.attempts); trust.putAll(data.trust)
                    state!!.reconcileInterrupted(time())
                }
                persist()
                config = TerminalConfig.read(File(context.filesDir, "terminal-config.json"))
                ready = true; error = null
            } catch (failure: Exception) { ready = false; error = safe(failure) }
            finally { busy.set(false); publish() }
        }
    }

    fun acceptTrust(ticket: TerminalTrustTicket) {
        checkMain()
        if (!latest.canTrust || latest.trustTicket != ticket || !busy.compareAndSet(false, true)) return
        io.execute {
            try {
                val spec = requireNotNull(config)
                require(TerminalConfig.read(File(context.filesDir, "terminal-config.json")).configIdentity == spec.configIdentity)
                val row = requireNotNull(state).snapshot().last()
                val key = TerminalTrustPolicy.requireApproved(ticket, spec, row, trust[spec.endpointId])
                require(spec.endpointId in trust || trust.size < TerminalState.ATTEMPT_LIMIT)
                trust[spec.endpointId] = key
                persist() // No connection can follow an unpersisted trust decision.
                error = null
            } catch (failure: Exception) { ready = false; error = safe(failure) }
            finally { busy.set(false); publish() }
        }
    }

    fun connect(identity: TerminalIdentity, size: TerminalSize, rendererDetails: Map<String, String>, isStillVisible: () -> Boolean,
        output: (ByteArray, Boolean) -> Unit, drainOutput: () -> Unit,
        abortOutput: () -> Unit, stopped: () -> Unit): Boolean {
        checkMain()
        if (!latest.canConnect || !hasLanAccess() || !isStillVisible() || !busy.compareAndSet(false, true)) return false
        val operation = Active(identity, TerminalTransport(), abortOutput)
        check(active.compareAndSet(null, operation))
        io.execute {
            var started = false
            var cleanupCertain = true
            var cleanupReason: String? = null
            var deadline: java.util.concurrent.ScheduledFuture<*>? = null
            var admissionWatch: java.util.concurrent.ScheduledFuture<*>? = null
            try {
                require(ready && hasLanAccess() && isStillVisible() && operation.stop.get() == null) { "TerminalAdmissionUnavailable" }
                val spec = TerminalConfig.read(File(context.filesDir, "terminal-config.json"))
                require(spec.configIdentity == config?.configIdentity) { "TerminalConfigChanged" }
                val owner = requireNotNull(state)
                require(owner.begin(identity, process, spec.configIdentity, build, time(), rendererDetails)) { "TerminalAttemptUnavailable" }
                started = true; error = null; persist(); publish()
                deadline = timer.schedule({ stop(identity, "session-deadline", true) },
                    TerminalLimits.SESSION_DEADLINE_SECONDS, TimeUnit.SECONDS)
                admissionWatch = timer.scheduleWithFixedDelay({
                    if (!hasLanAccess()) stop(identity, "local-network-revoked", false)
                    else if (!isStillVisible()) stop(identity, "owner-no-longer-visible", false)
                }, 1, 1, TimeUnit.SECONDS)
                val result = operation.transport.run(spec, File(context.filesDir, "terminal-client-key"),
                    identity, trust[spec.endpointId], size, object : TerminalTransportListener {
                        override fun onObservation(observation: TerminalObservation) {
                            require(hasLanAccess() && isStillVisible()) { "TerminalAdmissionLost" }
                            require(Security.getProviders().map { it.name } == providers) { "TerminalProviderOrderChanged" }
                            owner.update(identity, observation.status, null, time(), observation.observedHostKey,
                                observation.authenticated, observation.details)
                            persist(); publish()
                        }
                        override fun onOutput(bytes: ByteArray, isStderr: Boolean) {
                            check(operation.stop.get() == null && hasLanAccess() && isStillVisible()) { "TerminalOutputStopped" }
                            output(bytes, isStderr)
                        }
                    })
                cleanupCertain = result.cleanupCertain
                cleanupReason = if (!cleanupCertain) result.reason ?: "helper-cleanup-uncertain" else null
                if (result.status == "closed" && cleanupCertain && operation.stop.get() == null) {
                    owner.update(identity, "closing", null, time(), result.observedHostKey,
                        result.authenticated, result.details)
                    persist(); publish()
                    // SSH EOF is not a renderer ACK. Preserve queued final output before closing.
                    drainOutput()
                }
                val request = operation.stop.get()
                val status = when {
                    !cleanupCertain -> "failed"
                    request?.failure == true -> "failed"
                    request != null -> if (request.reason == "user-disconnect" && result.authenticated) "closed" else "interrupted"
                    else -> result.status
                }
                val reason = cleanupReason ?: request?.reason ?: result.reason
                owner.update(identity, status, reason, time(), result.observedHostKey, result.authenticated, result.details)
                persist()
            } catch (failure: Exception) {
                // Transport owns its finally cleanup; journal failure never permits a new socket.
                if (started) {
                    val row = state?.snapshot()?.lastOrNull()
                    if (row?.terminal == false) state!!.update(identity,
                        if (operation.stop.get()?.failure == false) "interrupted" else "failed",
                        operation.stop.get()?.reason ?: safe(failure), time())
                    try { persist() } catch (writeFailure: Exception) { ready = false; error = safe(writeFailure) }
                }
                error = error ?: safe(failure)
            } finally {
                deadline?.cancel(false); admissionWatch?.cancel(false)
                operation.transport.cancel("owner-finished")
                operation.abortOutput()
                active.compareAndSet(operation, null)
                if (!cleanupCertain) { ready = false; error = cleanupReason ?: "helper-cleanup-uncertain" }
                busy.set(!cleanupCertain)
                publish()
                main.post(stopped)
            }
        }
        return true
    }

    fun input(identity: TerminalIdentity, bytes: ByteArray): TerminalAdmission {
        checkMain(); val current = active.get() ?: return TerminalAdmission.STOPPED
        if (identity != current.identity) return TerminalAdmission.STALE
        if (!hasLanAccess() || latest.status != "open" || current.stop.get() != null) return TerminalAdmission.STOPPED
        return current.transport.offerInput(identity, bytes)
    }

    fun resize(identity: TerminalIdentity, size: TerminalSize): TerminalAdmission {
        checkMain(); val current = active.get() ?: return TerminalAdmission.STOPPED
        if (identity != current.identity) return TerminalAdmission.STALE
        if (!hasLanAccess() || latest.status != "open" || current.stop.get() != null) return TerminalAdmission.STOPPED
        return current.transport.offerResize(identity, size)
    }

    fun stop(identity: TerminalIdentity, reason: String, failure: Boolean = false) {
        require(reason.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}")))
        val current = active.get() ?: return
        if (current.identity != identity) return
        current.stop.compareAndSet(null, Stop(reason, failure))
        current.transport.cancel(reason) // Independent of a blocked worker, renderer or journal.
        current.abortOutput()
        if (Looper.myLooper() == Looper.getMainLooper()) listeners.toList().forEach { it.update(currentView()) }
    }

    private fun persist() {
        revision = Math.addExact(revision, 1)
        journal.write(process, revision, requireNotNull(state), trust, providers, Security.getProviders().map { it.name })
    }

    private fun publish() {
        val rows = state?.snapshot().orEmpty(); val row = rows.lastOrNull(); val spec = config
        val canTrust = ready && !busy.get() && spec != null && row?.status == "trust-required" &&
            row.configIdentity == spec.configIdentity && row.observedHostKey?.let {
                SshTrust(spec.expectedHostKeySha256, it, trust[spec.endpointId]).canAcceptUnknown
            } == true
        latest = TerminalUiState(error ?: row?.status ?: "ready",
            ready && !busy.get() && rows.size < TerminalState.ATTEMPT_LIMIT,
            canTrust, active.get() != null, active.get()?.identity,
            "Endpoint: ${spec?.let { "${it.host}:${it.port}" } ?: "not loaded"}\n" +
                "Expected: ${spec?.expectedHostKeySha256 ?: "none"}\n" +
                "Observed: ${row?.observedHostKey ?: "none"}\nTrusted: ${spec?.let { trust[it.endpointId] } ?: "none"}",
            "Attempts: ${rows.size}/${TerminalState.ATTEMPT_LIMIT} · revision $revision\n" +
                (row?.let { "${it.status}: ${it.reason ?: "no failure recorded"}\n" +
                    "Sent ${it.details["inputBytes"] ?: "0"} bytes · received ${it.details["stdoutBytes"] ?: "0"} stdout / " +
                    "${it.details["stderrBytes"] ?: "0"} stderr bytes" } ?: "No terminal session has been started."),
            if (canTrust) TerminalTrustTicket(requireNotNull(row).identity, requireNotNull(spec).configIdentity,
                spec.endpointId, requireNotNull(row.observedHostKey)) else null)
        if (publishQueued.compareAndSet(false, true)) main.post {
            publishQueued.set(false); listeners.toList().forEach { it.update(currentView()) }
        }
    }
    private fun currentView(): TerminalUiState = if (active.get()?.stop?.get() != null) {
        latest.copy(status = "interruption-requested", canConnect = false, canDisconnect = false)
    } else latest
    private fun hasLanAccess(): Boolean = Build.VERSION.SDK_INT < 37 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
    private fun time() = TransferTime(Instant.now().toString(), SystemClock.elapsedRealtime())
    private fun safe(failure: Exception) = failure.javaClass.simpleName.take(80)
}
