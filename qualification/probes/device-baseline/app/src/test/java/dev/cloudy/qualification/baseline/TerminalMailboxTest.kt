package dev.cloudy.qualification.baseline

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalMailboxTest {
    private fun identity() = TerminalIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString())
    private fun ready(id: TerminalIdentity) = TerminalMailbox().apply { bind(id); arm(); release() }

    @Test fun inputAndResizeRejectOldAttachmentAndStoppedSession() {
        val id = identity()
        val queue = TerminalMailbox()
        queue.bind(id)
        assertEquals(TerminalAdmission.STOPPED, queue.offerInput(id, byteArrayOf(1)))
        queue.arm()
        val stale = id.copy(attachmentId = UUID.randomUUID().toString())
        assertEquals(TerminalAdmission.STALE, queue.offerInput(stale, byteArrayOf(1)))
        assertEquals(TerminalAdmission.STALE, queue.offerResize(stale, TerminalSize(80, 24)))
        queue.close()
        assertEquals(TerminalAdmission.STOPPED, queue.offerInput(id, byteArrayOf(1)))
        assertEquals(TerminalAdmission.STOPPED, queue.offerResize(id, TerminalSize(80, 24)))
        assertNull(queue.take())
    }

    @Test fun cancellationBeforeBindingCannotBeReopened() {
        val queue = TerminalMailbox()
        queue.close()
        val id = identity()
        queue.bind(id)
        queue.arm()
        queue.release()
        assertEquals(TerminalAdmission.STOPPED, queue.offerInput(id, byteArrayOf(1)))
        assertNull(queue.take())
    }

    @Test fun fullPasteAdmissionIsAtomicAndCountsInFlightBytes() {
        val id = identity()
        val queue = ready(id)
        val bytes = ByteArray(TerminalLimits.INPUT_QUEUE_BYTES) { (it % 251).toByte() }
        assertEquals(TerminalAdmission.ACCEPTED, queue.offerInput(id, bytes))
        assertEquals(TerminalAdmission.FULL, queue.offerInput(id, byteArrayOf(1)))
        val first = queue.take() as TerminalWrite.Input
        assertEquals(TerminalLimits.OUTPUT_CHUNK_BYTES, first.bytes.size)
        assertEquals(TerminalAdmission.FULL, queue.offerInput(id, byteArrayOf(2)))
        queue.completed(first)
        assertEquals(TerminalAdmission.FULL, queue.offerInput(id, ByteArray(first.bytes.size + 1)))
        val suffix = ByteArray(first.bytes.size) { 9 }
        assertEquals(TerminalAdmission.ACCEPTED, queue.offerInput(id, suffix))
        val actual = java.io.ByteArrayOutputStream().apply { write(first.bytes) }
        repeat(4) {
            val work = queue.take() as TerminalWrite.Input
            actual.write(work.bytes)
            queue.completed(work)
        }
        assertArrayEquals(bytes + suffix, actual.toByteArray())
        queue.close()
    }

    @Test fun copyingPreservesBinaryUtf8AndRingWrapWithoutCallerMutation() {
        val id = identity()
        val queue = ready(id)
        val prefix = ByteArray(60_000) { (it % 255).toByte() }
        val expected = prefix.copyOf()
        queue.offerInput(id, prefix)
        prefix.fill(0)
        val out = java.io.ByteArrayOutputStream()
        repeat(3) {
            val next = queue.take() as TerminalWrite.Input
            out.write(next.bytes)
            queue.completed(next)
        }
        val suffix = "\u001b[31m你好\r\n\u0000".toByteArray(Charsets.UTF_8) + ByteArray(20_000) { 42 }
        assertEquals(TerminalAdmission.ACCEPTED, queue.offerInput(id, suffix))
        while (out.size() < expected.size + suffix.size) {
            val next = queue.take() as TerminalWrite.Input
            out.write(next.bytes)
            queue.completed(next)
        }
        assertArrayEquals(expected + suffix, out.toByteArray())
        queue.close()
    }

    @Test fun resizeCoalescesWithoutStarvingQueuedInput() {
        val id = identity()
        val queue = ready(id)
        queue.offerResize(id, TerminalSize(90, 30))
        queue.offerResize(id, TerminalSize(100, 40))
        queue.offerInput(id, byteArrayOf(1, 2))
        assertEquals(TerminalWrite.Resize(TerminalSize(100, 40)), queue.take())
        queue.offerResize(id, TerminalSize(110, 41))
        val input = queue.take() as TerminalWrite.Input
        assertArrayEquals(byteArrayOf(1, 2), input.bytes)
        queue.completed(input)
        assertEquals(TerminalWrite.Resize(TerminalSize(110, 41)), queue.take())
        queue.close()
    }

    @Test fun armedAdmissionCannotDispatchBeforeDurableOpenReturns() {
        val id = identity()
        val queue = TerminalMailbox().apply { bind(id); arm() }
        queue.offerInput(id, byteArrayOf(7))
        val entered = CountDownLatch(1)
        val task = FutureTask { entered.countDown(); queue.take() }
        val thread = Thread(task)
        thread.start()
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { task.get(30, TimeUnit.MILLISECONDS) }
            queue.release()
            val input = task.get(1, TimeUnit.SECONDS) as TerminalWrite.Input
            assertArrayEquals(byteArrayOf(7), input.bytes)
            queue.completed(input)
        } finally {
            queue.close()
            thread.interrupt()
            thread.join(1_000)
        }
        assertTrue(!thread.isAlive)
    }

    @Test fun closeReleasesRealBlockedWriterAndDropsPendingData() {
        val id = identity()
        val queue = ready(id)
        val entered = CountDownLatch(1)
        val task = FutureTask { entered.countDown(); queue.take() }
        val thread = Thread(task)
        thread.start()
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            queue.close()
            assertNull(task.get(1, TimeUnit.SECONDS))
        } finally {
            queue.close()
            thread.interrupt()
            thread.join(1_000)
        }
        assertTrue(!thread.isAlive)
    }

    @Test fun zeroLengthIsNoOpAndOversizedInputDoesNotConsumeCapacity() {
        val id = identity()
        val queue = ready(id)
        assertEquals(TerminalAdmission.ACCEPTED, queue.offerInput(id, byteArrayOf()))
        assertEquals(TerminalAdmission.FULL, queue.offerInput(id, ByteArray(TerminalLimits.INPUT_QUEUE_BYTES + 1)))
        assertEquals(TerminalAdmission.ACCEPTED, queue.offerInput(id, ByteArray(TerminalLimits.INPUT_QUEUE_BYTES)))
        queue.close()
    }
}
