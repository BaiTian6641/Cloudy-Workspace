package io.github.baitian6641.cloudy.desktop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure conversion/storage boundary only; does not claim Canvas or remote presentation execution. */
class VncFramePixelsTest {
    @Test fun rgbaChannelsAlphaAndSecondRowAreExact() {
        val source = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -128, 0, 0, -1, 0, -1, -1, -1, -1)
        val row = IntArray(2)
        VncFramePixels.row(source, 0, row)
        assertArrayEquals(intArrayOf(0xffff0000.toInt(), 0x8000ff00.toInt()), row)
        VncFramePixels.row(source, 8, row)
        assertArrayEquals(intArrayOf(0x000000ff, 0xffffffff.toInt()), row)
    }
    @Test fun rejectsMalformedDimensionsAndExcessiveStorage() {
        assertTrue(VncFramePixels.valid(4096, 2048, 33554432))
        assertFalse(VncFramePixels.valid(4096, 4096, 67108864))
        assertFalse(VncFramePixels.valid(0, 2, 0))
        assertFalse(VncFramePixels.valid(2, 2, 15))
        assertFalse(VncFramePixels.valid(Int.MAX_VALUE, Int.MAX_VALUE, 0))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedRow() { VncFramePixels.row(ByteArray(7), 0, IntArray(2)) }
}
