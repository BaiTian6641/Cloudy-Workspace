package dev.cloudy.qualification.rdp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpTextPolicyTest {
    @Test
    fun bmpAndProperSupplementaryPairsPassWithoutClaimingServerSupport() {
        assertTrue(RdpTextPolicy.accepts("Rdp-é中"))
        assertTrue(RdpTextPolicy.accepts("a\uD83D\uDE00b"))
        assertTrue(RdpTextPolicy.accepts("\uD800\uDC00\uDBFF\uDFFF"))
    }

    @Test
    fun malformedPairsAndEmbeddedNulAreRejectedBeforeEnqueue() {
        for (value in listOf("\uD800", "\uDC00", "a\uD800b", "\uDC00\uD800",
            "\uD800\uD800\uDC00", "\uD800\uDC00\uDC00", "a\u0000b")) {
            assertFalse(RdpTextPolicy.accepts(value))
        }
    }

    @Test
    fun boundCountsUtf16UnitsAndCannotSplitASupplementaryPair() {
        assertFalse(RdpTextPolicy.accepts(""))
        assertTrue(RdpTextPolicy.accepts("a".repeat(256)))
        assertFalse(RdpTextPolicy.accepts("a".repeat(257)))
        assertTrue(RdpTextPolicy.accepts("\uD83D\uDE00".repeat(128)))
        assertFalse(RdpTextPolicy.accepts("\uD83D\uDE00".repeat(128) + "a"))
        assertFalse(RdpTextPolicy.accepts("a".repeat(255) + "\uD83D"))
    }
}
