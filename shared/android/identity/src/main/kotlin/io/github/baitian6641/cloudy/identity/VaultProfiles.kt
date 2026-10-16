package io.github.baitian6641.cloudy.identity

import android.content.Context
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Non-secret expected authentication metadata; never includes an account name or display label. */
class VaultProfile(val id: CredentialId, val binding: VaultBinding) {
    override fun toString(): String = "VaultProfile(redacted)"
}

/** Blocking, per-app metadata. Missing first-use storage is empty; malformed storage never is. */
class VaultProfiles internal constructor(
    private val directory: File,
    private val ownerPackage: String,
    private val onMainThread: () -> Boolean,
) {
    constructor(context: Context) : this(
        File(context.applicationContext.noBackupFilesDir, "cloudy-vault-profiles-v1"),
        context.applicationContext.packageName,
        { Looper.myLooper() == Looper.getMainLooper() },
    ) { require(!context.isDeviceProtectedStorage) { "VAULT_PROFILE_CONTEXT" } }

    fun list(): List<VaultProfile> = guarded { read().toList() }

    fun put(profile: VaultProfile) = guarded {
        check(profile.binding.ownerPackage == ownerPackage)
        val current = read()
        val previous = current.find { it.id == profile.id }
        if (previous != null) {
            check(previous.binding == profile.binding)
        } else {
            check(current.size < MAX_RECORDS)
            write(current + profile)
        }
    }

    /** Caller serializes with enrollment and verifies credential deletion or complete absence first. */
    fun remove(id: CredentialId) = guarded {
        val current = read()
        if (current.any { it.id == id }) write(current.filterNot { it.id == id })
    }

    private fun <T> guarded(action: () -> T): T {
        // Reject UI misuse before waiting behind a worker's disk operation.
        check(!onMainThread()) { "VAULT_PROFILE_MAIN_THREAD" }
        return synchronized(lock) {
            try { action() } catch (_: Exception) { throw IllegalStateException("VAULT_PROFILE_UNAVAILABLE") }
        }
    }

    private fun file(): AtomicFile {
        check(!Files.isSymbolicLink(directory.toPath()))
        check(!directory.exists() || directory.isDirectory)
        val base = File(directory, "profiles.bin")
        for (suffix in listOf("", ".bak", ".new")) {
            val candidate = File(base.path + suffix)
            check(!Files.isSymbolicLink(candidate.toPath()))
            check(!candidate.exists() || candidate.isFile)
        }
        return AtomicFile(base)
    }

    private fun read(): List<VaultProfile> {
        val atomic = file()
        val base = atomic.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists()) {
            check(!File(base.path + ".new").exists())
            return emptyList()
        }
        val raw = atomic.openRead().use { it.readNBytes(MAX_BYTES + 1) }
        check(raw.size <= MAX_BYTES)
        return decode(raw)
    }

    private fun write(profiles: List<VaultProfile>) {
        val bytes = encode(profiles)
        val atomic = file()
        check(directory.isDirectory || directory.mkdirs())
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (failure: Exception) { atomic.failWrite(stream); throw failure }
        val actual = atomic.openRead().use { it.readNBytes(MAX_BYTES + 1) }
        check(bytes.contentEquals(actual))
        decode(actual)
    }

    private fun encode(profiles: List<VaultProfile>): ByteArray {
        check(profiles.size <= MAX_RECORDS)
        val output = ByteBuffer.allocate(MAX_BYTES).putInt(MAGIC).putInt(1).putInt(profiles.size)
        fun text(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            check(bytes.size <= MAX_FIELD)
            output.putInt(bytes.size).put(bytes)
        }
        for (profile in profiles) {
            check(profile.binding.ownerPackage == ownerPackage)
            text(profile.id.value)
            with(profile.binding) {
                text(ownerPackage); text(protocol.name); text(host); output.putInt(port)
                text(role.name); text(accountBindingId)
            }
        }
        return output.array().copyOf(output.position())
    }

    private fun decode(raw: ByteArray): List<VaultProfile> {
        val input = ByteBuffer.wrap(raw)
        check(input.remaining() >= 12 && input.int == MAGIC && input.int == 1)
        val count = input.int
        check(count in 0..MAX_RECORDS)
        fun text(): String {
            check(input.remaining() >= 4)
            val size = input.int
            check(size in 1..MAX_FIELD && size <= input.remaining())
            val slice = input.slice().apply { limit(size) }
            val value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(slice).toString()
            input.position(input.position() + size)
            return value
        }
        val ids = mutableSetOf<CredentialId>()
        val result = ArrayList<VaultProfile>(count)
        repeat(count) {
            val id = CredentialId.parse(text())
            check(ids.add(id))
            val owner = text()
            check(owner == ownerPackage)
            val protocol = VaultProtocol.valueOf(text())
            val host = text()
            check(input.remaining() >= 4)
            val port = input.int
            val role = VaultRole.valueOf(text())
            val account = text()
            val binding = VaultBinding.create(owner, protocol, host, port, role, account)
            check(binding.host == host) // Serialized metadata must already be canonical.
            result += VaultProfile(id, binding)
        }
        check(!input.hasRemaining())
        return result
    }

    companion object {
        private const val MAGIC = 0x43565031 // CVP1
        private const val MAX_RECORDS = 64
        private const val MAX_FIELD = 1024
        private const val MAX_BYTES = 131072
        private val lock = Any()
    }
}
