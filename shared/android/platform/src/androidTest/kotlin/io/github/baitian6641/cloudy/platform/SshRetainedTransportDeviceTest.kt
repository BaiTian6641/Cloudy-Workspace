package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustChallenge
import io.github.baitian6641.cloudy.domain.SshTrustEditToken
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedOwnership
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedTransport
import io.github.baitian6641.cloudy.ssh.SshAuthenticatedTransportFactory
import io.github.baitian6641.cloudy.ssh.SshFileKind
import io.github.baitian6641.cloudy.ssh.SshFilesResult
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.ssh.SshTransportCloseResult
import io.github.baitian6641.cloudy.ssh.SshTransportCode
import io.github.baitian6641.cloudy.ssh.SshTransportOpenResult
import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in LOWER-LEVEL native consumer: real engine-issued authenticated proof plus isolated real
 * SQLite coordination. Not product UI/lifecycle, physical input or supported-session evidence.
 * Parent owns the real listener, USB reverse, generated credential provisioning and secret cleanup.
 */
@RunWith(AndroidJUnit4::class)
class SshRetainedTransportDeviceTest {
    @Test fun actualSftpConnectionIsReusedThenTrustRevocationRetiresOwnership() = exercise("revoke")
    @Test fun actualProfileEditCancelsEstablishedTransportAndPreventsFurtherReads() = exercise("edit")
    @Test fun actualAuthenticatedProofCannotPromoteAfterProfileChanges() = exercise("reject")

