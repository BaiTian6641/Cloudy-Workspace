package io.github.baitian6641.cloudy.testing

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfileVersion
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in production-store isolation phases. Never clears app data or writes a sibling's files. */
@RunWith(AndroidJUnit4::class)
class ProfileIsolationTest {
    @Test fun installedProductsKeepProfileCatalogsAndFilesSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val run = arguments.getString("cloudyProfileIsolationRun")
        val phase = arguments.getString("cloudyProfileIsolationPhase")
        // Default test discovery skips before a Context path, receipt or store is accessed.
        assumeTrue("Requires explicit isolation run and phase", run != null || phase != null)
        try {
            expect(run != null && uuid.matches(run), Code.INVALID_ARGUMENTS)
            expect(phase in setOf("seed", "verify", "cleanup"), Code.INVALID_ARGUMENTS)
            val peerPathWitness = arguments.getString("cloudyProfileIsolationPeerPathSha256")
            if (phase == "verify") {
                expect(peerPathWitness != null && sha256.matches(peerPathWitness), Code.INVALID_ARGUMENTS)
            }
            val context = instrumentation.targetContext
            val owner = when (context.packageName) {
                CONSOLE -> WindowOwner.CONSOLE
                DESKTOP -> WindowOwner.DESKTOP
                else -> throw Rejected(Code.WRONG_OWNER)
            }
            expect(!context.isDeviceProtectedStorage, Code.WRONG_STORAGE)
            val fixture = Fixture(context, owner, checkNotNull(run))
            when (phase) {
                "seed" -> fixture.seed()
                "verify" -> fixture.verify(checkNotNull(peerPathWitness)) { observation ->
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("cloudyProfileIsolationOwnPathSha256", observation.ownPathSha256)
                        putString("cloudyProfileIsolationPeerPathSha256", observation.peerPathSha256)
                        putString("cloudyProfileIsolationOwnAccess", "READABLE_REGULAR_FILE")
                        putInt("cloudyProfileIsolationPeerErrno", observation.errno)
                        putString("cloudyProfileIsolationPeerAccess", observation.outcome)
                    })
                }
                "cleanup" -> fixture.cleanup()
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("cloudyProfileIsolationStatus", "PASSED")
                putString("cloudyProfileIsolationPhase", phase)
                putInt("cloudyProfileIsolationUid", Process.myUid())
                putInt("cloudyProfileIsolationPid", Process.myPid())
            })
        } catch (failure: Rejected) {
            throw AssertionError(failure.code.name)
        } catch (_: Exception) {
            // Platform exceptions can contain private paths or submitted metadata.
            throw AssertionError(Code.UNAVAILABLE.name)
        }
    }

    private class Fixture(private val context: Context, private val owner: WindowOwner, private val run: String) {
        private val store = ConnectionProfiles(context)
        private val directory = File(context.noBackupFilesDir, "profile-isolation-qualification")
        private val receipt = AtomicFile(File(directory, "receipt.bin"))
        private val label get() = label(run, owner)
        private val peerLabel get() = label(run, if (owner == WindowOwner.CONSOLE) WindowOwner.DESKTOP else WindowOwner.CONSOLE)

        fun seed() {
            checkReceiptPaths()
            expect(!receiptExists(), Code.EXISTING_RECEIPT)
            val before = loaded()
            expect(before.profiles.none { it.label == label || it.label == peerLabel }, Code.EXISTING_FIXTURE)
            expect(directory.isDirectory || directory.mkdir(), Code.RECEIPT_UNAVAILABLE)
            // Persist intent before the only create call. An interrupted or uncertain attempt
            // leaves evidence that refuses automatic reseeding, even if no row is visible later.
            write(Receipt("CREATING", run, owner.wireId, before.generation, "", 0,
                label, ConnectionProtocol.SSH.wireId, HOST, PORT))
            val draft = ConnectionProfileDraft.create(owner, label, ConnectionProtocol.SSH, HOST, PORT)
            val entry = successful(store.create(before.generation, draft))
            expectProfile(entry.profile, entry.version)
            write(Receipt("READY", run, owner.wireId, entry.version.generation, entry.version.id, entry.version.revision,
                entry.profile.label, entry.profile.protocol.wireId, entry.profile.host, entry.profile.port))
            val after = loaded()
            expect(after.generation == before.generation && after.profiles.size == before.profiles.size + 1,
                Code.CATALOG_CHANGED)
            // The test creates only its own new ID; existing metadata must remain unchanged.
            expect(before.profiles.all { previous -> after.profiles.any { sameProfile(previous, it) } }, Code.CATALOG_CHANGED)
            expect(after.profiles.none { it.label == peerLabel }, Code.PEER_VISIBLE)
        }

        fun verify(peerPathWitness: String, report: (AccessObservation) -> Unit) {
            val saved = readyReceipt()
            val catalog = loaded()
            expect(catalog.generation == saved.generation, Code.STALE_RECEIPT)
            val profile = catalog.profiles.singleOrNull { it.id == saved.id } ?: throw Rejected(Code.FIXTURE_MISSING)
            expectProfile(profile, saved.version())
            expect(catalog.profiles.count { it.label == label } == 1, Code.CATALOG_CHANGED)
            expect(catalog.profiles.none { it.label == peerLabel }, Code.PEER_VISIBLE)
            checkPeerDatabaseInaccessible(peerPathWitness, report)
        }

        fun cleanup() {
            val saved = readyReceipt()
            val before = loaded()
            expect(before.generation == saved.generation, Code.STALE_RECEIPT)
            val profile = before.profiles.singleOrNull { it.id == saved.id } ?: throw Rejected(Code.FIXTURE_MISSING)
            expectProfile(profile, saved.version())
            successful(store.delete(saved.version()))
            val after = loaded()
            expect(after.generation == before.generation && after.profiles.none { it.id == saved.id || it.label == label },
                Code.DELETE_UNCONFIRMED)
            expect(after.profiles.size == before.profiles.size - 1, Code.CATALOG_CHANGED)
            expect(before.profiles.filterNot { it.id == saved.id }.all { previous ->
                after.profiles.any { sameProfile(previous, it) }
            }, Code.CATALOG_CHANGED)
            // Delete only this app's receipt after verifying the exact fixture row is gone.
            receipt.delete()
            expect(!receiptExists(), Code.RECEIPT_UNAVAILABLE)
        }

        private fun loaded(): ConnectionProfileCatalog = successful(store.load())

        private fun expectProfile(profile: ConnectionProfile, version: ConnectionProfileVersion) {
            expect(profile.id == version.id && profile.revision == version.revision && profile.owner == owner &&
                profile.label == label && profile.protocol == ConnectionProtocol.SSH && profile.host == HOST && profile.port == PORT,
                Code.FIXTURE_CHANGED)
        }

        private fun checkPeerDatabaseInaccessible(peerPathWitness: String, report: (AccessObservation) -> Unit) {
            val peer = if (owner == WindowOwner.CONSOLE) DESKTOP else CONSOLE
            val parent = File(context.applicationInfo.dataDir).parentFile ?: throw Rejected(Code.WRONG_STORAGE)
            val peerDatabase = File(parent, "$peer/no_backup/connection-profiles-v1/profiles.db")
            val peerDigest = pathDigest(peerDatabase)
            // The operator must obtain this exact peer path from package state and read the
            // existing database as its owning run-as UID immediately before EACH verify phase.
            // A digest binds the external witness to this probe; it does not itself prove that
            // the operator performed that read. Retain those receipts with the phase results.
            expect(peerDigest == peerPathWitness, Code.PEER_PATH_WITNESS_MISMATCH)
            val ownDatabase = File(context.noBackupFilesDir, "connection-profiles-v1/profiles.db")
            val ownDescriptor = try {
                Os.open(ownDatabase.path, OsConstants.O_RDONLY, 0)
            } catch (_: ErrnoException) {
                throw Rejected(Code.OWN_DATABASE_UNREADABLE)
            }
            try {
                val status = Os.fstat(ownDescriptor)
                expect(OsConstants.S_ISREG(status.st_mode) && status.st_size > 0L, Code.OWN_DATABASE_UNREADABLE)
            } finally { Os.close(ownDescriptor) }
            val ownDigest = pathDigest(ownDatabase)
            val descriptor = try {
                Os.open(peerDatabase.path, OsConstants.O_RDONLY, 0)
            } catch (failure: ErrnoException) {
                val outcome = when (failure.errno) {
                    OsConstants.EACCES, OsConstants.EPERM -> "PERMISSION_DENIED"
                    OsConstants.ENOENT -> "NOT_VISIBLE"
                    else -> "OTHER_ERROR"
                }
                report(AccessObservation(ownDigest, peerDigest, failure.errno, outcome))
                // ENOENT is eligible only under the exact externally witnessed peer-path
                // contract above, never for an arbitrary missing path. It does not establish
                // why the existing peer database is not visible in this app's context.
                expect(outcome == "PERMISSION_DENIED" || outcome == "NOT_VISIBLE", Code.PEER_DENIAL_UNCONFIRMED)
                return
            }
            // Opening is already a failed isolation check, regardless of a later close error.
            try { Os.close(descriptor) } catch (_: ErrnoException) { /* Preserve the opening verdict. */ }
            report(AccessObservation(ownDigest, peerDigest, 0, "READABLE"))
            throw Rejected(Code.PEER_READ_ALLOWED)
        }

        private fun readyReceipt(): Receipt {
            checkReceiptPaths()
            expect(receiptExists(), Code.RECEIPT_MISSING)
            val value = read()
            expect(value.state == "READY" && value.run == run && value.owner == owner.wireId,
                Code.STALE_RECEIPT)
            expect(uuid.matches(value.generation) && uuid.matches(value.id) && value.revision == 1L,
                Code.INVALID_RECEIPT)
            expect(value.label == label && value.protocol == ConnectionProtocol.SSH.wireId &&
                value.host == HOST && value.port == PORT, Code.INVALID_RECEIPT)
            value.version()
            return value
        }

        private fun checkReceiptPaths() {
            expect(!Files.isSymbolicLink(directory.toPath()) && (!directory.exists() || directory.isDirectory),
                Code.RECEIPT_UNAVAILABLE)
            for (suffix in listOf("", ".bak", ".new")) {
                val path = File(receipt.baseFile.path + suffix)
                expect(!Files.isSymbolicLink(path.toPath()) && (!path.exists() || path.isFile), Code.RECEIPT_UNAVAILABLE)
            }
        }

        private fun receiptExists(): Boolean = listOf("", ".bak", ".new").any {
            File(receipt.baseFile.path + it).exists()
        }

        private fun write(value: Receipt) {
            val bytes = encode(value)
            val stream = receipt.startWrite()
            try { stream.write(bytes); receipt.finishWrite(stream) }
            catch (error: Exception) { receipt.failWrite(stream); throw error }
            expect(bytes.contentEquals(readBytes()), Code.RECEIPT_UNAVAILABLE)
        }

        private fun readBytes(): ByteArray = receipt.openRead().use {
            it.readNBytes(MAX_RECEIPT_BYTES + 1).also { bytes -> expect(bytes.size <= MAX_RECEIPT_BYTES, Code.INVALID_RECEIPT) }
        }

        private fun read(): Receipt = DataInputStream(ByteArrayInputStream(readBytes())).use { input ->
            expect(input.readInt() == 1, Code.INVALID_RECEIPT)
            fun text(): String {
                val size = input.readUnsignedShort()
                expect(size in 0..256 && size <= input.available(), Code.INVALID_RECEIPT)
                val bytes = input.readNBytes(size)
                return Charsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            }
            val state = text()
            val run = text()
            val owner = text()
            val generation = text()
            val id = text()
            val revision = input.readLong()
            val value = Receipt(state, run, owner, generation, id, revision, text(), text(), text(), input.readInt())
            expect(input.available() == 0, Code.INVALID_RECEIPT)
            value
        }

        private fun encode(value: Receipt): ByteArray = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(1)
                for (text in listOf(value.state, value.run, value.owner, value.generation, value.id)) {
                    expect(text.length <= 256 && text.all { it.code < 128 }, Code.INVALID_RECEIPT)
                    output.writeShort(text.length)
                    output.write(text.toByteArray(Charsets.US_ASCII))
                }
                output.writeLong(value.revision)
                for (text in listOf(value.label, value.protocol, value.host)) {
                    expect(text.length <= 256 && text.all { it.code < 128 }, Code.INVALID_RECEIPT)
                    output.writeShort(text.length)
                    output.write(text.toByteArray(Charsets.US_ASCII))
                }
                output.writeInt(value.port)
            }
            bytes.toByteArray().also { expect(it.size <= MAX_RECEIPT_BYTES, Code.INVALID_RECEIPT) }
        }
    }

    private data class Receipt(
        val state: String, val run: String, val owner: String, val generation: String, val id: String, val revision: Long,
        val label: String, val protocol: String, val host: String, val port: Int,
    ) {
        fun version(): ConnectionProfileVersion = ConnectionProfileVersion.create(generation, id, revision)
        override fun toString(): String = "IsolationReceipt(redacted)"
    }

    private class AccessObservation(
        val ownPathSha256: String, val peerPathSha256: String, val errno: Int, val outcome: String,
    )

    private enum class Code {
        INVALID_ARGUMENTS, WRONG_OWNER, WRONG_STORAGE, EXISTING_RECEIPT, EXISTING_FIXTURE,
        RECEIPT_UNAVAILABLE, RECEIPT_MISSING, INVALID_RECEIPT, STALE_RECEIPT, FIXTURE_MISSING,
        FIXTURE_CHANGED, CATALOG_CHANGED, PEER_VISIBLE, PEER_READ_ALLOWED, PEER_DENIAL_UNCONFIRMED,
        PEER_PATH_WITNESS_MISMATCH, OWN_DATABASE_UNREADABLE,
        DELETE_UNCONFIRMED, STORE_FAILURE, UNAVAILABLE,
    }
    private class Rejected(val code: Code) : Exception(code.name)

    companion object {
        private const val CONSOLE = "io.github.baitian6641.cloudy.console"
        private const val DESKTOP = "io.github.baitian6641.cloudy.desktop"
        private const val HOST = "isolation.invalid"
        private const val PORT = 22
        private const val MAX_RECEIPT_BYTES = 2048
        private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        private val sha256 = Regex("[0-9a-f]{64}")
        private fun pathDigest(path: File): String = MessageDigest.getInstance("SHA-256")
            .digest(path.path.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        private fun label(run: String, owner: WindowOwner): String = "Cloudy isolation $run ${owner.wireId}"
        private fun expect(condition: Boolean, code: Code) { if (!condition) throw Rejected(code) }
        private fun <T> successful(result: ConnectionProfilesResult<T>): T = when (result) {
            is ConnectionProfilesResult.Success -> result.value
            is ConnectionProfilesResult.Failure -> throw Rejected(Code.STORE_FAILURE)
        }
        private fun sameProfile(a: ConnectionProfile, b: ConnectionProfile): Boolean =
            a.id == b.id && a.revision == b.revision && a.owner == b.owner && a.label == b.label &&
                a.protocol == b.protocol && a.host == b.host && a.port == b.port
    }
}
