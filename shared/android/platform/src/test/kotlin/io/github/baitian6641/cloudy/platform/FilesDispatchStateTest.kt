package io.github.baitian6641.cloudy.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Policy-only race checks. Actual BAL/Activity behavior requires the signed sibling device lane. */
class FilesDispatchStateTest {
    @Test fun cancelledOrExpiredPendingWorkCannotLaunchLater() {
        val cancelled = FilesDispatchState()
        assertTrue(cancelled.cancelPending())
        assertFalse(cancelled.begin(true))
        assertEquals(FilesSubmitStatus.ADMITTED_NOT_DISPATCHED, cancelled.status())
        val expired = FilesDispatchState()
        assertFalse(expired.begin(false))
        assertFalse(expired.begin(true))
    }

    @Test fun timeoutCannotCancelAnActualInFlightLaunch() {
        val state = FilesDispatchState()
        assertTrue(state.begin(true))
        assertFalse(state.cancelPending())
        assertEquals(FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED, state.status())
        state.returned(true)
        assertEquals(FilesSubmitStatus.DISPATCH_REQUESTED, state.status())
        assertFalse(state.begin(true))
    }

    @Test fun launchFailureDoesNotClaimDelivery() {
        val state = FilesDispatchState()
        assertTrue(state.begin(true))
        state.returned(false)
        assertEquals(FilesSubmitStatus.ADMITTED_NOT_DISPATCHED, state.status())
    }

    @Test fun unexpectedReturnedFailureRetainsDeliveryUncertainty() {
        val state = FilesDispatchState()
        assertTrue(state.begin(true))
        state.returnedUnconfirmed()
        assertEquals(FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED, state.status())
        assertFalse(state.cancelPending())
        assertFalse(state.begin(true))
    }

    @Test fun competingDeadlineAndMainAdmissionHaveOneWinner() {
        repeat(100) {
            val state = FilesDispatchState()
            val start = CountDownLatch(1)
            val winners = AtomicInteger()
            val launch = Thread { start.await(); if (state.begin(true)) winners.incrementAndGet() }
            val cancel = Thread { start.await(); if (state.cancelPending()) winners.incrementAndGet() }
            launch.start(); cancel.start(); start.countDown()
            launch.join(1_000); cancel.join(1_000)
            assertFalse(launch.isAlive || cancel.isAlive)
            assertEquals(1, winners.get())
        }
    }
}
