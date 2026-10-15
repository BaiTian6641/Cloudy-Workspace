package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedHistoryTest {
    @Test
    fun newestEventsRemainOrderedAndEvictionIsReported() {
        val history = BoundedHistory<String>(2)
        history.add("created")
        history.add("resumed")
        history.add("focus-lost")
        history.add("paused")
        assertEquals(listOf("focus-lost", "paused"), history.snapshot())
        assertEquals(2L, history.droppedCount)
    }

    @Test
    fun capturedSnapshotDoesNotChangeWhenNewEventsArrive() {
        val history = BoundedHistory<String>(1)
        history.add("created")
        val captured = history.snapshot()
        history.add("destroyed")
        assertEquals(listOf("created"), captured)
        assertEquals(listOf("destroyed"), history.snapshot())
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityIsRejected() {
        BoundedHistory<String>(0)
    }
}
