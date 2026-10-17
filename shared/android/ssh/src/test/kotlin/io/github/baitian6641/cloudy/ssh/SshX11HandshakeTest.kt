package io.github.baitian6641.cloudy.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SshX11HandshakeTest {
    private val fake = ByteArray(16) { (0x30 + it).toByte() }
    private val real = ByteArray(16) { (0x70 + it).toByte() }
    private val mit = "MIT-MAGIC-COOKIE-1".toByteArray(Charsets.US_ASCII)

    private fun expectCode(code: SshX11Code, block: () -> Unit) {
        val error = assertThrows(SshX11Exception::class.java, block)
        assertEquals(code, error.code)
    }

    @Test fun setupBlocksRoundTripInBothByteOrders() {
        for (little in listOf(true, false)) {
            val block = SshX11Handshake.encode(little, 11, 0, mit, fake)
            val setup = SshX11Handshake.parse(block)
            assertEquals(little, setup.littleEndian)
            assertEquals(11, setup.protocolMajor)
            assertEquals(0, setup.protocolMinor)
            assertArrayEquals(mit, setup.authName)
            assertArrayEquals(fake, setup.authData)
            // Stream framing reads the 12-byte header, then exactly the padded remainder.
            val header = block.copyOf(12)
            assertEquals(block.size, SshX11Handshake.paddedLength(header))
        }
    }

    @Test fun substitutionReplacesOnlyTheCookieBytes() {
        val block = SshX11Handshake.encode(true, 11, 0, mit, fake)
        val rewritten = SshX11Handshake.substituteCookie(block, fake, real)
        assertEquals(block.size, rewritten.size)
        // Header, name and padding are untouched; only the 16 cookie bytes differ.
        for (index in block.indices) {
            val cookieStart = 12 + 20 // padded name length: 18 -> 20
            if (index in cookieStart until cookieStart + 16) {
                assertEquals(real[index - cookieStart], rewritten[index])
            } else {
                assertEquals(block[index], rewritten[index])
            }
        }
        assertArrayEquals(real, SshX11Handshake.parse(rewritten).authData)
    }

    @Test fun referenceVectorsMatchTheWireSpecExactly() {
        // Hand-constructed per the X11 setup request layout: order(1), unused(1), major(2),
        // minor(2), name length(2), data length(2), unused(2), then 4-padded name and data.
        val littleBlock = byteArrayOf(
            'l'.code.toByte(), 0, 11, 0, 0, 0, 18, 0, 16, 0, 0, 0,
            *mit, 0, 0, *fake)
        assertEquals(48, littleBlock.size)
        val little = SshX11Handshake.parse(littleBlock)
        assertEquals(true, little.littleEndian)
        assertEquals(11, little.protocolMajor)
        assertEquals(0, little.protocolMinor)
        assertArrayEquals(mit, little.authName)
        assertArrayEquals(fake, little.authData)
        assertEquals(48, SshX11Handshake.paddedLength(littleBlock.copyOf(12)))
        val rewritten = SshX11Handshake.substituteCookie(littleBlock, fake, real)
        assertEquals(48, rewritten.size)
        assertArrayEquals(real, rewritten.copyOfRange(32, 48))
        assertArrayEquals(littleBlock.copyOfRange(0, 32), rewritten.copyOfRange(0, 32))

        val bigBlock = byteArrayOf(
            'B'.code.toByte(), 0, 0, 11, 0, 0, 0, 18, 0, 16, 0, 0,
            *mit, 0, 0, *fake)
        val big = SshX11Handshake.parse(bigBlock)
        assertEquals(false, big.littleEndian)
        assertEquals(11, big.protocolMajor)
        assertArrayEquals(fake, big.authData)
        assertEquals(48, SshX11Handshake.paddedLength(bigBlock.copyOf(12)))

        // The encoder agrees with the hand-built reference byte for byte.
        assertArrayEquals(littleBlock, SshX11Handshake.encode(true, 11, 0, mit, fake))
        assertArrayEquals(bigBlock, SshX11Handshake.encode(false, 11, 0, mit, fake))
    }

    @Test fun wrongCookieAndWrongProtocolAndMalformedBlocksReject() {
        val block = SshX11Handshake.encode(true, 11, 0, mit, fake)
        expectCode(SshX11Code.COOKIE_MISMATCH) {
            SshX11Handshake.substituteCookie(block, ByteArray(16) { 0x01 }, real)
        }
        expectCode(SshX11Code.AUTH_PROTOCOL_REJECTED) {
            SshX11Handshake.substituteCookie(
                SshX11Handshake.encode(true, 11, 0, "OTHER".toByteArray(), fake), fake, real)
        }
        expectCode(SshX11Code.PROTOCOL_VIOLATION) {
            SshX11Handshake.parse(block.copyOf(block.size - 1))
        }
        expectCode(SshX11Code.PROTOCOL_VIOLATION) {
            SshX11Handshake.parse(block + 0x00)
        }
        expectCode(SshX11Code.PROTOCOL_VIOLATION) {
            SshX11Handshake.parse(byteArrayOf('x'.code.toByte()) + block.copyOfRange(1, block.size))
        }
        expectCode(SshX11Code.PROTOCOL_VIOLATION) { SshX11Handshake.parse(block.copyOf(11)) }
        expectCode(SshX11Code.INVALID_INPUT) {
            SshX11Handshake.substituteCookie(block, fake, ByteArray(15))
        }
    }

    @Test fun zeroLengthAuthorizationDataHasNoCookieToSubstitute() {
        val anonymous = SshX11Handshake.encode(false, 11, 0, ByteArray(0), ByteArray(0))
        val setup = SshX11Handshake.parse(anonymous)
        assertEquals(0, setup.authName.size)
        expectCode(SshX11Code.AUTH_PROTOCOL_REJECTED) {
            SshX11Handshake.substituteCookie(anonymous, fake, real)
        }
    }
}
