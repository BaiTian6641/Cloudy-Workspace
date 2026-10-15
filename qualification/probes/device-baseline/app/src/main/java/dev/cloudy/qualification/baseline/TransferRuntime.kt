package dev.cloudy.qualification.baseline

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

internal data class TransferUiState(
    val canStart: Boolean,
    val canCancel: Boolean,
    val status: String,
    val summary: String,
)

/** The process owns the journal, operation and executors; Activities only observe.
 * Main owns JobParameters eligibility. IO never captures an Activity or view.
 */
internal class TransferRuntime(context: Context) {
    companion object {
        const val JOB_ID = 7352
        const val ATTEMPT_EXTRA = "cloudy.transfer.attempt"
        const val NOTIFICATION_ID = 7352
        private const val CHANNEL = "cloudy-transfer-probe"
        private const val TOTAL_LIMIT_MS = 10 * 60 * 1000L
        fun get(context: Context): TransferRuntime = (context.applicationContext as ProbeApplication).transfers
    }

    fun interface Listener { fun changed(state: TransferUiState) }

    private class Run(val id: String, val service: TransferJobService, val params: JobParameters) {
        val cancelled = AtomicBoolean(false)
        val connection = AtomicReference<HttpsURLConnection?>(null)
        val assignedNetwork = params.network
        var mayFinish = true // main thread only; false as soon as onStopJob is received
        var workerExited = false // main thread only
        val disconnectRequested = AtomicBoolean(false)
    }

    private class CancelReceipt(val id: String, val finished: () -> Unit) {
        var revision: Long? = null
        lateinit var timeout: Runnable
    }

