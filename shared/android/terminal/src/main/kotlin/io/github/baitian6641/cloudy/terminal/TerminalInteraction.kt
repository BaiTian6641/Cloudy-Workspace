package io.github.baitian6641.cloudy.terminal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Bounds shared by the native clipboard, the renderer UI channel and the search addon. */
object TerminalUiLimits {
    /** Leaves headroom below the 64 KiB INPUT frame for bracketed-paste and line-ending bytes. */
    const val TEXT_BYTES = 60 * 1024
    const val SEARCH_QUERY_UTF16_UNITS = 256
    const val SEARCH_QUERY_BYTES = 1024
    const val SEARCH_RESULT_BYTES = 13
    const val HIGHLIGHT_LIMIT = 1000
}

/** Renderer-to-native local actions. Wire values are part of the terminal-only envelope. */
enum class TerminalUiRequestAction(val wireId: Int) {
    COPY_SELECTION(1),
    REQUEST_PASTE(2),
    REQUEST_RIGHT_CLICK(3),
    REQUEST_MENU(4),
    REQUEST_FIND(5),
    SEARCH_RESULT(6),
    MOUSE_TRACKING(7),
    NOTICE(8),
    ;

    companion object {
        fun fromWire(value: Int): TerminalUiRequestAction? = entries.firstOrNull { it.wireId == value }
    }
}

/** Native local actions; PASTE alone enters xterm's normal input path after native approval. */
enum class TerminalUiCommandAction(val wireId: Int) {
    SELECT_ALL(1),
    COPY(2),
    PASTE(3),
    FOCUS(4),
    FIND_NEXT(5),
    FIND_PREVIOUS(6),
    SEARCH_CLEAR(7),
    ;

}

/** Local renderer refusals that must not be mistaken for a remote operation. */
enum class TerminalNotice(val wireId: Int) {
    NO_SELECTION(1),
    SELECTION_TOO_LARGE(2),
    SELECTION_INVALID(3),
    ;

    companion object {
        fun fromWire(value: Int): TerminalNotice? = entries.firstOrNull { it.wireId == value }
    }
}

sealed interface TerminalUiPayload {
    data object Empty : TerminalUiPayload
    class Text(val bytes: ByteArray) : TerminalUiPayload
    data class SearchResult(
        val commandSequence: Int,
        val resultIndex: Int,
        val resultCount: Int,
        val found: Boolean,
        val atThreshold: Boolean,
    ) : TerminalUiPayload
    data class MouseTracking(val active: Boolean) : TerminalUiPayload
    data class Notice(val code: TerminalNotice) : TerminalUiPayload
}

data class TerminalUiRequest(
    val sequence: Int,
    val action: TerminalUiRequestAction,
    val payload: TerminalUiPayload,
)

private val EMPTY_UI_TEXT = ByteArray(0)

data class TerminalUiCommand(val action: TerminalUiCommandAction, val text: ByteArray = EMPTY_UI_TEXT) {
    override fun toString(): String = "TerminalUiCommand(${action.name}, redacted)"
}

/** Strict UTF-8/UTF-16 helpers shared by the binary boundary and the native clipboard policy. */
object TerminalText {
    /** Decodes complete UTF-8 only; malformed or partial sequences are refused rather than replaced. */
    fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }


    /** Returns the exact UTF-8 length, or null for unpaired surrogates. Never allocates the output. */
    fun utf8Size(value: String): Int? {
        var index = 0
        var size = 0L
        while (index < value.length) {
            val current = value[index]
            size += when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return null
                    index++
                    4
                }
                Character.isLowSurrogate(current) -> return null
                current.code < 0x80 -> 1
                current.code < 0x800 -> 2
                else -> 3
            }
            if (size > Int.MAX_VALUE) return null
            index++
        }
        return size.toInt()
    }
}

