package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test-only queues/backends drive real lifecycle policy, never device evidence. */
class EngagementControllerTest {
    private class Queue : EngagementQueue {
        val work = ArrayDeque<() -> Unit>()
        override fun execute(work: () -> Unit) { this.work.addLast(work) }
        fun drain() { while (work.isNotEmpty()) work.removeFirst().invoke() }
    }

    private class Backend : EngagementBackend<String> {
        inner class Registration(val owner: String, val callback: (EngagementModes) -> Unit) : EngagementRegistration {
            var registrations = 0
            var removals = 0
            override fun register() {
                registrations++
                onRegister?.invoke(this)
                registerFailure?.let { throw it }
            }
            override fun unregister() {
                removals++
                cleanupFailure?.let { throw it }
            }
            fun emit(vararg modes: String) = callback(EngagementModes(modes.toList()))
        }
        val prepared = mutableListOf<Registration>()
        var onRegister: ((Registration) -> Unit)? = null
        var registerFailure: Throwable? = null
        var cleanupFailure: Throwable? = null
        override fun prepare(owner: String, callback: (EngagementModes) -> Unit): EngagementRegistration =
            Registration(owner, callback).also { prepared.add(it) }
    }

    private class Fixture(capacity: Int = 8, historyCapacity: Int = 32) {
        val serial = Queue()
        val main = Queue()
        val backend = Backend()
        val slots = EngagementSlots(capacity)
        val reports = mutableListOf<EngagementUpdate>()
        private var tick = 0L
        val clock = EngagementClock { EngagementTime("test-clock-${++tick}", tick) }
        val controller = EngagementController(serial, main, backend, slots, clock, reports::add, historyCapacity)
        fun drain() { serial.drain(); main.drain() }
        fun state(): EngagementSnapshot = reports.last().snapshot
    }

    @Test
    fun stoppedBeforeQueuedRegistrationNeverTouchesBackend() {
        val f = Fixture()
        f.controller.start("window-a", true)
        f.controller.stop()
        f.controller.destroy()
        f.drain()
        assertTrue(f.backend.prepared.isEmpty())
        assertEquals(0, f.slots.count())
        assertFalse(f.state().active)
        assertEquals("cleanup-complete", f.state().registrationState)
        assertNull(f.state().modes)
    }

    @Test
    fun pendingIsDifferentFromARealTouchCallbackAndEmptyReportedModes() {
        val f = Fixture()
        f.controller.start("window-a", true)
        f.drain()
        assertEquals("pending", f.state().status)
        assertEquals("registered", f.state().registrationState)
        assertNull(f.state().modes)
        f.backend.prepared.single().emit("TOUCH")
        f.main.drain()
        assertEquals("reported", f.state().status)
        assertEquals(listOf("TOUCH"), f.state().modes?.names)
        f.backend.prepared.single().emit()
        f.main.drain()
        assertEquals("reported", f.state().status)
        assertEquals(emptyList<String>(), f.state().modes?.names)
    }

    @Test
    fun delayedMainDeliveryAndLateOldCallbackCannotOverwriteRestartedEpoch() {
        val f = Fixture()
        f.controller.start("window-a", true)
        f.drain()
        val first = f.backend.prepared.single()
        first.emit("TOUCH") // Accepted, but its main delivery has not run.
        f.controller.stop()
        f.controller.start("window-a", true)
        first.emit("PRECISE_POINTER") // Rejected even before removal executes.
        f.main.drain()
        assertEquals(2L, f.state().epoch)
        assertEquals("pending", f.state().status)
        assertNull(f.state().modes)
        assertEquals(1L, f.state().rejectedLateCallbackCount)
        f.serial.drain()
        f.backend.prepared.last().emit("VISUALS_ON", "PRECISE_POINTER")
        f.main.drain()
        assertEquals(1, first.removals)
        assertEquals(2L, f.state().epoch)
        assertEquals(listOf("VISUALS_ON", "PRECISE_POINTER"), f.state().modes?.names)
        assertEquals(1L, f.state().currentEpochCallbackCount)
    }

    @Test
    fun stoppingFirstWindowDoesNotRemoveSecondWindowsSubscription() {
        val f = Fixture()
        val secondReports = mutableListOf<EngagementUpdate>()
        val second = EngagementController(f.serial, f.main, f.backend, f.slots, f.clock, secondReports::add)
        f.controller.start("window-a", true)
        second.start("window-b", true)
        f.drain()
        f.controller.stop()
        f.controller.destroy()
        f.drain()
        f.backend.prepared[1].emit("PRECISE_POINTER")
        f.main.drain()
        assertEquals(1, f.backend.prepared[0].removals)
        assertEquals(0, f.backend.prepared[1].removals)
        assertEquals("reported", secondReports.last().snapshot.status)
        assertEquals(1, f.slots.count())
        second.stop()
        f.drain()
        assertEquals(0, f.slots.count())
    }

    @Test
    fun partialRegistrationFailureCleansSameObjectAndRejectsItsCallback() {
        val f = Fixture()
        f.backend.onRegister = { it.emit("TOUCH") }
        f.backend.registerFailure = IllegalStateException("test-only partial registration")
        f.controller.start("window-a", true)
        f.drain()
        val registration = f.backend.prepared.single()
        assertEquals(1, registration.registrations)
        assertEquals(1, registration.removals)
        assertEquals("error", f.state().status)
        assertEquals("IllegalStateException", f.state().errorType)
        registration.emit("PRECISE_POINTER")
        f.controller.refresh()
        f.main.drain()
        assertEquals(1L, f.state().rejectedLateCallbackCount)
        assertEquals(listOf("TOUCH"), f.state().modes?.names)
        f.controller.stop()
        f.controller.destroy()
        f.drain()
        assertEquals(1, registration.removals)
        assertEquals("cleanup-complete", f.state().registrationState)
        assertEquals(0, f.slots.count())
    }

