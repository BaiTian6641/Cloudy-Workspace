package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Identity/retention policy tests use fixture strings, never qualification artifacts. */
class WindowLedgerTest {
    private val a = WindowIdentity("window-a", 10, "generation-a1")
    private val b = WindowIdentity("window-b", 20, "generation-b1")
    private val c = WindowIdentity("window-c", 30, "generation-c1")

    private fun fresh(identity: WindowIdentity) = RestoredWindow(WindowLocalState(identity.logicalWindowId, 0, 0), "fresh-no-bundle")

    @Test
    fun observationsAndMarkerChangesCannotOverwriteAnotherTask() {
        val ledger = WindowLedger()
        ledger.register(a, fresh(a), 1)
        ledger.register(b, fresh(b), 2)
        ledger.observe(a, "geometry-a", 3)
        ledger.observe(b, "geometry-b", 4)
        ledger.changeState(a, fresh(a).state.changed(), 5)
        assertEquals("geometry-a", ledger.record(a)?.observationJson)
        assertEquals("geometry-b", ledger.record(b)?.observationJson)
        assertEquals(1L, ledger.record(a)?.localState?.changeCount)
        assertEquals(0L, ledger.record(b)?.localState?.changeCount)
        assertEquals(listOf(a, b, a, b, a), ledger.events().map { it.identity })
    }

    @Test
    fun replacementGenerationRejectsLateCallbacksWithoutLosingNewState() {
        val ledger = WindowLedger()
        ledger.register(a, fresh(a), 1)
        ledger.observe(a, "old-geometry", 2)
        val replacement = a.copy(generationId = "generation-a2")
        val restored = RestoredWindow(fresh(a).state.changed().changed(), "restored-bundle")
        ledger.register(replacement, restored, 3)
        assertNull(ledger.record(replacement)?.observationJson)
        ledger.event(a, "activity-destroyed", "late old callback", 4, close = true)
        ledger.changeState(a, fresh(a).state, 5)
        ledger.observe(a, "late-old-geometry", 6)
        assertEquals("current", ledger.attribution(replacement))
        assertEquals(restored.state, ledger.record(replacement)?.localState)
        assertNull(ledger.record(replacement)?.observationJson)
        assertEquals(3L, ledger.rejectedCallbackCount)
        assertTrue(ledger.events().takeLast(3).all { it.identity == a && it.attribution == "stale-generation-or-task" })
        assertEquals(1L, ledger.supersededGenerationCount)
    }

    @Test
    fun duplicateIdentityInAnotherLiveTaskIsRejectedWithoutMerging() {
        val ledger = WindowLedger()
        ledger.register(a, fresh(a), 1)
        ledger.changeState(a, fresh(a).state.changed(), 2)
        val collision = a.copy(taskId = 99, generationId = "other-task")
        assertEquals("live-task-identity-conflict", ledger.register(collision, fresh(collision), 3))
        assertEquals(1L, ledger.record(a)?.localState?.changeCount)
        assertNull(ledger.record(collision))
        assertEquals(1L, ledger.rejectedRegistrationCount)
        assertEquals(collision, ledger.events().last().identity)
    }

    @Test
    fun fullRegistryKeepsStoppedActivitiesAndRefusesExcessRegistration() {
        val ledger = WindowLedger(capacity = 2)
        ledger.register(a, fresh(a), 1)
        ledger.register(b, fresh(b), 2)
        ledger.event(a, "activity-stopped", "", 3)
        assertEquals("registry-capacity", ledger.register(c, fresh(c), 4))
        assertEquals(listOf(a, b), ledger.records().map { it.identity })
        assertEquals(2, ledger.aliveCount)
        assertEquals(0L, ledger.evictedWindowCount)
        assertNull(ledger.record(c))
    }

    @Test
    fun onlyClosedRecordsAreEvictedAndLateCallbacksCannotReinsertThem() {
        val ledger = WindowLedger(capacity = 2)
        ledger.register(a, fresh(a), 1)
        ledger.register(b, fresh(b), 2)
        ledger.event(a, "activity-destroyed", "", 3, close = true)
        assertEquals("current", ledger.register(c, fresh(c), 4))
        ledger.observe(a, "late-geometry", 5)
        assertEquals(listOf(b, c), ledger.records().map { it.identity })
        assertEquals(1L, ledger.evictedWindowCount)
        assertEquals("not-retained", ledger.events().last().attribution)
    }

    @Test
    fun boundedHistoryRetainsAttributionAndReportsDroppedEvents() {
        val ledger = WindowLedger(eventCapacity = 3)
        ledger.register(a, fresh(a), 1)
        ledger.register(b, fresh(b), 2)
        ledger.changeState(a, fresh(a).state.changed(), 3)
        ledger.event(a.copy(generationId = "unknown-old"), "window-focus", "false", 4)
        ledger.event(b, "window-focus", "true", 5)
        val events = ledger.events()
        assertEquals(listOf(3L, 4L, 5L), events.map { it.sequence })
        assertEquals(listOf("window-a", "window-a", "window-b"), events.map { it.identity?.logicalWindowId })
        assertEquals("stale-generation-or-task", events[1].attribution)
        assertEquals(2L, ledger.droppedEventCount)
    }

    @Test
    fun repeatedGeometryProducesNoEventsAndCapturedRecordsRemainImmutable() {
        val ledger = WindowLedger()
        ledger.register(a, fresh(a), 1)
        assertTrue(ledger.observe(a, "geometry", 2))
        val captured = ledger.records()
        val revision = ledger.revision
        assertFalse(ledger.observe(a, "geometry", 3))
        assertEquals(revision, ledger.revision)
        ledger.changeState(a, fresh(a).state.changed(), 4)
        assertEquals(0L, captured.single().localState.changeCount)
        assertEquals(1L, ledger.record(a)?.localState?.changeCount)
    }
}
