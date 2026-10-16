package io.github.baitian6641.cloudy.domain

import java.net.IDN

/** Logical endpoint normalization only; never resolves DNS or network interfaces. */
object EndpointHost {
    fun canonicalize(host: String): String {
        require(host.isNotEmpty() && host.length <= 1024 && host.none {
            it.isWhitespace() || Character.isISOControl(it) || it in "/\\@?#"
        }) { "Invalid logical host" }
        if (host.contains(':') || host.startsWith('[') || host.endsWith(']')) {
            val address = if (host.startsWith('[') && host.endsWith(']')) host.substring(1, host.length - 1) else host
            require('[' !in address && ']' !in address) { "Invalid numeric host" }
            val parts = address.split('%')
            require(parts.size <= 2) { "Invalid address scope" }
            val zone = if (parts.size == 2) parts[1].also {
                require(it.length in 1..64 && it.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid address scope" }
            } else null
            val words = ipv6(parts[0])
            // A unique expanded representation avoids network/interface lookup entirely.
            return words.joinToString(":") { it.toString(16) } + (zone?.let { "%$it" } ?: "")
        }
        require('%' !in host) { "Invalid logical host" }
        val bare = if (host.endsWith('.')) host.dropLast(1) else host
        require(bare.isNotEmpty()) { "Invalid logical host" }
        if (bare.all { it in '0'..'9' || it == '.' }) return ipv4(bare).joinToString(".")
        val ascii = try { IDN.toASCII(bare, IDN.USE_STD3_ASCII_RULES).lowercase(java.util.Locale.ROOT) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid logical host") }
        require(ascii.length in 1..253 && ascii.split('.').all {
            it.length in 1..63 && it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
        }) { "Invalid logical host" }
        return ascii
    }

    private fun ipv4(value: String): List<Int> {
        val parts = value.split('.')
        require(parts.size == 4) { "Invalid numeric host" }
        return parts.map {
            require(it.length in 1..3 && it.all { c -> c in '0'..'9' } && (it.length == 1 || it[0] != '0')) {
                "Invalid numeric host"
            }
            it.toInt().also { number -> require(number <= 255) { "Invalid numeric host" } }
        }
    }

    private fun ipv6(value: String): List<Int> {
        require(value.isNotEmpty()) { "Invalid numeric host" }
        val numeric = if ('.' in value) {
            val colon = value.lastIndexOf(':')
            require(colon >= 0) { "Invalid numeric host" }
            val octets = ipv4(value.substring(colon + 1))
            value.substring(0, colon + 1) + ((octets[0] shl 8) or octets[1]).toString(16) +
                ":" + ((octets[2] shl 8) or octets[3]).toString(16)
        } else value
        val halves = numeric.split("::")
        require(halves.size <= 2) { "Invalid numeric host" }
        fun words(part: String): List<Int> = if (part.isEmpty()) emptyList() else part.split(':').map {
            require(it.length in 1..4 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' }) {
                "Invalid numeric host"
            }
            it.toInt(16)
        }
        val left = words(halves[0])
        if (halves.size == 1) {
            require(left.size == 8) { "Invalid numeric host" }
            return left
        }
        val right = words(halves[1])
        require(left.size + right.size < 8) { "Invalid numeric host" }
        return left + List(8 - left.size - right.size) { 0 } + right
    }
}
