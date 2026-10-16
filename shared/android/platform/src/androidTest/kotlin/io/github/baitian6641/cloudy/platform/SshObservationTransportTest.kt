package io.github.baitian6641.cloudy.platform

import android.net.InetAddresses
import android.os.Bundle
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.ssh.SshHostKeyObserver
import io.github.baitian6641.cloudy.ssh.SshObservationCancellation
import io.github.baitian6641.cloudy.ssh.SshObservationCode
import io.github.baitian6641.cloudy.ssh.SshObservationResult
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in actual SSH KEX observation, not authentication, engine adoption or general server support.
 * The parent owns the fixture listener and independent public-key reference. No credentials enter
 * this test. Host/fingerprint arguments and observed keys must never be copied into log messages.
 */
@RunWith(AndroidJUnit4::class)
class SshObservationTransportTest {
    private class Fixture(val peer: SshPeerIdentity, val expected: SshHostKeyIdentity)

    private fun fixture(): Fixture {
        val arguments = InstrumentationRegistry.getArguments()
        // Disabled before resolver construction or any socket operation.
        assumeTrue("Explicit owned SSH observation fixture only", arguments.getString("cloudySshObservation") == "true")
        check(Looper.myLooper() != Looper.getMainLooper()) { "SSH observation requires instrumentation worker" }
        val host = arguments.getString("cloudySshHost") ?: "127.0.0.1"
        require(host.length in 1..253 && InetAddresses.isNumericAddress(host)) { "SSH_FIXTURE_NUMERIC_HOST_REQUIRED" }
        val portText = checkNotNull(arguments.getString("cloudySshPort")) { "SSH_FIXTURE_PORT_REQUIRED" }
        require(portText.length in 1..5 && portText.all { it in '0'..'9' }) { "SSH_FIXTURE_INVALID_PORT" }
        val port = portText.toInt()
        val fingerprint = checkNotNull(arguments.getString("cloudySshFingerprint")) { "SSH_FIXTURE_REFERENCE_REQUIRED" }
        return Fixture(SshPeerIdentity.create(host, port),
            SshHostKeyIdentity.create("ecdsa-sha2-nistp256", fingerprint))
    }

    @Test fun independentlyReferencedSignedKeyRepeatsOnlyAfterRealRetirement() {
        val fixture = fixture()
        val address = resolveNumeric(fixture.peer)
        val observer = SshHostKeyObserver()
        repeat(2) {
            val result = observer.observe(fixture.peer, address, SshObservationCancellation(), 10_000)
            if (result is SshObservationResult.Failure) {
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("cloudySshObservationFailureCode", result.code.name)
                })
            }
            assertTrue("Signed observation unavailable; inspect sanitized result code", result is SshObservationResult.SignedKeyObserved)
            val observed = result as SshObservationResult.SignedKeyObserved
            assertTrue("Observed logical peer differs from requested peer", observed.peer == fixture.peer)
            assertTrue("Observed key differs from independently supplied fixture reference", observed.key == fixture.expected)
            assertTrue("Result leaked logical peer", !observed.toString().contains(fixture.peer.host))
            assertTrue("Result leaked public digest", !observed.toString().contains(fixture.expected.fingerprint))
        }
        // A second success on the same instance also excludes its cleanup-uncertain quarantine.
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshObservationPhase", "SIGNED_KEY_OBSERVED_AND_RETIRED")
            putInt("cloudySshObservationCount", 2)
            putBoolean("cloudySshAuthenticationRequested", false)
        })
    }

    @Test fun alreadyCancelledObservationStopsBeforeTransportAdmission() {
        val fixture = fixture()
        val address = resolveNumeric(fixture.peer)
        val cancellation = SshObservationCancellation().apply { cancel() }
        val result = SshHostKeyObserver().observe(fixture.peer, address, cancellation, 10_000)
        assertTrue("Pre-cancelled observation must fail", result is SshObservationResult.Failure)
        assertEquals(SshObservationCode.CANCELLED, (result as SshObservationResult.Failure).code)
        assertTrue(cancellation.isCancelled)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloudySshObservationPhase", "PRE_CANCELLED_BEFORE_ADMISSION")
            putInt("cloudySshObservationCount", 0)
        })
    }

    private fun resolveNumeric(peer: SshPeerIdentity): InetAddress {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CountDownLatch(1)
        val callbackMain = AtomicBoolean(false)
        val callbacks = AtomicInteger()
        val selected = AtomicReference<SshAddressResult?>(null)
        SshAddressResolver(instrumentation.targetContext).use { resolver ->
            resolver.resolve(peer, 5_000) { result ->
                callbackMain.set(Looper.myLooper() == Looper.getMainLooper())
                callbacks.incrementAndGet()
                selected.set(result)
                completed.countDown()
            }
            assertTrue("Numeric resolver callback deadline exceeded", completed.await(6, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertTrue("Resolver must deliver on main", callbackMain.get())
            assertEquals("Resolver must select one terminal callback", 1, callbacks.get())
            val result = selected.get()
            assertTrue("Numeric fixture resolution unavailable", result is SshAddressResult.Resolved)
            val addresses = (result as SshAddressResult.Resolved).addresses
            assertEquals("Numeric fixture must yield exactly one address", 1, addresses.size)
            val address = addresses.single()
            assertTrue("Resolver changed numeric route", address.hostAddress == InetAddresses.parseNumericAddress(peer.host).hostAddress)
            return address
        }
    }
}
