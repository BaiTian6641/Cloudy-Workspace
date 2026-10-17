package io.github.baitian6641.cloudy.terminal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real bounded queue/worker tests; no fake queue is used by the renderer implementation. */
class TerminalFlowTest {
    @Test fun chunkCopyOrderingAndExactParserAckOwnCredits() {
        val flow = TerminalFlow()
        val bytes = byteArrayOf(1, 2, 3)
        flow.put(bytes, false); bytes[0] = 9
        flow.put(byteArrayOf(4), true)
        val a = flow.claim(1)!!
        val b = flow.claim(3)!! // Admission replies can occupy native sequence 2.
        assertArrayEquals(byteArrayOf(1, 2, 3), a.bytes)
        assertTrue(b.isStderr)
        assertEquals(4, flow.outstandingBytes())
        for ((sequence, length) in listOf(3 to 1, 1 to 2, 2 to 3)) {
            assertThrows(IllegalArgumentException::class.java) { flow.acknowledge(sequence, length) }
            assertEquals(4, flow.outstandingBytes())
        }
        flow.acknowledge(1, 3)
        assertThrows(IllegalArgumentException::class.java) { flow.acknowledge(1, 3) }
        flow.acknowledge(3, 1)
        flow.awaitDrained()
        assertEquals(0, flow.outstandingBytes())
    }

    @Test fun highWaterWriterWaitsForLowWaterRatherThanOneFreeChunk() {
        val flow = TerminalFlow()
        repeat(16) { flow.put(ByteArray(16384), false); flow.claim(it + 1) }
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        try {
            val future = executor.submit { entered.countDown(); flow.put(byteArrayOf(7), false) }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { future.get(50, TimeUnit.MILLISECONDS) }
            flow.acknowledge(1, 16384)
            assertThrows(TimeoutException::class.java) { future.get(50, TimeUnit.MILLISECONDS) }
            for (sequence in 2..12) flow.acknowledge(sequence, 16384)
            future.get(1, TimeUnit.SECONDS)
            assertEquals(65537, flow.outstandingBytes())
        } finally { flow.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS)) }
    }

    @Test fun closeWakesBothCreditWaiterAndFinalDrainWithoutReportingSuccess() {
        val flow = TerminalFlow()
        repeat(16) { flow.put(ByteArray(16384), false); flow.claim(it + 1) }
        val executor = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(2)
        try {
            val writer = executor.submit<String> { entered.countDown(); try { flow.put(byteArrayOf(1), false); "wrong-success" } catch (e: TerminalFlowClosed) { e.reason } }
            val drain = executor.submit<String> { entered.countDown(); try { flow.awaitDrained(); "wrong-success" } catch (e: TerminalFlowClosed) { e.reason } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            flow.close("test-close")
            assertEquals("test-close", writer.get(1, TimeUnit.SECONDS))
            assertEquals("test-close", drain.get(1, TimeUnit.SECONDS))
            assertEquals(0, flow.outstandingBytes())
            assertThrows(TerminalFlowClosed::class.java) { flow.acknowledge(1, 16384) }
        } finally { flow.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS)) }
    }

    @Test fun eofDrainWaitsForLastAckNotOnlyEmptyPostingQueue() {
        val flow = TerminalFlow()
        flow.put(byteArrayOf(1, 2), false); flow.claim(1)
        assertFalse(flow.hasQueued())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val done = executor.submit { flow.awaitDrained() }
            assertThrows(TimeoutException::class.java) { done.get(50, TimeUnit.MILLISECONDS) }
            flow.acknowledge(1, 2)
            done.get(1, TimeUnit.SECONDS)
        } finally { flow.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS)) }
    }

    @Test fun onlyPendingOrQueuedBytesHaveDeadlineAndTimeoutAbortsDrain() {
        var now = 0L
        val flow = TerminalFlow { now }
        now = 100_000
        assertFalse(flow.ackExpired())
        flow.awaitDrained()
        flow.put(byteArrayOf(1), false); flow.claim(1)
        now += 9999
        assertFalse(flow.ackExpired())
        now++
        assertEquals("renderer-parser-ack-timeout", flow.expiredReason())
        assertEquals("renderer-parser-ack-timeout", assertThrows(TerminalFlowClosed::class.java) { flow.awaitDrained() }.reason)
        val queued = TerminalFlow { now }
        queued.put(byteArrayOf(1), false)
        now += 10_000
        assertEquals("renderer-delivery-timeout", queued.expiredReason())
    }

    @Test fun concurrentProducersPreserveEveryChunkWithinCreditBound() {
        val flow = TerminalFlow()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val producers = (0..1).map { stream -> executor.submit {
                repeat(16) { n -> flow.put(byteArrayOf(stream.toByte(), n.toByte()), stream == 1) }
            } }
            producers.forEach { it.get(1, TimeUnit.SECONDS) }
            val observed = mutableSetOf<Pair<Int, Int>>()
            repeat(32) { n ->
                val chunk = flow.claim(n + 1)!!
                assertEquals(chunk.bytes[0] == 1.toByte(), chunk.isStderr)
                observed.add(chunk.bytes[0].toInt() to chunk.bytes[1].toInt())
                flow.acknowledge(n + 1, 2)
            }
            assertEquals(32, observed.size)
            flow.awaitDrained()
        } finally { flow.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS)) }
    }

    @Test fun lateAckAndLateDeliveryCannotEraseAnExpiredDeadlineBeforeTheTimerRuns() {
        var now = 0L
        val posted = TerminalFlow { now }
        posted.put(byteArrayOf(1), false); posted.claim(1)
        now = 10_000
        assertEquals("renderer-parser-ack-timeout", assertThrows(TerminalFlowClosed::class.java) {
            posted.acknowledge(1, 1)
        }.reason)
        assertThrows(TerminalFlowClosed::class.java) { posted.put(byteArrayOf(2), false) }
        val queued = TerminalFlow { now }
        queued.put(byteArrayOf(3), false)
        now = 20_000
        assertEquals("renderer-delivery-timeout", assertThrows(TerminalFlowClosed::class.java) {
            queued.claim(1)
        }.reason)
        assertThrows(TerminalFlowClosed::class.java) { queued.awaitDrained() }
    }

    @Test fun tinyChunksCannotCreateUnboundedPendingObjects() {
        val flow = TerminalFlow()
        repeat(64) { flow.put(byteArrayOf(1), false) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit { flow.put(byteArrayOf(2), false) }
            assertThrows(TimeoutException::class.java) { future.get(50, TimeUnit.MILLISECONDS) }
            flow.claim(1); flow.acknowledge(1, 1)
            future.get(1, TimeUnit.SECONDS)
            assertEquals(64, flow.outstandingBytes())
        } finally { flow.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS)) }
    }
}