    private val app = context.applicationContext
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val networkWorker = Executors.newSingleThreadExecutor()
    private val disconnectWorker = Executors.newSingleThreadExecutor()
    private val disconnectBusy = AtomicBoolean(false)
    private val scheduler = app.getSystemService(JobScheduler::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val listeners = ListenerRegistry<Listener>()
    private val processId = UUID.randomUUID().toString()
    private val processStartedAt = Instant.now().toString()
    private val journal = TransferJournal(app.filesDir)
    private val payloadDirectory = File(app.filesDir, "transfer-payloads")
    private var state = TransferState(processId)
    private var config: TransferConfig? = null
    private var configStatus = "not-loaded"
    private var ready = false
    private var setupError: String? = null
    private var persistenceError: String? = null
    private var controlError: String? = null
    private var savedRevision = -1L
    private var persistQueued = false
    private var publishQueued = false
    private var refreshQueued = false
    private var run: Run? = null // accessed only on main, or under lock
    private val cancelReceipts = mutableListOf<CancelReceipt>() // main only; bounded broadcast acknowledgments

    init {
        io.execute {
            try {
                val restored = journal.read()
                val recovered = TransferState(processId, restored)
                recovered.recover(now())
                val deadline = SystemClock.elapsedRealtime() + TOTAL_LIMIT_MS
                restored.filter { it.status == "completed" }.forEach { attempt ->
                    val matches = try {
                        TransferIntegrity.verify(partFile(attempt.id), attempt.expectedLengthBytes, attempt.expectedSha256) {
                            if (SystemClock.elapsedRealtime() >= deadline) throw TransferFailure("startup-audit-time-limit")
                        }
                        true
                    } catch (_: IOException) { false }
                    recovered.auditSavedCompletion(attempt.id, matches, now())
                }
                synchronized(lock) { state = recovered }
                loadConfig()
                val committed = persistLatest()
                main.post {
                    // A process restart never resumes the old scheduler request,
                    // even if the OS redelivers a job after process death.
                    try { scheduler?.cancel(JOB_ID) } catch (failure: RuntimeException) {
                        synchronized(lock) { setupError = "scheduler-cancel-${failure.javaClass.simpleName}" }
                    }
                    synchronized(lock) { ready = committed && setupError == null }
                    publish()
                }
            } catch (failure: IOException) { failSetup(failure) }
            catch (failure: JSONException) { failSetup(failure) }
            catch (failure: RuntimeException) { failSetup(failure) }
        }
    }

    fun addListener(listener: Listener) { listeners.add(listener); publish() }
    fun removeListener(listener: Listener) { listeners.removeMatching { it === listener } }

    fun refresh() {
        synchronized(lock) {
            if (!ready || refreshQueued) { publish(); return }
            refreshQueued = true
        }
        io.execute {
            try {
                val reload = synchronized(lock) { state.current()?.terminal != false && run == null }
                if (reload) loadConfig()
                if (synchronized(lock) { persistenceError != null }) persistLatest()
            } finally {
                synchronized(lock) { refreshQueued = false }
                publish()
            }
        }
    }

    /** Called only by a visible explicit Start click. The one-shot gate uses a
     * weak Activity/lifecycle token and is rechecked on main after journal IO.
     */
    fun start(stillVisible: () -> Boolean) {
        checkMain()
        val attempt: TransferAttempt
        synchronized(lock) {
            val currentConfig = config
            if (!canStartLocked() || currentConfig == null) return
            attempt = state.begin(currentConfig.spec.expectedLengthBytes, currentConfig.spec.expectedSha256, now()) ?: return
            controlError = null
        }
        publish()
        io.execute {
            val committed = persistLatest()
            main.post {
                val pending = synchronized(lock) { state.current()?.takeIf { it.id == attempt.id && it.status == "queued" } }
                if (pending == null) return@post
                if (!committed) {
                    stop(attempt.id, "interrupted", "journal-write-failed", null, cancelScheduler = false)
                } else if (!stillVisible()) {
                    stop(attempt.id, "interrupted", "start-no-longer-visible", null, cancelScheduler = false)
                } else {
                    schedule(attempt)
                }
            }
        }
    }

    private fun schedule(attempt: TransferAttempt) {
        checkMain()
        try {
            val manager = scheduler ?: throw IllegalStateException("JobSchedulerUnavailable")
            val network = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build() // Default NOT_VPN; no Internet, metering or validation requirement.
            val extras = PersistableBundle().apply { putString(ATTEMPT_EXTRA, attempt.id) }
            val job = JobInfo.Builder(JOB_ID, ComponentName(app, TransferJobService::class.java))
                .setUserInitiated(true).setRequiredNetwork(network)
                .setEstimatedNetworkBytes(attempt.expectedLengthBytes, 0)
                .setPersisted(false).setExtras(extras).build()
            if (manager.schedule(job) != JobScheduler.RESULT_SUCCESS) failAttempt(attempt.id, "scheduler-rejected")
        } catch (failure: RuntimeException) {
            failAttempt(attempt.id, "scheduler-${failure.javaClass.simpleName}")
        }
    }

    fun beginJob(service: TransferJobService, params: JobParameters): Boolean {
        checkMain()
        val id = params.extras.getString(ATTEMPT_EXTRA) ?: return false
        val binding: Run
        val selectedConfig: TransferConfig
        synchronized(lock) {
            val selected = config
            val current = state.current()
            if (!ready || run != null || params.jobId != JOB_ID || selected == null || current == null ||
                current.expectedLengthBytes != selected.spec.expectedLengthBytes || current.expectedSha256 != selected.spec.expectedSha256 ||
                persistenceError != null || !state.claim(id, now())) return false
            selectedConfig = selected
            binding = Run(id, service, params)
            run = binding
        }
        publish()
        io.execute {
            // Claim is committed before DNS/TLS/socket/file payload work. A
            // repeated onStart in this or a new process cannot claim it again.
            val committed = persistLatest()
            networkWorker.execute { transfer(binding, selectedConfig, committed) }
        }
        return true
    }

    fun notificationFailed(params: JobParameters, failure: RuntimeException) {
        checkMain()
        params.extras.getString(ATTEMPT_EXTRA)?.let { failAttempt(it, "notification-${failure.javaClass.simpleName}") }
    }

    fun stopJob(params: JobParameters) {
        checkMain()
        val id = params.extras.getString(ATTEMPT_EXTRA) ?: return
        synchronized(lock) { run?.takeIf { it.id == id }?.mayFinish = false }
        stop(id, "interrupted", "os-stopped-job", params.stopReason, cancelScheduler = false)
    }

    fun networkChanged(params: JobParameters) {
        checkMain()
        params.extras.getString(ATTEMPT_EXTRA)?.let {
            stop(it, "interrupted", "assigned-network-changed", null, cancelScheduler = false)
        }
    }

    fun serviceDestroyed(service: TransferJobService) {
        checkMain()
        val binding = synchronized(lock) { run?.takeIf { it.service === service } } ?: return
        binding.mayFinish = false
        stop(binding.id, "interrupted", "job-service-destroyed", null, cancelScheduler = false)
    }

    fun cancelCurrent() {
        checkMain()
        val id = synchronized(lock) { state.current()?.id } ?: return
        cancel(id)
    }

    fun cancel(id: String) = stop(id, "cancelled", "user-cancelled", null, cancelScheduler = true)

    /** goAsync keeps a notification-only cancellation alive for its journal
     * acknowledgment, never for the socket or entire download. Timeout means
     * durability is unconfirmed; a later process still reconciles unfinished work.
     */
    fun cancelFromNotification(id: String, finished: () -> Unit) {
        checkMain()
        if (cancelReceipts.size >= TransferState.ATTEMPT_LIMIT) {
            if (synchronized(lock) { ready }) cancel(id)
            synchronized(lock) { controlError = "cancel-acknowledgment-capacity" }
            finished()
            publish()
            return
        }
        val receipt = CancelReceipt(id, finished)
        receipt.timeout = Runnable {
            if (cancelReceipts.remove(receipt)) {
                synchronized(lock) { controlError = "cancel-acknowledgment-timeout" }
                receipt.finished()
                publish()
            }
        }
        cancelReceipts.add(receipt)
        main.postDelayed(receipt.timeout, 8_000)
        acknowledgeCancellations()
    }

    private fun acknowledgeCancellations() {
        checkMain()
        cancelReceipts.toList().forEach { receipt ->
            val initialized = synchronized(lock) { ready }
            if (initialized && receipt.revision == null) {
                cancel(receipt.id)
                receipt.revision = synchronized(lock) { state.revision }
            }
            val finished = synchronized(lock) {
                setupError != null || persistenceError != null ||
                    (receipt.revision?.let { savedRevision >= it } == true)
            }
            if (finished && cancelReceipts.remove(receipt)) {
                main.removeCallbacks(receipt.timeout)
                receipt.finished()
            }
        }
    }

    private fun stop(id: String, outcome: String, reason: String, stopReason: Int?, cancelScheduler: Boolean) {
        checkMain()
        val binding: Run?
        synchronized(lock) {
            if (!state.stop(id, outcome, reason, stopReason, now())) return
            binding = run?.takeIf { it.id == id }
            binding?.cancelled?.set(true)
            if (binding == null) state.finishStopped(id, now())
            queuePersistLocked()
        }
        // This executor is independent of the blocked read and the journal.
        binding?.let(::disconnectAsync)
        if (cancelScheduler) {
            try {
                scheduler?.cancel(JOB_ID)
                binding?.mayFinish = false
            } catch (failure: RuntimeException) {
                synchronized(lock) { controlError = "cancel-${failure.javaClass.simpleName}" }
            }
        }
        publish()
    }

    private fun disconnectAsync(binding: Run) {
        if (!binding.disconnectRequested.compareAndSet(false, true)) return
        if (!disconnectBusy.compareAndSet(false, true)) return // No unbounded cleanup queue; socket timeout still applies.
        disconnectWorker.execute {
            try { binding.connection.get()?.disconnect() }
            catch (_: RuntimeException) { /* Worker still reports the actual read/stop result. */ }
            finally { disconnectBusy.set(false) }
        }
    }

    private fun transfer(binding: Run, selectedConfig: TransferConfig, claimCommitted: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TOTAL_LIMIT_MS
        var lastCheckpoint = 0L
        var lastBytes = 0L
        fun checkContinue() {
            if (binding.cancelled.get()) throw TransferFailure("attempt-stopped")
            if (SystemClock.elapsedRealtime() >= deadline) throw TransferFailure("transfer-time-limit")
            if (synchronized(lock) { persistenceError != null }) throw TransferFailure("journal-write-failed")
        }
        try {
            if (!claimCommitted) throw TransferFailure("claim-not-durable")
            checkContinue()
            if (android.os.Build.VERSION.SDK_INT >= 37 &&
                app.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                throw TransferFailure("local-network-permission-denied")
            }
            val network = binding.assignedNetwork ?: throw TransferFailure("assigned-network-unavailable")
            val connection = network.openConnection(selectedConfig.spec.url.toURL()) as? HttpsURLConnection
                ?: throw TransferFailure("https-connection-required")
            binding.connection.set(connection)
            checkContinue()
            connection.sslSocketFactory = selectedConfig.socketFactory
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept-Encoding", "identity")
            val response = connection.responseCode
            if (response != 200) throw TransferFailure("http-status-$response")
            val length = selectedConfig.spec.expectedLengthBytes
            if (connection.contentLengthLong != length) throw TransferFailure("http-length-mismatch")
            val encoding = connection.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) throw TransferFailure("http-encoding-rejected")
            if (!payloadDirectory.isDirectory && !payloadDirectory.mkdir()) throw TransferFailure("part-directory-unavailable")
            connection.inputStream.use { input ->
                TransferIntegrity.copy(input, partFile(binding.id), length, ::checkContinue) { bytes ->
                    synchronized(lock) { state.progress(binding.id, bytes) }
                    val elapsed = SystemClock.elapsedRealtime()
                    if (bytes - lastBytes >= 1024 * 1024 || elapsed - lastCheckpoint >= 1000 || bytes == length) {
                        lastCheckpoint = elapsed
                        lastBytes = bytes
                        synchronized(lock) { queuePersistLocked() }
                        publish()
                    }
                }
            }
            checkContinue()
            if (!synchronized(lock) { state.verifying(binding.id, now()).also { queuePersistLocked() } }) throw TransferFailure("attempt-stopped")
            publish()
            val verified = TransferIntegrity.verify(partFile(binding.id), length, selectedConfig.spec.expectedSha256, ::checkContinue)
            synchronized(lock) { state.complete(binding.id, verified, now()); queuePersistLocked() }
        } catch (failure: IOException) {
            failAttempt(binding.id, if (failure is TransferFailure) failure.code else failure.javaClass.simpleName)
        } catch (failure: RuntimeException) {
            failAttempt(binding.id, failure.javaClass.simpleName)
        } finally {
            try { binding.connection.getAndSet(null)?.disconnect() }
            catch (_: RuntimeException) { /* No completion claim depends on disconnect returning successfully. */ }
            synchronized(lock) { state.finishStopped(binding.id, now()); queuePersistLocked() }
            main.post {
                binding.workerExited = true
                finishWhenDurable(binding)
                publish()
            }
        }
    }

