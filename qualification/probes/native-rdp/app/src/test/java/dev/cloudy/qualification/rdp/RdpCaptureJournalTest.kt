package dev.cloudy.qualification.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpCaptureJournalTest {
    @Test fun retiredOrReplacedAttemptsRejectLateCallbacksAndKeepClosedEvidence() {
        val journal = RdpCaptureJournal()
        journal.begin("old")
        journal.capture("old", "capture-confirmed", "system", 10, 4, 2)
        journal.retire()
        assertNull(journal.capture("old", "capture-lost", "system", 11, 4, 2))
        assertEquals(1, journal.snapshot().size)
        journal.begin("new")
        assertNull(journal.capture("old", "capture-released", "operator", 12, 4, 2))
        assertTrue(journal.snapshot().isEmpty())
    }

    @Test fun requestAndActualCallbackKeepDifferentTimesAndOwnerCounters() {
        val journal = RdpCaptureJournal()
        journal.begin("attempt")
        journal.capture("attempt", "release-requested", "operator", 20, 6, 3)
        journal.capture("attempt", "capture-released", "operator", 25, 9, 5)
        journal.acknowledgePending(24, 8, 4)
        assertEquals(24L, journal.snapshot()[0]["ownerElapsedMillis"])
        assertTrue("ownerElapsedMillis" !in journal.snapshot()[1])
        journal.retire() // Native reap can beat the queued evidence task.
        journal.acknowledgePending(30, 10, 6)
        assertEquals(25L, journal.snapshot()[1]["elapsedMillis"])
        assertEquals(9L, journal.snapshot()[1]["inputEvents"])
        assertEquals(10L, journal.snapshot()[1]["ownerInputEvents"])
        assertEquals("capture-released", journal.snapshot()[1]["edge"])
    }

    @Test fun boundedAllowlistRejectsSecretsAndOverflowsWithoutGrowingTheJournal() {
        val journal = RdpCaptureJournal()
        journal.begin("attempt")
        assertNull(journal.capture("attempt", "private-message", "system", 0, 0, 0))
        assertNull(journal.capture("attempt", "capture-confirmed", "private-reason", 0, 0, 0))
        assertNull(journal.capture("attempt", "capture-confirmed", "system", -1, 0, 0))
        assertNull(journal.capture("attempt", "capture-confirmed", "system", 0, 1, 2))
        repeat(RdpCaptureJournal.MAX_EDGES + 3) { index ->
            journal.capture("attempt", "capture-confirmed", "system", index.toLong(), index.toLong(), 0)
        }
        assertEquals(RdpCaptureJournal.MAX_EDGES, journal.snapshot().size)
        assertEquals(3, journal.omitted())
        val copy = journal.snapshot()
        journal.begin("new")
        assertEquals(RdpCaptureJournal.MAX_EDGES, copy.size)
    }
}
