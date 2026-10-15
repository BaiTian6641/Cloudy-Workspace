package dev.cloudy.qualification.rdp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpFrameGeometryTest {
    @Test
    fun observedWorkspaceFitsTheSingleBitmapBudgetInEitherOrientation() {
        assertTrue(RdpFrameGeometry.accepts(2880, 1800))
        assertTrue(RdpFrameGeometry.accepts(1800, 2880))
    }

    @Test
    fun exactBudgetFitsAndTheNextRowDoesNot() {
        assertTrue(RdpFrameGeometry.accepts(4096, 1280))
        assertTrue(RdpFrameGeometry.accepts(1280, 4096))
        assertFalse(RdpFrameGeometry.accepts(4096, 1281))
        assertFalse(RdpFrameGeometry.accepts(1281, 4096))
    }

    @Test
    fun invalidAndOverflowProneDimensionsAreRejectedBeforeAllocation() {
        for ((width, height) in listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1,
            -1 to -1, Int.MIN_VALUE to Int.MIN_VALUE, Int.MAX_VALUE to Int.MAX_VALUE,
            Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE, 4097 to 1, 1 to 4097)) {
            assertFalse("Unexpected accepted dimensions $width x $height", RdpFrameGeometry.accepts(width, height))
        }
        assertTrue(RdpFrameGeometry.accepts(4096, 1))
        assertTrue(RdpFrameGeometry.accepts(1, 4096))
    }

    @Test
    fun actualAllocationMustFitEvenWhenNominalGeometryFits() {
        val budget = RdpFrameGeometry.BITMAP_BUDGET_BYTES.toInt()
        assertTrue(RdpFrameGeometry.acceptsAllocation(budget))
        assertFalse(RdpFrameGeometry.acceptsAllocation(budget + 1))
        assertFalse(RdpFrameGeometry.acceptsAllocation(0))
        assertFalse(RdpFrameGeometry.acceptsAllocation(-1))
        assertFalse(RdpFrameGeometry.acceptsAllocation(Int.MAX_VALUE))
    }
}
