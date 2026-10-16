package io.github.baitian6641.cloudy.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** Actual provider policy, not an assertion of hardware backing. */
data class VaultKeyInfo(val securityLevel: Int, val authenticationInHardware: Boolean)

internal interface VaultCrypto {
    fun exists(id: CredentialId): Boolean
    fun generate(id: CredentialId): VaultKeyInfo
    fun encrypt(id: CredentialId, aad: ByteArray, plaintext: ByteArray): VaultEnvelope
    fun decrypt(envelope: VaultEnvelope, aad: ByteArray): ByteArray
    fun delete(id: CredentialId)
}

internal class AndroidVaultCrypto : VaultCrypto {
    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun exists(id: CredentialId): Boolean = store().containsAlias(alias(id))
    override fun generate(id: CredentialId): VaultKeyInfo {
        check(!exists(id))
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias(id),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(30,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .setUnlockedDeviceRequired(true).build())
        val key = generator.generateKey()
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        check(info.isUserAuthenticationRequired && info.userAuthenticationValidityDurationSeconds == 30 &&
            info.userAuthenticationType == (KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL))
        return VaultKeyInfo(info.securityLevel, info.isUserAuthenticationRequirementEnforcedBySecureHardware)
    }
    private fun key(id: CredentialId): SecretKey =
        checkNotNull(store().getKey(alias(id), null) as? SecretKey)
    override fun encrypt(id: CredentialId, aad: ByteArray, plaintext: ByteArray): VaultEnvelope {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // The provider chooses a fresh randomized IV; caller-supplied encryption IVs are forbidden.
        cipher.init(Cipher.ENCRYPT_MODE, key(id))
        check(cipher.parameters.getParameterSpec(GCMParameterSpec::class.java).tLen == 128)
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext)
        return try { VaultEnvelope.create(id, 1, cipher.iv, encrypted) }
        finally { encrypted.fill(0) }
    }
    override fun decrypt(envelope: VaultEnvelope, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = envelope.copyIv()
        val encrypted = envelope.copyCiphertext()
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key(envelope.id), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            // No partial plaintext is exposed before tag verification completes.
            cipher.doFinal(encrypted)
        } finally { iv.fill(0); encrypted.fill(0) }
    }
    override fun delete(id: CredentialId) { store().deleteEntry(alias(id)) }
    companion object {
        internal fun alias(id: CredentialId): String {
            CredentialId.parse(id.value)
            return "cloudy.vault.v1.${id.value}"
        }
    }
}
