package io.github.baitian6641.cloudy.platform

import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshLocalNetworkPolicyTest {
    @Test fun privateAddressIsNotPermissionAuthorityForVpnCellularOrUnknownRoutes() {
        for (route in SshLocalNetworkPolicy.Route.entries) {
            assertFalse(SshLocalNetworkPolicy.shouldRequestPermission(route, false))
            org.junit.Assert.assertEquals(route == SshLocalNetworkPolicy.Route.BROADCAST,
                SshLocalNetworkPolicy.shouldRequestPermission(route, true))
        }
    }
    private fun ipv4(vararg bytes: Int) = InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray())
    private fun ipv6(first: Int, second: Int = 0) = InetAddress.getByAddress(ByteArray(16).apply {
        this[0] = first.toByte(); this[1] = second.toByte(); this[15] = 1
    })

    @Test fun localNamesAndPrivateAddressesPromptWithoutBlockingPublicOrLoopback() {
        assertTrue(SshLocalNetworkPolicy.requiresLocalAccess(SshPeerIdentity.create("workstation.local", 22)))
        assertFalse(SshLocalNetworkPolicy.requiresLocalAccess(SshPeerIdentity.create("workstation.example", 22)))
        for (address in listOf(ipv4(10, 0, 0, 1), ipv4(172, 16, 0, 1), ipv4(192, 168, 0, 1),
            ipv4(169, 254, 1, 1), ipv6(0xfc), ipv6(0xfd), ipv6(0xfe, 0x80))) {
            assertTrue(SshLocalNetworkPolicy.requiresLocalAccess(address))
        }
        for (address in listOf(ipv4(127, 0, 0, 1), ipv6(0), ipv4(192, 0, 2, 1), ipv6(0x20, 0x01))) {
            assertFalse(SshLocalNetworkPolicy.requiresLocalAccess(address))
        }
    }
}
