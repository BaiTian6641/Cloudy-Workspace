package dev.cloudy.qualification.baseline

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.core.util.Consumer
import androidx.window.layout.WindowEngagementInfo
import androidx.window.layout.WindowInfoTracker
import java.time.Instant
import java.util.concurrent.Executor

/** One prepared Looper for all registrations, removals and library jobs. It is
 * process-owned and never stopped when the first window stops or is destroyed.
 */
internal class AndroidEngagementRuntime {
    private val thread = HandlerThread("cloudy-engagement").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val serial = EngagementQueue { work -> check(handler.post(work)) }
    private val delivery = EngagementQueue { work -> check(main.post(work)) }
    private val executor = Executor { job ->
        // AndroidX SerialExecutor requires every supplied job to run/finally
        // advance. Bound our reporting buffer, never discard library jobs.
        check(handler.post(job))
    }
    private val slots = EngagementSlots()
    private val clock = EngagementClock { EngagementTime(Instant.now().toString(), SystemClock.elapsedRealtime()) }
    private val backend = EngagementBackend<Activity> { activity, callback ->
        val tracker = WindowInfoTracker.getOrCreate(activity)
        val consumer = Consumer<WindowEngagementInfo> { info ->
            val values = info.engagementModes
            val names = values.take(16).map { it.toString() }
            callback(EngagementModes(names.map { it.take(80) }.sorted(),
                (values.size - names.size).coerceAtLeast(0), names.count { it.length > 80 }))
        }
        object : EngagementRegistration {
            private var owner: Activity? = activity
            override fun register() {
                try {
                    tracker.registerWindowEngagementInfoListener(checkNotNull(owner), executor, consumer)
                } finally {
                    owner = null
                }
            }
            override fun unregister() {
                tracker.unregisterWindowEngagementInfoListener(consumer)
            }
        }
    }

    fun controller(report: (EngagementUpdate) -> Unit): EngagementController<Activity> =
        EngagementController(serial, delivery, backend, slots, clock, report)
}
