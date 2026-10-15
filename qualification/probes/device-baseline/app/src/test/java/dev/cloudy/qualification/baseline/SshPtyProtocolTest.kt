package dev.cloudy.qualification.baseline

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Real byte-stream parsing tests only; fixed fixture bytes do not establish an SSH or PTY result. */
class SshPtyProtocolTest {
    @Test fun fragmentedExactHandshakePreservesByteBoundariesAndRequiresFinalEof() {
        val wire = SshPtyProtocol.READY + SshPtyProtocol.RESIZED + SshPtyProtocol.DONE
        val input = object : ByteArrayInputStream(wire.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 2))
        }
        for (expected in listOf(SshPtyProtocol.READY, SshPtyProtocol.RESIZED, SshPtyProtocol.DONE)) {
            assertArrayEquals(expected.toByteArray(), SshPtyProtocol.readExactly(input, expected, {}))
        }
        SshPtyProtocol.requireEof(input, {})
    }

    @Test fun alteredSizeEchoNewlineAndAdditionalPrefixAreRejected() {
        for (wire in listOf("SIZE 80 24\n", "SIZE\nSIZE 132 43\n", "SIZE 132 43\r\n", "SIZE 132 44\n")) {
            assertFailure("pty-protocol-mismatch") {
                SshPtyProtocol.readExactly(wire.byteInputStream(), SshPtyProtocol.RESIZED, {})
            }
        }
    }

    @Test fun missingBytesAndTrailingDataCannotPass() {
        assertFailure("pty-unexpected-eof") {
            SshPtyProtocol.readExactly("CLOUDY_PTY_".byteInputStream(), SshPtyProtocol.DONE, {})
        }
        assertFailure("pty-extra-data-or-missing-eof") {
            SshPtyProtocol.requireEof("extra".byteInputStream(), {})
        }
    }

    @Test fun outputCapAllowsExactBoundButRejectsOneExtraByte() {
        val exact = ByteArray(SshPtyProtocol.OUTPUT_LIMIT) { 65 }
        assertArrayEquals(exact, SshPtyProtocol.drain(ByteArrayInputStream(exact), {}))
        assertFailure("channel-output-limit") {
            SshPtyProtocol.drain(ByteArrayInputStream(exact + byteArrayOf(65)), {})
        }
    }

    @Test fun zeroProgressAndImpossibleReadCountsFailWithoutSpinning() {
        for (count in listOf(0, 300)) {
            val input = object : InputStream() {
                override fun read(): Int = error("Bulk read expected")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = count
            }
            assertFailure("channel-invalid-read-count") { SshPtyProtocol.drain(input, {}) }
        }
    }

    @Test fun cancellationBeforeReadAndBeforeAcceptanceCannotBecomeSuccess() {
        assertFailure("test-cancel") {
            SshPtyProtocol.readExactly(SshPtyProtocol.DONE.byteInputStream(), SshPtyProtocol.DONE) {
                throw SshProbeFailure("test-cancel")
            }
        }
        var checks = 0
        assertFailure("test-cancel") {
            SshPtyProtocol.requireEof(ByteArrayInputStream(byteArrayOf())) {
                if (++checks == 2) throw SshProbeFailure("test-cancel")
            }
        }
    }

    @Test fun fixedExecResultsHaveIndependentDigestsAndRejectSwappedStreams() {
        val out = SshPtyProtocol.drain(SshPtyProtocol.EXEC_STDOUT.byteInputStream(), {})
        val err = SshPtyProtocol.drain(SshPtyProtocol.EXEC_STDERR.byteInputStream(), {})
        assertEquals(19, out.size)
        assertEquals(19, err.size)
        assertEquals("66a606a71a67baef12910e3efd5c0ad4bcdeabecdc9dae790bf0f505f811438d",
            SshPtyProtocol.sha256(out))
        assertFailure("test-stream-mismatch") {
            SshPtyProtocol.requireBytes(err, SshPtyProtocol.EXEC_STDOUT, "test-stream-mismatch")
        }
    }

    private fun assertFailure(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(SshProbeFailure::class.java, block).code)
    }
}
