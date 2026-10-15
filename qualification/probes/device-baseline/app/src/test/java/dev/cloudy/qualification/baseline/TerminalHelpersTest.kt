package dev.cloudy.qualification.baseline

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalHelpersTest {
    @Test fun helperFailureClosesSocketAndRetainsSafeException() {
        val closures = AtomicInteger()
        val helpers = TerminalHelpers({ closures.incrementAndGet() }, 1_000)
        val task = helpers.start<Unit>("test") { throw IOException("must not become a public reason") }
        assertThrows(ExecutionException::class.java) { task.get(1, TimeUnit.SECONDS) }
        assertTrue(helpers.closeAndJoin())
        assertEquals(1, closures.get())
        assertEquals(IOException::class.java, helpers.failure.get()!!.javaClass)
    }

    @Test fun ordinaryBlockedHelperIsInterruptedAndJoined() {
        val entered = CountDownLatch(1)
        val helpers = TerminalHelpers({}, 1_000)
        helpers.start("writer") { entered.countDown(); CountDownLatch(1).await() }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertTrue(helpers.closeAndJoin())
    }

    @Test fun uninterruptibleCleanupIsReportedUncertainWithoutUnboundedJoin() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val helpers = TerminalHelpers({}, 30)
        val task = helpers.start("cleanup") { entered.countDown(); release.await() }
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(helpers.closeAndJoin())
        } finally { release.countDown() }
        task.get(1, TimeUnit.SECONDS)
        assertTrue(helpers.closeAndJoin())
    }

    @Test fun joinRestoresOwnerInterruptAfterStillCleaningHelpers() {
        val helpers = TerminalHelpers({}, 1_000)
        helpers.start("cleanup") { Unit }
        Thread.currentThread().interrupt()
        try {
            assertTrue(helpers.closeAndJoin())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}
