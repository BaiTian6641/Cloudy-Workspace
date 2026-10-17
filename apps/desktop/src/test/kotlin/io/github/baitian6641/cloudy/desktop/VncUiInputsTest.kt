package io.github.baitian6641.cloudy.desktop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unrun pure local encoding boundaries; no authentication or native-field lifecycle claim. */
class VncUiInputsTest {
    @Test fun exactAsciiHexPinHasCanonicalBytesAndRejectsLookalikes() {
        val lower = "ab".repeat(32)
        assertArrayEquals(ByteArray(32) { 0xab.toByte() }, VncUiInputs.decodePin(lower))
        assertArrayEquals(VncUiInputs.decodePin(lower), VncUiInputs.decodePin(lower.uppercase()))
        assertNull(VncUiInputs.decodePin("SHA256:" + lower))
        assertNull(VncUiInputs.decodePin("a:".repeat(32)))
        assertNull(VncUiInputs.decodePin("０".repeat(64)))
    }
    @Test fun utf8UsesByteCapacityAndRejectsMalformedOrNulText() {
        assertArrayEquals(byteArrayOf(0xc3.toByte(), 0xa9.toByte()), VncUiInputs.encodeSecret("é", 2))
        assertNull(VncUiInputs.encodeSecret("é", 1))
        assertNull(VncUiInputs.encodeSecret("\ud800", 256))
        assertNull(VncUiInputs.encodeSecret("u\u0000p", 256))
        assertNull(VncUiInputs.encodeSecret("", 256))
        assertNull(VncUiInputs.encodeSecret("p".repeat(1025), 1024))
    }
}
