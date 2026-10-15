package dev.cloudy.qualification.baseline

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Fixed qualification wire grammar only; this is neither a terminal emulator nor a shell parser. */
internal object SshPtyProtocol {
    const val EXEC_COMMAND = "cloudy-exec-probe-v1"
    const val EXEC_STDOUT = "Cloudy exec stdout\n"
    const val EXEC_STDERR = "Cloudy exec stderr\n"
    const val EXEC_EXIT = 17
    const val READY = "CLOUDY_PTY_READY_V1\nSIZE 80 24\n"
    const val RESIZED = "SIZE 132 43\n"
    const val DONE = "CLOUDY_PTY_DONE_V1\n"
    const val SIZE_INPUT = "SIZE\n"
    const val FINISH_INPUT = "FINISH\n"
    const val OUTPUT_LIMIT = 4_096

    fun readExactly(input: InputStream, expected: String, checkActive: () -> Unit): ByteArray {
        val wanted = expected.toByteArray(Charsets.US_ASCII)
        require(wanted.isNotEmpty() && wanted.size <= OUTPUT_LIMIT)
        val actual = ByteArray(wanted.size)
        var offset = 0
        while (offset < actual.size) {
            checkActive()
            val count = input.read(actual, offset, actual.size - offset)
            if (count == -1) throw SshProbeFailure("pty-unexpected-eof")
            if (count <= 0 || count > actual.size - offset) throw SshProbeFailure("pty-invalid-read-count")
            for (i in offset until offset + count) {
                if (actual[i] != wanted[i]) throw SshProbeFailure("pty-protocol-mismatch")
            }
            offset += count
        }
        checkActive()
        return actual
    }

    fun requireEof(input: InputStream, checkActive: () -> Unit) {
        checkActive()
        if (input.read() != -1) throw SshProbeFailure("pty-extra-data-or-missing-eof")
        checkActive()
    }

    /** Read both exec streams concurrently in the engine; the cap is enforced before appending. */
    fun drain(input: InputStream, checkActive: () -> Unit): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (true) {
            checkActive()
            val requested = minOf(buffer.size, OUTPUT_LIMIT + 1 - result.size())
            val count = input.read(buffer, 0, requested)
            if (count == -1) break
            if (count <= 0 || count > requested) throw SshProbeFailure("channel-invalid-read-count")
            if (result.size() + count > OUTPUT_LIMIT) throw SshProbeFailure("channel-output-limit")
            result.write(buffer, 0, count)
        }
        checkActive()
        return result.toByteArray()
    }

    fun requireBytes(actual: ByteArray, expected: String, failureCode: String) {
        if (!actual.contentEquals(expected.toByteArray(Charsets.US_ASCII))) throw SshProbeFailure(failureCode)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
