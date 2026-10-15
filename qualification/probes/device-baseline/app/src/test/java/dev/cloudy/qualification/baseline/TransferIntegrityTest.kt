package dev.cloudy.qualification.baseline

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real temporary files and test-only streams; no HTTPS, UIDT or Android disk qualification. */
class TransferIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun part(name: String = "attempt.part") = File(temporary.root, name)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun payload(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }
    private fun failure(code: String, action: () -> Unit) {
        assertEquals(code, assertThrows(TransferFailure::class.java) { action() }.code)
    }

    @Test
    fun completeCopyIsVerifiedByRereadingTheExactPrivateFile() {
        val bytes = payload(65573)
        val file = part()
        val progress = mutableListOf<Long>()
        TransferIntegrity.copy(ByteArrayInputStream(bytes), file, bytes.size.toLong(), {}, progress::add)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> after > before })
        val verified = TransferIntegrity.verify(file, bytes.size.toLong(), hash(bytes), {})
        assertEquals(TransferDigest(bytes.size.toLong(), hash(bytes)), verified)
    }

    @Test
    fun anExistingPartIsNeverTruncatedOrConsumedAsANewDownload() {
        val file = part()
        val original = byteArrayOf(9, 8, 7)
        file.writeBytes(original)
        var reads = 0
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                return super.read(buffer, offset, length)
            }
        }
        failure("part-already-exists") { TransferIntegrity.copy(input, file, 3, {}, {}) }
        assertEquals(0, reads)
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun shortCopyKeepsPartialBytesAndCannotReportSuccess() {
        val file = part()
        val bytes = payload(17)
        val progress = mutableListOf<Long>()
        failure("payload-short") {
            TransferIntegrity.copy(ByteArrayInputStream(bytes), file, 18, {}, progress::add)
        }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf(17L), progress)
    }

    @Test
    fun oversizedChunkIsRejectedBeforeItCanExceedTheExpectedFileBound() {
        val file = part()
        val bytes = payload(65544)
        val progress = mutableListOf<Long>()
        failure("payload-exceeds-expected") {
            TransferIntegrity.copy(ByteArrayInputStream(bytes), file, 65543, {}, progress::add)
        }
        assertEquals(65536L, file.length())
        assertArrayEquals(bytes.copyOf(65536), file.readBytes())
        assertEquals(listOf(65536L), progress)
    }

    @Test
    fun cancellationArrivingDuringReadPreventsWritingTheReturnedBytes() {
        val file = part()
        var cancelled = false
        val input = object : ByteArrayInputStream(payload(17)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val result = super.read(buffer, offset, length)
                cancelled = true
                return result
            }
        }
        failure("test-cancelled") {
            TransferIntegrity.copy(input, file, 17,
                { if (cancelled) throw TransferFailure("test-cancelled") },
                { error("A chunk returned after cancellation must not be written") })
        }
        assertTrue(file.exists())
        assertEquals(0L, file.length())
    }

    @Test
    fun cancellationAfterProgressPreservesOnlyTheWrittenPrefix() {
        val bytes = payload(131072)
        val file = part()
        var cancelled = false
        failure("test-cancelled") {
            TransferIntegrity.copy(ByteArrayInputStream(bytes), file, bytes.size.toLong(),
                { if (cancelled) throw TransferFailure("test-cancelled") },
                { cancelled = true })
        }
        assertArrayEquals(bytes.copyOf(65536), file.readBytes())
    }

    @Test
    fun readFailurePropagatesWithoutReplacingAnExistingWrittenPrefix() {
        val bytes = payload(65536)
        val file = part()
        var reads = 0
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (++reads > 1) throw IOException("test-only read failure")
                return super.read(buffer, offset, length)
            }
        }
        assertThrows(IOException::class.java) { TransferIntegrity.copy(input, file, 65537, {}, {}) }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun verificationRejectsBothWrongSizeAndSameSizeCorruption() {
        val bytes = payload(17)
        val file = part().apply { writeBytes(bytes) }
        failure("file-size-mismatch") { TransferIntegrity.verify(file, 18, hash(bytes), {}) }
        val corrupted = bytes.copyOf().apply { this[5] = (this[5].toInt() xor 1).toByte() }
        file.writeBytes(corrupted)
        failure("sha256-mismatch") { TransferIntegrity.verify(file, 17, hash(bytes), {}) }
        assertArrayEquals(corrupted, file.readBytes())
    }

    @Test
    fun verificationDetectsGrowthOrTruncationAfterTheInitialLengthCheck() {
        val bytes = payload(17)
        val growing = part("growing.part").apply { writeBytes(bytes) }
        var grew = false
        failure("verification-size-exceeded") {
            TransferIntegrity.verify(growing, 17, hash(bytes)) {
                if (!grew) { grew = true; growing.appendBytes(byteArrayOf(1)) }
            }
        }
        val shrinking = part("shrinking.part").apply { writeBytes(bytes) }
        var shrank = false
        failure("verification-size-mismatch") {
            TransferIntegrity.verify(shrinking, 17, hash(bytes)) {
                if (!shrank) {
                    shrank = true
                    RandomAccessFile(shrinking, "rw").use { it.setLength(16) }
                }
            }
        }
    }

    @Test
    fun cancellationDuringVerificationNeverReturnsADigestOrChangesTheFile() {
        val bytes = payload(131072)
        val file = part().apply { writeBytes(bytes) }
        var checks = 0
        failure("test-cancelled") {
            TransferIntegrity.verify(file, bytes.size.toLong(), hash(bytes)) {
                if (++checks == 2) throw TransferFailure("test-cancelled")
            }
        }
        assertEquals(2, checks)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun cancellationAfterVerificationEofStillPreventsSuccessfulReturn() {
        val bytes = byteArrayOf(1, 2, 3)
        val file = part().apply { writeBytes(bytes) }
        var checks = 0
        failure("test-cancelled") {
            TransferIntegrity.verify(file, 3, hash(bytes)) {
                if (++checks == 3) throw TransferFailure("test-cancelled")
            }
        }
        assertEquals(3, checks)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun invalidExpectedLengthCannotCreateAnOutputFile() {
        listOf(0L, -1L, TransferConfigSpec.MAX_BYTES + 1).forEachIndexed { index, length ->
            val file = part("invalid-$index.part")
            assertThrows(IllegalArgumentException::class.java) {
                TransferIntegrity.copy(ByteArrayInputStream(byteArrayOf(1)), file, length, {}, {})
            }
            assertFalse(file.exists())
        }
    }
}
