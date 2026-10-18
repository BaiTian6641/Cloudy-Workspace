package io.github.baitian6641.cloudy.ssh

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import java.io.StringReader
import java.util.concurrent.atomic.AtomicBoolean
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

/** The only key failures this adapter classifies; upstream exception text is never surfaced. */
internal enum class SshKeyFailure { UNREADABLE, UNSUPPORTED_FORMAT, PASSPHRASE_REQUIRED }

internal class SshKeyException(val failure: SshKeyFailure) : Exception("KEY_MATERIAL_REJECTED")

/**
 * Parses one admitted key with the maintained SSHJ readers. Supported formats are OpenSSH v1
 * (`BEGIN OPENSSH PRIVATE KEY`), legacy OpenSSH PEM and RSA PKCS#8. PuTTY keys, EC PKCS#8 (the
 * upstream reader cannot derive its public half) and unknown armor are refused explicitly rather
 * than guessed. Parsing happens only after exact host-pin admission. SSHJ's Reader API forces an
 * immutable text copy, so this adapter makes no complete-JVM-zeroization claim; the owned byte
 * array, username and passphrase are wiped, and no text enters logs or storage.
 */
internal object SshKeyAuthentication {
    fun provider(
        material: SshKeyCredentials.Material,
        cancellation: SshObservationCancellation,
        active: () -> Boolean,
    ): KeyProvider {
        // SSHJ requires text; the bounded UTF-8 copy is validated by SshKeyCredentials and is
        // released with the material. No text is retained by this object.
        val text = String(material.key, Charsets.UTF_8)
        val format = try {
            KeyProviderUtil.detectKeyFileFormat(StringReader(text), true)
        } catch (_: Exception) {
            throw SshKeyException(SshKeyFailure.UNREADABLE)
        }
        val provider: FileKeyProvider = when (format) {
            KeyFormat.OpenSSHv1 -> OpenSSHKeyV1KeyFile()
            KeyFormat.OpenSSH -> OpenSSHKeyFile()
            KeyFormat.PKCS8 -> PKCS8KeyFile()
            else -> throw SshKeyException(SshKeyFailure.UNSUPPORTED_FORMAT)
        }
        val finder = material.passphrase?.let {
            SshOneReleaseKeyPassphraseFinder(material, cancellation, active)
        }
        try {
            provider.init(StringReader(text), finder)
            // Force the parse now so an encrypted/wrong-passphrase key fails at this owned boundary.
            provider.getPublic()
            provider.getPrivate()
            return provider
        } catch (_: Exception) {
            throw SshKeyException(
                if (material.passphrase == null && encrypted(text, format)) {
                    SshKeyFailure.PASSPHRASE_REQUIRED
                } else {
                    SshKeyFailure.UNREADABLE
                },
            )
        }
    }

    /** Bounded armor/KDF probe; only selects the local failure code, never parses decrypted content. */
    internal fun encrypted(text: String, format: KeyFormat): Boolean {
        if (text.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----") ||
            text.contains("Proc-Type: 4,ENCRYPTED")
        ) return true
        if (format != KeyFormat.OpenSSHv1) return false
        val body = text.lineSequence()
            .dropWhile { it.trim() != "-----BEGIN OPENSSH PRIVATE KEY-----" }
            .drop(1)
            .takeWhile { !it.startsWith("-----END OPENSSH PRIVATE KEY-----") }
            .joinToString("")
        return try {
            val bytes = java.util.Base64.getDecoder().decode(body)
            if (bytes.size < 19 || String(bytes, 0, 15, Charsets.US_ASCII) != "openssh-key-v1\u0000") return false
            val length = java.nio.ByteBuffer.wrap(bytes, 15, 4).int
            if (length !in 1..64 || bytes.size < 19 + length) return false
            val cipher = String(bytes, 19, length, Charsets.US_ASCII)
            !cipher.equals("none", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}

/** Releases the supplied passphrase at most once; no second guess and no fallback method. */
internal class SshOneReleaseKeyPassphraseFinder(
    private val material: SshKeyCredentials.Material,
    private val cancellation: SshObservationCancellation,
    private val active: () -> Boolean,
) : PasswordFinder {
    private val released = AtomicBoolean(false)

    override fun reqPassword(resource: Resource<*>): CharArray = cancellation.tryIfActive {
        if (!active() || !released.compareAndSet(false, true)) throw UserAuthException("CREDENTIALS_UNAVAILABLE")
        material.passphrase ?: throw UserAuthException("KEY_PASSPHRASE_UNAVAILABLE")
    } ?: throw UserAuthException("AUTHENTICATION_STOPPED")

    override fun shouldRetry(resource: Resource<*>): Boolean = false
}
