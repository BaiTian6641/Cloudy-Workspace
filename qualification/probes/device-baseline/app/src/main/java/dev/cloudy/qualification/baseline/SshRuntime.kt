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
    val canTrust: Boolean, val canCancel: Boolean, val trustSummary: String = "")

/** A finite process-owned worker outlives recreation; screens never own sockets or credentials. */
internal class SshRuntime(private val context: Context) {
    fun interface Listener { fun update(state: SshUiState) }
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val listeners = mutableSetOf<Listener>() // Main-thread only, bounded by native windows.
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val publicationQueued = AtomicBoolean(false)
    private val engine = AtomicReference<SshEngine?>()
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
        listener.update(latest)
    }

    fun canOpenWindow(): Boolean = listeners.size < 4

    fun removeListener(listener: Listener, changingConfiguration: Boolean) {
        listeners.remove(listener)
        // This increment has no service classification. Last-window hide cancels real work;
        // recreation merely changes the observer and retains the same finite owner.
        if (listeners.isEmpty() && !changingConfiguration) cancel()
    }

    fun refresh() {
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

    fun run(isStillVisible: () -> Boolean) {
        if (!latest.canRun || !busy.compareAndSet(false, true)) return
        cancelled.set(false)
        io.execute {
            var id: String? = null
            var deadline: java.util.concurrent.ScheduledFuture<*>? = null
            // A late watchdog belongs only to this attempt, never to the next explicit run.
            val timedOut = AtomicBoolean(false)
            try {
                require(ready && isStillVisible() && hasLanAccess()) { "SshAdmissionUnavailable" }
                val spec = SshConfig.read(File(context.filesDir, "ssh-config.json"))
                require(spec.configIdentity == config?.configIdentity) { "SshConfigChanged" }
                val owner = requireNotNull(state)
                error = null
                id = UUID.randomUUID().toString()
                requireNotNull(owner.begin(id, process, spec.configIdentity, time())) { "SshAttemptUnavailable" }
                persist()
                publish()
                val operation = SshEngine()
                engine.set(operation)
                if (cancelled.get() || !isStillVisible() || !hasLanAccess()) operation.cancel()
                deadline = timer.schedule({ timedOut.set(true); operation.cancel() }, 60, TimeUnit.SECONDS)
                val result = operation.run(spec, File(context.filesDir, "ssh-client-key"), id,
                    trust[spec.endpointId]) { observation ->
                    if (!hasLanAccess()) { operation.cancel(); throw IllegalStateException("SshLanRevoked") }
                    require(Security.getProviders().map { it.name } == originalProviders) { "SshProviderOrderChanged" }
                    owner.update(id, "running", observation.status, observation.observedHostKey,
                        observation.authenticated, observation.details, time())
                    persist() // Failure aborts before the engine proceeds to its next protocol operation.
                    publish()
                }
                val status = if (timedOut.get()) "failed" else if (cancelled.get()) "cancelled" else result.status
                val reason = if (timedOut.get()) "deadline" else result.reason
                owner.update(id, status, reason, result.observedHostKey, result.authenticated, result.details, time())
                persist()
            } catch (failure: Exception) {
                id?.let { state?.update(it, if (cancelled.get()) "cancelled" else "failed",
                    safe(failure), time = time()) }
                try { if (state != null) persist() } catch (writeFailure: Exception) {
                    ready = false; error = safe(writeFailure)
                }
                error = error ?: safe(failure)
            } finally {
                deadline?.cancel(false)
                engine.getAndSet(null)?.cancel()
                // A cancel request does not release the slot. Only actual engine return reaches here.
                busy.set(false)
                publish()
            }
        }
    }

    fun cancel() {
        if (!busy.get()) return
        cancelled.set(true)
        engine.get()?.cancel()
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
        val view = SshUiState(error ?: attempt?.status ?: "ready", summary,
            ready && !busy.get() && (owner?.snapshot()?.size ?: 8) < SshState.ATTEMPT_LIMIT,
            canTrust, busy.get() && engine.get() != null, keySummary)
        latest = view
        if (publicationQueued.compareAndSet(false, true)) main.post {
            publicationQueued.set(false)
            val current = latest
            listeners.toList().forEach { it.update(current) }
        }
    }

    private fun hasLanAccess(): Boolean = Build.VERSION.SDK_INT < 37 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    private fun time() = TransferTime(Instant.now().toString(), SystemClock.elapsedRealtime())
    private fun safe(failure: Exception) = failure.javaClass.simpleName.replace(Regex("[^A-Za-z0-9_-]"), "").take(80)
}
