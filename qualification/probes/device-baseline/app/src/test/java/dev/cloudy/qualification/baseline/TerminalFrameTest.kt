package dev.cloudy.qualification.baseline

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Binary boundary checks, not Android/WebView interoperability evidence. */
class TerminalFrameTest {
    private val identity = TerminalIdentity("00112233-4455-6677-8899-aabbccddeeff", "ffeeddcc-bbaa-9988-7766-554433221100")

    @Test fun canonicalNetworkHeaderAndFragmentedUtf8BytesRoundTrip() {
        val partialUtf8 = byteArrayOf(0xe4.toByte(), 0xb8.toByte())
        val encoded = TerminalFrame(TerminalFrame.OUTPUT, 7, partialUtf8, true).encode(identity)
        assertEquals("435754520105000100112233445566778899aabbccddeeffffeeddccbbaa998877665544332211000000000700000002e4b8",
            encoded.joinToString("") { "%02x".format(it.toInt() and 255) })
        val decoded = TerminalFrame.decode(encoded, identity)
        assertEquals(7, decoded.sequence)
        assertEquals(true, decoded.isStderr)
        assertArrayEquals(partialUtf8, decoded.payload)
    }

    @Test fun alteredVersionFlagsIdentityAndDeclaredLengthAreRejected() {
        val original = TerminalFrame(TerminalFrame.INPUT, 1, byteArrayOf(0, 127, -1)).encode(identity)
        for (offset in listOf(0, 4, 6, 7, 8, 24, 44, 47)) {
            val changed = original.copyOf()
            changed[offset] = (changed[offset].toInt() xor 4).toByte()
            assertThrows(IllegalArgumentException::class.java) { TerminalFrame.decode(changed, identity) }
        }
        assertThrows(IllegalArgumentException::class.java) { TerminalFrame.decode(original + 0.toByte(), identity) }
        assertThrows(IllegalArgumentException::class.java) { TerminalFrame.decode(original.copyOf(47), identity) }
    }

    @Test fun exactWholeInputAndOutputBoundsAreEnforcedBeforeEncoding() {
        for ((type, length) in listOf(TerminalFrame.INPUT to 65536, TerminalFrame.OUTPUT to 16384)) {
            val frame = TerminalFrame(type, 1, ByteArray(length) { it.toByte() })
            assertArrayEquals(frame.payload, TerminalFrame.decode(frame.encode(identity), identity).payload)
            for (bad in listOf(0, length + 1)) assertThrows(IllegalArgumentException::class.java) {
                TerminalFrame(type, 1, ByteArray(bad)).encode(identity)
            }
        }
    }

    @Test fun negativeZeroAndOverflowSequenceCannotEnterProtocol() {
        for (n in listOf(-1, 0, Int.MIN_VALUE)) assertThrows(IllegalArgumentException::class.java) {
            TerminalFrame(TerminalFrame.INPUT, n, byteArrayOf(1)).encode(identity)
        }
        val bytes = TerminalFrame(TerminalFrame.INPUT, 1, byteArrayOf(1)).encode(identity)
        ByteBuffer.wrap(bytes).putInt(40, Int.MIN_VALUE)
        assertThrows(IllegalArgumentException::class.java) { TerminalFrame.decode(bytes, identity) }
    }

    @Test fun resizeRejectsOutOfRangeValuesInsteadOfClamping() {
        val frame = TerminalFrame(TerminalFrame.RESIZE, 2, TerminalFrame.sizePayload(TerminalSize(132, 43)))
        assertEquals(TerminalSize(132, 43), TerminalFrame.decode(frame.encode(identity), identity).size())
        for ((columns, rows) in listOf(1 to 24, 513 to 24, 80 to 0, 80 to 257)) {
            val payload = ByteBuffer.allocate(4).putShort(columns.toShort()).putShort(rows.toShort()).array()
            assertThrows(IllegalArgumentException::class.java) { TerminalFrame(TerminalFrame.READY, 1, payload).size() }
        }
    }

    @Test fun unknownOperationsStderrOnInputAndMalformedControlLengthsAreRejected() {
        for (frame in listOf(TerminalFrame(99, 1, byteArrayOf(1)),
            TerminalFrame(TerminalFrame.INPUT, 1, byteArrayOf(1), true),
            TerminalFrame(TerminalFrame.ACK, 1, ByteArray(7)),
            TerminalFrame(TerminalFrame.CLOSED, 1, byteArrayOf(1)),
            TerminalFrame(TerminalFrame.ERROR, 1, byteArrayOf(2)))) {
            assertThrows(IllegalArgumentException::class.java) { frame.encode(identity) }
        }
    }

    @Test fun admissionBindsOriginalRequestAndPreservesDistinctResult() {
        val request = TerminalFrame(TerminalFrame.INPUT, 31, byteArrayOf(1))
        val buffer = ByteBuffer.wrap(TerminalFrame.admissionPayload(request, TerminalAdmission.FULL))
        assertEquals(31, buffer.int)
        assertEquals(TerminalFrame.INPUT.toByte(), buffer.get())
        assertEquals(TerminalAdmission.FULL.ordinal.toByte(), buffer.get())
        assertEquals(0.toShort(), buffer.short)
    }
}
