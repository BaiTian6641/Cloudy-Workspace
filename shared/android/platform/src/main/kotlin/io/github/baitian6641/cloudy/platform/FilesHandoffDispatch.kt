package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import io.github.baitian6641.cloudy.domain.HandoffFreshness
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A finite acknowledgment of startActivity, never a claim of visible navigation. Package/store
 * checks happen before this boundary and at ticket consumption, never on main or while waiting.
 */
internal object FilesHandoffDispatch {
    private val main = Handler(Looper.getMainLooper())
    private const val ACK_MILLIS = 1_000L

    /** Owns retired from entry, including rejected posts; an in-flight launch retains capacity. */
    fun dispatch(context: Context, token: String, envelope: HandoffEnvelope,
                 retired: () -> Unit): FilesSubmitStatus {
        val state = FilesDispatchState()
        val completed = CountDownLatch(1)
        val released = AtomicBoolean(false)
        val retireOnce = { if (released.compareAndSet(false, true)) retired() }
        val deadline = SystemClock.elapsedRealtime() + ACK_MILLIS
        val launch = Runnable {
            if (!state.begin(SystemClock.elapsedRealtime() < deadline &&
                    envelope.freshAt(Instant.now()) == HandoffFreshness.CURRENT)) {
                FilesLocationTickets.discard(token)
                retireOnce()
                completed.countDown()
                return@Runnable
            }
            try {
                context.startActivity(Intent(context, CloudySshFilesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra(FilesLocationTickets.EXTRA, token))
                state.returned(true)
            } catch (_: ActivityNotFoundException) {
                FilesLocationTickets.discard(token)
                state.returned(false)
            } catch (_: SecurityException) {
                FilesLocationTickets.discard(token)
                state.returned(false)
            } catch (_: Exception) {
                // The system may have accepted the request before a framework/remote failure.
                FilesLocationTickets.discard(token)
                state.returnedUnconfirmed()
            } finally {
                retireOnce()
                completed.countDown()
            }
        }
        try {
            if (!main.post(launch)) {
                state.cancelPending()
                FilesLocationTickets.discard(token)
                retireOnce()
            } else {
                try { completed.await(ACK_MILLIS, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                if (state.cancelPending()) {
                    // Cancelled work cannot start later, even when main was blocked at the deadline.
                    main.removeCallbacks(launch)
                    FilesLocationTickets.discard(token)
                    retireOnce()
                }
            }
            return state.status()
        } catch (_: Exception) {
            if (state.cancelPending()) {
                main.removeCallbacks(launch)
                FilesLocationTickets.discard(token)
                retireOnce()
            }
            return state.status()
        }
    }
}

/** Pure atomic ownership protocol, separately testable without claiming framework launch evidence. */
internal class FilesDispatchState {
    private enum class State { PENDING, DISPATCHING, RETURNED, FAILED, UNCONFIRMED, CANCELLED }
    private val state = AtomicReference(State.PENDING)

    fun begin(current: Boolean): Boolean {
        if (!current) { cancelPending(); return false }
        return state.compareAndSet(State.PENDING, State.DISPATCHING)
    }
    fun cancelPending(): Boolean = state.compareAndSet(State.PENDING, State.CANCELLED)
    fun returned(success: Boolean) {
        check(state.compareAndSet(State.DISPATCHING, if (success) State.RETURNED else State.FAILED))
    }
    fun returnedUnconfirmed() { check(state.compareAndSet(State.DISPATCHING, State.UNCONFIRMED)) }
    fun status(): FilesSubmitStatus = when (state.get()) {
        State.RETURNED -> FilesSubmitStatus.DISPATCH_REQUESTED
        State.DISPATCHING, State.UNCONFIRMED -> FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED
        else -> FilesSubmitStatus.ADMITTED_NOT_DISPATCHED
    }
}
