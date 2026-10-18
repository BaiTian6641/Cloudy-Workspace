package io.github.baitian6641.cloudy.terminal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** A dedicated terminal byte protocol. Identities are opaque fences, never credentials. */
internal data class TerminalFrame(
    val type: Int,
    val sequence: Int,
    val payload: ByteArray,
    val isStderr: Boolean = false,
) {
    fun encode(identity: TerminalIdentity): ByteArray {
        validate()
        return ByteBuffer.allocate(HEADER_BYTES + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(MAGIC)
            put(VERSION.toByte())
            put(type.toByte())
            putShort(if (isStderr) 1.toShort() else 0.toShort())
            for (id in listOf(identity.sessionId, identity.attachmentId)) {
                val uuid = UUID.fromString(id)
                putLong(uuid.mostSignificantBits)
                putLong(uuid.leastSignificantBits)
            }
            putInt(sequence)
            putInt(payload.size)
            put(payload)
        }.array()
    }

    private fun validate() {
        require(sequence > 0) { "terminal-sequence" }
        require(!isStderr || type == OUTPUT) { "terminal-flags" }
        val validLength = when (type) {
            READY, RESIZE -> payload.size == 4
            INPUT -> payload.size in 1..TerminalLimits.INPUT_QUEUE_BYTES
            ACK, ADMISSION -> payload.size == 8
            OUTPUT -> payload.size in 1..TerminalLimits.OUTPUT_CHUNK_BYTES
            ERROR -> payload.size == 1 && payload[0] == 1.toByte()
            CLOSED -> payload.isEmpty()
            UI_REQUEST, UI_COMMAND -> payload.size in 1..1 + TerminalUiLimits.TEXT_BYTES
            else -> false
        }
        require(validLength) { "terminal-frame-length-or-type" }
    }

    fun size(): TerminalSize {
        require(type == READY || type == RESIZE)
        val b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        return TerminalSize(b.short.toInt() and 65535, b.short.toInt() and 65535)
    }

    companion object {
        const val HEADER_BYTES = 48
        const val MAGIC = 0x43575452 // CWTR, network byte order.
        const val VERSION = 1
        const val READY = 1
        const val INPUT = 2
        const val RESIZE = 3
        const val ACK = 4
        const val OUTPUT = 5
        const val ADMISSION = 6
        const val ERROR = 7
        const val CLOSED = 8
        const val UI_REQUEST = 9
        const val UI_COMMAND = 10

        fun decode(bytes: ByteArray, expected: TerminalIdentity): TerminalFrame {
            require(bytes.size in HEADER_BYTES..HEADER_BYTES + TerminalLimits.INPUT_QUEUE_BYTES) {
                "terminal-envelope-length"
            }
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            require(b.int == MAGIC && b.get().toInt() == VERSION) { "terminal-version" }
            val type = b.get().toInt() and 255
            val flags = b.short.toInt() and 65535
            require(flags in 0..1 && (flags == 0 || type == OUTPUT)) { "terminal-flags" }
            val session = UUID(b.long, b.long).toString()
            val attachment = UUID(b.long, b.long).toString()
            require(session == expected.sessionId && attachment == expected.attachmentId) { "terminal-identity" }
            val sequence = b.int
            val length = b.int
            require(length == bytes.size - HEADER_BYTES) { "terminal-payload-length" }
            return TerminalFrame(type, sequence, bytes.copyOfRange(HEADER_BYTES, bytes.size), flags == 1)
                .also { it.validate() }
        }

        fun sizePayload(size: TerminalSize): ByteArray = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN).putShort(size.columns.toShort()).putShort(size.rows.toShort()).array()

        fun admissionPayload(request: TerminalFrame, admission: TerminalAdmission): ByteArray =
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(request.sequence)
                .put(request.type.toByte()).put(admission.ordinal.toByte()).putShort(0).array()
    }
}
