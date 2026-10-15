package dev.cloudy.qualification.rdp

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real synchronization/serialization policy checks, not a native callback simulation. */
class RdpCallbackFailureTest {
    @Test
    fun firstCauseIsCopiedAndSurvivesLaterFailuresAndRetirement() {
        val tracker = RdpFailureTracker()
        val token = tracker.begin(7)
        val values = intArrayOf(7680, 2160, 32)
        val cancellations = AtomicInteger()
        assertEquals(token, tracker.capture(7, "resize", "IllegalArgumentException", values) { cancellations.incrementAndGet() })
        values[0] = 1
        assertNull(tracker.capture(7, "graphics-update", "IllegalStateException", intArrayOf(0, 0, 4, 4)) { cancellations.incrementAndGet() })
        tracker.retire(token)
        val fields = checkNotNull(tracker.snapshot(token)).journalFields()
        assertEquals("resize", fields["operation"])
        assertEquals("IllegalArgumentException", fields["exceptionClass"])
        assertEquals(7680, fields["width"])
        assertEquals(2160, fields["height"])
        assertEquals(1, cancellations.get())
        assertFalse(tracker.isActive(token))
        assertNull(tracker.capture(7, "resize", "Error", intArrayOf(1, 1, 32)) { cancellations.incrementAndGet() })
    }

    @Test
    fun staleInstanceAndReusedAddressCannotActAsTheNewAttempt() {
        val tracker = RdpFailureTracker()
        val old = tracker.begin(7)
        val cancellations = AtomicInteger()
        assertNull(tracker.capture(8, "authenticate", "Error", intArrayOf()) { cancellations.incrementAndGet() })
        tracker.retire(old)
        val current = tracker.begin(7)
        assertNotSame(old, current)
        assertFalse(tracker.isActive(old))
        assertNull(tracker.snapshot(old))
        assertTrue(tracker.isActive(current))
        assertNull(tracker.snapshot(current))
        assertEquals(0, cancellations.get())
    }

    @Test
    fun ownerFailureBeforeAllocationAndCallbackBeforeCleanupKeepTheirFirstCause() {
        val tracker = RdpFailureTracker()
        val beforeAllocation = tracker.begin()
        assertEquals(beforeAllocation, tracker.captureOwner(beforeAllocation, "IOException", 3) {})
        assertEquals("owner-action", tracker.snapshot(beforeAllocation)?.operation)
        assertEquals(3L, tracker.snapshot(beforeAllocation)?.elapsedMillis)
        tracker.retire(beforeAllocation)
        val live = tracker.begin()
        tracker.bind(live, 7)
        tracker.capture(7, "resize", "IllegalArgumentException", intArrayOf(7680, 2160, 32), 20) {}
        assertNull(tracker.captureOwner(live, "IOException", 30) {})
        assertEquals("resize", tracker.snapshot(live)?.operation)
        assertEquals(20L, tracker.snapshot(live)?.elapsedMillis)
        assertNull(tracker.captureOwner(beforeAllocation, "Error", 40) {})
    }

    @Test
    fun concurrentFailuresPublishOneCancellationAndOneWholeCause() {
        val tracker = RdpFailureTracker()
        val token = tracker.begin(7)
        val gate = CountDownLatch(1)
        val cancellations = AtomicInteger()
        val threads = (1..2).map { value ->
            Thread {
                gate.await()
                tracker.capture(7, "resize", "IllegalArgumentException", intArrayOf(value, value * 2, 32)) {
                    cancellations.incrementAndGet()
                }
            }.apply { start() }
        }
        gate.countDown()
        threads.forEach { it.join(5000); assertFalse("Capture thread did not finish", it.isAlive) }
        val fields = checkNotNull(tracker.snapshot(token)).journalFields()
        val width = fields["width"] as Int
        assertTrue(width in 1..2)
        assertEquals(width * 2, fields["height"])
        assertEquals(1, cancellations.get())
    }

    @Test
    fun serializationContainsOnlyAllowlistedBoundedIdentifiersAndIntegerSlots() {
        val cause = checkNotNull(RdpCallbackFailure.create("pointer-set", "x".repeat(81),
            intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE, -3, 4)))
        val fields = cause.journalFields()
        assertEquals(setOf("operation", "exceptionClass", "elapsedMillis", "width", "height", "hotX", "hotY"), fields.keys)
        assertEquals("Throwable", fields["exceptionClass"])
        assertEquals(Int.MAX_VALUE, fields["width"])
        assertEquals(Int.MIN_VALUE, fields["height"])
        assertTrue(fields.values.all { it is String || it is Int || it is Long })
        assertTrue(cause.summary().length <= 120)
        assertNull(RdpCallbackFailure.create("private-endpoint-in-operation", "Error", intArrayOf()))
        assertNull(RdpCallbackFailure.create("resize", "Error", intArrayOf(1, 2)))
        assertEquals("Throwable", checkNotNull(RdpCallbackFailure.create("authenticate", "message\nsecret", intArrayOf())).exceptionClass)
    }

    @Test
    fun nonDimensionalCallbacksNeverSerializeSuppliedExtraValues() {
        val cause = checkNotNull(RdpCallbackFailure.create("connected", "Error", intArrayOf(1, 2, 3)))
        assertEquals(setOf("operation", "exceptionClass", "elapsedMillis"), cause.journalFields().keys)
    }
}
