package io.github.baitian6641.cloudy.terminal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Untrusted wire/text boundaries; actual renderer/search/clipboard checks are separate. */
class TerminalInteractionTest {
    private val identity = TerminalIdentity(
        "00112233-4455-6677-8899-aabbccddeeff", "ffeeddcc-bbaa-9988-7766-554433221100",
    )

    @Test fun malformedActionBodiesCannotBecomeLocalCommands() {
        for (payload in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(4, 1),
            byteArrayOf(7, 2), byteArrayOf(8, 99))) {
            assertThrows(IllegalArgumentException::class.java) { TerminalUiProtocol.decodeRequest(1, payload) }
        }
        assertThrows(IllegalArgumentException::class.java) { TerminalUiProtocol.decodeRequest(0, byteArrayOf(2)) }
    }

    @Test fun selectionRefusesTheWholeOverBoundAction() {
        val exact = ByteArray(TerminalUiLimits.TEXT_BYTES + 1) { 'a'.code.toByte() }
        exact[0] = TerminalUiRequestAction.COPY_SELECTION.wireId.toByte()
        assertEquals(TerminalUiLimits.TEXT_BYTES,
            (TerminalUiProtocol.decodeRequest(1, exact).payload as TerminalUiPayload.Text).bytes.size)
        assertThrows(IllegalArgumentException::class.java) { TerminalUiProtocol.decodeRequest(2, exact.copyOf(exact.size + 1)) }
    }

    @Test fun searchRejectsImpossibleIndexesCountsAndFlags() {
        val found = TerminalUiProtocol.decodeRequest(1, searchResult(41, 2, 3, 1)).payload as TerminalUiPayload.SearchResult
        assertEquals(41, found.commandSequence)
        assertEquals(2, found.resultIndex)
        assertEquals(3, found.resultCount)
        assertTrue(found.found)
        assertFalse(found.atThreshold)
        val limited = TerminalUiProtocol.decodeRequest(2, searchResult(42, -1, 1000, 3)).payload as TerminalUiPayload.SearchResult
        assertTrue(limited.atThreshold)
        val none = TerminalUiProtocol.decodeRequest(3, searchResult(43, -1, 0, 0)).payload as TerminalUiPayload.SearchResult
        assertEquals(0, none.resultCount)
        assertFalse(none.found)
        for (payload in listOf(searchResult(0, 0, 1, 1), searchResult(1, -2, 3, 1),
            searchResult(1, 0, 0, 0), searchResult(1, 3, 3, 1), searchResult(1, 0, 1001, 1),
            searchResult(1, -1, -1, 0), searchResult(1, 0, 1, 4))) {
            assertThrows(IllegalArgumentException::class.java) { TerminalUiProtocol.decodeRequest(4, payload) }
        }
    }

    @Test fun commandsRejectMalformedOrPartialTextRatherThanTruncating() {
        val exact = ByteArray(TerminalUiLimits.TEXT_BYTES) { 'a'.code.toByte() }
        assertEquals(exact.size + 1,
            TerminalUiProtocol.encodeCommand(TerminalUiCommand(TerminalUiCommandAction.PASTE, exact)).size)
        for (command in listOf(
            TerminalUiCommand(TerminalUiCommandAction.PASTE),
            TerminalUiCommand(TerminalUiCommandAction.PASTE, exact.copyOf(exact.size + 1)),
            TerminalUiCommand(TerminalUiCommandAction.PASTE, byteArrayOf(0xff.toByte())),
            TerminalUiCommand(TerminalUiCommandAction.SELECT_ALL, byteArrayOf(1)),
            TerminalUiCommand(TerminalUiCommandAction.FIND_NEXT),
            TerminalUiCommand(TerminalUiCommandAction.FIND_NEXT, "a".repeat(257).toByteArray()),
        )) {
            assertThrows(IllegalArgumentException::class.java) { TerminalUiProtocol.encodeCommand(command) }
        }
        val query = "\u6f22".repeat(256).toByteArray()
        assertEquals(query.size + 1,
            TerminalUiProtocol.encodeCommand(TerminalUiCommand(TerminalUiCommandAction.FIND_PREVIOUS, query)).size)
    }

    @Test fun textRefusesMalformedUnicodeAndMeasuresEncodedSize() {
        assertEquals(10, TerminalText.utf8Size("e\u0301\u6f22\uD83D\uDC4D"))
        assertEquals(4, TerminalText.utf8Size("\uD83D\uDE00"))
        assertNull(TerminalText.utf8Size("\uD83D"))
        assertNull(TerminalText.utf8Size("\uDE00"))
        assertNull(TerminalText.decodeUtf8(byteArrayOf(0xe4.toByte(), 0xb8.toByte())))
        assertNull(TerminalText.decodeUtf8(byteArrayOf(0xff.toByte())))
    }

    @Test fun interactionEnvelopesRejectExcessBytesAndOutputOnlyFlags() {
        for (type in listOf(TerminalFrame.UI_REQUEST, TerminalFrame.UI_COMMAND)) {
            assertThrows(IllegalArgumentException::class.java) {
                TerminalFrame(type, 1, ByteArray(TerminalUiLimits.TEXT_BYTES + 2)).encode(identity)
            }
            assertThrows(IllegalArgumentException::class.java) { TerminalFrame(type, 1, ByteArray(0)).encode(identity) }
            assertThrows(IllegalArgumentException::class.java) { TerminalFrame(type, 1, byteArrayOf(1), true).encode(identity) }
        }
    }

    private fun searchResult(command: Int, index: Int, count: Int, flags: Int): ByteArray =
        ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN).put(6.toByte())
            .putInt(command).putInt(index).putInt(count).put(flags.toByte()).array()
}
