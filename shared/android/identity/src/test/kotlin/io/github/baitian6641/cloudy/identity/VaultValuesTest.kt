package io.github.baitian6641.cloudy.identity

import org.junit.Assert.*
import org.junit.Test

class VaultValuesTest {
    private val account = "12345678-1234-4234-8234-123456789abc"
    private fun binding(host: String) = VaultBinding.create("io.cloudy.console", VaultProtocol.SSH,
        host, 22, VaultRole.TARGET, account)

    @Test fun logicalAliasesCanonicalizeWithoutResolvingTransport() {
        assertEquals(binding("EXAMPLE.org."), binding("example.org"))
        assertEquals(binding("bücher.example"), binding("xn--bcher-kva.example"))
        assertEquals(binding("[2001:DB8::1]"), binding("2001:0db8:0:0:0:0:0:1"))
        assertEquals(binding("::ffff:192.0.2.1"), binding("0:0:0:0:0:ffff:c000:201"))
        assertEquals("0:0:0:0:0:0:0:0", binding("::").host)
        assertEquals(binding("[FE80::1%Eth_0]"), binding("fe80:0:0:0:0:0:0:1%Eth_0"))
        assertNotEquals(binding("fe80::1%Eth_0"), binding("fe80::1%eth_0"))
        assertFalse(binding("example.org").toString().contains("example.org"))
    }

    @Test fun malformedOrAmbiguousHostsAndIdsAreRejected() {
        for (host in listOf("https://example.org", "u@example.org", "x/y", "x\ny", "x y", "a..b",
                "01.2.3.4", "256.0.0.1", "127.1", "1::2::3", "1:2:3", "1:2:3:4:5:6:7:8:9",
                "1:2:3:4:5:6:7:8::", "::ffff:01.2.3.4", "fe80::1%", "fe80::1%eth/0", "[::1]:22")) {
            assertThrows(host, IllegalArgumentException::class.java) { binding(host) }
        }
        for (id in listOf(account.uppercase(), "1-1-1-1-1", account.replace("4234", "1234"),
                account.replace("8234", "7234"))) {
            assertThrows(IllegalArgumentException::class.java) { CredentialId.parse(id) }
        }
        assertEquals(CredentialId.parse(account), CredentialId.parse(account))
        assertNotEquals(CredentialId.create(), CredentialId.create())
    }

    @Test fun payloadOwnsCopiesAndCloseRevokesAccessWithoutChangingCallerArrays() {
        val secret = charArrayOf('s', 'é', '密', '碼')
        val payload = SensitivePayload.create("user".toCharArray(), charArrayOf(), secret)
        secret[0] = 'x'
        val copy = payload.copyPassword()
        assertEquals('s', copy[0])
        copy.fill('z')
        assertEquals('s', payload.copyPassword()[0])
        assertEquals("SensitivePayload(redacted)", payload.toString())
        payload.close(); payload.close()
        assertThrows(IllegalStateException::class.java) { payload.copyPassword() }
        assertThrows(IllegalStateException::class.java) { VaultCodec.encodePayload(payload) }
        assertEquals('x', secret[0])
        secret.fill('\u0000'); copy.fill('\u0000')
    }
}
