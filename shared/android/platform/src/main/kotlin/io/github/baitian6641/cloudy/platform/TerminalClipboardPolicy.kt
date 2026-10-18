package io.github.baitian6641.cloudy.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import io.github.baitian6641.cloudy.domain.TerminalRightClick
import io.github.baitian6641.cloudy.terminal.TerminalText
import io.github.baitian6641.cloudy.terminal.TerminalUiLimits

internal enum class TerminalClipboardRefusal { EMPTY, TOO_LARGE, MALFORMED, UNSUPPORTED, UNAVAILABLE }

internal sealed interface TerminalClipboardRead {
    /** Exact admitted text plus validated UTF-8 bytes; diagnostics never reveal the content. */
    data class Text(val value: String, val utf8: ByteArray) : TerminalClipboardRead {
        override fun toString(): String = "TerminalClipboardRead.Text(redacted)"
    }
    data class Refused(val reason: TerminalClipboardRefusal) : TerminalClipboardRead
}

/** Bounded escaped display of consequential clipboard text; the original text stays untouched. */
internal data class TerminalClipboardPreview(
    val display: String,
    val shownCharacters: Int,
    val totalCharacters: Int,
) {
    val truncated: Boolean get() = shownCharacters < totalCharacters
    override fun toString(): String = "TerminalClipboardPreview(redacted)"
}

/**
 * Pure clipboard admission. Text is accepted whole or refused whole; nothing is truncated and no
 * URI conversion is attempted. Preview rules are deliberately conservative because an unbracketed
 * shell executes line breaks and other C0/C1 controls.
 */
internal object TerminalClipboardPolicy {
    const val MAX_TEXT_BYTES = TerminalUiLimits.TEXT_BYTES

    /** Null when the text may be used whole; the scan does not allocate encoded output. */
    fun refusal(value: CharSequence?): TerminalClipboardRefusal? {
        if (value == null) return TerminalClipboardRefusal.UNSUPPORTED
        // Every UTF-16 unit is at least one UTF-8 byte, so this bound avoids scanning huge clips.
        if (value.length > MAX_TEXT_BYTES) return TerminalClipboardRefusal.TOO_LARGE
        if (value.isEmpty()) return TerminalClipboardRefusal.EMPTY
        val text = value as? String ?: value.toString()
        val size = TerminalText.utf8Size(text) ?: return TerminalClipboardRefusal.MALFORMED
        return if (size > MAX_TEXT_BYTES) TerminalClipboardRefusal.TOO_LARGE else null
    }

    fun validate(value: CharSequence?): TerminalClipboardRead {
        if (value == null) return TerminalClipboardRead.Refused(TerminalClipboardRefusal.UNSUPPORTED)
        if (value.length > MAX_TEXT_BYTES) return TerminalClipboardRead.Refused(TerminalClipboardRefusal.TOO_LARGE)
        val text = value.toString()
        refusal(text)?.let { return TerminalClipboardRead.Refused(it) }
        return TerminalClipboardRead.Text(text, text.encodeToByteArray())
    }

    fun requiresPreview(text: String): Boolean = text.any { character ->
        character.code <= 0x1F || character.code in 0x7F..0x9F ||
            character == '\u2028' || character == '\u2029'
    }

    /**
     * Escapes C0/C1/DEL and line separators, doubles literal backslashes so they cannot be confused
     * with an escape, and bounds the source slice without splitting a surrogate pair.
     */
    fun preview(text: String, maxCharacters: Int): TerminalClipboardPreview {
        require(maxCharacters >= 2) { "terminal-preview-bound" }
        val display = StringBuilder(minOf(text.length, maxCharacters))
        var index = 0
        var shown = 0
        while (index < text.length && shown < maxCharacters) {
            val current = text[index]
            if (Character.isHighSurrogate(current) &&
                index + 1 < text.length && Character.isLowSurrogate(text[index + 1])) {
                if (shown + 2 > maxCharacters) break
                display.append(current).append(text[index + 1])
                index += 2
                shown += 2
            } else {
                appendEscaped(display, current)
                index++
                shown++
            }
        }
        return TerminalClipboardPreview(display.toString(), shown, text.length)
    }

    private fun appendEscaped(output: StringBuilder, character: Char) {
        val code = character.code
        when {
            character == '\\' -> output.append("\\\\")
            code <= 0x1F || code in 0x7F..0x9F ->
                output.append("\\x").append(HEX[code ushr 4]).append(HEX[code and 15])
            code == 0x2028 -> output.append("\\u2028")
            code == 0x2029 -> output.append("\\u2029")
            Character.isSurrogate(character) -> output.append("\\u")
                .append(HEX[code ushr 12]).append(HEX[(code ushr 8) and 15])
                .append(HEX[(code ushr 4) and 15]).append(HEX[code and 15])
            else -> output.append(character)
        }
    }

    private const val HEX = "0123456789ABCDEF"

    /** Null until a saved choice is actually confirmed; defaults are never treated as an approval. */
    fun confirmedRightClick(
        ready: Boolean,
        loading: Boolean,
        saving: Boolean,
        recoveredValues: Boolean,
        persistenceUncertain: Boolean,
        value: TerminalRightClick,
    ): TerminalRightClick? =
        value.takeIf { ready && !loading && !saving && !recoveredValues && !persistenceUncertain }
}

/** The app owns clipboard access; the renderer never invokes the browser Clipboard API. */
internal class TerminalClipboardAccess(private val manager: ClipboardManager?) {
    fun read(): TerminalClipboardRead {
        val clipboard = manager ?: return TerminalClipboardRead.Refused(TerminalClipboardRefusal.UNAVAILABLE)
        val clip = try {
            clipboard.primaryClip
        } catch (_: RuntimeException) {
            return TerminalClipboardRead.Refused(TerminalClipboardRefusal.UNAVAILABLE)
        } ?: return TerminalClipboardRead.Refused(TerminalClipboardRefusal.EMPTY)
        if (clip.itemCount != 1) return TerminalClipboardRead.Refused(TerminalClipboardRefusal.UNSUPPORTED)
        val description = clip.description
        // newHtmlText advertises text/html but also requires a plain-text alternative.
        // Consume that supplied text only; never parse HTML or coerce a URI/Intent.
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
            return TerminalClipboardRead.Refused(TerminalClipboardRefusal.UNSUPPORTED)
        }
        val item = clip.getItemAt(0)
        // A URI/Intent representation is refused; coerceToText would fetch or launch external content.
        if (item.uri != null || item.intent != null) {
            return TerminalClipboardRead.Refused(TerminalClipboardRefusal.UNSUPPORTED)
        }
        return TerminalClipboardPolicy.validate(item.text)
    }

    fun copy(text: String): Boolean {
        val clipboard = manager ?: return false
        return try {
            val clip = ClipData.newPlainText(CLIP_LABEL, text)
            // Best-effort system hint only; it is not secure erasure or a secrecy guarantee.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clipboard.setPrimaryClip(clip)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private companion object {
        const val CLIP_LABEL = "Cloudy terminal text"
    }
}
