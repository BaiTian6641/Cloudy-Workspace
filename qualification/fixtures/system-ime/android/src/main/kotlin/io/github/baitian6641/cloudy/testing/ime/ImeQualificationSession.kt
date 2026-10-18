package io.github.baitian6641.cloudy.testing.ime

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.webkit.WebView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import io.github.baitian6641.cloudy.testing.ime.ImeFixtureResult.Outcome
import io.github.baitian6641.cloudy.testing.ime.ImeFixtureResult.Reason

/**
 * Finite OS-served IME client. Each command uses validated whitespace-free argument tokens:
 * UiAutomation uses Runtime.exec(String), not shell parsing. Request text never enters argv
 * or logs; only component/subtype tokens enter the explicit platform setup operations.
 */
class ImeQualificationSession private constructor(
    private val instrumentation: Instrumentation,
    private val automation: UiAutomation,
    private val context: Context,
    private val run: String,
    private val allowDeviceChange: Boolean,
    private val cancellation: () -> Boolean,
) : AutoCloseable {
    private val component = "${context.packageName}/${ImeFixtureProtocol.SERVICE_CLASS}"
    private val receiverComponent = "${context.packageName}/${ImeFixtureProtocol.RECEIVER_CLASS}"
    private val shell = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ime-qualification-shell")
    }

    private var previousDefault: String? = null
    private var previousEnabled: String? = null
    private var previousSubtype: String? = null
    private var enabledByUs = false
    private var leaseRequests = mutableListOf<String>()
    private var snapshotted = false
    private var ownLease = false
    private var lockOwned = false
    private var ignoreCancellation = false
    private var closed = false
    private var mutationUnknown = false
    private var shellUnknown = false
    private var workerSettled = false

    @Volatile
    var restored: Boolean = false
        private set

    @Volatile
    var shellRetired: Boolean = false
        private set

    @Volatile
    var cleanupConfirmed: Boolean = false
        private set

    @Volatile
    var closeReason: Reason? = null
        private set

    companion object {
        private const val TARGET_TIMEOUT_MILLIS = 5_000L
        private const val SETTLE_QUIET_MILLIS = 750L
        private const val RESULT_GRACE_MILLIS = 2_000L
        private const val SHELL_TIMEOUT_MILLIS = 5_000L
        private const val SNAPSHOT_TIMEOUT_MILLIS = 2_000L
        private const val CLOSE_TIMEOUT_MILLIS = 5_000L
        private const val MAX_SHELL_BYTES = 64 * 1024

        fun open(
            instrumentation: Instrumentation,
            automation: UiAutomation,
            run: String,
            allowDeviceChange: Boolean = false,
            cancellation: () -> Boolean = { Thread.currentThread().isInterrupted },
        ): ImeQualificationSession {
            check(Looper.myLooper() != Looper.getMainLooper()) { "IME_CLIENT_REQUIRES_WORKER_THREAD" }
            if (!ImeFixtureProtocol.isCanonicalUuid(run)) {
                throw ImeQualificationException("RUN_INVALID", Reason.SETUP_FAILED)
            }
            val session = ImeQualificationSession(
                instrumentation,
                automation,
                instrumentation.targetContext,
                run,
                allowDeviceChange,
                cancellation,
            )
            try {
                session.setup()
            } catch (failure: Throwable) {
                session.rollback()
                throw failure
            }
            return session
        }
    }

    fun commit(view: WebView, text: String): ImeFixtureResult =
        submit(view, ImeFixtureOperation.COMMIT, text)

    fun compose(view: WebView, text: String): ImeFixtureResult =
        submit(view, ImeFixtureOperation.COMPOSE, text)

    fun finishComposition(view: WebView): ImeFixtureResult =
        submit(view, ImeFixtureOperation.FINISH_COMPOSITION, null)

    fun enter(view: WebView): ImeFixtureResult = submit(view, ImeFixtureOperation.ENTER, null)

    fun interrupt(view: WebView): ImeFixtureResult = submit(view, ImeFixtureOperation.INTERRUPT, null)

    override fun close() {
        if (closed) return
        check(Looper.myLooper() != Looper.getMainLooper()) { "IME_CLIENT_REQUIRES_WORKER_THREAD" }
        closed = true
        ignoreCancellation = true
        val restoreInterrupt = Thread.interrupted()
        var reason: Reason? = null
        try {
            reason = when {
                mutationUnknown -> Reason.RESTORE_UNCONFIRMED
                ownLease && !awaitInputRetired() -> Reason.RESTORE_UNCONFIRMED
                snapshotted -> restoreSelection()
                else -> Reason.SETUP_FAILED
            }
        } catch (failure: Throwable) {
            reason = if (failure is ImeQualificationException) failure.reason else Reason.IO_FAILED
            if (!mutationUnknown && snapshotted) runCatching { shellRestore() }
        } finally {
            val requestsRetired = runCatching { retireRequestFiles() }.getOrDefault(false) &&
                requestsAbsent()
            if (!mutationUnknown && snapshotted && reason == null &&
                !runCatching { snapshotsMatch() }.getOrDefault(false)
            ) {
                reason = Reason.RESTORE_UNCONFIRMED
            }
            restored = snapshotted && !mutationUnknown && reason == null &&
                runCatching { snapshotsMatch() }.getOrDefault(false)
            if (!restored && reason == null) reason = Reason.RESTORE_UNCONFIRMED
            retireShell()

            var leaseRetired = false
            if (restored && requestsRetired && shellRetired) {
                leaseRetired = runCatching { deleteLeaseIfOwned() }.getOrDefault(false)
            }
            val lockRetired = when {
                leaseRetired && shellRetired ->
                    runCatching { ImeFixtureProtocol.releaseLock(context) }.getOrDefault(false)
                !ownLease && lockOwned && shellRetired &&
                    runCatching { ImeFixtureProtocol.recordAbsent(ImeFixtureProtocol.leaseFile(context)) }
                        .getOrDefault(false) ->
                    runCatching { ImeFixtureProtocol.releaseLock(context) }.getOrDefault(false)
                else -> false
            }
            closeReason = reason
            cleanupConfirmed = restored && requestsRetired && leaseRetired && lockRetired && shellRetired
            runCatching { writeSessionResult(requestsRetired, leaseRetired, lockRetired) }
            ignoreCancellation = false
            if (restoreInterrupt) Thread.currentThread().interrupt()
        }
    }

    // ---- setup / rollback -------------------------------------------------------------

    private fun setup() {
        checkCancelled()
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            fail(Reason.NOT_DEBUGGABLE)
        }
        checkToken(context.packageName)
        checkToken(component)
        checkToken(receiverComponent)
        val qemu = readShell("getprop ro.kernel.qemu", SHELL_TIMEOUT_MILLIS).trim()
        if (qemu != "1" && !allowDeviceChange) fail(Reason.DEVICE_CHANGE_NOT_ALLOWED)

        if (!ImeFixtureProtocol.acquireLock(context)) fail(Reason.LEASE_EXISTS)
        lockOwned = true
        if (!ImeFixtureProtocol.recordAbsent(ImeFixtureProtocol.leaseFile(context))) {
            fail(Reason.LEASE_EXISTS)
        }
        val selected = setting("default_input_method") ?: fail(Reason.PREVIOUS_IME_MISSING)
        checkToken(selected)
        if (android.content.ComponentName.unflattenFromString(selected) == null || selected.startsWith("-")) {
            fail(Reason.PREVIOUS_IME_MISSING)
        }
        if (selected.endsWith("/${ImeFixtureProtocol.SERVICE_CLASS}")) {
            fail(Reason.FIXTURE_ALREADY_SELECTED)
        }
        val enabled = setting("enabled_input_methods")
        val subtype = setting("selected_input_method_subtype")
        if (subtype != null && (!subtype.matches(Regex("-?[0-9]+")) || subtype.toIntOrNull() == null)) {
            fail(Reason.SETUP_FAILED)
        }
        previousDefault = selected
        previousEnabled = enabled
        previousSubtype = subtype
        snapshotted = true
        enabledByUs = enabled?.let { !enabledComponents(it).contains(component) } ?: true
        ImeFixtureProtocol.writeLease(
            context,
            LeaseSnapshot(
                run = run,
                component = component,
                ownerPid = Process.myPid(),
                previousDefault = selected,
                previousEnabled = enabled,
                previousSubtype = subtype,
                requests = emptyList(),
            ),
            replace = false,
        )
        ownLease = true
        leaseRequests = mutableListOf()
        checkCancelled()
        mutateShell("cmd input_method ime enable $component")
        checkCancelled()
        mutateShell("cmd input_method ime set $component")
        if (!awaitSelected()) fail(Reason.SETUP_FAILED)
    }

    private fun rollback() {
        ignoreCancellation = true
        val restoreInterrupt = Thread.interrupted()
        try {
            val reason = when {
                mutationUnknown -> Reason.RESTORE_UNCONFIRMED
                !snapshotted -> null
                else -> runCatching { restoreSelection() }.getOrElse { Reason.IO_FAILED }
            }
            val requestsRetired = runCatching { retireRequestFiles() }.getOrDefault(false) &&
                requestsAbsent()
            restored = snapshotted && !mutationUnknown && reason == null &&
                runCatching { snapshotsMatch() }.getOrDefault(false)
            retireShell()
            var leaseRetired = false
            if (restored && requestsRetired && shellRetired) {
                leaseRetired = runCatching { deleteLeaseIfOwned() }.getOrDefault(false)
            }
            val lockRetired = when {
                leaseRetired && shellRetired ->
                    runCatching { ImeFixtureProtocol.releaseLock(context) }.getOrDefault(false)
                !ownLease && lockOwned && shellRetired &&
                    runCatching { ImeFixtureProtocol.recordAbsent(ImeFixtureProtocol.leaseFile(context)) }
                        .getOrDefault(false) ->
                    runCatching { ImeFixtureProtocol.releaseLock(context) }.getOrDefault(false)
                else -> false
            }
            closeReason = reason ?: if (!restored && snapshotted) Reason.RESTORE_UNCONFIRMED else null
            cleanupConfirmed = restored && requestsRetired && leaseRetired && lockRetired && shellRetired
            if (ownLease || lockOwned) {
                runCatching { writeSessionResult(requestsRetired, leaseRetired, lockRetired) }
            }
        } finally {
            ignoreCancellation = false
            if (restoreInterrupt) Thread.currentThread().interrupt()
        }
    }

    private fun awaitSelected(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + SNAPSHOT_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            checkCancelled()
            if (setting("default_input_method") == component) return true
            SystemClock.sleep(20)
        }
        return false
    }

    private fun fail(reason: Reason): Nothing = throw ImeQualificationException(reason.name, reason)

    private fun checkOpen() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "IME_CLIENT_REQUIRES_WORKER_THREAD" }
        if (closed) throw ImeQualificationException("SESSION_CLOSED", Reason.SESSION_CLOSED)
    }

    // ---- request path -----------------------------------------------------------------

    private fun submit(view: WebView, operation: ImeFixtureOperation, text: String?): ImeFixtureResult {
        checkOpen()
        checkCancelled()
        if (text != null) {
            val encoded = try {
                ImeFixtureProtocol.utf8(text)
            } catch (failure: IOException) {
                return ImeFixtureResult(Outcome.REJECTED, Reason.INVALID_TEXT)
            }
            if (encoded.size > ImeFixtureProtocol.MAX_TEXT_BYTES) {
                return ImeFixtureResult(Outcome.REJECTED, Reason.TEXT_TOO_LARGE)
            }
        }
        var viewId = 0
        onMain {
            // IMM isActive/isAcceptingText call checkFocus and may start a new connection.
            // Observe native focus here; only the framework-bound service supplies IC readiness.
            if (view.isAttachedToWindow && view.hasWindowFocus() && view.hasFocus()) viewId = view.id
        }
        if (viewId <= 0) return ImeFixtureResult(Outcome.REJECTED, Reason.NO_ACTIVE_INPUT)
        val state = awaitTarget(viewId, SystemClock.elapsedRealtime() + TARGET_TIMEOUT_MILLIS)
            ?: return ImeFixtureResult(Outcome.REJECTED, Reason.NO_ACTIVE_INPUT)
        checkCancelled()
        val request = FixtureRequest(
            schema = ImeFixtureProtocol.SCHEMA,
            run = run,
            request = UUID.randomUUID().toString(),
            instance = state.instance,
            generation = state.generation,
            targetPackage = context.packageName,
            clientPid = Process.myPid(),
            fieldId = viewId,
            expiresElapsedMillis = SystemClock.elapsedRealtime() + ImeFixtureProtocol.MAX_REQUEST_AGE_MILLIS,
            operation = operation,
            text = text,
        )
        recordRequest(request.request)
        ImeFixtureProtocol.writeRequest(context, request)
        checkCancelled()
        dispatch(request.request)
        return awaitResult(request)
    }


    private fun awaitTarget(viewId: Int, deadline: Long): ImeState? {
        var observed: String? = null
        var quietSince = 0L
        while (true) {
            checkCancelled()
            val state = try {
                ImeFixtureProtocol.readState(context)
            } catch (failure: Exception) {
                throw ImeQualificationException("STATE_INVALID", Reason.IO_FAILED)
            }
            val target = state?.target
            val matches = state != null && target != null &&
                target.packageName == context.packageName &&
                target.fieldId == viewId &&
                target.clientPid == Process.myPid() &&
                target.clientUid == Process.myUid() &&
                target.hasConnection
            if (matches) {
                // Any bind/start/finish advances the published generation, including a restart
                // to the same editor. Submitting during that transition can be accepted by a
                // connection the app is replacing and never reach the renderer, so snapshot the
                // request fence only after the observed session has been quiescent.
                val token = "${checkNotNull(state).instance}:${state.generation}:${checkNotNull(target).fieldId}"
                val now = SystemClock.elapsedRealtime()
                if (token != observed) {
                    observed = token
                    quietSince = now
                } else if (now - quietSince >= SETTLE_QUIET_MILLIS) {
                    return state
                }
            } else {
                observed = null
            }
            if (SystemClock.elapsedRealtime() >= deadline) return null
            SystemClock.sleep(10)
        }
    }

    internal fun recordRequest(id: String) {
        val updated = leaseRequests + id
        if (updated.size > ImeFixtureProtocol.MAX_REQUESTS) {
            throw ImeQualificationException("REQUEST_LIMIT", Reason.REQUEST_LIMIT)
        }
        val selected = checkNotNull(previousDefault)
        ImeFixtureProtocol.writeLease(
            context,
            LeaseSnapshot(
                run = run,
                component = component,
                ownerPid = Process.myPid(),
                previousDefault = selected,
                previousEnabled = previousEnabled,
                previousSubtype = previousSubtype,
                requests = updated,
            ),
            replace = true,
        )
        leaseRequests = updated.toMutableList()
    }

    internal fun dispatch(requestId: String) {
        checkCancelled()
        val command = "cmd activity broadcast -a ${ImeFixtureProtocol.ACTION} -n $receiverComponent " +
            "--es ${ImeFixtureProtocol.EXTRA_REQUEST} $requestId"
        checkToken(requestId)
        val output = mutateShell(command)
        if (!output.contains("Broadcast completed")) {
            throw ImeQualificationException("SHELL_FAILED", Reason.SHELL_FAILED)
        }
    }

    private fun awaitResult(request: FixtureRequest): ImeFixtureResult {
        val deadline = request.expiresElapsedMillis + RESULT_GRACE_MILLIS
        while (true) {
            checkCancelled()
            val parsed = try {
                ImeFixtureProtocol.readResult(context, run, request.request)
            } catch (failure: Exception) {
                throw ImeQualificationException("RESULT_INVALID", Reason.RESULT_INVALID)
            }
            if (parsed != null) {
                if (parsed.run != run ||
                    parsed.request != request.request ||
                    parsed.instance != request.instance ||
                    parsed.generation != request.generation ||
                    parsed.operation != request.operation
                ) {
                    throw ImeQualificationException("RESULT_INVALID", Reason.RESULT_INVALID)
                }
                return ImeFixtureResult(parsed.outcome, parsed.reason)
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                throw ImeQualificationException("RESULT_MISSING", Reason.RESULT_MISSING)
            }
            SystemClock.sleep(10)
        }
    }

    private fun awaitInputRetired(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + SNAPSHOT_TIMEOUT_MILLIS
        while (true) {
            val state = runCatching { ImeFixtureProtocol.readState(context) }.getOrElse { return false }
            if (state?.active != true) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            SystemClock.sleep(10)
        }
    }

    // ---- restoration ------------------------------------------------------------------

    private fun restoreSelection(): Reason? {
        if (mutationUnknown) return Reason.RESTORE_UNCONFIRMED
        if (snapshotted && awaitSnapshots(SNAPSHOT_TIMEOUT_MILLIS)) return null
        val current = runCatching { setting("default_input_method") }.getOrNull()
        if (current == component && leaseMatches()) {
            runCatching { requestRestore() }
            if (!mutationUnknown && !awaitSnapshots(SNAPSHOT_TIMEOUT_MILLIS) &&
                leaseMatches() && runCatching { setting("default_input_method") }.getOrNull() == component
            ) {
                runCatching { shellRestore() }
            }
        }
        if (!mutationUnknown && enabledByUs && leaseMatches()) {
            runCatching { removeOwnedEnablement() }
        }
        return if (!mutationUnknown && awaitSnapshots(SNAPSHOT_TIMEOUT_MILLIS)) null
        else Reason.RESTORE_UNCONFIRMED
    }

    private fun requestRestore(): Boolean {
        if (mutationUnknown) return false
        val state = ImeFixtureProtocol.readState(context) ?: return false
        val requestId = UUID.randomUUID().toString()
        val request = FixtureRequest(
            schema = ImeFixtureProtocol.SCHEMA,
            run = run,
            request = requestId,
            instance = state.instance,
            generation = state.generation,
            targetPackage = context.packageName,
            clientPid = Process.myPid(),
            fieldId = 0,
            expiresElapsedMillis = SystemClock.elapsedRealtime() + CLOSE_TIMEOUT_MILLIS,
            operation = ImeFixtureOperation.RESTORE_PREVIOUS,
            text = null,
        )
        recordRequest(requestId)
        ImeFixtureProtocol.writeRequest(context, request)
        dispatch(requestId)
        val deadline = SystemClock.elapsedRealtime() + CLOSE_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val result = runCatching {
                ImeFixtureProtocol.readResult(context, run, requestId)
            }.getOrNull()
            if (result != null) {
                return result.outcome == Outcome.SUBMITTED &&
                    result.request == requestId &&
                    result.generation == request.generation &&
                    result.instance == request.instance &&
                    result.operation == request.operation
            }
            SystemClock.sleep(10)
        }
        return false
    }

    /** Requires a matching lease, then exact previous component, before touching subtype. */
    private fun shellRestore() {
        if (mutationUnknown || !leaseMatches()) return
        val previous = previousDefault ?: return
        if (setting("default_input_method") != component) return
        mutateShell("cmd input_method ime set $previous")
        if (setting("default_input_method") != previous) return
        val subtype = previousSubtype
        if (subtype == null) {
            mutateShell("cmd settings delete secure selected_input_method_subtype")
        } else {
            mutateShell("cmd settings put secure selected_input_method_subtype $subtype")
        }
    }

    private fun removeOwnedEnablement() {
        if (mutationUnknown || !enabledByUs || !leaseMatches()) return
        if (setting("default_input_method") == component) return
        val current = setting("enabled_input_methods")?.split(':').orEmpty()
        val previous = previousEnabled?.split(':').orEmpty()
        if (current.filter { it.substringBefore(';') != component } != previous) return
        if (current.any { it.substringBefore(';') == component }) {
            mutateShell("cmd input_method ime disable $component")
        }
    }

    private fun leaseMatches(): Boolean {
        val lease = runCatching { ImeFixtureProtocol.readLease(context) }.getOrNull() ?: return false
        return lease.run == run &&
            lease.component == component &&
            lease.ownerPid == Process.myPid() &&
            lease.previousDefault == previousDefault &&
            lease.previousEnabled == previousEnabled &&
            lease.previousSubtype == previousSubtype
    }

    /** Parses `component` or `component;subtype` entries without normalizing foreign tokens. */
    private fun enabledComponents(raw: String? = previousEnabled): List<String> =
        raw?.split(':')?.map { it.substringBefore(';') }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun snapshotsMatch(): Boolean {
        val default = setting("default_input_method")
        val enabled = setting("enabled_input_methods")
        val subtype = setting("selected_input_method_subtype")
        return default == previousDefault && enabled == previousEnabled && subtype == previousSubtype
    }

    private fun awaitSnapshots(timeoutMillis: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (snapshotsMatch()) return true
            SystemClock.sleep(50)
        }
        return snapshotsMatch()
    }

    private fun setting(name: String): String? {
        checkToken(name)
        return normalize(readShell("cmd settings get secure $name", SHELL_TIMEOUT_MILLIS))
    }

    private fun normalize(raw: String): String? {
        val value = raw.removeSuffix("\n").removeSuffix("\r")
        if (value.any { it == '\n' || it == '\r' || it == '\u0000' }) {
            throw ImeQualificationException("SETTING_INVALID", Reason.IO_FAILED)
        }
        return if (value == "null") null else value
    }

    // ---- retirement -------------------------------------------------------------------

    private fun retireRequestFiles(): Boolean {
        var ok = true
        for (id in ownedRequestIds()) {
            val request = runCatching {
                ImeFixtureProtocol.deleteRecord(ImeFixtureProtocol.requestFile(context, run, id))
            }.getOrDefault(false)
            val result = runCatching {
                ImeFixtureProtocol.deleteRecord(ImeFixtureProtocol.resultFile(context, run, id))
            }.getOrDefault(false)
            if (!request || !result) ok = false
        }
        return ok
    }

    private fun requestsAbsent(): Boolean = ownedRequestIds().all { id ->
        runCatching {
            ImeFixtureProtocol.recordAbsent(ImeFixtureProtocol.requestFile(context, run, id)) &&
                ImeFixtureProtocol.recordAbsent(ImeFixtureProtocol.resultFile(context, run, id))
        }.getOrDefault(false)
    }

    private fun ownedRequestIds(): List<String> {
        val fromLease = runCatching { ImeFixtureProtocol.readLease(context) }.getOrNull()
            ?.takeIf { it.run == run && it.component == component }
            ?.requests
            .orEmpty()
        return (leaseRequests + fromLease).distinct().filter { ImeFixtureProtocol.isCanonicalUuid(it) }
    }

    private fun deleteLeaseIfOwned(): Boolean {
        val lease = runCatching { ImeFixtureProtocol.readLease(context) }.getOrNull() ?: return false
        if (lease.run != run || lease.component != component) return false
        return ImeFixtureProtocol.deleteRecord(ImeFixtureProtocol.leaseFile(context))
    }

    private fun writeSessionResult(
        requestsRetired: Boolean,
        leaseRetired: Boolean,
        lockRetired: Boolean,
    ) {
        if (!ownLease && !lockOwned) return
        val ids = leaseRequests.distinct().filter { ImeFixtureProtocol.isCanonicalUuid(it) }
        runCatching {
            ImeFixtureProtocol.writeSessionResult(
                context = context,
                run = run,
                component = component,
                restored = restored,
                cleanupCode = closeReason?.name ?: "NONE",
                requests = ids,
                requestsRetired = requestsRetired,
                leaseRetired = leaseRetired,
                lockRetired = lockRetired,
                shellRetired = shellRetired,
                cleanupConfirmed = cleanupConfirmed,
            )
        }
    }

    private fun retireShell() {
        shell.shutdownNow()
        workerSettled = runCatching { shell.awaitTermination(5, TimeUnit.SECONDS) }.getOrDefault(false)
        shellRetired = workerSettled && !shellUnknown
    }

    // ---- process / shell ---------------------------------------------------------------

    private fun cancelled(): Boolean {
        if (ignoreCancellation) return false
        return runCatching { cancellation() }.getOrDefault(true)
    }

    private fun checkCancelled() {
        if (cancelled()) throw ImeQualificationException("CANCELLED", Reason.CANCELLED)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else instrumentation.runOnMainSync(Runnable { block() })
    }


    /** UiAutomation passes the string to Runtime.exec(String): no quoting, no shell syntax. */
    private fun checkToken(value: String) {
        if (value.isEmpty() || value.any { it.isWhitespace() || it < ' ' || it == '\u007f' }) {
            throw ImeQualificationException("SHELL_ARGUMENT", Reason.SHELL_FAILED)
        }
    }

    private class ShellOutcome(val output: String, val completed: Boolean)

    private class ShellOperation {
        var cancelled = false
        var descriptor: ParcelFileDescriptor? = null
    }

    private fun readShell(command: String, timeoutMillis: Long): String {
        val outcome = runShell(command, timeoutMillis)
        if (!outcome.completed) {
            if (cancelled()) throw ImeQualificationException("CANCELLED", Reason.CANCELLED)
            throw ImeQualificationException("SHELL_FAILED", Reason.SHELL_FAILED)
        }
        return outcome.output
    }

    /** A mutator that does not reach a recorded completion makes restoration unsafe. */
    private fun mutateShell(command: String): String {
        val outcome = runShell(command, SHELL_TIMEOUT_MILLIS)
        if (!outcome.completed) {
            mutationUnknown = true
            throw ImeQualificationException("SHELL_FAILED", Reason.SHELL_FAILED)
        }
        return outcome.output
    }

    private fun runShell(command: String, timeoutMillis: Long): ShellOutcome {
        if (shellUnknown) return ShellOutcome("", false)
        val operation = ShellOperation()
        val task = FutureTask(Callable {
            val descriptor = automation.executeShellCommand(command)
            synchronized(operation) {
                if (operation.cancelled) {
                    runCatching { descriptor.close() }
                    throw IOException("IME_SHELL_CANCELLED")
                }
                operation.descriptor = descriptor
            }
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_SHELL_BYTES) throw IOException("IME_SHELL_BOUNDS")
                    output.write(buffer, 0, count)
                }
                ImeFixtureProtocol.decode(output.toByteArray())
            }
        })
        shell.execute(task)
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (true) {
            try {
                return ShellOutcome(task.get(50, TimeUnit.MILLISECONDS), true)
            } catch (timeout: TimeoutException) {
                val stop = (!ignoreCancellation && cancelled()) ||
                    SystemClock.elapsedRealtime() >= deadline
                if (!stop) continue
                synchronized(operation) {
                    operation.cancelled = true
                    runCatching { operation.descriptor?.close() }
                }
                task.cancel(true)
                shellUnknown = true
                return ShellOutcome("", false)
            } catch (failure: ExecutionException) {
                shellUnknown = true
                return ShellOutcome("", false)
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                synchronized(operation) {
                    operation.cancelled = true
                    runCatching { operation.descriptor?.close() }
                }
                task.cancel(true)
                shellUnknown = true
                return ShellOutcome("", false)
            }
        }
    }
}
