package io.github.baitian6641.cloudy.identity

import io.github.baitian6641.cloudy.domain.EndpointHost
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

        fun canonicalHost(host: String): String = EndpointHost.canonicalize(host)
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
