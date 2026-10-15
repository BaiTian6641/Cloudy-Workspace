package dev.cloudy.qualification.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpRelativeMotionTest {
    @Test
    fun fractionalSamplesAccumulateWithoutFramebufferScalingOrSignLoss() {
        val motion = RdpRelativeMotion()
        repeat(3) { assertTrue(checkNotNull(motion.append(listOf(0.25 to -0.25))).isEmpty()) }
        assertEquals(listOf(RdpRelativeStep(1, -1)), motion.append(listOf(0.25 to -0.25)))
        motion.append(listOf(0.75 to -0.75))
        motion.reset()
        assertTrue(checkNotNull(motion.append(listOf(0.25 to -0.25))).isEmpty())
    }

    @Test
    fun signedSixteenBitSplittingPreservesBothTotals() {
        val steps = checkNotNull(RdpRelativeMotion().append(listOf(70000.0 to -70000.0)))
        assertEquals(70000L, steps.sumOf { it.dx.toLong() })
        assertEquals(-70000L, steps.sumOf { it.dy.toLong() })
        assertTrue(steps.all { it.dx in -32768..32767 && it.dy in -32768..32767 })
        assertEquals(listOf(RdpRelativeStep(-32768, 32767)), RdpRelativeMotion().append(listOf(-32768.0 to 32767.0)))
    }

    @Test
    fun historyOrderIsPreservedRatherThanCollapsedAcrossTransitions() {
        assertEquals(listOf(RdpRelativeStep(2, 1), RdpRelativeStep(-3, -2)),
            RdpRelativeMotion().append(listOf(2.0 to 1.0, -3.0 to -2.0)))
    }

    @Test
    fun invalidOrOversizedBatchesAreAtomicAndCannotOverflow() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE)) {
            val motion = RdpRelativeMotion()
            motion.append(listOf(0.75 to 0.0))
            assertNull(motion.append(listOf(5.0 to 0.0, invalid to 0.0)))
            assertEquals(listOf(RdpRelativeStep(1, 0)), motion.append(listOf(0.25 to 0.0)))
        }
        assertNull(RdpRelativeMotion().append(List(RdpRelativeMotion.MAX_SAMPLES + 1) { 0.0 to 0.0 }))
        assertNull(RdpRelativeMotion().append(List(RdpRelativeMotion.MAX_STEPS + 1) { 1.0 to 0.0 }))
        assertEquals(RdpRelativeMotion.MAX_STEPS,
            checkNotNull(RdpRelativeMotion().append(listOf(32767.0 * RdpRelativeMotion.MAX_STEPS to 0.0))).size)
        assertNull(RdpRelativeMotion().append(listOf(32767.0 * (RdpRelativeMotion.MAX_STEPS + 1) to 0.0)))
    }

    @Test
    fun pendingReleaseChordPrefixesDoNotReplayAfterOwnershipLoss() {
        val pending = RdpPendingModifiers()
        pending.press(113); pending.press(57); pending.press(59); pending.press(113)
        pending.clear() // Local release chord or focus/capture loss, before any remote action.
        assertTrue(pending.drain().isEmpty())
        assertTrue(!pending.release(113))
        pending.press(113); pending.press(59)
        assertEquals(listOf(113, 59), pending.drain()) // A real remote action flushes once, in order.
        assertTrue(pending.drain().isEmpty())
    }
}
