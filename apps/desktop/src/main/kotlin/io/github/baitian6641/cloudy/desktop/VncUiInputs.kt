package io.github.baitian6641.cloudy.desktop

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Local-field boundary only; output arrays transfer to the real model, never saved or logged.
 * Framework/JVM text is immutable; field epoch disposal does not promise physical String erasure.
 */
internal object VncUiInputs {
    fun decodePin(text: String): ByteArray? {
        if (text.length != 64 || !text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return ByteArray(32) { index ->
            ((text[index * 2].digitToInt(16) shl 4) or text[index * 2 + 1].digitToInt(16)).toByte()
        }
    }
    fun encodeSecret(text: String, maximum: Int): ByteArray? {
        require(maximum in 1..1024)
        if (text.isEmpty() || text.length > maximum || '\u0000' in text) return null
        val storage = ByteBuffer.allocate(maximum + 1)
        return try {
            val encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val encoded = encoder.encode(CharBuffer.wrap(text), storage, true)
            if (!encoded.isUnderflow) null
            else {
                val flushed = encoder.flush(storage)
                if (!flushed.isUnderflow || storage.position() !in 1..maximum) null
                else ByteArray(storage.position()).also { storage.flip(); storage.get(it) }
            }
        } finally { storage.array().fill(0) }
    }
}
