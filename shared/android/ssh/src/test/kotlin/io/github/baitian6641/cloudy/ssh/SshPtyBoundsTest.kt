package io.github.baitian6641.cloudy.ssh

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class SshPtyBoundsTest {
    @Test fun inputCapacityIncludesInFlightAndCopiesCallerBytes() {
        val mailbox = SshPtyMailbox()
        val bytes = ByteArray(16 * 1024) { 7 }
        repeat(4) { assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(bytes)) }
        bytes.fill(0)
        val first = mailbox.take() as SshPtyWrite.Bytes
        assertTrue(first.value.all { it == 7.toByte() })
        assertEquals(SshPtyAdmission.FULL, mailbox.offer(byteArrayOf(1)))
        mailbox.completed(first)
        assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(byteArrayOf(1)))
        mailbox.close()
    }
    @Test fun invalidInputNeverPartiallyQueues() {
        val mailbox = SshPtyMailbox()
        assertEquals(SshPtyAdmission.INVALID_INPUT, mailbox.offer(ByteArray(0)))
        assertEquals(SshPtyAdmission.INVALID_INPUT, mailbox.offer(ByteArray(64 * 1024 + 1)))
        assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(byteArrayOf(3)))
        val actual = mailbox.take() as SshPtyWrite.Bytes
        assertArrayEquals(byteArrayOf(3), actual.value)
        mailbox.completed(actual)
        mailbox.close()
    }
    @Test fun wholeLargeActionsShareTheExistingCapacityWithoutPartialAdmission() {
        val mailbox = SshPtyMailbox()
        try {
            assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(ByteArray(16 * 1024)))
            assertEquals(SshPtyAdmission.FULL, mailbox.offer(ByteArray(60 * 1024)))
            assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(byteArrayOf(42)))
            val first = mailbox.take() as SshPtyWrite.Bytes
            assertEquals(16 * 1024, first.value.size)
            mailbox.completed(first)
            val next = mailbox.take() as SshPtyWrite.Bytes
            assertArrayEquals(byteArrayOf(42), next.value)
            mailbox.completed(next)

            assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(ByteArray(64 * 1024)))
            val entire = mailbox.take() as SshPtyWrite.Bytes
            assertEquals(64 * 1024, entire.value.size)
            assertEquals(SshPtyAdmission.FULL, mailbox.offer(byteArrayOf(1)))
            mailbox.completed(entire)
            assertEquals(SshPtyAdmission.ACCEPTED, mailbox.offer(byteArrayOf(1)))
        } finally {
            mailbox.close()
        }
    }
    @Test fun resizeCoalescesWithoutStarvingQueuedInput() {
        val mailbox = SshPtyMailbox()
        mailbox.offer(byteArrayOf(5))
        mailbox.resize(SshPtySize(80, 24))
        mailbox.resize(SshPtySize(123, 41))
        val size = (mailbox.take() as SshPtyWrite.Resize).size
        assertEquals(123, size.columns)
        mailbox.resize(SshPtySize(90, 30))
        val bytes = mailbox.take() as SshPtyWrite.Bytes
        mailbox.completed(bytes)
        assertEquals(90, (mailbox.take() as SshPtyWrite.Resize).size.columns)
        mailbox.close()
    }
    @Test fun closeWakesIdleWriterAndRefusesStaleHandle() {
        val mailbox = SshPtyMailbox()
        val entered = CountDownLatch(1)
        val done = CountDownLatch(1)
        val value = AtomicReference<SshPtyWrite?>()
        val worker = Thread { entered.countDown(); value.set(mailbox.take()); done.countDown() }
        worker.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        mailbox.close()
        assertTrue(done.await(1, TimeUnit.SECONDS))
        worker.join(1_000)
        assertFalse(worker.isAlive)
        assertNull(value.get())
        assertEquals(SshPtyAdmission.STOPPED, mailbox.offer(byteArrayOf(1)))
        assertEquals(SshPtyAdmission.STOPPED, mailbox.resize(SshPtySize(80, 24)))
    }
    @Test fun boundedOutputCloseUnblocksProducerAndPreservesQueuedBytes() {
        val output = SshPtyOutput()
        repeat(16) { output.put(ByteArray(16 * 1024) { 4 }, false) }
        val entered = CountDownLatch(1)
        val done = CountDownLatch(1)
        val rejected = byteArrayOf(9)
        val worker = Thread { entered.countDown(); output.put(rejected, true); done.countDown() }
        worker.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        output.close()
        assertTrue(done.await(1, TimeUnit.SECONDS))
        worker.join(1_000)
        assertFalse(worker.isAlive)
        assertArrayEquals(byteArrayOf(0), rejected)
        repeat(16) {
            val chunk = output.read(10) { SshTransportCode.CLOSED } as SshPtyReadResult.Output
            assertEquals(16 * 1024, chunk.bytes.size)
            assertFalse(chunk.isStderr)
        }
        assertEquals(SshPtyReadResult.End(SshTransportCode.CLOSED), output.read(10) { SshTransportCode.CLOSED })
    }
    @Test fun emptyOutputTimeoutDoesNotInventEof() {
        val output = SshPtyOutput()
        assertEquals(SshPtyReadResult.Idle, output.read(1) { SshTransportCode.CLOSED })
        output.close()
    }
    @Test fun dimensionsRejectUnsupportedBounds() {
        for ((cols, rows) in listOf(1 to 24, 513 to 24, 80 to 0, 80 to 257)) {
            try { SshPtySize(cols, rows); fail("PTY_DIMENSION_REQUIRED") }
            catch (_: IllegalArgumentException) { }
        }
        SshPtySize(2, 1)
        SshPtySize(512, 256)
    }
}
