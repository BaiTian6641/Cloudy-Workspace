package dev.cloudy.qualification.baseline

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pre-network failure boundaries use the real transport, without an SSH server or key material. */
class TerminalTransportTest {
    private fun config() = TerminalConfigSpec("192.0.2.1", 22, "probe", "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32)))
    private fun identity() = TerminalIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString())
    private val missingKey = File("must-not-be-read-terminal-test-key")

    @Test fun stickyPreRunCancellationPerformsNoObservationOrKeyRead() {
        val transport = TerminalTransport()
        transport.cancel("activity-detached")
        val result = transport.run(config(), missingKey, identity(), config().expectedHostKeySha256,
            TerminalSize(80, 24), object : TerminalTransportListener {
                override fun onObservation(observation: TerminalObservation) = error("unexpected observation")
                override fun onOutput(bytes: ByteArray, isStderr: Boolean) = error("unexpected output")
            })
        assertEquals("interrupted", result.status)
        assertEquals("activity-detached", result.reason)
        assertFalse(result.authenticated)
        assertEquals("0", result.details["hostVerificationCount"])
        assertTrue(result.cleanupCertain)
    }

    @Test fun failedConnectingPublicationPreventsSocketConnectAndIsCleaned() {
        val phases = mutableListOf<String>()
        val result = TerminalTransport().run(config(), missingKey, identity(), config().expectedHostKeySha256,
            TerminalSize(80, 24), object : TerminalTransportListener {
                override fun onObservation(observation: TerminalObservation) {
                    phases.add(observation.status)
                    throw IOException("private endpoint or transcript must not be exposed")
                }
                override fun onOutput(bytes: ByteArray, isStderr: Boolean) = error("unexpected output")
            })
        assertEquals(listOf("connecting"), phases)
        assertEquals("failed", result.status)
        assertEquals("IOException", result.reason)
        assertEquals("0", result.details["hostVerificationCount"])
        assertFalse(result.authenticated)
        assertTrue(result.cleanupCertain)
    }

    @Test fun staleTrustedReferenceRejectsBeforeConnectingObservation() {
        var observed = false
        val result = TerminalTransport().run(config(), missingKey, identity(), "SHA256:wrong-reference",
            TerminalSize(80, 24), object : TerminalTransportListener {
                override fun onObservation(observation: TerminalObservation) { observed = true }
                override fun onOutput(bytes: ByteArray, isStderr: Boolean) = error("unexpected output")
            })
        assertFalse(observed)
        assertEquals("failed", result.status)
        assertEquals("trusted-reference-mismatch", result.reason)
        assertTrue(result.cleanupCertain)
    }
}
