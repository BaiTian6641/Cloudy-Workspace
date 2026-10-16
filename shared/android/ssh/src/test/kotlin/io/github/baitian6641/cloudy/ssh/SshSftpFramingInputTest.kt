package io.github.baitian6641.cloudy.ssh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class SshSftpFramingInputTest {
    private fun packet(vararg bytes: Int): ByteArray = byteArrayOf(0, 0, 0, bytes.size.toByte()) +
        ByteArray(bytes.size) { bytes[it].toByte() }

    @Test fun acceptedPacketsPreserveEveryByteAcrossMixedSingleAndBulkReads() {
        val expected = packet(1, 2, 3) + packet(255) + packet(4, 5, 6, 7)
        val guarded = SshSftpFramingInput(ByteArrayInputStream(expected), 4)
        val actual = ByteArrayOutputStream()
        val scratch = ByteArray(7)
        var single = true
        while (true) {
            if (single) {
                val value = guarded.read()
                if (value < 0) break
                actual.write(value)
            } else {
                val count = guarded.read(scratch, 2, 4)
                if (count < 0) break
                actual.write(scratch, 2, count)
            }
            single = !single
        }
        assertArrayEquals(expected, actual.toByteArray())
    }

    @Test fun oversizedUnsignedLengthFailsBeforeFourthByteIsPublished() {
        for (header in listOf(byteArrayOf(0, 4, 0, 1), byteArrayOf(127, -1, -1, -1),
                byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 0, 0, 0))) {
            val guarded = SshSftpFramingInput(ByteArrayInputStream(header + byteArrayOf(99)))
            val target = ByteArray(4) { 42 }
            assertThrows(IOException::class.java) { guarded.read(target, 0, 4) }
            assertEquals("Invalid complete prefix never escapes", 42.toByte(), target[3])
            assertThrows(IOException::class.java) { guarded.read() }
        }
    }

    @Test fun truncatedHeaderAndBodyFailRatherThanLookLikeCompleteFrames() {
        for (bytes in listOf(byteArrayOf(0), byteArrayOf(0, 0, 0), byteArrayOf(0, 0, 0, 2, 1))) {
            val guarded = SshSftpFramingInput(ByteArrayInputStream(bytes))
            assertThrows(IOException::class.java) { while (guarded.read() >= 0) { /* Read to actual truncation. */ } }
        }
        assertEquals(-1, SshSftpFramingInput(ByteArrayInputStream(byteArrayOf())).read())
    }

    @Test fun fragmentedAndZeroProgressBulkInputStillPreservesFraming() {
        val expected = packet(1, 2, 3, 4) + packet(5)
        val delegate = object : ByteArrayInputStream(expected) {
            private var empty = true
            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                if (empty) { empty = false; return 0 }
                return super.read(target, offset, minOf(length, 1))
            }
        }
        assertArrayEquals(expected, SshSftpFramingInput(delegate).readBytes())
    }

    @Test fun closeAndDelegateFailureAreStickyAndDoNotExposeExceptionContent() {
        var closes = 0
        val delegate = object : InputStream() {
            override fun read(): Int = throw IOException("REMOTE_PRIVATE_TEXT")
            override fun close() { closes++ }
        }
        val guarded = SshSftpFramingInput(delegate)
        val failure = assertThrows(IOException::class.java) { guarded.read() }
        assertEquals("SFTP_READ_FAILED", failure.message)
        assertNull(failure.cause)
        assertThrows(IOException::class.java) { guarded.read() }
        guarded.close(); guarded.close()
        assertEquals(1, closes)
        assertThrows(IOException::class.java) { guarded.read() }
    }
}