    @Test
    fun failedCleanupIsStickyNeverRetriedAndKeepsAdmissionReserved() {
        val f = Fixture(capacity = 1)
        f.controller.start("window-a", true)
        f.drain()
        f.backend.cleanupFailure = UnsupportedOperationException("test-only cleanup failure")
        f.controller.stop()
        f.drain()
        assertEquals("cleanup-failed", f.state().registrationState)
        assertEquals("error", f.state().status)
        assertEquals(1L, f.state().cleanupFailureCount)
        assertEquals(1, f.reports.sumOf { it.newCleanupFailures.size })
        f.controller.start("window-a", true)
        f.drain()
        assertEquals("capacity-rejected", f.state().status)
        assertEquals(1, f.backend.prepared.size)
        f.controller.destroy()
        f.controller.refresh()
        f.drain()
        assertEquals(1, f.backend.prepared.single().removals)
        assertEquals(1, f.slots.count())
        assertEquals(1, f.reports.sumOf { it.newCleanupFailures.size })
    }

    @Test
    fun oldCleanupFailureReportsHistoricalIssueWithoutReplacingNewEpoch() {
        val f = Fixture()
        f.controller.start("window-a", true)
        f.drain()
        f.backend.cleanupFailure = IllegalStateException("test-only removal failure")
        f.controller.stop()
        f.controller.start("window-a", true)
        f.drain()
        assertEquals(2L, f.state().epoch)
        assertEquals("pending", f.state().status)
        assertEquals("registered", f.state().registrationState)
        assertEquals(1L, f.reports.last().newCleanupFailures.single().epoch)
        assertEquals(2, f.slots.count())
    }

    @Test
    fun boundedAdmissionAlsoBoundsQueuedLifecycleWorkDuringBlockedBackend() {
        val f = Fixture(capacity = 3)
        repeat(100) {
            f.controller.start("window-a", true)
            f.controller.stop()
        }
        assertEquals(6, f.serial.work.size) // Three admitted registration/removal pairs.
        assertEquals(1, f.main.work.size)
        f.controller.start("window-a", true)
        f.main.drain()
        assertEquals("capacity-rejected", f.state().status)
        assertTrue(f.backend.prepared.isEmpty())
        f.drain()
        assertEquals(0, f.slots.count())
        assertEquals("capacity-rejected", f.state().status)
    }

    @Test
    fun untrackedWindowCannotRequestABackendAndDestroyedControllerCannotRestart() {
        val f = Fixture()
        f.controller.start("untracked", false)
        f.drain()
        assertEquals("not-tracked", f.state().status)
        assertEquals(0, f.slots.count())
        f.controller.destroy()
        f.controller.start("window-a", true)
        f.drain()
        assertTrue(f.backend.prepared.isEmpty())
        assertEquals(1L, f.state().epoch)
    }

    @Test
    fun repeatedValuesAreCountedWithoutUnboundedDeliveryOrSaveTraffic() {
        val f = Fixture()
        f.controller.start("window-a", true)
        f.drain()
        val registration = f.backend.prepared.single()
        repeat(1000) { registration.emit("TOUCH") }
        assertEquals(1, f.main.work.size)
        f.main.drain()
        assertEquals(1000L, f.state().callbackCount)
        assertEquals(999L, f.state().repeatedCallbackCount)
        val published = f.reports.size
        repeat(1000) { registration.emit("TOUCH") }
        assertEquals(0, f.main.work.size)
        assertEquals(published, f.reports.size)
        f.controller.refresh()
        f.main.drain()
        assertEquals(2000L, f.state().callbackCount)
        assertEquals(1999L, f.state().repeatedCallbackCount)
        assertEquals(1, f.state().events.count { it.kind == "modes-reported" })
    }

    @Test
    fun changingValuesRetainBoundedHistoryWithExplicitOmissionCount() {
        val f = Fixture(historyCapacity = 4)
        f.controller.start("window-a", true)
        f.drain()
        val captured = f.state()
        repeat(20) { f.backend.prepared.single().emit(if (it % 2 == 0) "TOUCH" else "PRECISE_POINTER") }
        assertEquals(1, f.main.work.size)
        f.main.drain()
        assertEquals(4, f.state().events.size)
        assertEquals(18L, f.state().droppedEventCount)
        assertEquals(2, captured.events.size)
        assertNull(captured.modes)
        assertEquals(20L, f.state().callbackCount)
    }

    @Test
    fun missingAlphaImplementationAndRuntimeLinkageFailureAreExplicitErrors() {
        listOf(NotImplementedError("test-only interface default"), NoClassDefFoundError("test-only linkage")).forEach { failure ->
            val f = Fixture()
            f.backend.registerFailure = failure
            f.controller.start("window-a", true)
            f.drain()
            assertEquals("error", f.state().status)
            assertEquals(failure.javaClass.simpleName, f.state().errorType)
            assertEquals(1, f.backend.prepared.single().removals)
        }
    }
}
