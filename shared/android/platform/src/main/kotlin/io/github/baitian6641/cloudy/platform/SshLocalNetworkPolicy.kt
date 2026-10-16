package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.net.InetAddress

/**
 * Permission-prompt hints for direct SSH, not an override of Android's network enforcement.
 * DNS itself is permitted without LAN access, except local service names. Public-address
 * routes can still be local on a particular network; the platform remains authoritative.
 * Loopback does not access a LAN. USB-forwarded qualification must be labeled as such.
 */
object SshLocalNetworkPolicy {
    enum class Route { BROADCAST, VPN, CELLULAR, UNKNOWN }

    fun shouldRequestPermission(context: Context, peer: SshPeerIdentity): Boolean =
        shouldRequestPermission(route(context), requiresLocalAccess(peer))

    fun shouldRequestPermission(context: Context, address: InetAddress): Boolean =
        shouldRequestPermission(route(context), requiresLocalAccess(address))

    fun shouldRequestPermission(route: Route, knownLocalCandidate: Boolean): Boolean =
        route == Route.BROADCAST && knownLocalCandidate

    private fun route(context: Context): Route = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
        when {
            capabilities == null -> Route.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Route.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Route.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Route.BROADCAST
            else -> Route.UNKNOWN
        }
    } catch (_: SecurityException) { Route.UNKNOWN }

    fun requiresLocalAccess(peer: SshPeerIdentity): Boolean =
        peer.host == "local" || peer.host.endsWith(".local")

    fun requiresLocalAccess(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        if (address.isAnyLocalAddress || address.isSiteLocalAddress ||
            address.isLinkLocalAddress || address.isMulticastAddress) return true
        // InetAddress.isSiteLocalAddress covers IPv4 RFC1918 and historical IPv6 site-local,
        // but does not include RFC4193 unique-local unicast (fc00::/7).
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }
}
