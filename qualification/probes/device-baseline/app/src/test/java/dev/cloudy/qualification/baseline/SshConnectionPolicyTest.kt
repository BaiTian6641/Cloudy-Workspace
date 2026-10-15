package dev.cloudy.qualification.baseline

import java.security.Security
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConnectionPolicyTest {
    @Test fun policyRetainsOnlyReviewedAlgorithmsWithoutChangingProviderOrder() {
        val before = Security.getProviders().map { it.name }
        val policy = SshConnectionPolicy.create()
        assertEquals(before, Security.getProviders().map { it.name })
        assertTrue(policy.keyExchangeFactories.map { it.name }.all { it in setOf(SshConnectionPolicy.KEX, "ext-info-c") })
        assertTrue(policy.keyExchangeFactories.any { it.name == SshConnectionPolicy.KEX })
        assertEquals(listOf(SshConnectionPolicy.HOST_SIGNATURE), policy.keyAlgorithms.map { it.name })
        assertEquals(listOf(SshConnectionPolicy.CIPHER), policy.cipherFactories.map { it.name })
        assertEquals(listOf(SshConnectionPolicy.MAC), policy.getMACFactories().map { it.name })
        assertEquals(listOf("none"), policy.compressionFactories.map { it.name })
        assertEquals(10_000, policy.timeoutMs)
        assertEquals(10_000, policy.channelReadTimeoutMs)
        assertEquals(10_000, policy.channelErrorReadTimeoutMs)
        assertEquals(1_048_576, policy.maxCircularBufferSize)
    }

    @Test fun terminalIdleReadOverrideDoesNotMutateTheNextFiniteProbePolicy() {
        val terminal = SshConnectionPolicy.create()
        terminal.channelReadTimeoutMs = 0
        terminal.channelErrorReadTimeoutMs = 0
        val finite = SshConnectionPolicy.create()
        assertEquals(10_000, finite.channelReadTimeoutMs)
        assertEquals(10_000, finite.channelErrorReadTimeoutMs)
        assertEquals(10_000, terminal.timeoutMs)
    }
}
