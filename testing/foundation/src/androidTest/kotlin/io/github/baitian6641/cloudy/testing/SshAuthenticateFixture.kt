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

/** Private fixture credentials never enter args, receipts, assertion values or diagnostics. */
internal class SshAuthenticateFixture private constructor(
    private val context: Context, private val owner: WindowOwner, val run: String,
    val case: String, private val configuration: JSONObject,
) {
    val username: String get() = configuration.getString(when (case) {
        "success" -> "usernameAccept"
        "denial" -> "usernameDeny"
        "change" -> "usernameChange"
        else -> "usernameWait"
    })
    val password: String get() = configuration.getString("password")
    private val profiles = ConnectionProfiles(context)
    private val trust = SshHostTrustStore(context)
    private val profileBaseline = profile { profiles.load() }
    private val trustBaseline = trust { trust.load() }
    private val peer = SshPeerIdentity.create("127.0.0.1", configuration.getInt("port"))
    private val jump = configuration.optBoolean("jump", false)
    private val targetPeer = SshPeerIdentity.create("127.0.0.1",
        if (jump) configuration.getInt("targetPort") else configuration.getInt("port"))
    private val key = SshHostKeyIdentity.create(configuration.getString("keyFormat"), configuration.getString("fingerprint"))
    private val trustFixture = SshObserveTrustFixture(context, owner, trustBaseline)
    private val plannedPin = SshTrustSnapshot.create(owner, trustBaseline.generation, peer, 1, SshTrustStatus.accepted(key))
    private val targetPin = SshTrustSnapshot.create(owner, trustBaseline.generation, targetPeer, 1, SshTrustStatus.accepted(key))
    private val plannedPins = listOf(plannedPin) + if (jump && targetPeer != peer) listOf(targetPin) else emptyList()
    private val directory = File(context.noBackupFilesDir, "ssh-authenticate-qualification")
    private val receiptFile = File(directory, "$run-${owner.wireId}-$case.json")
    /** Wall-clock start of this attempt; a home open marker must fall in [start, observation]. */
    private val attemptStartedEpochSeconds = System.currentTimeMillis() / 1000
    private val journal = JSONArray()
    private val receipt = JSONObject().put("schema", 1).put("run", run).put("owner", owner.wireId).put("case", case)
        .put("package", context.packageName).put("uid", Process.myUid()).put("caseOutcome", "STARTED")
        .put("profileBaseline", profileJson(profileBaseline)).put("trustBaseline", trustJson(trustBaseline))
        .put("trustMetadata", JSONArray(trustFixture.baselineMetadata())).put("phases", journal)
    private var ownedProfile: ConnectionProfile? = null
    private var ownedJump: ConnectionProfile? = null
    private var homeOpenAccepted = false
    val profileId: String get() = checkNotNull(ownedProfile).id

    /** Exact owned label; callers must never reconstruct it from a guessed format. */
    val profileLabel: String get() = checkNotNull(ownedProfile).label

    init {
        check(trustBaseline.snapshots.none { it.peer == peer || it.peer == targetPeer }) { "AUTH_FIXTURE_PEER_NOT_ABSENT" }
        check(!Files.isSymbolicLink(directory.toPath()) && (directory.isDirectory || directory.mkdir()))
        check(listOf("", ".bak", ".new").none { File(receiptFile.path + it).exists() ||
            Files.isSymbolicLink(File(receiptFile.path + it).toPath()) })
        if (case == "cancel" || case == "recreate") {
            val ack = File(File(context.noBackupFilesDir, "ssh-authenticate-fixture"), "$run-$case-server-request.json")
            check(!ack.exists() && !Files.isSymbolicLink(ack.toPath())) { "EXISTING_SERVER_ACK_PRESERVE" }
        }
        phase("BASELINE_BEFORE_MUTATION")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshAuthenticateReceipt", receiptFile.absolutePath)
            putString("cloudySshAuthenticateCase", case)
        })
    }

    fun seed() {
        val label = "Authentication ${owner.wireId} $run $case"
        receipt.put("plannedLabel", label).put("plannedHost", targetPeer.host).put("plannedPort", targetPeer.port)
        receipt.put("plannedTrust", trustJson(trustBaseline, trustBaseline.snapshots + plannedPins))
        phase("PROFILE_CREATE_PLANNED")
        if (jump) {
            ownedJump = profile { profiles.create(profileBaseline.generation,
                ConnectionProfileDraft.create(owner, "Jump ${owner.wireId} $run $case",
                    ConnectionProtocol.SSH, peer.host, peer.port)) }.profile
            receipt.put("ownedJump", profileJson(profileBaseline.generation, listOf(checkNotNull(ownedJump))))
        }
        ownedProfile = profile { profiles.create(profileBaseline.generation,
            ConnectionProfileDraft.create(owner, label, ConnectionProtocol.SSH, targetPeer.host, targetPeer.port,
                jumpProfileId = ownedJump?.id)) }.profile
        receipt.put("ownedProfile", profileJson(profileBaseline.generation, listOf(checkNotNull(ownedProfile))))
        phase("TRUST_ACCEPT_PLANNED")
        for (pin in plannedPins) {
            val accepted = trust { trust.accept(SshTrustChallenge.create(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), trust { trust.evaluate(pin.peer, key) })) }
            check(accepted == pin) { "AUTH_FIXTURE_ENROLLMENT_CHANGED" }
        }
        assertUnchanged()
        phase("READY")
    }

    fun recordInputHistoryCheck() {
        receipt.put("clearedUsernameUndoRedoEmpty", true).put("clearedPasswordUndoRedoEmpty", true)
            .put("keyboardRoute", "Compose framework key injection; not physical keyboard")
            .put("keys", JSONArray(listOf("CtrlLeft+Z", "CtrlLeft+ShiftLeft+Z")))
        phase("CLEARED_INPUT_UNDO_REDO_CONFIRMED")
    }

    fun recordVerification(savedStates: Int, savedStateClean: Boolean) {
        receipt.put("flagSecureConfirmed", true).put("autofillExcluded", true).put("inputRetired", true)
        if (case == "recreate") receipt.put("savedStateCount", savedStates)
            .put("savedStateExcludedFixtureCredentials", savedStateClean).put("recreatedSelectionAbsent", true)
    }

    fun phase(value: String) {
        if (value == "PASSED" || value == "FAILED") receipt.put("caseOutcome", value)
        check(journal.length() < 32)
        journal.put(JSONObject().put("phase", value).put("elapsedRealtimeMillis", SystemClock.elapsedRealtime()))
        receipt.put("phase", value)
        val atomic = AtomicFile(receiptFile)
        val output = atomic.startWrite()
        try { output.write(receipt.toString(2).toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    fun awaitActualServerRequest() {
        phase("WAITING_FOR_SERVER_REQUEST")
        val file = File(File(context.noBackupFilesDir, "ssh-authenticate-fixture"), "$run-$case-server-request.json")
        val deadline = SystemClock.elapsedRealtime() + 7_000
        while (!file.exists() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        check(file.exists()) { "ACTUAL_SERVER_REQUEST_UNCONFIRMED" }
        val bytes = privateBytes(file, 1024)
        val ack = parse(bytes)
        check(ack.keys().asSequence().toSet() == setOf("schema", "run", "owner", "case", "requestObserved") &&
            ack.opt("schema") == 1 && ack.opt("run") == run && ack.opt("owner") == owner.wireId &&
            ack.opt("case") == case && ack.opt("requestObserved") == true) { "SERVER_REQUEST_ACK_INVALID" }
        receipt.put("serverRequestAckSha256", MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) })
        phase("SERVER_REQUEST_CONFIRMED")
    }

    fun assertUnchanged() {
        check(profileJson(profile { profiles.load() }) ==
            profileJson(profileBaseline.generation, profileBaseline.profiles + listOfNotNull(ownedJump, ownedProfile))) {
            "AUTH_PROFILE_CHANGED"
        }
        check(trustJson(trust { trust.load() }) == trustJson(trustBaseline, trustBaseline.snapshots + plannedPins)) {
            "AUTH_TRUST_CHANGED"
        }
    }

    /**
     * Adopts exactly one home Connect marker on the owned row. The store must differ from the
     * seeded ledger only by one revision bump and an in-attempt last-used timestamp; every other
     * owned field and every foreign row keeps its byte-identical metadata. Any other difference
     * fails here instead of being folded into a broader restore.
     */
    fun acceptHomeOpenMarker() {
        check(!homeOpenAccepted) { "AUTH_HOME_OPEN_ALREADY_ACCEPTED" }
        val seeded = checkNotNull(ownedProfile)
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var adopted: ConnectionProfile? = null
        while (adopted == null) {
            val catalog = profile { profiles.load() }
            check(catalog.generation == profileBaseline.generation) { "AUTH_PROFILE_GENERATION_CHANGED" }
            check(rowsJson(catalog.profiles.filter { it.id != seeded.id && it.id != ownedJump?.id }).toString() ==
                rowsJson(profileBaseline.profiles).toString()) { "AUTH_FOREIGN_PROFILE_CHANGED" }
            val row = catalog.profiles.singleOrNull { it.id == seeded.id } ?: error("AUTH_HOME_OPEN_ROW_MISSING")
            if (row.revision != seeded.revision) {
                adopted = row
            } else {
                check(SystemClock.elapsedRealtime() < deadline) { "AUTH_HOME_OPEN_MARKER_UNCONFIRMED" }
                SystemClock.sleep(20)
            }
        }
        val row = checkNotNull(adopted)
        check(row.revision == seeded.revision + 1) { "AUTH_HOME_OPEN_UNEXPECTED_REVISION" }
        check(row.owner == seeded.owner && row.label == seeded.label && row.protocol == seeded.protocol &&
            row.host == seeded.host && row.port == seeded.port && row.tags == seeded.tags &&
            row.favorite == seeded.favorite && row.jumpProfileId == seeded.jumpProfileId) {
            "AUTH_HOME_OPEN_METADATA_CHANGED"
        }
        check(row.lastUsedAt in attemptStartedEpochSeconds..(System.currentTimeMillis() / 1000)) {
            "AUTH_HOME_OPEN_TIME_UNEXPECTED"
        }
        ownedProfile = row
        homeOpenAccepted = true
        receipt.put("ownedProfile", profileJson(profileBaseline.generation, listOf(row)))
            .put("homeOpenMarker", JSONObject().put("revision", row.revision).put("lastUsedAt", row.lastUsedAt)
                .put("windowStartEpochSeconds", attemptStartedEpochSeconds))
        phase("HOME_OPEN_MARKER_ACCEPTED")
    }

    /** Adopts exactly the terminal's persisted key-URI mutation after the home marker. */
    fun acceptKeyReference() {
        val seeded = ownedProfile ?: return
        val row = profile { profiles.load() }.profiles.singleOrNull { it.id == seeded.id }
            ?: error("KEY_REFERENCE_ROW_MISSING")
        check(row.revision > seeded.revision && !row.keyUri.isNullOrEmpty()) { "KEY_REFERENCE_NOT_SAVED" }
        check(row.owner == seeded.owner && row.label == seeded.label && row.protocol == seeded.protocol &&
            row.host == seeded.host && row.port == seeded.port && row.tags == seeded.tags &&
            row.favorite == seeded.favorite && row.jumpProfileId == seeded.jumpProfileId &&
            row.lastUsedAt == seeded.lastUsedAt) { "KEY_REFERENCE_METADATA_CHANGED" }
        ownedProfile = row
        receipt.put("ownedProfile", profileJson(profileBaseline.generation, listOf(row)))
            .put("keyReferencePresent", true)
        phase("KEY_REFERENCE_ACCEPTED")
    }

    /** Caller first closes every Activity; fences cover the real process runtime, not a fake queue. */
    fun restore(passed: Boolean) {
        settle()
        check(profileJson(profile { profiles.load() }) ==
            profileJson(profileBaseline.generation, profileBaseline.profiles + listOfNotNull(ownedJump, ownedProfile))) {
            "AUTH_PROFILE_UNCERTAIN_PRESERVE_FIXTURE"
        }
        phase("RESTORING_EXACT_FIXTURE")
        trustFixture.removeOnlyExpected(plannedPins)
        // The target references the jump, so retire the dependent row first.
        for (id in listOfNotNull(ownedProfile?.id, ownedJump?.id)) {
            val row = profile { profiles.load() }.profiles.singleOrNull { it.id == id } ?: continue
            profile { profiles.delete(ConnectionProfileVersion.create(profileBaseline.generation, row.id, row.revision)) }
        }
        check(profileJson(profile { profiles.load() }) == profileJson(profileBaseline))
        check(trustJson(trust { trust.load() }) == trustJson(trustBaseline))
        phase(if (passed) "PASSED" else "FAILED")
        phase("EXACT_BASELINE_RESTORED")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshAuthenticateCleanup", "EXACT_BASELINE_RESTORED")
            putString("cloudySshAuthenticateCase", case)
        })
    }

    companion object {
        fun open(context: Context, owner: WindowOwner, run: String, case: String): SshAuthenticateFixture {
            check(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(run))
            check(case in setOf("success", "denial", "change", "cancel", "recreate"))
            val directory = File(context.noBackupFilesDir, "ssh-authenticate-fixture")
            val info = Os.lstat(directory.path)
            check(!Files.isSymbolicLink(directory.toPath()) && OsConstants.S_ISDIR(info.st_mode) &&
                info.st_uid == Process.myUid() && info.st_mode and 511 == 448)
            val config = parse(privateBytes(File(directory, "$run.json"), 65_536))
            check(config.keys().asSequence().toSet() == setOf("schema", "run", "owner", "host", "port", "keyFormat", "fingerprint",
                "usernameAccept", "usernameDeny", "usernameChange", "usernameWait", "password", "jump", "targetPort"))
            check(config.opt("schema") == 1 && config.opt("run") == run && config.opt("owner") == owner.wireId &&
                config.opt("host") == "127.0.0.1" && config.opt("port") is Int && config.getInt("port") in 1..65535 &&
                config.opt("keyFormat") == "ecdsa-sha2-nistp256" && config.opt("fingerprint") is String &&
                config.opt("jump") is Boolean && config.opt("targetPort") is Int &&
                config.getInt("targetPort") in 1..65535)
            for (field in listOf("usernameAccept", "usernameDeny", "usernameChange", "usernameWait")) {
                val value = config.opt(field) as? String ?: error("AUTH_FIXTURE_FIELD_INVALID")
                check(value.length in 1..256 && value.none { Character.isISOControl(it) || Character.isSurrogate(it) })
            }
            val password = config.opt("password") as? String ?: error("AUTH_FIXTURE_FIELD_INVALID")
            check(password.length in 1..4096 && password.none { Character.isSurrogate(it) })
            return SshAuthenticateFixture(context, owner, run, case, config)
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
            JSONObject(text)
        } catch (_: Exception) { error("PRIVATE_AUTH_FIXTURE_INVALID") }

        fun settle() {
            val runtime = Class.forName("io.github.baitian6641.cloudy.platform.AuthenticationRuntime")
            val singleton = runtime.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            val field = runtime.getDeclaredField("worker")
            check(Modifier.isStatic(field.modifiers) && field.type == ThreadPoolExecutor::class.java)
            field.isAccessible = true
            val executor = field.get(singleton) as ThreadPoolExecutor
            check(executor.corePoolSize == 1 && executor.maximumPoolSize == 1 && executor.queue is ArrayBlockingQueue<*>)
            check(executor.queue.size + executor.queue.remainingCapacity() == 1)
            repeat(2) {
                val worker = CountDownLatch(1)
                executor.execute { worker.countDown() }
                check(worker.await(20, TimeUnit.SECONDS)) { "AUTH_WORK_UNSETTLED" }
                val main = CountDownLatch(1)
                check(Handler(Looper.getMainLooper()).post { main.countDown() })
                check(main.await(5, TimeUnit.SECONDS)) { "AUTH_MAIN_UNSETTLED" }
            }
            check(executor.queue.isEmpty()) { "AUTH_QUEUE_UNSETTLED" }
            val current = runtime.getDeclaredField("current").apply { isAccessible = true }
            check(Modifier.isStatic(current.modifiers) && current.type == AtomicReference::class.java)
            check((current.get(singleton) as AtomicReference<*>).get() == null) { "AUTH_RUNTIME_OWNERSHIP_UNSETTLED" }
            val engineField = runtime.getDeclaredField("engine").apply { isAccessible = true }
            check(Modifier.isStatic(engineField.modifiers) &&
                engineField.type.name == "io.github.baitian6641.cloudy.ssh.SshAuthenticationCheck")
            val engine = checkNotNull(engineField.get(singleton))
            for (name in listOf("busy", "quarantined")) {
                val state = engine.javaClass.getDeclaredField(name).apply { isAccessible = true }
                check(state.type == AtomicBoolean::class.java && !(state.get(engine) as AtomicBoolean).get()) {
                    "AUTH_ENGINE_RETIREMENT_UNCONFIRMED"
                }
            }
        }
        private fun <T> profile(action: () -> ConnectionProfilesResult<T>): T {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            do {
                when (val value = action()) {
                    is ConnectionProfilesResult.Success -> return value.value
                    is ConnectionProfilesResult.Failure -> check(value.code == ConnectionProfilesCode.BUSY) { "AUTH_PROFILE_DENIED" }
                }
                SystemClock.sleep(15)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("AUTH_PROFILE_BUSY")
        }
        private fun <T> trust(action: () -> SshTrustStoreResult<T>): T {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            do {
                when (val value = action()) {
                    is SshTrustStoreResult.Success -> return value.value
                    is SshTrustStoreResult.Failure -> check(value.code == SshTrustStoreCode.BUSY) { "AUTH_TRUST_DENIED" }
                }
                SystemClock.sleep(15)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("AUTH_TRUST_BUSY")
        }
        private fun profileJson(catalog: ConnectionProfileCatalog) = profileJson(catalog.generation, catalog.profiles)
        private fun profileJson(generation: String, rows: List<ConnectionProfile>): String = JSONObject()
            .put("generation", generation).put("rows", rowsJson(rows)).toString()
        /** Full non-secret row metadata, so a baseline comparison never ignores tags/favorite/last-use. */
        private fun rowsJson(rows: List<ConnectionProfile>): JSONArray =
            JSONArray(rows.sortedBy { it.id }.map {
                JSONObject().put("id", it.id).put("revision", it.revision).put("owner", it.owner.wireId)
                    .put("label", it.label).put("protocol", it.protocol.wireId).put("host", it.host).put("port", it.port)
                    .put("tags", JSONArray(it.tags.toList())).put("favorite", it.favorite).put("lastUsedAt", it.lastUsedAt)
            })
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
