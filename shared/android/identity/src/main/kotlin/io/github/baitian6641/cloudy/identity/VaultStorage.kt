package io.github.baitian6641.cloudy.identity

import android.content.Context
import android.os.Looper
import android.security.keystore.UserNotAuthenticatedException
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** All results are bounded codes; provider exceptions and private metadata never escape. */
enum class VaultCode { OK, AUTH_REQUIRED, WRONG_OWNER, NOT_FOUND, INCOMPLETE, DELETE_PENDING,
    ALREADY_EXISTS, CAPACITY, UNAVAILABLE, MAIN_THREAD, STORAGE_ERROR }
enum class VaultRecordState { CREATING, READY, DELETE_PENDING, DELETED }
data class VaultResult(val code: VaultCode, val keyInfo: VaultKeyInfo? = null)
data class VaultRecordStatus(val id: CredentialId, val state: VaultRecordState)
data class VaultInspection(val code: VaultCode, val records: List<VaultRecordStatus>)

/** Blocking API: the owning app submits work to its single background worker. No IPC or export. */
class VaultStorage internal constructor(
    private val directory: File,
    private val ownerPackage: String,
    private val crypto: VaultCrypto,
    private val onMainThread: () -> Boolean,
) {
    constructor(context: Context) : this(
        File(context.applicationContext.noBackupFilesDir, "cloudy-vault-v1"),
        context.applicationContext.packageName, AndroidVaultCrypto(),
        { Looper.myLooper() == Looper.getMainLooper() },
    ) {
        require(!context.isDeviceProtectedStorage) { "Credential-encrypted context required" }
    }

    fun create(id: CredentialId, binding: VaultBinding, grant: VaultGrant,
               payload: SensitivePayload): VaultResult = try {
        serialized {
            guarded(binding) {
                grant.consume(VaultOperation.SAVE, id, binding).use { lease ->
                    lease.check()
                    ensureDirectory()
                    if (hasFiles(id) || crypto.exists(id)) return@guarded VaultResult(VaultCode.ALREADY_EXISTS)
                    if (scan().size >= MAX_RECORDS) return@guarded VaultResult(VaultCode.CAPACITY)
                    writeState(id, VaultRecordState.CREATING)
                    lease.check()
                    val info = crypto.generate(id)
                    val plaintext = VaultCodec.encodePayload(payload)
                    val aad = VaultCodec.aad(binding, id)
                    try {
                        lease.check()
                        val envelope = crypto.encrypt(id, aad, plaintext)
                        val bytes = VaultCodec.encodeEnvelope(envelope)
                        try { writeReadback(record(id), bytes) } finally { bytes.fill(0) }
                        lease.check()
                        writeState(id, VaultRecordState.READY)
                        VaultResult(VaultCode.OK, info)
                    } finally { plaintext.fill(0); aad.fill(0) }
                }
            }
        }
    } finally { payload.close() }

    fun use(id: CredentialId, binding: VaultBinding, grant: VaultGrant,
            consumer: (SensitivePayload) -> Unit): VaultResult = serialized {
        guarded(binding) {
            grant.consume(VaultOperation.USE, id, binding).use { lease ->
                lease.check()
                when (state(id)) {
                    null -> return@guarded VaultResult(VaultCode.NOT_FOUND)
                    VaultRecordState.READY -> Unit
                    VaultRecordState.DELETE_PENDING, VaultRecordState.DELETED ->
                        return@guarded VaultResult(VaultCode.DELETE_PENDING)
                    else -> return@guarded VaultResult(VaultCode.INCOMPLETE)
            }
            if (!crypto.exists(id)) return@guarded VaultResult(VaultCode.UNAVAILABLE)
            val envelope = VaultCodec.decodeEnvelope(readBounded(record(id), MAX_ENVELOPE))
            check(envelope.id == id)
            val aad = VaultCodec.aad(binding, id)
            try {
                lease.check()
                val plaintext = crypto.decrypt(envelope, aad)
                try {
                    VaultCodec.decodePayload(plaintext).use { payload ->
                        lease.check()
                        consumer(payload)
                    }
                } finally { plaintext.fill(0) }
            } finally { aad.fill(0) }
            VaultResult(VaultCode.OK)
            }
        }
    }

    fun delete(id: CredentialId, binding: VaultBinding, grant: VaultGrant): VaultResult =
        serialized {
            guarded(binding) {
                grant.consume(VaultOperation.DELETE, id, binding).use { lease ->
                    lease.check()
                    if (state(id) == null) return@guarded VaultResult(VaultCode.NOT_FOUND)
                    // Durable denial precedes destructive cleanup, including interrupted creations.
                    writeState(id, VaultRecordState.DELETE_PENDING)
                    finishDeletion(id)
                }
            }
        }

    /**
     * Absence snapshot for profile-only cleanup, not a reservation. The caller must serialize
     * metadata removal with enrollment through the same owning app worker. Corrupt/remnant
     * files never grant removal, and this operation deletes neither files nor keys.
     */
    fun verifyAbsent(id: CredentialId): VaultResult = serialized {
        guarded(null) {
            if (!hasFiles(id) && !crypto.exists(id)) VaultResult(VaultCode.OK)
            else VaultResult(VaultCode.INCOMPLETE)
        }
    }

    /** Explicit local retry cannot decrypt and requires an existing durable deletion tombstone. */
    fun retryPendingDeletion(id: CredentialId): VaultResult = serialized {
        guarded(null) {
            when (state(id)) {
                VaultRecordState.DELETE_PENDING -> finishDeletion(id)
                VaultRecordState.DELETED -> {
                    check(!crypto.exists(id) && !atomicExists(record(id)))
                    VaultResult(VaultCode.OK)
                }
                else -> VaultResult(VaultCode.INCOMPLETE)
            }
        }
    }

    /** Remove only a completed, read-back tombstone after independently verifying absence again. */
    fun purgeCompletedDeletion(id: CredentialId): VaultResult = serialized {
        guarded(null) {
            if (state(id) != VaultRecordState.DELETED) return@guarded VaultResult(VaultCode.INCOMPLETE)
            check(!crypto.exists(id) && !atomicExists(record(id)))
            journal(id).delete()
            check(!atomicExists(journal(id)) && !atomicExists(record(id)) && !crypto.exists(id))
            VaultResult(VaultCode.OK)
        }
    }

    fun inspect(): VaultInspection {
        if (onMainThread()) return VaultInspection(VaultCode.MAIN_THREAD, emptyList())
        return synchronized(processLock) {
        try {
            VaultInspection(VaultCode.OK, scan())
        } catch (_: Exception) { VaultInspection(VaultCode.STORAGE_ERROR, emptyList()) }
        }
    }

    private inline fun serialized(action: () -> VaultResult): VaultResult {
        // Reject before acquiring the worker lock so a mistaken UI call cannot block behind disk I/O.
        if (onMainThread()) return VaultResult(VaultCode.MAIN_THREAD)
        return synchronized(processLock) { action() }
    }

    private fun finishDeletion(id: CredentialId): VaultResult {
        try {
            crypto.delete(id)
            record(id).delete()
            check(!crypto.exists(id) && !atomicExists(record(id)))
            writeState(id, VaultRecordState.DELETED)
            // Retain bounded completion proof. Tombstones count toward capacity; no flash-erasure claim.
            return VaultResult(VaultCode.OK)
        } catch (_: Exception) { return VaultResult(VaultCode.DELETE_PENDING) }
    }
    private inline fun guarded(binding: VaultBinding?, action: () -> VaultResult): VaultResult {
        if (onMainThread()) return VaultResult(VaultCode.MAIN_THREAD)
        if (binding != null && binding.ownerPackage != ownerPackage) return VaultResult(VaultCode.WRONG_OWNER)
        return try { action() }
        catch (_: VaultAuthorizationException) { VaultResult(VaultCode.AUTH_REQUIRED) }
        catch (_: UserNotAuthenticatedException) { VaultResult(VaultCode.AUTH_REQUIRED) }
        catch (_: java.security.GeneralSecurityException) { VaultResult(VaultCode.UNAVAILABLE) }
        catch (_: Exception) { VaultResult(VaultCode.STORAGE_ERROR) }
    }
    private fun ensureDirectory() { check(directory.isDirectory || directory.mkdirs()) }
    private fun journal(id: CredentialId): AtomicFile = AtomicFile(File(directory, "${id.value}.state"))
    private fun record(id: CredentialId): AtomicFile = AtomicFile(File(directory, "${id.value}.record"))
    private fun atomicExists(file: AtomicFile): Boolean = listOf("", ".new", ".bak").any {
        File(file.baseFile.path + it).exists()
    }
    private fun hasFiles(id: CredentialId): Boolean = atomicExists(journal(id)) || atomicExists(record(id))
    private fun state(id: CredentialId): VaultRecordState? {
        val file = journal(id)
        if (!atomicExists(file)) {
            check(!atomicExists(record(id)))
            return null
        }
        val text = readBounded(file, 128).toString(Charsets.US_ASCII)
        val fields = text.split('\n')
        check(fields.size == 3 && fields[0] == "1" && fields[1] == id.value)
        return VaultRecordState.valueOf(fields[2])
    }
    private fun writeState(id: CredentialId, state: VaultRecordState) {
        writeReadback(journal(id), "1\n${id.value}\n${state.name}".toByteArray(Charsets.US_ASCII))
        check(this.state(id) == state)
    }
    private fun writeReadback(file: AtomicFile, bytes: ByteArray) {
        ensureDirectory()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
        val readback = readBounded(file, MAX_ENVELOPE)
        try { check(bytes.contentEquals(readback)) } finally { readback.fill(0) }
    }
    private fun readBounded(file: AtomicFile, maximum: Int): ByteArray = file.openRead().use {
        val bytes = it.readNBytes(maximum + 1)
        if (bytes.size > maximum) { bytes.fill(0); throw IOException("Vault size limit") }
        bytes
    }
    private fun scan(): List<VaultRecordStatus> {
        if (!directory.exists()) return emptyList()
        val ids = linkedSetOf<CredentialId>()
        var entries = 0
        Files.newDirectoryStream(directory.toPath()).use { paths ->
            for (path in paths) {
                check(++entries <= MAX_RECORDS * 6)
                val name = path.fileName.toString()
                val match = checkNotNull(FILE_NAME.matchEntire(name))
                ids.add(CredentialId.parse(match.groupValues[1]))
                check(ids.size <= MAX_RECORDS)
            }
        }
        return ids.map { VaultRecordStatus(it, checkNotNull(state(it))) }
    }
    companion object {
        const val MAX_RECORDS = 64
        private const val MAX_ENVELOPE = 131072
        private val FILE_NAME = Regex("([0-9a-f-]{36})\\.(?:state|record)(?:\\.new|\\.bak)?")
        // AtomicFile does not serialize writers. All instances in this process share this lock.
        private val processLock = Any()
    }
}