    private fun failAttempt(id: String, reason: String) {
        synchronized(lock) {
            state.fail(id, reason.take(100), now())
            queuePersistLocked()
        }
        publish()
    }

    private fun queuePersistLocked() {
        if (persistQueued) return
        persistQueued = true
        io.execute { persistLatest() }
    }

    /** Only the IO executor calls this. A pending terminal phase is not shown
     * as completed/cancelled until its journal revision passes readback.
     */
    private fun persistLatest(): Boolean {
        var revision = -1L
        var success = false
        try {
            val value = synchronized(lock) {
                persistQueued = false
                revision = state.revision
                journal.encode(processId, processStartedAt, revision, state.snapshot())
            }
            journal.write(value)
            synchronized(lock) { savedRevision = revision; persistenceError = null }
            success = true
        } catch (failure: IOException) { persistenceFailed(failure) }
        catch (failure: JSONException) { persistenceFailed(failure) }
        catch (failure: RuntimeException) { persistenceFailed(failure) }
        synchronized(lock) { if (success && state.revision != revision) queuePersistLocked() }
        main.post {
            val binding = synchronized(lock) { run }
            binding?.let(::finishWhenDurable)
            publish()
        }
        return success
    }

    private fun persistenceFailed(failure: Exception) {
        val binding = synchronized(lock) {
            persistenceError = failure.javaClass.simpleName
            run?.also {
                it.cancelled.set(true)
                state.stop(it.id, "interrupted", "journal-write-failed", null, now())
            }
        }
        binding?.let(::disconnectAsync)
    }

