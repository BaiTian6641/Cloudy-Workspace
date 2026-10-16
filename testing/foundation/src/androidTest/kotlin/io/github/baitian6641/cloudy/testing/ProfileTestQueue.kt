package io.github.baitian6641.cloudy.testing

import android.os.Handler
import android.os.Looper
import io.github.baitian6641.cloudy.platform.CloudyProfilesActivity
import java.io.Closeable
import java.lang.reflect.Modifier
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** White-box scheduler control in test source only; every product mutation still uses its real store. */
internal class ProfileTestQueue private constructor(private val executor: ThreadPoolExecutor) {
    private val gates = mutableListOf<Gate>()
    val queuedTasks: Int get() = executor.queue.size

    fun gate(): Gate {
        requireWorkerThread()
        val gate = Gate()
        gates += gate
        try {
            executor.execute { gate.holdWorker() }
            check(gate.entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Profile test queue gate did not start" }
            gate.requireHeld()
            return gate
        } catch (error: Exception) {
            gate.close()
            throw error
        }
    }

    fun releaseGates() { gates.forEach { it.close() } }

    /**
     * Worker fences precede main fences because a completed real operation posts its callback.
     * The second pair settles a follow-up read submitted by such a callback. Cleanup must close
     * the Activity first, preventing new admissions; a timeout never authorizes fixture deletion.
     */
    fun drain() {
        requireWorkerThread()
        repeat(2) {
            val worker = CountDownLatch(1)
            executor.execute { worker.countDown() }
            check(worker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Profile queue settlement unproved; preserve fixture" }
            val main = CountDownLatch(1)
            check(Handler(Looper.getMainLooper()).post { main.countDown() }) { "Profile main fence rejected; preserve fixture" }
            check(main.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Profile callback settlement unproved; preserve fixture" }
        }
        check(executor.queue.isEmpty()) { "Profile queue not empty; preserve fixture" }
    }

    internal class Gate : Closeable {
        val entered = CountDownLatch(1)
        private val release = CountDownLatch(1)
        private val finished = CountDownLatch(1)
        private val expired = AtomicBoolean(false)

        internal fun holdWorker() {
            entered.countDown()
            try {
                if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) expired.set(true)
            } catch (_: InterruptedException) {
                expired.set(true)
                Thread.currentThread().interrupt()
            } finally { finished.countDown() }
        }

        fun requireHeld() {
            check(entered.count == 0L && release.count == 1L && finished.count == 1L && !expired.get()) {
                "Profile test gate was not held across recreation"
            }
        }

        fun requireNotExpired() { check(!expired.get()) { "Profile test gate deadline expired" } }
        override fun close() { release.countDown() }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 15L

        fun actual(): ProfileTestQueue {
            // Only Cloudy's pinned app-owned field is inspected; no Android private API or fallback.
            val field = CloudyProfilesActivity::class.java.getDeclaredField("operations")
            check(Modifier.isStatic(field.modifiers) && field.type == ThreadPoolExecutor::class.java)
            field.isAccessible = true
            val value = field.get(null)
            check(value is ThreadPoolExecutor && value.corePoolSize == 1 && value.maximumPoolSize == 1)
            check(!value.isShutdown && value.queue is ArrayBlockingQueue<*>)
            check(value.queue.size + value.queue.remainingCapacity() == 16)
            return ProfileTestQueue(value)
        }

        private fun requireWorkerThread() {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Profile queue waits require the test thread" }
        }
    }
}