    private fun exercise(case: String) {
        val argument = InstrumentationRegistry.getArguments().getString("cloudySshSessionRun")
        assumeTrue("Explicit real retained SSH qualification only", argument != null)
        val run = argument?.takeIf {
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(it)
        }
        var root: File? = null
        var handle: SshAuthenticationHandle? = null
        var transport: SshAuthenticatedTransport? = null
        var credentials: SshPasswordCredentials? = null
        val proof = AtomicReference<SshAuthenticatedOwnership?>()
        val callbackRetired = CountDownLatch(1)
        val retirementCallbacks = AtomicInteger()
        var engineStarted = false
        var engineReturned = false
        var failure = false
        var exactRows: (() -> Boolean)? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            check(Looper.myLooper() != Looper.getMainLooper())
            val fixture = readFixture(context, checkNotNull(run))
            val ownedRoot = File(context.noBackupFilesDir, "ssh-session-device-$run-$case")
            check(!ownedRoot.exists() && !Files.isSymbolicLink(ownedRoot.toPath()) && ownedRoot.mkdir())
            root = ownedRoot
            phase(run, case, "ISOLATED_ROOT_CREATED")
            val owner = WindowOwner.CONSOLE
            val profiles = ConnectionProfiles(ownedRoot, owner, "io.github.baitian6641.cloudy.console", { false })
            val trust = SshHostTrustStore(ownedRoot, owner, "io.github.baitian6641.cloudy.console", { false })
            val profileGeneration = profile(profiles.load()).generation
            val trustGeneration = pin(trust.load()).generation
            var expectedProfile: SshObservationProfile? = null
            var expectedPin: SshTrustSnapshot? = null
            exactRows = {
                val catalog = profile(profiles.load())
                val pins = pin(trust.load())
                catalog.generation == profileGeneration && pins.generation == trustGeneration &&
                    catalog.profiles.size == (if (expectedProfile == null) 0 else 1) &&
                    (expectedProfile == null || SshObservationProfile.create(catalog.generation, catalog.profiles.single()) == expectedProfile) &&
                    pins.snapshots == listOfNotNull(expectedPin)
            }
            val initialDraft = ConnectionProfileDraft.create(owner, "Retained native fixture", ConnectionProtocol.SSH,
                fixture.peer.host, fixture.peer.port)
            val created = profile(profiles.create(profileGeneration, initialDraft))
            val selected = SshObservationProfile.create(profileGeneration, created.profile)
            expectedProfile = selected
            expectedPin = SshTrustSnapshot.create(owner, trustGeneration, fixture.peer, 1, SshTrustStatus.accepted(fixture.key))
            check(pin(trust.accept(SshTrustChallenge.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                pin(trust.evaluate(fixture.peer, fixture.key))))) == expectedPin)
            val user = fixture.user.toCharArray()
            val secret = fixture.password.toCharArray()
            credentials = try { SshPasswordCredentials.create(user, secret) }
            finally { user.fill('\u0000'); secret.fill('\u0000') }
            val prepared = SshAuthenticationCoordinator(ownedRoot, owner, profiles, trust).begin(selected, checkNotNull(credentials))
            check(prepared is SshAuthenticationPreparation.Ready)
            val authority = prepared.handle
            handle = authority
            fun editProfile() {
                val changed = ConnectionProfileDraft.create(owner, "Changed retained fixture", ConnectionProtocol.SSH,
                    fixture.peer.host, fixture.peer.port)
                expectedProfile = SshObservationProfile.create(profileGeneration, selected.profile.revised(changed))
                val result = profile(profiles.update(created.version, changed))
                check(SshObservationProfile.create(profileGeneration, result.profile) == expectedProfile)
            }
            val factory = SshAuthenticatedTransportFactory()
            phase(run, case, "REAL_CONNECT_STARTING")
            engineStarted = true
            val opened = factory.connectPassword(fixture.peer, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)),
                authority.expectedKey, authority.cancellation, authority::admit,
                establish = { issued ->
                    check(proof.compareAndSet(null, issued))
                    if (case == "reject") editProfile()
                    authority.establishSession(issued)
                }, retired = { issued ->
                    authority.releaseSession(issued)
                    if (issued === proof.get() && issued.isRetired) retirementCallbacks.incrementAndGet()
                    callbackRetired.countDown()
                }, timeoutMillis = 8_000)
            engineReturned = true
            if (opened is SshTransportOpenResult.Connected) transport = opened.transport
            check(checkNotNull(credentials).isClosed)
            if (case == "reject") {
                // The real profile mutation wins by sticky cancellation, unlike a callback that
                // simply declines without mutation. Authentication occurred; promotion did not.
                check(opened is SshTransportOpenResult.Failure && opened.code == SshTransportCode.CANCELLED)
                check(authority.cancellation.isCancelled && transport == null)
                check(proof.get() != null && proof.get()!!.isRetired)
                check(callbackRetired.await(3, TimeUnit.SECONDS) && retirementCallbacks.get() == 1)
                check(authority !in SshAuthenticationRegistry.attempts)
                phase(run, case, "REAL_AUTHENTICATED_PROMOTION_CANCELLED_AND_RETIRED")
            } else {
                check(opened is SshTransportOpenResult.Connected)
                val active = opened.transport
                check(active.ownership === proof.get() && !active.ownership.isRetired)
                check(active.ownership.cancellation === authority.cancellation)
                check(authority in SshAuthenticationRegistry.attempts)
                if (case == "revoke") {
                    val listing = active.files.list(".", maxEntries = 16, timeoutMillis = 3_000)
                    check(listing is SshFilesResult.Success && listing.value.any {
                        it.name == "hello.txt" && it.kind == SshFileKind.REGULAR
                    })
                }
                readHello(active)
                if (case == "revoke") readHello(active) // Same retained transport, no second factory/connect call.
                phase(run, case, "ACTUAL_SFTP_CONTENT_CONFIRMED")
                if (case == "edit") editProfile()
                else {
                    expectedPin = SshTrustSnapshot.create(owner, trustGeneration, fixture.peer, 2,
                        SshTrustStatus.revoked(fixture.key))
                    check(pin(trust.revoke(SshTrustEditToken.create(owner, trustGeneration, fixture.peer, 1))) == expectedPin)
                }
                check(authority.cancellation.isCancelled)
                check(active.awaitClosed(3_000) == SshTransportCloseResult.Retired)
                check(callbackRetired.await(3, TimeUnit.SECONDS) && retirementCallbacks.get() == 1)
                check(active.ownership.isRetired && authority !in SshAuthenticationRegistry.attempts)
                check(active.files.read("hello.txt", maxBytes = 1024, timeoutMillis = 3_000) is SshFilesResult.Failure)
                phase(run, case, "ACTUAL_INVALIDATION_RETIRED_WITHOUT_REPLAY")
            }
            check(checkNotNull(exactRows).invoke())
        } catch (_: Throwable) {
            failure = true
            phase(run, case, "FAILED")
        } finally {
            var settled = true
            try {
                transport?.let { active ->
                    active.requestClose()
                    settled = active.awaitClosed(3_000) == SshTransportCloseResult.Retired && active.ownership.isRetired
                }
                if (engineStarted) {
                    settled = settled && engineReturned && proof.get()?.isRetired == true &&
                        callbackRetired.await(3, TimeUnit.SECONDS) && retirementCallbacks.get() == 1
                }
            } catch (_: Throwable) { settled = false }
            finally {
                // Even a shutdown exception cannot skip cancellation or pending-secret disposal.
                try { handle?.close() } catch (_: Throwable) { settled = false }
                try { credentials?.close() } catch (_: Throwable) { settled = false }
            }
            try {
                settled = settled && (handle?.let { it !in SshAuthenticationRegistry.attempts } ?: true)
                check(settled && checkNotNull(exactRows).invoke())
                val ownedRoot = checkNotNull(root)
                check(!Files.isSymbolicLink(ownedRoot.toPath()) && ownedRoot.parentFile == context.noBackupFilesDir)
                check(ownedRoot.name == "ssh-session-device-$run-$case")
                checkOwnedFiles(ownedRoot)
                check(ownedRoot.deleteRecursively())
                phase(run, case, "EXACT_TEST_ROOT_REMOVED")
            } catch (_: Throwable) {
                failure = true
                // Never force-delete an unretired transport's registry entry or unknown SQLite root.
                phase(run, case, "UNCERTAIN_PRESERVE_TEST_ROOT")
            }
        }
        if (failure) throw AssertionError("RETAINED_SSH_NATIVE_CONSUMER_FAILED")
        phase(run, case, "PASSED_NATIVE_CONSUMER_ONLY")
    }

    private fun checkOwnedFiles(root: File) {
        val allowed = mutableSetOf("")
        for ((directory, database) in listOf("connection-profiles-v1" to "profiles.db", "ssh-host-trust-v1" to "trust.db")) {
            allowed.addAll(listOf(directory, "$directory.instance", "$directory.lock", "$directory/$database",
                "$directory/$database-journal"))
        }
        for (path in root.walkTopDown()) {
            check(!Files.isSymbolicLink(path.toPath()))
            val info = Os.lstat(path.path)
            check(info.st_uid == Process.myUid() && (OsConstants.S_ISDIR(info.st_mode) || OsConstants.S_ISREG(info.st_mode)))
            val relative = if (path == root) "" else path.path.removePrefix(root.path + "/")
            check(relative in allowed) { "UNEXPECTED_ISOLATED_ROOT_FILE_PRESERVE" }
        }
    }

    private fun readHello(transport: SshAuthenticatedTransport) {
        val result = transport.files.read("hello.txt", maxBytes = 1024, timeoutMillis = 3_000)
        check(result is SshFilesResult.Success)
        val bytes = result.value.copyBytes()
        val expected = "Cloudy retained SFTP\n".toByteArray(Charsets.UTF_8)
        try { check(bytes.contentEquals(expected)) { "REMOTE_FIXTURE_BYTES_DIFFER" } }
        finally { bytes.fill(0); expected.fill(0) }
    }
    private fun <T> profile(result: ConnectionProfilesResult<T>): T = (result as ConnectionProfilesResult.Success).value
    private fun <T> pin(result: SshTrustStoreResult<T>): T = (result as SshTrustStoreResult.Success).value
    private fun phase(run: String?, case: String, value: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshSessionRun", run)
            putString("cloudySshSessionCase", case)
            putString("cloudySshSessionPhase", value)
        })
    }

    private class Fixture(val peer: SshPeerIdentity, val key: SshHostKeyIdentity, val user: String, val password: String)
    private fun readFixture(context: Context, run: String): Fixture {
        check(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(run))
        val base = File(context.noBackupFilesDir, "ssh-session-fixture")
        val directory = File(base, run)
        for (path in listOf(base, directory)) {
            val info = Os.lstat(path.path)
            check(!Files.isSymbolicLink(path.toPath()) && OsConstants.S_ISDIR(info.st_mode) &&
                info.st_uid == Process.myUid() && (info.st_mode and 511) == 448)
        }
        val file = File(directory, "fixture.json")
        val info = Os.lstat(file.path)
        check(!Files.isSymbolicLink(file.toPath()) && OsConstants.S_ISREG(info.st_mode) &&
            info.st_uid == Process.myUid() && (info.st_mode and 511) == 384 && info.st_size in 1L..16_384L)
        val bytes = FileChannel.open(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            check(channel.size() == info.st_size)
            val buffer = ByteBuffer.allocate(info.st_size.toInt())
            while (buffer.hasRemaining()) check(channel.read(buffer) > 0)
            check(channel.size() == info.st_size)
            buffer.array()
        }
        val input = try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            JSONObject(text)
        } finally { bytes.fill(0) }
        check(input.keys().asSequence().toSet() == setOf("schemaVersion", "runId", "port", "fingerprint", "user", "password"))
        check(input.opt("schemaVersion") == 1 && input.opt("runId") == run && input.opt("port") is Int)
        val port = input.getInt("port").also { check(it in 1..65535) }
        val fingerprint = input.opt("fingerprint") as? String ?: error("FIXTURE_INVALID")
        val user = input.opt("user") as? String ?: error("FIXTURE_INVALID")
        val password = input.opt("password") as? String ?: error("FIXTURE_INVALID")
        check(user.length in 1..256 && password.length in 1..4096)
        return Fixture(SshPeerIdentity.create("127.0.0.1", port),
            SshHostKeyIdentity.create("ecdsa-sha2-nistp256", fingerprint), user, password)
    }
}
