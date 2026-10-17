package io.github.baitian6641.cloudy.ssh

import java.security.MessageDigest
import java.security.SecureRandom

/** Errors in X11 forwarding handling are typed; they never carry cookie material. */
enum class SshX11Code {
    INVALID_INPUT, PROTOCOL_VIOLATION, COOKIE_MISMATCH, AUTH_PROTOCOL_REJECTED,
    AUTHORITY_UNAVAILABLE, COOKIE_NOT_FOUND, CONNECT_FAILED, CHANNEL_FAILED,
    TIMED_OUT, LIMIT_EXCEEDED, CLOSED,
}

class SshX11Exception(val code: SshX11Code, detail: String? = null) :
    IllegalStateException(code.name + (detail?.let { ":$it" } ?: ""))

/**
 * Fake-cookie generation and lowercase-hex wire form for the x11-req request, matching
 * RFC 4254 §6.3 and OpenSSH client behavior. The fake cookie identifies forwarded traffic to
 * substitute; it is never valid on any real server and is discarded with its owner.
 */
object SshX11Cookie {
    const val BYTES = 16
    private val random = SecureRandom()

    fun generate(): ByteArray = ByteArray(BYTES).also { random.nextBytes(it) }

    fun hex(cookie: ByteArray): String {
        if (cookie.size != BYTES) throw SshX11Exception(SshX11Code.INVALID_INPUT)
        val out = StringBuilder(BYTES * 2)
        for (byte in cookie) out.append((byte.toInt() shr 4 and 0xF).toString(16))
            .append((byte.toInt() and 0xF).toString(16))
        return out.toString()
    }
}

/** One parsed Xauthority record; data is compared, never logged. */
class SshXauthorityRecord(
    val family: Int,
    val address: ByteArray,
    val display: Int,
    val name: String,
    val data: ByteArray,
) {
    override fun toString(): String = "SshXauthorityRecord(display=$display,redacted)"
}

/**
 * Strict Xauthority parsing (big-endian u16 fields per the X11 authorization file format).
 * Malformed or truncated input rejects as a whole; nothing is partially imported.
 */
object SshXauthorityCodec {
    const val FAMILY_INTERNET = 0
    const val FAMILY_LOCAL = 256
    const val FAMILY_WILD = 65535
    const val MIT_MAGIC_COOKIE_1 = "MIT-MAGIC-COOKIE-1"
    const val MAX_FILE_BYTES = 1024 * 1024

    fun parse(bytes: ByteArray): List<SshXauthorityRecord> {
        if (bytes.size > MAX_FILE_BYTES) throw SshX11Exception(SshX11Code.INVALID_INPUT, "oversized")
        val records = mutableListOf<SshXauthorityRecord>()
        var offset = 0
        fun u16(): Int {
            if (offset + 2 > bytes.size) throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "truncated")
            val value = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            offset += 2
            return value
        }
        fun blob(length: Int): ByteArray {
            if (offset + length > bytes.size) throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "truncated")
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }
        while (offset < bytes.size) {
            val family = u16()
            val address = blob(u16())
            val displayText = String(blob(u16()), Charsets.US_ASCII)
            val name = String(blob(u16()), Charsets.US_ASCII)
            val data = blob(u16())
            val display = displayText.toIntOrNull()?.takeIf { it in 0..65535 && it.toString() == displayText }
                ?: throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "display")
            records += SshXauthorityRecord(family, address, display, name, data)
        }
        return records
    }

    /**
     * The real cookie for a local display: exact local hostname or wildcard entries only.
     * Internet-family entries are accepted only for the loopback address of this host name.
     * A missing match is reported, never guessed.
     */
    fun findCookie(records: List<SshXauthorityRecord>, display: Int, hostname: String): ByteArray? {
        val hostBytes = hostname.toByteArray(Charsets.US_ASCII)
        return records.firstOrNull { record ->
            record.display == display && record.name == MIT_MAGIC_COOKIE_1 && when (record.family) {
                FAMILY_WILD -> true
                FAMILY_LOCAL -> MessageDigest.isEqual(record.address, hostBytes)
                else -> false
            }
        }?.data
    }
}
