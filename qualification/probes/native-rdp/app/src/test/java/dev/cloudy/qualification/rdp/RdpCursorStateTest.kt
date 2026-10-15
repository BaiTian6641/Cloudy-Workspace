package dev.cloudy.qualification.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpCursorStateTest {
    @Test fun defaultCustomAndHiddenRemainDistinctAcrossCapture() {
        val cursor = RdpCursorState()
        cursor.resize(1280, 720)
        assertFalse(cursor.softwareVisible())
        cursor.capture(true)
        assertTrue(cursor.softwareVisible())
        cursor.shape(RdpCursorMode.HIDDEN)
        assertFalse(cursor.softwareVisible())
        cursor.admittedRelative(20, 30)
        assertFalse(cursor.softwareVisible())
        cursor.capture(false)
        assertFalse(cursor.softwareVisible())
        cursor.shape(RdpCursorMode.CUSTOM)
        assertTrue(cursor.softwareVisible())
    }

    @Test fun admittedMotionIsClampedWithoutIntegerOverflowAndNeverChangesTheAnchor() {
        val cursor = RdpCursorState()
        assertFalse(cursor.admittedRelative(10, 10))
        cursor.resize(1280, 720)
        assertEquals(RdpCursorSource.INITIAL, cursor.source)
        assertEquals(640, cursor.x)
        cursor.absolute(100, 200)
        cursor.capture(true)
        assertTrue(cursor.admittedRelative(Int.MAX_VALUE, Int.MIN_VALUE))
        assertEquals(1279, cursor.x)
        assertEquals(0, cursor.y)
        assertEquals(RdpCursorSource.PREDICTED, cursor.source)
        cursor.capture(false)
        assertEquals(100, cursor.x)
        assertEquals(200, cursor.y)
        assertEquals(RdpCursorSource.ABSOLUTE, cursor.source)
        assertFalse(cursor.admittedRelative(1, 1))
    }

    @Test fun serverCorrectionSupersedesPredictionAndRejectsInvalidGeometryWithoutMutation() {
        val cursor = RdpCursorState()
        cursor.resize(100, 50); cursor.capture(true); cursor.admittedRelative(10, 10)
        assertTrue(cursor.server(7, 9))
        assertEquals(RdpCursorSource.SERVER, cursor.source)
        assertFalse(cursor.server(-1, 9))
        assertFalse(cursor.server(100, 0))
        assertEquals(7, cursor.x)
        cursor.admittedRelative(-2, 3)
        assertEquals(5, cursor.x)
        assertEquals(12, cursor.y)
        cursor.capture(false); cursor.capture(true)
        assertEquals(7, cursor.x)
        assertEquals(9, cursor.y)
    }

    @Test fun correctionOrOwnershipLossCrossingAdmissionWinsWithoutDoubleApplyingDelta() {
        val cursor = RdpCursorState()
        cursor.resize(100, 50); cursor.capture(true)
        val beforeCorrection = cursor.revision
        cursor.server(70, 30)
        assertFalse(cursor.admittedRelative(20, 0, beforeCorrection))
        assertEquals(70, cursor.x)
        val beforeCancellation = cursor.revision
        cursor.capture(false); cursor.capture(true)
        assertFalse(cursor.admittedRelative(20, 0, beforeCancellation))
        assertEquals(70, cursor.x)
    }

    @Test fun resizeAndDisconnectCannotRetainOldCoordinatesOrHiddenMode() {
        val cursor = RdpCursorState()
        cursor.resize(1280, 720); cursor.server(1279, 719); cursor.capture(true)
        cursor.resize(720, 1280)
        assertEquals(360, cursor.x)
        assertEquals(640, cursor.y)
        assertEquals(RdpCursorSource.INITIAL, cursor.source)
        cursor.shape(RdpCursorMode.HIDDEN); cursor.clear()
        assertEquals(RdpCursorMode.DEFAULT, cursor.mode)
        assertFalse(cursor.captured)
        assertFalse(cursor.server(0, 0))
        assertFalse(cursor.admittedRelative(1, 1))
    }
}