/** Envelope payload codec; identities and sequencing stay in [TerminalFrame]. */
internal object TerminalUiProtocol {
    fun decodeRequest(sequence: Int, payload: ByteArray): TerminalUiRequest {
        require(sequence > 0 && payload.isNotEmpty()) { "terminal-ui-request" }
        val action = TerminalUiRequestAction.fromWire(payload[0].toInt() and 255)
            ?: throw IllegalArgumentException("terminal-ui-action")
        val typed: TerminalUiPayload = when (action) {
            TerminalUiRequestAction.COPY_SELECTION -> {
                require(payload.size <= TerminalUiLimits.TEXT_BYTES + 1) { "terminal-ui-selection" }
                TerminalUiPayload.Text(payload.copyOfRange(1, payload.size))
            }
            TerminalUiRequestAction.REQUEST_PASTE,
            TerminalUiRequestAction.REQUEST_RIGHT_CLICK,
            TerminalUiRequestAction.REQUEST_MENU,
            TerminalUiRequestAction.REQUEST_FIND -> {
                require(payload.size == 1) { "terminal-ui-empty" }
                TerminalUiPayload.Empty
            }
            TerminalUiRequestAction.SEARCH_RESULT -> decodeSearchResult(payload)
            TerminalUiRequestAction.MOUSE_TRACKING -> {
                require(payload.size == 2 && (payload[1].toInt() == 0 || payload[1].toInt() == 1)) {
                    "terminal-ui-mouse"
                }
                TerminalUiPayload.MouseTracking(payload[1].toInt() == 1)
            }
            TerminalUiRequestAction.NOTICE -> {
                require(payload.size == 2) { "terminal-ui-notice" }
                val notice = TerminalNotice.fromWire(payload[1].toInt() and 255)
                    ?: throw IllegalArgumentException("terminal-ui-notice")
                TerminalUiPayload.Notice(notice)
            }
        }
        return TerminalUiRequest(sequence, action, typed)
    }

    fun encodeCommand(command: TerminalUiCommand): ByteArray {
        val limit = when (command.action) {
            TerminalUiCommandAction.PASTE -> TerminalUiLimits.TEXT_BYTES
            TerminalUiCommandAction.FIND_NEXT, TerminalUiCommandAction.FIND_PREVIOUS ->
                TerminalUiLimits.SEARCH_QUERY_BYTES
            TerminalUiCommandAction.SELECT_ALL, TerminalUiCommandAction.COPY,
            TerminalUiCommandAction.FOCUS, TerminalUiCommandAction.SEARCH_CLEAR -> 0
        }
        val valid = if (limit == 0) command.text.isEmpty() else command.text.size in 1..limit
        require(valid) { "terminal-ui-command" }
        if (command.text.isNotEmpty()) {
            val decoded = TerminalText.decodeUtf8(command.text)
                ?: throw IllegalArgumentException("terminal-ui-command-text")
            if (command.action == TerminalUiCommandAction.FIND_NEXT ||
                command.action == TerminalUiCommandAction.FIND_PREVIOUS) {
                require(decoded.length in 1..TerminalUiLimits.SEARCH_QUERY_UTF16_UNITS) {
                    "terminal-ui-search-query"
                }
            }
        }
        return ByteArray(1 + command.text.size).also { payload ->
            payload[0] = command.action.wireId.toByte()
            command.text.copyInto(payload, destinationOffset = 1)
        }
    }

    private fun decodeSearchResult(payload: ByteArray): TerminalUiPayload.SearchResult {
        require(payload.size == 1 + TerminalUiLimits.SEARCH_RESULT_BYTES) { "terminal-search-size" }
        val buffer = ByteBuffer.wrap(payload, 1, TerminalUiLimits.SEARCH_RESULT_BYTES).order(ByteOrder.BIG_ENDIAN)
        val command = buffer.int
        val index = buffer.int
        val count = buffer.int
        val flags = buffer.get().toInt() and 255
        require(command > 0 && (index == -1 || (index >= 0 && index < count))) { "terminal-search-index" }
        require(count in 0..TerminalUiLimits.HIGHLIGHT_LIMIT) { "terminal-search-count" }
        require(flags and (FLAG_FOUND or FLAG_THRESHOLD).inv() == 0) { "terminal-search-flags" }
        return TerminalUiPayload.SearchResult(command, index, count, flags and FLAG_FOUND != 0,
            flags and FLAG_THRESHOLD != 0 || count >= TerminalUiLimits.HIGHLIGHT_LIMIT)
    }

    private const val FLAG_FOUND = 1
    private const val FLAG_THRESHOLD = 2
}
