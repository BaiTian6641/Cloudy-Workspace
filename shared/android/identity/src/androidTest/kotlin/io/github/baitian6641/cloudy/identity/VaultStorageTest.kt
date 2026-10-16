package io.github.baitian6641.cloudy.identity

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/** Real AtomicFile recovery tests; the injected key failure is not Keystore/device qualification. */
class VaultStorageTest {
    private lateinit var directory: File
    private lateinit var crypto: FailureCrypto
    private lateinit var storage: VaultStorage
    private val id = CredentialId.create()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.noBackupFilesDir, "vault-test-${CredentialId.create().value}")
        check(directory.mkdir())
        crypto = FailureCrypto()
        storage = VaultStorage(directory, context.packageName, crypto) { false }
    }
    @After fun cleanup() { directory.deleteRecursively() }
    private fun journal(state: VaultRecordState) = File(directory, "${id.value}.state").apply {
        writeText("1\n${id.value}\n${state.name}", Charsets.US_ASCII)
    }

    @Test fun incompleteCreationIsVisibleAndCannotBeCleanupAdopted() {
        journal(VaultRecordState.CREATING)
        crypto.present = true
        assertEquals(VaultRecordState.CREATING, storage.inspect().records.single().state)
        assertEquals(VaultCode.INCOMPLETE, storage.retryPendingDeletion(id).code)
        assertEquals(VaultCode.INCOMPLETE, storage.purgeCompletedDeletion(id).code)
        assertTrue(crypto.present)
        assertEquals(0, crypto.deletions)
    }
    @Test fun failedDeletionRemainsPendingThenCompletesAndPurges() {
        val stateFile = journal(VaultRecordState.DELETE_PENDING)
        val record = File(directory, "${id.value}.record").apply { writeBytes(byteArrayOf(1, 2)) }
        crypto.present = true
        crypto.failDelete = true
        assertEquals(VaultCode.DELETE_PENDING, storage.retryPendingDeletion(id).code)
        assertEquals(VaultRecordState.DELETE_PENDING, storage.inspect().records.single().state)
        assertTrue(record.exists())
        crypto.failDelete = false
        assertEquals(VaultCode.OK, storage.retryPendingDeletion(id).code)
        assertFalse(record.exists())
        assertFalse(crypto.present)
        assertEquals(VaultRecordState.DELETED, storage.inspect().records.single().state)
        assertTrue(stateFile.exists())
        assertEquals(VaultCode.OK, storage.purgeCompletedDeletion(id).code)
        assertFalse(stateFile.exists())
        assertTrue(storage.inspect().records.isEmpty())
    }
    @Test fun completedTombstoneCannotBePurgedOverRemainingKey() {
        val stateFile = journal(VaultRecordState.DELETED)
        crypto.present = true
        assertEquals(VaultCode.STORAGE_ERROR, storage.purgeCompletedDeletion(id).code)
        assertTrue(stateFile.exists())
        assertTrue(crypto.present)
    }
    @Test fun malformedOrOrphanedFilesAreUnavailableWithoutAdoption() {
        File(directory, "${id.value}.record").writeBytes(byteArrayOf(1))
        assertEquals(VaultCode.STORAGE_ERROR, storage.inspect().code)
        assertEquals(0, crypto.deletions)
    }
    @Test fun excessiveDirectoryEntriesStopInspection() {
        repeat(VaultStorage.MAX_RECORDS + 1) {
            val next = CredentialId.create()
            File(directory, "${next.value}.state").writeText("1\n${next.value}\nCREATING")
        }
        assertEquals(VaultCode.STORAGE_ERROR, storage.inspect().code)
    }
    @Test fun mainThreadGuardRejectsInspectionAndCleanup() {
        val blocked = VaultStorage(directory, "test.owner", crypto) { true }
        assertEquals(VaultCode.MAIN_THREAD, blocked.inspect().code)
        assertEquals(VaultCode.MAIN_THREAD, blocked.retryPendingDeletion(id).code)
        assertEquals(VaultCode.MAIN_THREAD, blocked.purgeCompletedDeletion(id).code)
    }
    @Test fun verifiedAbsenceAllowsProfileCleanupOnlyWithoutAnyOwnedArtifact() {
        assertEquals(VaultCode.OK, storage.verifyAbsent(id).code)
        crypto.present = true
        assertEquals(VaultCode.INCOMPLETE, storage.verifyAbsent(id).code)
        assertTrue(crypto.present)
        crypto.present = false
        for (state in VaultRecordState.entries) {
            val file = journal(state)
            assertEquals(VaultCode.INCOMPLETE, storage.verifyAbsent(id).code)
            assertTrue(file.exists())
            check(file.delete())
        }
        assertEquals(VaultCode.OK, storage.verifyAbsent(id).code)
    }

    @Test fun recordAndAtomicWriteRemnantsNeverAuthorizeMetadataRemoval() {
        for (suffix in listOf(".state", ".state.new", ".state.bak",
                ".record", ".record.new", ".record.bak")) {
            val remnant = File(directory, id.value + suffix).apply { writeBytes(byteArrayOf(1)) }
            assertEquals(VaultCode.INCOMPLETE, storage.verifyAbsent(id).code)
            assertTrue(remnant.exists())
            check(remnant.delete())
        }
        assertEquals(VaultCode.OK, storage.verifyAbsent(id).code)
    }

    private fun binding(host: String = "localhost") = VaultBinding.create(
        ApplicationProvider.getApplicationContext<Context>().packageName, VaultProtocol.SSH,
        host, 22, VaultRole.TARGET, id.value)
    private fun authority() = VaultAuthorization(binding().ownerPackage, java.util.UUID.randomUUID()) { 100L }
        .apply { setVisible(true) }
    private fun grant(auth: VaultAuthorization, operation: VaultOperation, binding: VaultBinding = binding()) =
        auth.succeeded(auth.begin(operation, id, binding))
    private fun payload() = SensitivePayload.create(charArrayOf('u'), charArrayOf(), charArrayOf('p'))

    @Test fun wrongOwnerRejectsBeforeFileOrKeyMutationAndClosesInput() {
        val auth = authority()
        val wrong = VaultBinding.create("different.owner", VaultProtocol.SSH, "localhost", 22,
            VaultRole.TARGET, id.value)
        val owned = payload()
        assertEquals(VaultCode.WRONG_OWNER,
            storage.create(id, wrong, grant(auth, VaultOperation.SAVE), owned).code)
        assertTrue(storage.inspect().records.isEmpty())
        assertFalse(crypto.present)
        var closed = false
        try { owned.copyPassword() } catch (_: IllegalStateException) { closed = true }
        assertTrue(closed)
    }
    @Test fun revokedCreationRetainsIncompleteAndDoesNotAdoptKey() {
        val auth = authority()
        crypto.beforeEncrypt = { auth.cancel() }
        assertEquals(VaultCode.AUTH_REQUIRED,
            storage.create(id, binding(), grant(auth, VaultOperation.SAVE), payload()).code)
        assertTrue(crypto.present)
        assertEquals(VaultRecordState.CREATING, storage.inspect().records.single().state)
        auth.setVisible(true)
        assertEquals(VaultCode.ALREADY_EXISTS,
            storage.create(id, binding(), grant(auth, VaultOperation.SAVE), payload()).code)
    }
    @Test fun scopedUseChecksBindingAndClosesConsumerPayload() {
        val auth = authority()
        assertEquals(VaultCode.OK,
            storage.create(id, binding(), grant(auth, VaultOperation.SAVE), payload()).code)
        var called = false
        val changed = binding("changed.invalid")
        assertEquals(VaultCode.UNAVAILABLE,
            storage.use(id, changed, grant(auth, VaultOperation.USE, changed)) { called = true }.code)
        assertFalse(called)
        var escaped: SensitivePayload? = null
        assertEquals(VaultCode.OK,
            storage.use(id, binding(), grant(auth, VaultOperation.USE)) {
                escaped = it
                val password = it.copyPassword()
                try { assertTrue(password.contentEquals(charArrayOf('p'))) }
                finally { password.fill('\u0000') }
            }.code)
        var closed = false
        try { escaped!!.copyPassword() } catch (_: IllegalStateException) { closed = true }
        assertTrue(closed)
    }
    @Test fun revocationDuringDecryptPreventsConsumerAndGrantReplay() {
        val auth = authority()
        assertEquals(VaultCode.OK,
            storage.create(id, binding(), grant(auth, VaultOperation.SAVE), payload()).code)
        crypto.afterDecrypt = { auth.cancel() }
        val useGrant = grant(auth, VaultOperation.USE)
        var called = false
        assertEquals(VaultCode.AUTH_REQUIRED,
            storage.use(id, binding(), useGrant) { called = true }.code)
        assertFalse(called)
        assertEquals(VaultCode.AUTH_REQUIRED,
            storage.use(id, binding(), useGrant) { called = true }.code)
    }
    private class FailureCrypto : VaultCrypto {
        var present = false
        var failDelete = false
        var deletions = 0
        var beforeEncrypt: () -> Unit = {}
        var afterDecrypt: () -> Unit = {}
        private val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 7 }, "AES")
        override fun exists(id: CredentialId) = present
        override fun delete(id: CredentialId) {
            deletions++
            if (failDelete) throw IOException("Injected test failure")
            present = false
        }
        override fun generate(id: CredentialId): VaultKeyInfo {
            check(!present); present = true; return VaultKeyInfo(0, false)
        }
        override fun encrypt(id: CredentialId, aad: ByteArray, plaintext: ByteArray): VaultEnvelope {
            beforeEncrypt()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(aad)
            return VaultEnvelope.create(id, 1, cipher.iv, cipher.doFinal(plaintext))
        }
        override fun decrypt(envelope: VaultEnvelope, aad: ByteArray): ByteArray {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key,
                javax.crypto.spec.GCMParameterSpec(128, envelope.copyIv()))
            cipher.updateAAD(aad)
            return cipher.doFinal(envelope.copyCiphertext()).also { afterDecrypt() }
        }
    }
}