    private fun finishWhenDurable(binding: Run) {
        checkMain()
        val finish = synchronized(lock) {
            if (run !== binding || !binding.workerExited) false
            else if (savedRevision < state.revision && persistenceError == null) false
            else { run = null; true }
        }
        if (finish && binding.mayFinish) {
            // onStopJob invalidates mayFinish synchronously, before this post.
            try { binding.service.jobFinished(binding.params, false) }
            catch (failure: RuntimeException) {
                synchronized(lock) { controlError = "job-finished-${failure.javaClass.simpleName}" }
            }
        }
    }

    private fun loadConfig() {
        try {
            val loaded = TransferConfig.read(File(app.filesDir, "transfer-config.json"))
            synchronized(lock) { config = loaded; configStatus = "validated-private-fixture" }
        } catch (failure: IOException) { configFailed(failure) }
        catch (failure: JSONException) { configFailed(failure) }
        catch (failure: GeneralSecurityException) { configFailed(failure) }
        catch (failure: RuntimeException) { configFailed(failure) }
    }

    private fun configFailed(failure: Exception) = synchronized(lock) {
        config = null
        configStatus = "unavailable-${failure.javaClass.simpleName}"
    }

    private fun failSetup(failure: Exception) {
        synchronized(lock) { setupError = failure.javaClass.simpleName; ready = false }
        publish()
    }

