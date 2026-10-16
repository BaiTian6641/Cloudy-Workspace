package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.io.File
import java.net.InetAddress
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real SSH exchange with a leader-owned maintained server, never a product credential. */
class SshAuthenticationHostTest {
    private class Fixture(val file: File, val values: Properties) {
        val peer = SshPeerIdentity.create("127.0.0.1", values.getProperty("port").toInt())
        val address: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val key = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", values.getProperty("fingerprint"))
        fun credentials(mode: String, wrong: Boolean = false): SshPasswordCredentials {
            val user = values.getProperty("user.$mode").toCharArray()
            val password = (values.getProperty("password") + if (wrong) "-wrong" else "").toCharArray()
            return try { SshPasswordCredentials.create(user, password) }
            finally { user.fill('\u0000'); password.fill('\u0000') }
        }
        fun waitRequests(): Int = events().count { it.contains("\"mode\":\"wait\"") && it.contains("\"event\":\"auth_request\"") }
        fun events(): List<String> {
            val text = File(file.parentFile, "events.jsonl").readText()
            require(text.length <= 262_144) { "FIXTURE_EVIDENCE_BOUND" }
            // The server appends concurrently; an incomplete final record is not evidence yet.
            return text.split('\n').dropLast(1)
        }
        fun lastSession(): Int = events().map { session(it) }.maxOrNull() ?: 0
        fun session(line: String): Int = checkNotNull(Regex("\"session\":(\\d+)").find(line)).groupValues[1].toInt()

        // The fixture emits only bounded, fixed-schema ASCII metadata. Per-session assertions
        // establish negative wire claims, not just client status or credential-provider counts.
        fun assertRetiredSession(previous: Int, mode: String, requests: Int, extraEvent: String? = null): List<String> {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (System.nanoTime() < deadline) {
                if (events().any { session(it) > previous && it.contains("\"event\":\"session_closed\"") }) break
                Thread.sleep(10)
            }
            val records = events().filter { session(it) > previous }
            assertEquals("One real connection; no reconnect", 1, records.map { session(it) }.distinct().size)
            assertEquals("Server observes actual retirement", 1, records.count { it.contains("\"event\":\"session_closed\"") })
            val auth = records.filter { it.contains("\"event\":\"auth_request\"") }
            assertEquals("Exact real userauth request count", requests, auth.size)
            assertTrue("Only ordinary password requests are allowed", auth.all {
                it.contains("\"mode\":\"$mode\"") && it.contains("\"method\":\"password\"") && it.contains("\"passwordChange\":false")
            })
            assertFalse("No changed-password request reaches upstream", records.any { it.contains("\"event\":\"change_handler\"") })
            assertFalse("Finite authentication opens no channels", records.any { it.contains("\"event\":\"channel_open\"") })
            if (extraEvent != null) assertEquals("Actual server outcome", 1, records.count { it.contains("\"event\":\"$extraEvent\"") })
            return records
        }
    }

    private fun fixture(): Fixture {
        val path = System.getenv("CLOUDY_SSH_AUTH_FIXTURE")
        assumeTrue("Explicit owned SSH password fixture only", path != null)
        val file = File(checkNotNull(path))
        require(file.isFile) { "FIXTURE_CONFIG_REQUIRED" }
        return Fixture(file, Properties().apply { file.inputStream().use { load(it) } })
    }

    private fun authenticate(f: Fixture, mode: String, check: SshAuthenticationCheck = SshAuthenticationCheck(),
                             wrong: Boolean = false): SshAuthenticationResult {
        val calls = AtomicInteger()
        val credentials = f.credentials(mode, wrong)
        val previous = f.lastSession()
        try {
            val result = check.authenticate(f.peer, f.address, f.key, SshObservationCancellation(), { peer, key ->
                assertTrue("Actual connection must reverify the exact logical peer and key", peer == f.peer && key == f.key)
                calls.incrementAndGet()
                credentials
            })
            assertEquals("One admission per actual connection", 1, calls.get())
            assertTrue("Transferred credentials must retire", credentials.isClosed)
            val event = when {
                wrong || mode == "deny" -> "auth_denied"
                mode == "change" -> "change_required"
                mode == "close" -> "close_requested"
                mode == "rekey" -> "rekey_started"
                else -> "session_authenticated"
            }
            val records = f.assertRetiredSession(previous, mode, 1, event)
            if (mode == "rekey") {
                // SSHJ0.41.2 refuses to initiate another KEX before authentication completes.
                // Prove the server actually wrote KEXINIT, rather than merely calling rekey.
                // The public peer hook counts parsed proposals reaching negotiation, not raw
                // malformed ingress. This is a bounded limitation test, not rekey support.
                for (name in listOf("server_initial_kexinit_written", "client_initial_kexinit_negotiation_start",
                        "server_rekey_kexinit_written", "key_established")) {
                    assertEquals("One actual $name", 1, records.count { it.contains("\"event\":\"$name\"") })
                }
                for (name in listOf("server_initial_kexinit_write_failed", "server_rekey_kexinit_write_failed",
                        "client_rekey_kexinit_negotiation_start", "action_failed", "action_cancelled",
                        "duplicate_action_request", "session_authenticated", "auth_accepted")) {
                    assertFalse("No $name", records.any { it.contains("\"event\":\"$name\"") })
                }
            }
            return result
        } finally { credentials.close() }
    }

