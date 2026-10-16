package io.github.baitian6641.cloudy.ssh

import java.io.IOException
import java.io.InputStream

/**
 * Bounds the unsigned SFTP frame prefix before SSHJ can allocate its payload buffer. SSHJ0.41.2
 * otherwise permits an inherited 1GiB maximum. This guard preserves every accepted byte and does
 * not decode SFTP messages, names or contents. One owner reads it: init, then the PacketReader.
 */
internal class SshSftpFramingInput(
    private val input: InputStream,
    private val maxFrameBytes: Int = 262_144,
) : InputStream() {
    private var headerBytes = 0
    private var prefix = 0L
    private var remaining = 0
    private var failed = false
    private var closed = false

    init { require(maxFrameBytes in 1..1_048_576) { "SFTP_FRAME_CONFIGURATION" } }

    override fun read(): Int {
        ensureOpen()
        val value = try { input.read() } catch (_: IOException) { reject("SFTP_READ_FAILED") }
        if (value == -1) {
            if (remaining != 0 || headerBytes != 0) reject("SFTP_FRAME_TRUNCATED")
            return -1
        }
        if (remaining > 0) {
            remaining--
            return value
        }
        prefix = (prefix shl 8) or value.toLong()
        headerBytes++
        if (headerBytes == 4) {
            // Do not publish the fourth prefix byte on failure: upstream cannot observe an
            // oversized complete header and allocate before this guard reports the violation.
            if (prefix !in 1..maxFrameBytes.toLong()) reject("SFTP_FRAME_BOUND")
            remaining = prefix.toInt()
            headerBytes = 0
            prefix = 0
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        ensureOpen()
        if (remaining > 0) {
            val count = try { input.read(buffer, offset, minOf(length, remaining)) }
                catch (_: IOException) { reject("SFTP_READ_FAILED") }
            if (count < 0) reject("SFTP_FRAME_TRUNCATED")
            if (count == 0) {
                // Defend against a zero-progress wrapper without letting upstream spin.
                buffer[offset] = read().toByte()
                return 1
            }
            remaining -= count
            return count
        }
        val amount = minOf(length, 4 - headerBytes)
        for (index in 0 until amount) {
            val value = read()
            if (value < 0) return if (index == 0) -1 else index
            buffer[offset + index] = value.toByte()
        }
        return amount
    }

    override fun close() {
        if (!closed) {
            closed = true
            input.close()
        }
    }

    private fun ensureOpen() {
        if (closed || failed) throw IOException("SFTP_INPUT_RETIRED")
    }
    private fun reject(code: String): Nothing {
        failed = true
        throw IOException(code)
    }
}
