package dev.cloudy.qualification.baseline

import java.net.URISyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure endpoint/bounds checks; Android JSONObject and certificate loading need separate coverage. */
class TransferConfigSpecTest {
    private val address = "https://192.0.2.10:28443/fixture.bin"
    private val hash = "abcdef0123456789".repeat(4)

    private fun validate(
        url: String = address,
        length: Long = 64L * 1024 * 1024,
        digest: String = hash,
        version: Int = 1,
        fixture: String = TransferConfigSpec.FIXTURE_ID,
    ) = TransferConfigSpec.validate(version, fixture, url, length, digest)

    private fun reject(url: String) {
        val failure = assertThrows("Must reject $url", Exception::class.java) { validate(url = url) }
        assertTrue(failure is IllegalArgumentException || failure is URISyntaxException)
    }

    @Test
    fun frozenInterfaceAcceptsCanonicalHttpsIpv4AndNormalizesHashOnly() {
        val spec = validate(digest = hash.uppercase())
        assertEquals(address, spec.url.toASCIIString())
        assertEquals(67108864L, spec.expectedLengthBytes)
        assertEquals(hash, spec.expectedSha256)
        assertEquals("cloudy-uidt-sha256-blocks-v1", TransferConfigSpec.FIXTURE_ID)
        assertEquals(268435456L, TransferConfigSpec.MAX_BYTES)
    }

    @Test
    fun exactLengthAndHighPortBoundariesAreAccepted() {
        assertEquals(1L, validate(url = "https://127.0.0.1:1024/fixture.bin", length = 1).expectedLengthBytes)
        assertEquals(TransferConfigSpec.MAX_BYTES,
            validate(url = "https://192.0.2.10:65535/fixture.bin", length = TransferConfigSpec.MAX_BYTES).expectedLengthBytes)
    }

    @Test
    fun schemePathAndAuthorityAliasesAreRejected() {
        listOf(
            "http://192.0.2.10:28443/fixture.bin",
            "HTTPS://192.0.2.10:28443/fixture.bin",
            "https://192.0.2.10:28443//fixture.bin",
            "https://192.0.2.10:28443/./fixture.bin",
            "https://192.0.2.10:28443/a/../fixture.bin",
            "https://192.0.2.10:28443/%66ixture.bin",
            "https://192.0.2.10:28443/fixture%2ebin",
            "https://192.0.2.10:28443/fixture.bin/",
            "https://192.0.2.10:028443/fixture.bin",
            "https://192.0.2.10/fixture.bin",
            "https://192.0.2.10:443/fixture.bin",
            "https://192.0.2.10:65536/fixture.bin",
            " https://192.0.2.10:28443/fixture.bin",
        ).forEach(::reject)
    }

    @Test
    fun userInfoQueriesAndFragmentsAreAlwaysRejectedIncludingEmptyOnes() {
        listOf(
            "https://user@192.0.2.10:28443/fixture.bin",
            "https://user:password@192.0.2.10:28443/fixture.bin",
            "https://@192.0.2.10:28443/fixture.bin",
            "$address?", "$address?token=test", "$address#", "$address#test",
        ).forEach(::reject)
    }

    @Test
    fun invalidIpv4AndAlternativeHostRepresentationsAreRejected() {
        listOf(
            "0.0.0.0", "0.1.2.3", "224.0.0.1", "255.255.255.255", "256.1.2.3",
            "192.0.2.256", "192.00.2.10", "127.1", "2130706433", "0x7f000001",
            "192.0.2.10.", "192.0.2.-1", "localhost", "fixture.invalid", "[::1]",
        ).forEach { reject("https://$it:28443/fixture.bin") }
    }

    @Test
    fun schemaFixtureLengthAndDigestBoundsAreStrict() {
        listOf(0, 2, Int.MAX_VALUE).forEach { version ->
            assertThrows(IllegalArgumentException::class.java) { validate(version = version) }
        }
        listOf("", "cloudy-uidt-sha256-blocks-v2", " ${TransferConfigSpec.FIXTURE_ID}").forEach { fixture ->
            assertThrows(IllegalArgumentException::class.java) { validate(fixture = fixture) }
        }
        listOf(-1L, 0L, TransferConfigSpec.MAX_BYTES + 1, Long.MAX_VALUE).forEach { length ->
            assertThrows(IllegalArgumentException::class.java) { validate(length = length) }
        }
        listOf("", hash.dropLast(1), hash + "0", "g".repeat(64), " $hash").forEach { digest ->
            assertThrows(IllegalArgumentException::class.java) { validate(digest = digest) }
        }
        reject(address + "x".repeat(129))
    }
}
