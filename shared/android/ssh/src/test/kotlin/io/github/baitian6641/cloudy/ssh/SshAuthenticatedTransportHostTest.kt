package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in maintained SFTP server. Fixture secrets stay in a private file, never assertion text. */
class SshAuthenticatedTransportHostTest {
    private class Fixture(val directory: File) {
        private val values = Properties().apply { File(directory, "fixture.properties").inputStream().use { load(it) } }
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        fun credentials(): SshPasswordCredentials {
            val user = values.getProperty("user").toCharArray()
            val password = values.getProperty("password").toCharArray()
            return try { SshPasswordCredentials.create(user, password) }
            finally { user.fill('\u0000'); password.fill('\u0000') }
        }
        fun events(): List<String> {
            val file = File(directory, "events.jsonl")
            require(file.length() <= 262_144) { "FIXTURE_EVENT_BOUND" }
            return file.readText().split('\n').dropLast(1)
        }
        fun count(event: String): Int = events().count { it.contains("\"event\":\"$event\"") }
        fun command(command: String) {
            require(command in setOf("rekey-same", "rekey-changed"))
            val pending = File(directory, "control.request.tmp")
            pending.writeText(command + "\n")
            Files.move(pending.toPath(), File(directory, "control.request").toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        fun awaitEvent(event: String, previous: Int = 0) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (count(event) <= previous && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("Actual fixture event required: $event", count(event) > previous)
        }
    }
    private fun fixture(case: String = "basic"): Fixture {
        val path = System.getenv("CLOUDY_SSH_SESSION_FIXTURE_ROOT")
        assumeTrue("Explicit leader-owned retained SSH fixtures only", path != null)
        return Fixture(File(checkNotNull(path), case))
    }
    private class Opened(val transport: SshAuthenticatedTransport, val credential: SshPasswordCredentials,
                         val admissions: AtomicInteger, val retired: AtomicInteger)
    private fun open(f: Fixture, factory: SshAuthenticatedTransportFactory = SshAuthenticatedTransportFactory(),
                     cancellation: SshObservationCancellation = SshObservationCancellation(),
                     timeoutMillis: Int = 3_000): Opened {
        val credential = f.credentials()
        val admitted = AtomicInteger()
        val retired = AtomicInteger()
        val opened = factory.connectPassword(f.peer, f.address, f.key, cancellation, { peer, key ->
            assertTrue("Exact fresh route/key required", peer == f.peer && key == f.key)
            admitted.incrementAndGet(); credential
        }, { proof ->
            assertTrue("Engine proof must bind exact authentication", proof.peer == f.peer && proof.key == f.key &&
                proof.cancellation === cancellation && !proof.isRetired)
            true
        }, { proof ->
            assertTrue("Retirement notification requires actual retired ownership", proof.isRetired)
            retired.incrementAndGet()
        }, timeoutMillis)
        if (opened !is SshTransportOpenResult.Connected) {
            credential.close()
            fail("Actual retained authentication failed: $opened")
        }
        assertTrue("Password material must retire before live publication", credential.isClosed)
        assertEquals(1, admitted.get())
        return Opened((opened as SshTransportOpenResult.Connected).transport, credential, admitted, retired)
    }
    private fun settle(opened: Opened) {
        opened.transport.requestClose()
        assertEquals("Actual owned resources must retire", SshTransportCloseResult.Retired, opened.transport.awaitClosed(3_000))
        assertTrue(opened.transport.ownership.isRetired)
        assertEquals("Exactly one retirement callback", 1, opened.retired.get())
        opened.credential.close()
    }
    private fun assertFile(transport: SshAuthenticatedTransport) {
        val result = transport.files.read("/hello.txt")
        assertTrue("Actual SFTP read required: $result", result is SshFilesResult.Success)
        val bytes = (result as SshFilesResult.Success).value.copyBytes()
        assertTrue("Exact maintained-server bytes required", bytes.contentEquals("Cloudy retained SFTP\n".toByteArray(Charsets.UTF_8)))
    }

    @Test fun sequentialOperationsAndQuietIdleSurviveOriginalAuthenticationDeadline() {
        val f = fixture(); val before = f.count("auth_request"); val closed = f.count("session_closed")
        val opened = open(f)
        try {
            val listing = opened.transport.files.list("/")
            assertTrue("Actual directory list required", listing is SshFilesResult.Success)
            val entries = (listing as SshFilesResult.Success).value
            assertTrue(entries.any { it.name == "hello.txt" && it.kind == SshFileKind.REGULAR })
            assertTrue(entries.any { it.name == "overflow" && it.kind == SshFileKind.DIRECTORY })
            assertFile(opened.transport)
            Thread.sleep(3_500)
            assertFile(opened.transport)
            assertEquals("Sequential operations must not reauthenticate", before + 1, f.count("auth_request"))
        } finally { settle(opened) }
        f.awaitEvent("session_closed", closed)
    }

    @Test fun serverDirectoryOverflowIsExplicitAndNeverASuccessfulTruncatedList() {
        val opened = open(fixture())
        try { assertCode(SshTransportCode.LIMIT_EXCEEDED, opened.transport.files.list("/overflow", maxEntries = 2)) }
        finally { settle(opened) }
    }

    @Test fun serverFileOverflowIsExplicitAndNeverASuccessfulTruncatedRead() {
        val opened = open(fixture())
        try { assertCode(SshTransportCode.LIMIT_EXCEEDED, opened.transport.files.read("/hello.txt", maxBytes = 4)) }
        finally { settle(opened) }
    }

    @Test fun declinedOwnershipRetiresActuallyAuthenticatedTransportWithoutChannel() {
        val f = fixture(); val credential = f.credentials(); val proof = AtomicReference<SshAuthenticatedOwnership?>()
        val retired = AtomicInteger(); val channels = f.count("channel_open"); val authenticated = f.count("session_authenticated")
        try {
            val result = SshAuthenticatedTransportFactory().connectPassword(f.peer, f.address, f.key,
                SshObservationCancellation(), { _, _ -> credential }, { proof.set(it); false }, {
                    assertTrue(it.isRetired); retired.incrementAndGet()
                })
            assertTrue(result is SshTransportOpenResult.Failure)
            assertEquals(SshTransportCode.OWNERSHIP_REJECTED, (result as SshTransportOpenResult.Failure).code)
            assertTrue(proof.get()?.isRetired == true)
            assertEquals(1, retired.get())
            assertEquals(authenticated + 1, f.count("session_authenticated"))
            assertEquals(channels, f.count("channel_open"))
        } finally { credential.close() }
    }

    @Test fun wrongFreshKeyNeverAdmitsCredentials() {
        val f = fixture(); val before = f.count("auth_request"); val admission = AtomicInteger()
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
            Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 23 }))
        val result = SshAuthenticatedTransportFactory().connectPassword(f.peer, f.address, key,
            SshObservationCancellation(), { _, _ -> admission.incrementAndGet(); f.credentials() }, { true }, {})
        assertTrue(result is SshTransportOpenResult.Failure)
        assertEquals(SshTransportCode.KEY_REJECTED, (result as SshTransportOpenResult.Failure).code)
        assertEquals(0, admission.get()); assertEquals(before, f.count("auth_request"))
    }

    @Test fun sameKeyPostAuthenticationRekeyPreservesActualSftpAndSingleAuthentication() {
        val f = fixture("same-key"); val opened = open(f)
        try {
            assertFile(opened.transport)
            val keys = f.count("key_established")
            f.command("rekey-same")
            f.awaitEvent("key_established", keys)
            f.awaitEvent("rekey_future_completed")
            assertEquals(1, f.count("server_rekey_kexinit_written"))
            assertFile(opened.transport)
            assertEquals(1, f.count("auth_request"))
            assertEquals(2, f.count("key_established"))
            assertEquals(1, opened.admissions.get())
        } finally { settle(opened) }
    }

    @Test fun changedKeyPostAuthenticationRekeyClosesWithoutAnotherCredentialRelease() {
        val f = fixture("changed-key"); val opened = open(f)
        try {
            assertFile(opened.transport)
            f.command("rekey-changed")
            f.awaitEvent("session_closed")
            assertEquals(1, f.count("changed_key_rekey_requested"))
            assertEquals(1, f.count("server_rekey_kexinit_written"))
            assertEquals(1, f.count("client_rekey_kexinit_negotiation_start"))
            f.awaitEvent("rekey_future_failed")
            assertCode(SshTransportCode.KEY_REJECTED, opened.transport.files.read("/hello.txt"))
            assertEquals("Changed exchange must not complete", 1, f.count("key_established"))
            assertEquals(1, f.count("auth_request")); assertEquals(1, opened.admissions.get())
        } finally { settle(opened) }
    }

    @Test fun cancellationDuringActuallyHeldReadRetiresAndRejectsConcurrentFactoryUse() = heldRead(true)
    @Test fun deadlineDuringActuallyHeldReadRetiresWithoutReconnect() = heldRead(false)

    private fun heldRead(cancel: Boolean) {
        val f = fixture("hold-read"); val previous = f.count("read_hold_entered")
        val engine = SshAuthenticatedTransportFactory(); val cancellation = SshObservationCancellation()
        val opened = open(f, engine, cancellation)
        val result = AtomicReference<SshFilesResult<SshFileBytes>?>()
        val worker = Thread({ result.set(opened.transport.files.read("/hello.txt", timeoutMillis = 2_000)) },
            "cloudy-sftp-test-reader").apply { isDaemon = true }
        try {
            worker.start(); f.awaitEvent("read_hold_entered", previous)
            val busy = engine.connectPassword(f.peer, f.address, f.key, SshObservationCancellation(),
                { _, _ -> error("BUSY_MUST_NOT_ADMIT") }, { true }, {})
            assertTrue(busy is SshTransportOpenResult.Failure)
            assertEquals(SshTransportCode.BUSY, (busy as SshTransportOpenResult.Failure).code)
            if (cancel) cancellation.cancel()
            worker.join(5_000)
            assertFalse("Actual SFTP owner must return", worker.isAlive)
            assertCode(if (cancel) SshTransportCode.CANCELLED else SshTransportCode.TIMED_OUT, checkNotNull(result.get()))
        } finally {
            cancellation.cancel(); worker.join(3_000); settle(opened)
            assertFalse("No test-owned worker may escape", worker.isAlive)
        }
    }
    private fun assertCode(expected: SshTransportCode, result: SshFilesResult<*>) {
        assertTrue("Typed failed operation required: $result", result is SshFilesResult.Failure)
        assertEquals(expected, (result as SshFilesResult.Failure).code)
    }
}
