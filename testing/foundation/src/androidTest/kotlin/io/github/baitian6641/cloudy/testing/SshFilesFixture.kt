package io.github.baitian6641.cloudy.testing

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfileVersion
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfilesCode
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import io.github.baitian6641.cloudy.platform.SshHostTrustStore
import io.github.baitian6641.cloudy.platform.SshTrustCatalog
import io.github.baitian6641.cloudy.platform.SshTrustStoreCode
import io.github.baitian6641.cloudy.platform.SshTrustStoreResult
import java.io.File
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Private fixture credentials never enter args, receipts, assertion values or diagnostics. */
internal class SshFilesFixture private constructor(
    private val context: Context, private val owner: WindowOwner, val run: String,
    val case: String, private val configuration: JSONObject,
) {
    val username: String get() = configuration.getString("username")
    val password: String get() = configuration.getString("password")
    private val profiles = ConnectionProfiles(context)
    private val trust = SshHostTrustStore(context)
    private val profileBaseline = profile { profiles.load() }
    private val trustBaseline = trust { trust.load() }
    private val peer = SshPeerIdentity.create("127.0.0.1", configuration.getInt("port"))
    private val key = SshHostKeyIdentity.create(configuration.getString("keyFormat"), configuration.getString("fingerprint"))
    private val trustFixture = SshObserveTrustFixture(context, owner, trustBaseline)
    private val plannedPin = SshTrustSnapshot.create(owner, trustBaseline.generation, peer, 1, SshTrustStatus.accepted(key))
    private val directory = File(context.noBackupFilesDir, "ssh-files-qualification")
    private val receiptFile = File(directory, "$run-${owner.wireId}-$case.json")
    private val journal = JSONArray()
    private val receipt = JSONObject().put("schema", 1).put("run", run).put("owner", owner.wireId).put("case", case)
        .put("package", context.packageName).put("uid", Process.myUid()).put("caseOutcome", "STARTED")
        .put("profileBaseline", profileJson(profileBaseline)).put("trustBaseline", trustJson(trustBaseline))
        .put("trustMetadata", JSONArray(trustFixture.baselineMetadata())).put("phases", journal)
    private var ownedProfile: ConnectionProfile? = null
    val profileId: String get() = checkNotNull(ownedProfile).id
    val observedProfile: SshObservationProfile get() = SshObservationProfile.create(profileBaseline.generation, checkNotNull(expectedProfile))
    val acceptedPin: SshTrustSnapshot get() = plannedPin
    private var expectedProfile: ConnectionProfile? = null
    private var mutationUncertain = false

    init {
        check(trustBaseline.snapshots.none { it.peer == peer }) { "FILES_FIXTURE_PEER_NOT_ABSENT" }
        check(!Files.isSymbolicLink(directory.toPath()))
        if (!directory.exists()) { check(directory.mkdir()); Os.chmod(directory.path, 448) }
        val directoryInfo = Os.lstat(directory.path)
        check(OsConstants.S_ISDIR(directoryInfo.st_mode) && directoryInfo.st_uid == Process.myUid() &&
            directoryInfo.st_mode and 511 == 448)
        check(listOf("", ".bak", ".new").none { File(receiptFile.path + it).exists() ||
            Files.isSymbolicLink(File(receiptFile.path + it).toPath()) })
        if (case == "hold") {
            val ack = File(File(context.noBackupFilesDir, "ssh-files-fixture"), "$run-$case-read-hold.json")
            check(!ack.exists() && !Files.isSymbolicLink(ack.toPath())) { "EXISTING_SERVER_ACK_PRESERVE" }
        }
        settle()
        phase("BASELINE_BEFORE_MUTATION")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshFilesReceipt", receiptFile.absolutePath)
            putString("cloudySshFilesCase", case)
        })
    }

    fun seed() {
        val label = "Files ${owner.wireId} $run $case"
        receipt.put("plannedLabel", label).put("plannedHost", peer.host).put("plannedPort", peer.port)
        receipt.put("plannedTrust", trustJson(trustBaseline, trustBaseline.snapshots + plannedPin))
        phase("PROFILE_CREATE_PLANNED")
        ownedProfile = profile { profiles.create(profileBaseline.generation,
            ConnectionProfileDraft.create(owner, label, ConnectionProtocol.SSH, peer.host, peer.port)) }.profile
        expectedProfile = ownedProfile
        receipt.put("ownedProfile", profileJson(profileBaseline.generation, listOf(checkNotNull(ownedProfile))))
        phase("TRUST_ACCEPT_PLANNED")
        val accepted = trust { trust.accept(SshTrustChallenge.create(UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), trust { trust.evaluate(peer, key) })) }
        check(accepted == plannedPin) { "FILES_FIXTURE_ENROLLMENT_CHANGED" }
        assertUnchanged()
        phase("READY")
    }

    /** Independently record the exact revised row before attempting the only fixture update. */
    fun mutateProfile() {
        check(case == "profile" && !mutationUncertain)
        assertUnchanged()
        val old = checkNotNull(expectedProfile)
        val draft = ConnectionProfileDraft.create(owner, old.label + " edited", old.protocol, old.host, old.port)
        val planned = old.revised(draft)
        receipt.put("plannedProfileUpdate", profileJson(profileBaseline.generation, listOf(planned)))
        phase("PROFILE_UPDATE_PLANNED")
        mutationUncertain = true
        val updated = profile { profiles.update(ConnectionProfileVersion.create(profileBaseline.generation,
            old.id, old.revision), draft) }.profile
        check(profileJson(profileBaseline.generation, listOf(updated)) ==
            profileJson(profileBaseline.generation, listOf(planned))) { "FILES_PROFILE_UPDATE_CHANGED" }
        expectedProfile = planned
        mutationUncertain = false
        receipt.put("expectedProfile", profileJson(profileBaseline.generation, listOf(planned)))
        phase("PROFILE_UPDATED")
    }

    fun recordUiEvidence(savedStates: Int, savedClean: Boolean, frameworkFocusInjected: Boolean) {
        check(savedStates in 0..16)
        receipt.put("savedStateCount", savedStates).put("savedStateExcludedFixtureCredentials", savedClean)
            .put("frameworkFocusInjected", frameworkFocusInjected)
            .put("focusRoute", if (frameworkFocusInjected) "Framework callback injection; not physical desktop focus" else "Not injected")
        phase("UI_EVIDENCE_RECORDED")
    }

    fun recordFailure(stage: String, errorClass: String) {
        check(Regex("[A-Z_]{1,64}").matches(stage) && Regex("[A-Za-z0-9_$]{1,128}").matches(errorClass))
        receipt.put("failureStage", stage).put("failureClass", errorClass)
        phase("FAILED")
    }

    fun phase(value: String) {
        if (value == "PASSED" || value == "FAILED") receipt.put("caseOutcome", value)
        check(Regex("[A-Z_]{1,64}").matches(value))
        check(journal.length() < 48)
        journal.put(JSONObject().put("phase", value).put("elapsedRealtimeMillis", SystemClock.elapsedRealtime()))
        receipt.put("phase", value)
        val atomic = AtomicFile(receiptFile)
        val output = atomic.startWrite()
        try {
            output.write(receipt.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
            Os.chmod(receiptFile.path, 384)
        }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    fun awaitActualReadHold() {
        phase("WAITING_FOR_READ_HOLD")
        val file = File(File(context.noBackupFilesDir, "ssh-files-fixture"), "$run-$case-read-hold.json")
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (!file.exists() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        check(file.exists()) { "ACTUAL_READ_HOLD_UNCONFIRMED" }
        val bytes = privateBytes(file, 1024)
        val ack = parse(bytes)
        check(ack.keys().asSequence().toSet() == setOf("schema", "run", "owner", "case", "readHoldEntered") &&
            ack.opt("schema") == 1 && ack.opt("run") == run && ack.opt("owner") == owner.wireId &&
            ack.opt("case") == case && ack.opt("readHoldEntered") == true) { "READ_HOLD_ACK_INVALID" }
        receipt.put("readHoldAckSha256", MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) })
        phase("READ_HOLD_CONFIRMED")
    }

    fun assertUnchanged() {
        check(!mutationUncertain) { "FILES_MUTATION_UNCERTAIN_PRESERVE_FIXTURE" }
        check(profileJson(profile { profiles.load() }) ==
            profileJson(profileBaseline.generation, profileBaseline.profiles + listOfNotNull(expectedProfile))) { "FILES_PROFILE_CHANGED" }
        check(trustJson(trust { trust.load() }) == trustJson(trustBaseline, trustBaseline.snapshots + plannedPin)) {
            "FILES_TRUST_CHANGED"
        }
    }

    /** Caller first closes every Activity; fences cover the real process runtime, not a fake queue. */
    fun restore(passed: Boolean) {
        try { restoreExact(passed) }
        catch (error: Throwable) {
            try {
                recordFailure("CLEANUP", error.javaClass.simpleName.ifEmpty { "Throwable" })
                phase("UNSETTLED_OR_CHANGED_PRESERVE_FIXTURE")
            } catch (_: Throwable) { /* Existing receipt remains evidence if storage is unavailable. */ }
            throw AssertionError("FILES_FIXTURE_CLEANUP_UNCONFIRMED")
        }
    }

    private fun restoreExact(passed: Boolean) {
        settle()
        assertUnchanged()
        check(profileJson(profile { profiles.load() }) ==
            profileJson(profileBaseline.generation, profileBaseline.profiles + listOfNotNull(expectedProfile))) {
            "FILES_PROFILE_UNCERTAIN_PRESERVE_FIXTURE"
        }
        phase("RESTORING_EXACT_FIXTURE")
        trustFixture.removeOnlyExpected(plannedPin)
        expectedProfile?.let { row ->
            profile { profiles.delete(ConnectionProfileVersion.create(profileBaseline.generation, row.id, row.revision)) }
        }
        check(profileJson(profile { profiles.load() }) == profileJson(profileBaseline))
        check(trustJson(trust { trust.load() }) == trustJson(trustBaseline))
        phase(if (passed) "PASSED" else "FAILED")
        phase("EXACT_BASELINE_RESTORED")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshFilesCleanup", "EXACT_BASELINE_RESTORED")
            putString("cloudySshFilesCase", case)
        })
    }

    companion object {
        const val HELLO_NAME = "hello.txt"
        const val HELLO_TEXT = "Cloudy retained SFTP\n"

        fun open(context: Context, owner: WindowOwner, run: String, case: String): SshFilesFixture {
            check(InstrumentationRegistry.getArguments().getString("cloudySshFilesRun") == run) {
                "FILES_OPT_IN_REQUIRED"
            }
            check(Looper.myLooper() != Looper.getMainLooper()) { "FILES_FIXTURE_WRONG_THREAD" }
            check(context.packageName == "io.github.baitian6641.cloudy.${owner.wireId}" &&
                context.applicationInfo.uid == Process.myUid() && !context.isDeviceProtectedStorage)
            check(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(run))
            check(case in setOf("basic", "recreate", "hide", "focus", "profile", "hold", "register", "registration-focus", "registered-location"))
            val directory = File(context.noBackupFilesDir, "ssh-files-fixture")
            val info = Os.lstat(directory.path)
            check(!Files.isSymbolicLink(directory.toPath()) && OsConstants.S_ISDIR(info.st_mode) &&
                info.st_uid == Process.myUid() && info.st_mode and 511 == 448)
            val config = parse(privateBytes(File(directory, "$run.json"), 16_384))
            check(config.keys().asSequence().toSet() == setOf("schema", "run", "owner", "host", "port", "keyFormat", "fingerprint",
                "username", "password"))
            check(config.opt("schema") == 1 && config.opt("run") == run && config.opt("owner") == owner.wireId &&
                config.opt("host") == "127.0.0.1" && config.opt("port") is Int && config.getInt("port") in 1..65535 &&
                config.opt("keyFormat") == "ecdsa-sha2-nistp256" && config.opt("fingerprint") is String)
            for (field in listOf("username")) {
                val value = config.opt(field) as? String ?: error("FILES_FIXTURE_FIELD_INVALID")
                check(value.length in 1..256 && value.none { Character.isISOControl(it) || Character.isSurrogate(it) })
            }
            val password = config.opt("password") as? String ?: error("FILES_FIXTURE_FIELD_INVALID")
            check(password.length in 1..4096 && password.none { Character.isSurrogate(it) })
            return SshFilesFixture(context, owner, run, case, config)
        }

        private fun privateBytes(file: File, maximum: Int): ByteArray {
            val info = Os.lstat(file.path)
            check(!Files.isSymbolicLink(file.toPath()) && OsConstants.S_ISREG(info.st_mode) &&
                info.st_uid == Process.myUid() && info.st_mode and 511 == 384 && info.st_size in 1..maximum.toLong())
            return FileChannel.open(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                check(channel.size() == info.st_size)
                val buffer = ByteBuffer.allocate(info.st_size.toInt())
                while (buffer.hasRemaining()) check(channel.read(buffer) > 0)
                check(channel.size() == info.st_size)
                buffer.array()
            }
        }
        private fun parse(bytes: ByteArray): JSONObject = try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val parser = JSONTokener(text)
            val value = parser.nextValue() as? JSONObject ?: error("PRIVATE_FILES_FIXTURE_INVALID")
            check(parser.nextClean() == '\u0000')
            value
        } catch (_: Exception) { error("PRIVATE_FILES_FIXTURE_INVALID") }

        /** Caller closes ALL owned Activities first; no production runtime hook is added. */
        fun settle() {
            check(Looper.myLooper() != Looper.getMainLooper()) { "FILES_SETTLE_WRONG_THREAD" }
            val runtime = Class.forName("io.github.baitian6641.cloudy.platform.FilesRuntime")
            fun field(name: String): Any {
                val value = runtime.getDeclaredField(name).apply { isAccessible = true }
                check(Modifier.isStatic(value.modifiers))
                return checkNotNull(value.get(null))
            }
            val worker = field("worker") as ThreadPoolExecutor
            val retirement = field("retirement") as ThreadPoolExecutor
            check(worker.corePoolSize == 0 && worker.maximumPoolSize == 1 && worker.queue is java.util.concurrent.SynchronousQueue<*>)
            check(retirement.corePoolSize == 0 && retirement.maximumPoolSize == 1 && retirement.queue is ArrayBlockingQueue<*>)
            check(retirement.queue.size + retirement.queue.remainingCapacity() == 1)
            val deadline = SystemClock.elapsedRealtime() + 25_000
            fun fence(executor: ThreadPoolExecutor) {
                val fence = CountDownLatch(1)
                while (true) {
                    try { executor.execute { fence.countDown() }; break }
                    catch (_: java.util.concurrent.RejectedExecutionException) {
                        check(SystemClock.elapsedRealtime() < deadline) { "FILES_WORK_UNSETTLED" }
                        SystemClock.sleep(15)
                    }
                }
                check(fence.await((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                    "FILES_WORK_UNSETTLED"
                }
            }
            repeat(2) {
                fence(worker)
                fence(retirement)
                val main = CountDownLatch(1)
                check(Handler(Looper.getMainLooper()).post { main.countDown() })
                check(main.await(5, TimeUnit.SECONDS)) { "FILES_MAIN_UNSETTLED" }
            }
            while ((worker.activeCount != 0 || retirement.activeCount != 0) && SystemClock.elapsedRealtime() < deadline)
                SystemClock.sleep(10)
            check(worker.activeCount == 0 && retirement.activeCount == 0 && worker.queue.isEmpty() && retirement.queue.isEmpty()) {
                "FILES_LANES_UNSETTLED"
            }
            check((field("current") as AtomicReference<*>).get() == null) { "FILES_RUNTIME_OWNERSHIP_UNSETTLED" }
            check(!(field("quarantined") as AtomicBoolean).get()) { "FILES_RUNTIME_QUARANTINED" }
            val factory = field("factory")
            val quarantine = factory.javaClass.getDeclaredField("quarantined").apply { isAccessible = true }
            check(!(quarantine.get(factory) as AtomicBoolean).get()) { "FILES_ENGINE_QUARANTINED" }
            val previous = factory.javaClass.getDeclaredField("previous").apply { isAccessible = true }.get(factory)
            // Factory busy deliberately remains sticky until its next admission. Inspect actual
            // owner+notification retirement instead of clearing that flag or inventing a reset.
            if (previous != null) {
                val fullyRetired = previous.javaClass.getDeclaredMethod("fullyRetired").apply { isAccessible = true }
                check(fullyRetired.invoke(previous) == true) { "FILES_ENGINE_RETIREMENT_UNCONFIRMED" }
            } else {
                val busy = factory.javaClass.getDeclaredField("busy").apply { isAccessible = true }
                check(!(busy.get(factory) as AtomicBoolean).get()) { "FILES_ENGINE_BUSY" }
            }
        }
        private fun <T> profile(action: () -> ConnectionProfilesResult<T>): T {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            do {
                when (val value = action()) {
                    is ConnectionProfilesResult.Success -> return value.value
                    is ConnectionProfilesResult.Failure -> check(value.code == ConnectionProfilesCode.BUSY) { "FILES_PROFILE_DENIED" }
                }
                SystemClock.sleep(15)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("FILES_PROFILE_BUSY")
        }
        private fun <T> trust(action: () -> SshTrustStoreResult<T>): T {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            do {
                when (val value = action()) {
                    is SshTrustStoreResult.Success -> return value.value
                    is SshTrustStoreResult.Failure -> check(value.code == SshTrustStoreCode.BUSY) { "FILES_TRUST_DENIED" }
                }
                SystemClock.sleep(15)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("FILES_TRUST_BUSY")
        }
        private fun profileJson(catalog: ConnectionProfileCatalog) = profileJson(catalog.generation, catalog.profiles)
        private fun profileJson(generation: String, rows: List<ConnectionProfile>): String = JSONObject()
            .put("generation", generation).put("rows", JSONArray(rows.sortedBy { it.id }.map {
                JSONObject().put("id", it.id).put("revision", it.revision).put("owner", it.owner.wireId)
                    .put("label", it.label).put("protocol", it.protocol.wireId).put("host", it.host).put("port", it.port)
            })).toString()
        private fun trustJson(catalog: SshTrustCatalog, rows: List<SshTrustSnapshot> = catalog.snapshots): String = JSONObject()
            .put("owner", catalog.owner.wireId).put("generation", catalog.generation).put("rows", JSONArray(rows.sortedBy {
                "${it.peer.host}/${it.peer.port}/${it.peer.route.wireId}"
            }.map {
                val status = it.status
                val key = when (status) { is SshTrustStatus.Accepted -> status.key; is SshTrustStatus.Revoked -> status.lastKey }
                JSONObject().put("host", it.peer.host).put("port", it.peer.port).put("route", it.peer.route.wireId)
                    .put("owner", it.owner.wireId).put("revision", it.revision).put("revoked", status is SshTrustStatus.Revoked)
                    .put("format", key.keyFormat).put("fingerprint", key.fingerprint)
            })).toString()
    }
}
