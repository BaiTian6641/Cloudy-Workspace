package dev.cloudy.qualification.baseline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the production ticket/lease policy; it makes no socket or OS-survival claim. */
class SshRetentionPolicyTest {
    private val first = "00000000-0000-0000-0000-000000000001"
    private val second = "00000000-0000-0000-0000-000000000002"

    @Test fun oneUseRequiresUnexpiredMatchingRequestAndMonotonicClock() {
        val policy = SshRetentionPolicy()
        assertTrue(policy.reserve(first, 100))
        assertFalse(policy.reserve(second, 101))
        assertFalse(policy.consume(second, 101))
        assertFalse(policy.consume(first, 99))
        assertTrue(policy.consume(first, 101))
        assertFalse(policy.consume(first, 102))
        assertTrue(policy.retained(first))
    }

    @Test fun deadlineCannotPromoteAndAbandonDoesNotQuarantine() {
        val policy = SshRetentionPolicy()
        assertTrue(policy.reserve(first, 100))
        assertFalse(policy.consume(first, 10_100))
        assertTrue(policy.abandonPending(first))
        assertFalse(policy.quarantined())
        assertTrue(policy.reserve(second, 10_101))
        assertFalse(policy.revoke(first))
        assertTrue(policy.consume(second, 10_102))
    }

    @Test fun onlyPromotedLeaseChangesLastWindowBehaviorNotObserverCount() {
        val policy = SshRetentionPolicy()
        assertTrue(policy.shouldCancelLastWindow(0, false))
        assertFalse(policy.shouldCancelLastWindow(1, false))
        assertFalse(policy.shouldCancelLastWindow(0, true))
        assertTrue(policy.reserve(first, 0))
        assertTrue(policy.shouldCancelLastWindow(0, false))
        assertTrue(policy.consume(first, 1))
        // There are still zero visible windows: a service observer is not a window.
        assertFalse(policy.shouldCancelLastWindow(0, false))
        assertTrue(policy.revoke(first))
        assertFalse(policy.retained(first))
        assertTrue(policy.shouldCancelLastWindow(0, false))
        assertFalse(policy.abandonPending(first))
        assertFalse(policy.reserve(second, 2))
        assertTrue(policy.completed(first, true, true))
        assertTrue(policy.reserve(second, 3))
    }

    @Test fun cleanupOrEvidenceUncertaintyPermanentlyQuarantinesThisOwner() {
        listOf(false to true, true to false, false to false).forEach { (cleanup, evidence) ->
            val policy = SshRetentionPolicy()
            assertTrue(policy.reserve(first, 0))
            assertTrue(policy.consume(first, 1))
            assertFalse(policy.completed(second, true, true))
            assertTrue(policy.completed(first, cleanup, evidence))
            assertTrue(policy.quarantined())
            assertFalse(policy.retained(first))
            assertFalse(policy.reserve(second, 2))
            assertFalse(policy.completed(first, true, true))
            assertTrue(policy.quarantined())
        }
    }

    @Test fun successfulCompletionAndStaleCallbacksCannotRevokeNewAttempt() {
        val policy = SshRetentionPolicy()
        assertTrue(policy.reserve(first, 0))
        assertTrue(policy.consume(first, 1))
        assertTrue(policy.completed(first, true, true))
        assertTrue(policy.reserve(second, 2))
        assertTrue(policy.consume(second, 3))
        assertFalse(policy.revoke(first))
        assertFalse(policy.abandonPending(first))
        assertFalse(policy.completed(first, false, false))
        assertTrue(policy.retained(second))
        assertFalse(policy.quarantined())
    }

    @Test fun invalidIdentifiersAndOverflowCannotCreateAdmission() {
        val policy = SshRetentionPolicy()
        assertFalse(policy.reserve("not-a-uuid", 0))
        assertFalse(policy.reserve(first, -1))
        assertFalse(policy.reserve(first, Long.MAX_VALUE))
        assertFalse(policy.hasRequest())
    }
}
