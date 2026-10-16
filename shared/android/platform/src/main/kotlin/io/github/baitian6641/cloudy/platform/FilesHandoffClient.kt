package io.github.baitian6641.cloudy.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import io.github.baitian6641.cloudy.domain.FilesSharedDescriptor
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class FilesClientCode { BUSY, UNAVAILABLE, DENIED, INVALID_RESPONSE, UNCONFIRMED, CANCELLED }
internal sealed interface FilesClientResult {
    class Discovery(val reply: FilesDiscoveryReply) : FilesClientResult
    class Submission(val reply: FilesSubmitReply) : FilesClientResult
    class Failure(val code: FilesClientCode, val mayHaveSubmitted: Boolean) : FilesClientResult
}

/**
 * One explicit visible-window operation per binding. No reconnect/retry, background daemon or
 * credential channel. Unbind and a UI timeout do not retire a synchronous Binder transaction.
 */
internal class FilesHandoffClient(
    private val activity: ComponentActivity,
    private val canInteract: () -> Boolean,
    private val report: (FilesClientResult, Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var current: Call? = null // Main-thread ownership only.

    fun discover(afterResourceId: String = ""): Boolean {
        FilesHandoffWire.validateAfterResourceId(afterResourceId)
        return begin(Call(afterResourceId, null))
    }

    fun submit(descriptor: FilesSharedDescriptor): Boolean {
        val now = Instant.now()
        val reference = descriptor.reference
        // A UUID is created only for this explicit Open action, never by timeout/reconnect logic.
        val envelope = HandoffEnvelope.create(1, UUID.randomUUID().toString(), now.toString(),
            now.plusSeconds(30).toString(), "open-files", reference.hostId, reference.environmentId,
            reference.workspaceId, reference.resourceId)
        return begin(Call(null, HandoffJson.encode(envelope)))
    }

    /** Called on focus loss/Stop/destruction. It cannot undo a reservation already made remotely. */
    fun abandon(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val call = current ?: return false
        call.deactivate()
        call.connected.countDown()
        unbind(call)
        return call.bytes != null && call.submitted.get()
    }

    private fun begin(call: Call): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!canInteract()) return false
        if (current != null || !occupied.compareAndSet(false, true)) {
            report(FilesClientResult.Failure(FilesClientCode.BUSY, false), true)
            return false
        }
        current = call
        main.postDelayed({
            if (current === call && call.active.get() && !call.finished.get()) {
                call.deactivate()
                call.connected.countDown()
                unbind(call)
                report(FilesClientResult.Failure(FilesClientCode.UNCONFIRMED, call.submitted.get()), false)
            }
        }, UI_TIMEOUT_MILLIS)
        worker.execute { execute(call) }
        return true
    }

    private fun execute(call: Call) {
        var result: FilesClientResult = FilesClientResult.Failure(FilesClientCode.CANCELLED, false)
        try {
            val verified = HandoffEndpoint.verify(activity)
            if (verified !is HandoffEndpointResult.Verified) {
                result = FilesClientResult.Failure(FilesClientCode.UNAVAILABLE, false)
                return
            }
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (name == verified.component && call.active.get()) call.binder.compareAndSet(null, service)
                    call.connected.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName) { call.deactivate(); call.connected.countDown() }
                override fun onBindingDied(name: ComponentName) { call.deactivate(); call.connected.countDown(); unbind(call) }
                override fun onNullBinding(name: ComponentName) { call.deactivate(); call.connected.countDown(); unbind(call) }
            }
            if (!main.post {
                    if (current !== call || !call.active.get() || !canInteract()) {
                        call.deactivate(); call.connected.countDown()
                    } else {
                        call.connection = connection
                        call.bindingAttempted = true
                        try {
                            call.bindingSucceeded = activity.bindService(Intent().setComponent(verified.component),
                                connection, Context.BIND_AUTO_CREATE or Context.BIND_ALLOW_ACTIVITY_STARTS)
                            if (!call.bindingSucceeded) { call.deactivate(); call.connected.countDown() }
                        } catch (_: Exception) { call.deactivate(); call.connected.countDown() }
                    }
                }) return
            if (!call.connected.await(BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) || !call.active.get()) return
            val binder = call.binder.get() ?: return
            // Repeat exact endpoint verification after binding; do not trust a resolver or a stale
            // package name embedded in an Intent. The receiver independently verifies our real UID.
            val currentEndpoint = HandoffEndpoint.verify(activity)
            if (currentEndpoint !is HandoffEndpointResult.Verified || currentEndpoint.component != verified.component ||
                currentEndpoint.permission != verified.permission) {
                result = FilesClientResult.Failure(FilesClientCode.DENIED, false)
                return
            }
            if (!call.active.get()) return
            val endpoint = ICloudyFilesHandoff.Stub.asInterface(binder)
            result = if (call.bytes == null) {
                val bytes = endpoint.discover(1, checkNotNull(call.after))
                FilesClientResult.Discovery(FilesHandoffWire.decodeDiscovery(bytes, call.after))
            } else {
                // This flag deliberately precedes IPC: a thrown/lost reply may follow admission.
                if (!call.admitSubmission()) return
                val bytes = endpoint.submit(call.bytes)
                FilesClientResult.Submission(FilesHandoffWire.decodeSubmit(bytes))
            }
        } catch (_: SecurityException) {
            result = FilesClientResult.Failure(if (call.submitted.get()) FilesClientCode.UNCONFIRMED else FilesClientCode.DENIED,
                call.submitted.get())
        } catch (_: FilesHandoffWireException) {
            result = FilesClientResult.Failure(FilesClientCode.INVALID_RESPONSE, call.submitted.get())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            result = FilesClientResult.Failure(FilesClientCode.UNCONFIRMED, call.submitted.get())
        } catch (_: Exception) {
            result = FilesClientResult.Failure(FilesClientCode.UNCONFIRMED, call.submitted.get())
        } finally {
            call.finished.set(true)
            val finalResult = result
            // Capacity returns only after the actual call and owning binding retire. If main no
            // longer runs or unbinding cannot be established, retain capacity until process exit.
            main.post {
                if (unbind(call)) {
                    occupied.set(false)
                    if (current === call) {
                        current = null
                        report(if (call.active.get()) finalResult else
                            FilesClientResult.Failure(FilesClientCode.UNCONFIRMED, call.submitted.get()), true)
                    }
                } else if (current === call) {
                    report(FilesClientResult.Failure(FilesClientCode.UNCONFIRMED, call.submitted.get()), false)
                }
            }
        }
    }

    /** Main only. A failed bind may have no registered binding; a successful one must retire. */
    private fun unbind(call: Call): Boolean {
        if (!call.bindingAttempted) return true
        val connection = call.connection ?: return false
        return try {
            activity.unbindService(connection)
            call.bindingAttempted = false
            true
        } catch (_: IllegalArgumentException) {
            if (call.bindingSucceeded) false else { call.bindingAttempted = false; true }
        } catch (_: Exception) { false }
    }

    private class Call(val after: String?, val bytes: ByteArray?) {
        private val admission = Any()
        val active = AtomicBoolean(true)
        val submitted = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val connected = CountDownLatch(1)
        val binder = AtomicReference<IBinder?>(null)
        var connection: ServiceConnection? = null
        var bindingAttempted = false
        var bindingSucceeded = false
        fun deactivate() { synchronized(admission) { active.set(false) } }
        fun admitSubmission(): Boolean = synchronized(admission) {
            if (!active.get()) false else { submitted.set(true); true }
        }
    }

    companion object {
        private const val BIND_TIMEOUT_MILLIS = 4_000L
        private const val UI_TIMEOUT_MILLIS = 10_000L
        private val occupied = AtomicBoolean(false)
        private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "cloudy-files-handoff-client") }
    }
}