    private fun canStartLocked(): Boolean = ready && !refreshQueued && config != null && run == null && state.canStart() &&
        savedRevision == state.revision && persistenceError == null && scheduler != null

    private fun publish() {
        synchronized(lock) { if (publishQueued) return; publishQueued = true }
        main.post {
            val snapshot = synchronized(lock) {
                publishQueued = false
                val attempt = state.current()
                val pending = savedRevision != state.revision
                val phase = when {
                    setupError != null -> "initialization-failed"
                    persistenceError != null -> "persistence-failed"
                    !ready -> "loading-private-state"
                    attempt == null -> "idle"
                    attempt.terminal && pending -> "committing-${attempt.status}"
                    else -> attempt.status
                }
                val details = buildString {
                    appendLine("Process: $processId")
                    appendLine("Process started UTC: $processStartedAt")
                    appendLine("Configuration: $configStatus")
                    appendLine("Journal revision: ${state.revision}; saved: $savedRevision")
                    appendLine("Journal error: ${persistenceError ?: "none"}; setup error: ${setupError ?: "none"}")
                    appendLine("Control error: ${controlError ?: "none"}")
                    appendLine("Retained attempts: ${state.snapshot().size}/${TransferState.ATTEMPT_LIMIT}; no automatic deletion")
                    if (attempt != null) {
                        appendLine("Attempt: ${attempt.id}")
                        appendLine("Phase: $phase; bytes written: ${attempt.bytesWritten}/${attempt.expectedLengthBytes}")
                        appendLine("Reason: ${attempt.reason ?: "none"}; OS stop reason: ${attempt.stopReason ?: "none"}")
                        appendLine("Verified bytes: ${attempt.verifiedLengthBytes ?: "not-verified"}")
                        appendLine("Verified SHA-256: ${attempt.verifiedSha256 ?: "not-verified"}")
                        appendLine("Part: transfer-payloads/${attempt.id}.part")
                    }
                    append("No retry/resume, no remote publication, no physical-input or product qualification.")
                }
                TransferUiState(canStartLocked(), ready && attempt?.terminal == false && attempt.stopOutcome == null, phase, details)
            }
            listeners.snapshot().forEach { it.changed(snapshot) }
            acknowledgeCancellations()
            val binding = synchronized(lock) { run }
            binding?.let { updateNotification(it.params, it.service) }
        }
    }

    fun notification(attemptId: String?): Notification {
        checkMain()
        notifications?.createNotificationChannel(NotificationChannel(CHANNEL,
            app.getString(R.string.transfer_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(app, JOB_ID,
            Intent(app, TransferActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val attempt = synchronized(lock) { attemptId?.let(state::find) }
        val percent = if (attempt == null) 0 else (attempt.bytesWritten * 100 / attempt.expectedLengthBytes).toInt()
        val builder = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_probe)
            .setContentTitle(app.getString(R.string.transfer_title))
            .setContentText(app.getString(R.string.transfer_notification_scope))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100, percent, attempt == null)
        if (attemptId != null) {
            val intent = Intent(app, TransferCancelReceiver::class.java)
                .setData(Uri.parse("cloudy-transfer://cancel/$attemptId"))
                .putExtra(ATTEMPT_EXTRA, attemptId)
            val cancel = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, app.getString(R.string.transfer_cancel), cancel).build())
        }
        return builder.build()
    }

    fun updateNotification(params: JobParameters, service: TransferJobService) {
        checkMain()
        val binding = synchronized(lock) { run?.takeIf { it.service === service && it.id == params.extras.getString(ATTEMPT_EXTRA) } }
            ?: return
        if (!binding.mayFinish || binding.workerExited) return
        try {
            service.setNotification(params, NOTIFICATION_ID, notification(binding.id), android.app.job.JobService.JOB_END_NOTIFICATION_POLICY_REMOVE)
            val bytes = synchronized(lock) { state.find(binding.id)?.bytesWritten ?: 0 }
            service.updateTransferredNetworkBytes(params, bytes, 0)
        }
        catch (failure: RuntimeException) { stop(binding.id, "interrupted", "notification-${failure.javaClass.simpleName}", null, false) }
    }

    private fun partFile(id: String) = File(payloadDirectory, "$id.part")
    private fun now() = TransferTime(Instant.now().toString(), SystemClock.elapsedRealtime())
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
}
