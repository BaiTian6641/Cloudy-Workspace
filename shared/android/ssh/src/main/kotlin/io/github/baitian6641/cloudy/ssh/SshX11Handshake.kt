package io.github.baitian6641.cloudy.ssh

import java.security.MessageDigest

/**
 * The X11 client setup block crossing a forwarded channel (X protocol client setup request):
 * byte order(1), unused(1), protocol major(2), minor(2), authorization name length(2),
 * authorization data length(2), unused(2), then padded name and padded data. All u16 fields use
 * the declared byte order. Only the exact setup block is parsed; stream framing is the caller's.
 */
class SshX11SetupBlock internal constructor(
    val littleEndian: Boolean,
    val protocolMajor: Int,
    val protocolMinor: Int,
    val authName: ByteArray,
    val authData: ByteArray,
) {
    override fun toString(): String = "SshX11SetupBlock($protocolMajor.$protocolMinor,redacted)"
}

object SshX11Handshake {
    const val HEADER_BYTES = 12
    private const val MAX_FIELD_BYTES = 65535

    private fun u16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int =
        if (littleEndian) {
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        } else {
            ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        }

    private fun putU16(target: ByteArray, offset: Int, value: Int, littleEndian: Boolean) {
        if (littleEndian) {
            target[offset] = (value and 0xFF).toByte()
            target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        } else {
            target[offset] = ((value ushr 8) and 0xFF).toByte()
            target[offset + 1] = (value and 0xFF).toByte()
        }
    }

    private fun pad4(length: Int): Int = (length + 3) and 3.inv()

    /** Exact bytes needed for a complete setup block given its already-read 12-byte header. */
    fun paddedLength(header: ByteArray): Int {
        if (header.size != HEADER_BYTES) throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "header")
        val little = when (header[0]) {
            'l'.code.toByte() -> true
            'B'.code.toByte() -> false
            else -> throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "byte-order")
        }
        val nameLength = u16(header, 6, little)
        val dataLength = u16(header, 8, little)
        if (nameLength > MAX_FIELD_BYTES || dataLength > MAX_FIELD_BYTES) {
            throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "length")
        }
        return HEADER_BYTES + pad4(nameLength) + pad4(dataLength)
    }

    fun parse(block: ByteArray): SshX11SetupBlock {
        if (block.size < HEADER_BYTES) throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "short")
        val little = when (block[0]) {
            'l'.code.toByte() -> true
            'B'.code.toByte() -> false
            else -> throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "byte-order")
        }
        val major = u16(block, 2, little)
        val minor = u16(block, 4, little)
        val nameLength = u16(block, 6, little)
        val dataLength = u16(block, 8, little)
        val expected = HEADER_BYTES + pad4(nameLength) + pad4(dataLength)
        if (block.size != expected) throw SshX11Exception(SshX11Code.PROTOCOL_VIOLATION, "trailing")
        val name = block.copyOfRange(HEADER_BYTES, HEADER_BYTES + nameLength)
        val data = block.copyOfRange(HEADER_BYTES + pad4(nameLength), HEADER_BYTES + pad4(nameLength) + dataLength)
        return SshX11SetupBlock(little, major, minor, name, data)
    }

    /**
     * Client-side cookie substitution per RFC 4254 §6.3 / OpenSSH behavior: the forwarded setup
     * must present exactly the fake cookie this owner issued; it is replaced with the real local
     * cookie. Anything else closes instead of guessing. Both cookies are 16 bytes, so the wire
     * lengths are unchanged and the rewrite is in place.
     */
    fun substituteCookie(block: ByteArray, fakeCookie: ByteArray, realCookie: ByteArray): ByteArray {
        val setup = parse(block)
        val expectedName = SshXauthorityCodec.MIT_MAGIC_COOKIE_1.toByteArray(Charsets.US_ASCII)
        if (!MessageDigest.isEqual(setup.authName, expectedName)) {
            throw SshX11Exception(SshX11Code.AUTH_PROTOCOL_REJECTED)
        }
        if (fakeCookie.size != SshX11Cookie.BYTES || realCookie.size != SshX11Cookie.BYTES) {
            throw SshX11Exception(SshX11Code.INVALID_INPUT)
        }
        if (!MessageDigest.isEqual(setup.authData, fakeCookie)) {
            throw SshX11Exception(SshX11Code.COOKIE_MISMATCH)
        }
        val rewritten = block.copyOf()
        System.arraycopy(realCookie, 0, rewritten, HEADER_BYTES + pad4(setup.authName.size), SshX11Cookie.BYTES)
        return rewritten
    }

    /** Test/encoder support: serialize a setup block exactly as parse expects it. */
    fun encode(littleEndian: Boolean, protocolMajor: Int, protocolMinor: Int,
               authName: ByteArray, authData: ByteArray): ByteArray {
        val block = ByteArray(HEADER_BYTES + pad4(authName.size) + pad4(authData.size))
        block[0] = (if (littleEndian) 'l' else 'B').code.toByte()
        putU16(block, 2, protocolMajor, littleEndian)
        putU16(block, 4, protocolMinor, littleEndian)
        putU16(block, 6, authName.size, littleEndian)
        putU16(block, 8, authData.size, littleEndian)
        System.arraycopy(authName, 0, block, HEADER_BYTES, authName.size)
        System.arraycopy(authData, 0, block, HEADER_BYTES + pad4(authName.size), authData.size)
        return block
    }
}
