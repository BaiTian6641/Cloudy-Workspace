package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EngagementLedgerTest {
    private val identity = WindowIdentity("test-window", 10, "test-generation")
    private val restored = RestoredWindow(WindowLocalState(identity.logicalWindowId, 0, 0), "test-only")

    private fun snapshot(epoch: Long, active: Boolean = true, status: String = "pending") = EngagementSnapshot(
        epoch = epoch, active = active, status = status, registrationState = "test-only",
        modes = null, lastCallbackTime = null, callbackCount = 0, currentEpochCallbackCount = 0,
        repeatedCallbackCount = 0, rejectedLateCallbackCount = 0, cleanupFailureCount = 0,
        errorType = null, events = emptyList(), droppedEventCount = 0,
    )

    @Test
    fun staleEpochAndOldGenerationCannotOverwriteTheCurrentWindow() {
        val ledger = WindowLedger()
        ledger.register(identity, restored, 1)
        ledger.engagement(identity, snapshot(2), 2)
        ledger.engagement(identity, snapshot(1, status = "reported"), 3)
        assertEquals(2L, ledger.record(identity)?.engagement?.epoch)
        val next = identity.copy(generationId = "replacement-generation")
        ledger.register(next, restored, 4)
        ledger.engagement(identity, snapshot(3, false, "error"), 5)
        assertNull(ledger.record(next)?.engagement)
        assertEquals(2L, ledger.rejectedCallbackCount)
    }

    @Test
    fun closedGenerationCanRecordCleanupButCannotBeRevivedByActiveSnapshot() {
        val ledger = WindowLedger()
        ledger.register(identity, restored, 1)
        ledger.event(identity, "destroyed", "", 2, close = true)
        val stopped = snapshot(1, false, "stopped")
        ledger.engagement(identity, stopped, 3)
        val revision = ledger.revision
        assertFalse(ledger.engagement(identity, stopped, 4))
        assertEquals(revision, ledger.revision)
        ledger.engagement(identity, snapshot(1, true, "reported"), 5)
        assertEquals(stopped, ledger.record(identity)?.engagement)
        assertEquals("closed-generation", ledger.attribution(identity))
    }
}