    @Test fun actualAuthenticationSucceedsTwiceOnlyAfterTransportRetirement() {
        val f = fixture()
        val engine = SshAuthenticationCheck()
        repeat(2) { assertEquals(SshAuthenticationResult.Success, authenticate(f, "accept", engine)) }
    }

    @Test fun wrongPasswordIsDeniedWithoutRetry() {
        val f = fixture()
        assertFailure(SshAuthenticationCode.AUTHENTICATION_FAILED, authenticate(f, "accept", wrong = true))
    }

    @Test fun serverDenialIsTypedAndDoesNotTryAnotherMethod() {
        val f = fixture()
        assertFailure(SshAuthenticationCode.AUTHENTICATION_FAILED, authenticate(f, "deny"))
    }

    @Test fun realServerPasswordChangeChallengeIsRefusedWithoutChangeRequest() {
        val f = fixture()
        assertFailure(SshAuthenticationCode.PASSWORD_CHANGE_REFUSED, authenticate(f, "change"))
    }

    @Test fun transportLossAfterPasswordIsUnconfirmedAuthentication() {
        val f = fixture()
        assertFailure(SshAuthenticationCode.AUTHENTICATION_FAILED, authenticate(f, "close"))
    }

    @Test fun preAuthenticationRekeyLimitationTimesOutAndRetiresWithoutAnotherPassword() {
        val f = fixture()
        assertFailure(SshAuthenticationCode.TIMED_OUT, authenticate(f, "rekey"))
    }

    @Test fun wrongFreshKeyPreventsAdmissionAndAnyUserauth() {
        val f = fixture()
        val wrong = SshHostKeyIdentity.create("ecdsa-sha2-nistp256", "SHA256:" +
            Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 17 }))
        val calls = AtomicInteger()
        val previous = f.lastSession()
        val result = SshAuthenticationCheck().authenticate(f.peer, f.address, wrong, SshObservationCancellation(), { _, _ ->
            calls.incrementAndGet(); f.credentials("accept")
        })
        assertFailure(SshAuthenticationCode.KEY_REJECTED, result)
        assertEquals("A different real key must not release credentials", 0, calls.get())
        f.assertRetiredSession(previous, "unknown", 0)
    }

    @Test fun deniedCurrentStateAfterVerifiedKexPreventsUserauth() {
        val f = fixture()
        val calls = AtomicInteger()
        val previous = f.lastSession()
        val result = SshAuthenticationCheck().authenticate(f.peer, f.address, f.key, SshObservationCancellation(), { _, _ ->
            calls.incrementAndGet(); null
        })
        assertFailure(SshAuthenticationCode.ADMISSION_DENIED, result)
        assertEquals(1, calls.get())
        f.assertRetiredSession(previous, "unknown", 0)
    }

    @Test fun cancellationDuringRealAuthenticationWaitRetiresTransport() = stalledAuthentication(true)
    @Test fun deadlineDuringRealAuthenticationWaitRetiresTransport() = stalledAuthentication(false)

    private fun stalledAuthentication(cancel: Boolean) {
        val f = fixture()
        val before = f.waitRequests()
        val previous = f.lastSession()
        val cancellation = SshObservationCancellation()
        val result = AtomicReference<SshAuthenticationResult?>()
        val credentials = f.credentials("wait")
        val engine = SshAuthenticationCheck()
        val worker = Thread({ result.set(engine.authenticate(f.peer, f.address, f.key, cancellation,
            { _, _ -> credentials }, timeoutMillis = 3_000)) }, "cloudy-auth-test-owner").apply { isDaemon = true }
        try {
            worker.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (f.waitRequests() == before && System.nanoTime() < deadline && worker.isAlive) Thread.sleep(10)
            assertTrue("The server must receive an actual password request before the stimulus", f.waitRequests() > before)
            if (cancel) cancellation.cancel()
            worker.join(6_000)
            assertFalse("Authentication owner must retire", worker.isAlive)
            assertFailure(if (cancel) SshAuthenticationCode.CANCELLED else SshAuthenticationCode.TIMED_OUT, result.get())
            assertTrue("Password material must be disposed", credentials.isClosed)
            f.assertRetiredSession(previous, "wait", 1, "auth_wait")
            val preCancelled = SshObservationCancellation().apply { cancel() }
            assertFailure(SshAuthenticationCode.CANCELLED,
                engine.authenticate(f.peer, f.address, f.key, preCancelled, { _, _ -> error("UNREACHABLE_ADMISSION") }))
        } finally {
            cancellation.cancel(); worker.join(3_000); credentials.close()
            assertFalse("Test owner must not escape", worker.isAlive)
        }
    }

    private fun assertFailure(expected: SshAuthenticationCode, result: SshAuthenticationResult?) {
        assertTrue("Expected typed failure, received $result", result is SshAuthenticationResult.Failure)
        assertEquals(expected, (result as SshAuthenticationResult.Failure).code)
    }
}
