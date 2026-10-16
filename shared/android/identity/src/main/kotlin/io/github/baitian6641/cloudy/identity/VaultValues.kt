package io.github.baitian6641.cloudy.identity

import java.net.IDN
import java.util.UUID

enum class VaultProtocol { SSH, RDP, VNC, STREAM }
enum class VaultRole { TARGET, GATEWAY }

class CredentialId private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is CredentialId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "CredentialId(redacted)"
    companion object {
        fun create(): CredentialId = CredentialId(UUID.randomUUID().toString())
        fun parse(value: String): CredentialId {
            require(value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))) {
                "Invalid credential identifier"
            }
            return CredentialId(value)
        }
    }
}

/** Logical authentication identity; never a resolved transport/tunnel address. */
class VaultBinding private constructor(
    val ownerPackage: String,
    val protocol: VaultProtocol,
    val host: String,
    val port: Int,
    val role: VaultRole,
    val accountBindingId: String,
) {
    override fun equals(other: Any?): Boolean = other is VaultBinding &&
        ownerPackage == other.ownerPackage && protocol == other.protocol && host == other.host &&
        port == other.port && role == other.role && accountBindingId == other.accountBindingId
    override fun hashCode(): Int = listOf(ownerPackage, protocol, host, port, role, accountBindingId).hashCode()
    override fun toString(): String = "VaultBinding(redacted)"

    companion object {
        fun create(ownerPackage: String, protocol: VaultProtocol, host: String, port: Int,
                   role: VaultRole, accountBindingId: String): VaultBinding {
            require(ownerPackage.length <= 200 && ownerPackage.matches(
                Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) { "Invalid owning package" }
            require(port in 1..65535) { "Invalid endpoint port" }
            CredentialId.parse(accountBindingId)
            return VaultBinding(ownerPackage, protocol, canonicalHost(host), port, role, accountBindingId)
        }

        fun canonicalHost(host: String): String {
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
}

/** Owns copies; callers must wipe their original input arrays independently. */
class SensitivePayload private constructor(
    private val username: CharArray,
    private val domain: CharArray,
    private val password: CharArray,
) : AutoCloseable {
    private var closed = false
    @Synchronized fun copyUsername(): CharArray { check(!closed); return username.copyOf() }
    @Synchronized fun copyDomain(): CharArray { check(!closed); return domain.copyOf() }
    @Synchronized fun copyPassword(): CharArray { check(!closed); return password.copyOf() }
    @Synchronized override fun close() {
        username.fill('\u0000'); domain.fill('\u0000'); password.fill('\u0000'); closed = true
    }
    override fun toString(): String = "SensitivePayload(redacted)"
    companion object {
        const val MAX_IDENTITY_CHARS = 256
        const val MAX_PASSWORD_CHARS = 4096
        fun create(username: CharArray, domain: CharArray, password: CharArray): SensitivePayload {
            require(username.size <= MAX_IDENTITY_CHARS && domain.size <= MAX_IDENTITY_CHARS &&
                password.size in 1..MAX_PASSWORD_CHARS) { "Invalid credential field size" }
            // Validate without producing immutable secret Strings.
            for (field in listOf(username, domain, password)) {
                var i = 0
                while (i < field.size) {
                    val c = field[i++]
                    require(!Character.isLowSurrogate(c)) { "Invalid credential Unicode" }
                    if (Character.isHighSurrogate(c)) {
                        require(i < field.size && Character.isLowSurrogate(field[i++])) { "Invalid credential Unicode" }
                    }
                }
            }
            return SensitivePayload(username.copyOf(), domain.copyOf(), password.copyOf())
        }
    }
}

class VaultEnvelope private constructor(
    val id: CredentialId,
    val keyVersion: Int,
    private val iv: ByteArray,
    private val ciphertext: ByteArray,
) {
    fun copyIv(): ByteArray = iv.copyOf()
    fun copyCiphertext(): ByteArray = ciphertext.copyOf()
    override fun toString(): String = "VaultEnvelope(redacted)"
    companion object {
        const val MAX_CIPHERTEXT_BYTES = 65536
        fun create(id: CredentialId, keyVersion: Int, iv: ByteArray, ciphertext: ByteArray): VaultEnvelope {
            require(keyVersion == 1 && iv.size == 12 && ciphertext.size in 16..MAX_CIPHERTEXT_BYTES) {
                "Invalid vault envelope"
            }
            return VaultEnvelope(id, keyVersion, iv.copyOf(), ciphertext.copyOf())
        }
    }
}
