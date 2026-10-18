package io.github.baitian6641.cloudy.ssh

/**
 * Single-transfer per-session authentication material. A credential is validated and copied on
 * creation, transferred at most once to the owning engine, and wiped on closure. No serialization,
 * logging, diagnostic plaintext or persistence belongs to this type; app-owned storage resolves a
 * reference and supplies the material for exactly one attempt.
 */
abstract class SshSessionCredentials : AutoCloseable {
    abstract val isClosed: Boolean

    /** Exact account metadata from the still-owned copy; never a secret or authentication proof. */
    abstract fun accountName(): String?

    /** Transfers owned material once; a null result means the credential was already closed. */
    internal abstract fun take(): SshCredentialMaterial?
}

/** Engine-owned material. The concrete subtype is the only method the engine may attempt. */
internal interface SshCredentialMaterial : AutoCloseable {
    val username: CharArray
}
