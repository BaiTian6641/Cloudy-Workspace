package io.github.baitian6641.cloudy.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SshX11CookieTest {
    @Test fun fakeCookiesAreSixteenBytesWithLowercaseHexAndDistinctValues() {
        val first = SshX11Cookie.generate()
        val second = SshX11Cookie.generate()
        assertEquals(16, first.size)
        assertFalse(first.contentEquals(second))
        val hex = SshX11Cookie.hex(first)
        assertEquals(32, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
        assertThrows(SshX11Exception::class.java) { SshX11Cookie.hex(ByteArray(15)) }
    }
}

class SshXauthorityCodecTest {
    private fun record(family: Int, address: String, display: String, name: String, data: ByteArray): ByteArray {
        fun u16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
        val addressBytes = address.toByteArray(Charsets.US_ASCII)
        return u16(family) + u16(addressBytes.size) + addressBytes +
            u16(display.length) + display.toByteArray(Charsets.US_ASCII) +
            u16(name.length) + name.toByteArray(Charsets.US_ASCII) +
            u16(data.size) + data
    }

    private val cookie98 = ByteArray(16) { it.toByte() }
    private val cookie0 = ByteArray(16) { (0xF0 + it).toByte() }
    private val wildCookie = ByteArray(16) { (0x40 + it).toByte() }

    private fun file(): ByteArray =
        record(256, "testhost", "0", "MIT-MAGIC-COOKIE-1", cookie0) +
        record(256, "testhost", "98", "MIT-MAGIC-COOKIE-1", cookie98) +
        record(0, "10.0.0.7", "98", "MIT-MAGIC-COOKIE-1", ByteArray(16)) +
        record(65535, "", "98", "MIT-MAGIC-COOKIE-1", wildCookie) +
        record(256, "testhost", "98", "MIT-MAGIC-COOKIE-2", ByteArray(16))

    @Test fun localAndWildcardEntriesResolveExactlyByDisplay() {
        val records = SshXauthorityCodec.parse(file())
        assertEquals(5, records.size)
        // The wildcard entry sorts after the exact local record in file order; exact local wins.
        assertArrayEquals(cookie98, SshXauthorityCodec.findCookie(records, 98, "testhost"))
        assertArrayEquals(cookie0, SshXauthorityCodec.findCookie(records, 0, "testhost"))
        assertNull(SshXauthorityCodec.findCookie(records, 97, "testhost"))
        // A different hostname falls back only to the wildcard entry.
        assertArrayEquals(wildCookie, SshXauthorityCodec.findCookie(records, 98, "otherhost"))
    }

    @Test fun malformedFilesRejectCompletely() {
        val valid = file()
        assertThrows(SshX11Exception::class.java) {
            SshXauthorityCodec.parse(valid.copyOf(valid.size - 1))
        }
        assertThrows(SshX11Exception::class.java) { SshXauthorityCodec.parse(byteArrayOf(0)) }
        assertThrows(SshX11Exception::class.java) {
            SshXauthorityCodec.parse(record(256, "h", "not-a-number", "MIT-MAGIC-COOKIE-1", cookie0))
        }
        assertThrows(SshX11Exception::class.java) {
            SshXauthorityCodec.parse(ByteArray(SshXauthorityCodec.MAX_FILE_BYTES + 1))
        }
        // An internet-family record alone never satisfies a local lookup.
        val inet = SshXauthorityCodec.parse(record(0, "10.0.0.7", "98", "MIT-MAGIC-COOKIE-1", cookie98))
        assertNull(SshXauthorityCodec.findCookie(inet, 98, "testhost"))
    }
}
