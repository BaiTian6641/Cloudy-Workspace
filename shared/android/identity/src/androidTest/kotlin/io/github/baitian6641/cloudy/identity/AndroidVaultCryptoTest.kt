package io.github.baitian6641.cloudy.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Alias derivation only; actual authenticated AES policy/backing needs a separate device operation. */
class AndroidVaultCryptoTest {
    @Test fun aliasUsesOnlyFixedNamespaceAndCanonicalRecordId() {
        val first = CredentialId.create()
        val second = CredentialId.create()
        assertEquals("cloudy.vault.v1.${first.value}", AndroidVaultCrypto.alias(first))
        assertEquals(AndroidVaultCrypto.alias(first), AndroidVaultCrypto.alias(CredentialId.parse(first.value)))
        assertNotEquals(AndroidVaultCrypto.alias(first), AndroidVaultCrypto.alias(second))
    }
}
