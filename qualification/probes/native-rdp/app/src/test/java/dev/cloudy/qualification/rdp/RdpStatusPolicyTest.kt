package dev.cloudy.qualification.rdp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpStatusPolicyTest {
    private val connected = RdpStatusPolicy.Identity("attempt-a", "connected", "Ready", "3.32.2", 1280, 720, false, true)

    @Test
    fun counterBurstKeepsLatestOpportunityWithoutMovingTheDeadline() {
        val policy = RdpStatusPolicy()
        assertTrue(policy.shouldRender(connected, 1, 0, 1000))
        for (elapsed in 1L..249L) assertFalse(policy.shouldRender(connected, elapsed + 1, elapsed, 1000 + elapsed))
        assertTrue(policy.shouldRender(connected, 300, 299, 1250))
        assertFalse(policy.shouldRender(connected, 300, 299, 1500))
        assertTrue(policy.shouldRender(connected, 301, 300, 1501))
    }

    @Test
    fun terminalErrorAndNewAttemptCannotWaitBehindCounterThrottle() {
        val policy = RdpStatusPolicy()
        assertTrue(policy.shouldRender(connected, 400, 20, 1000))
        val failed = connected.copy(state = "failed", detail = "Native failure", connected = false)
        assertTrue(policy.shouldRender(failed, 400, 20, 1001))
        assertFalse(policy.shouldRender(failed, 400, 20, 1002))
        assertTrue(policy.shouldRender(connected.copy(attemptId = "attempt-b"), 0, 0, 1003))
    }

    @Test
    fun GeometryAndConnectionChangesAreImmediateAndRecreatedPolicyStartsFresh() {
        val policy = RdpStatusPolicy()
        assertTrue(policy.shouldRender(connected, 1, 1, 1000))
        assertTrue(policy.shouldRender(connected.copy(width = 1920, height = 1080), 2, 1, 1001))
        assertTrue(policy.shouldRender(connected.copy(busy = true, connected = false), 2, 1, 1002))
        assertTrue(RdpStatusPolicy().shouldRender(connected, 2, 1, 1002))
    }
}
