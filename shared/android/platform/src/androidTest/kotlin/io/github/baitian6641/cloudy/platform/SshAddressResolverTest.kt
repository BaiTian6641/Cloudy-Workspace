package io.github.baitian6641.cloudy.platform

import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android numeric parsing/lifecycle boundaries only: no DNS-provider/network qualification. */
@RunWith(AndroidJUnit4::class)
class SshAddressResolverTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun numericIpv4ReturnsImmutableRedactedCandidateOnMain() {
        SshAddressResolver(context).use { resolver ->
            val result = await { callback -> resolver.resolve(SshPeerIdentity.create("192.0.2.7", 22), callback = callback) }
            assertTrue(result is SshAddressResult.Resolved)
            val resolved = result as SshAddressResult.Resolved
            assertEquals(1, resolved.addresses.size)
            assertTrue(resolved.addresses.single().address.contentEquals(byteArrayOf(192.toByte(), 0, 2, 7)))
            assertFalse(resolved.toString().contains("192.0.2.7"))
            try {
                (resolved.addresses as MutableList<InetAddress>).clear()
                fail("Candidate list must be immutable")
            } catch (_: UnsupportedOperationException) { /* Authored result boundary. */ }
        }
    }

    @Test fun expandedIpv6UsesActualNumericParser() {
        SshAddressResolver(context).use { resolver ->
            val result = await { callback -> resolver.resolve(SshPeerIdentity.create("2001:db8::7", 22), callback = callback) }
            assertTrue(result is SshAddressResult.Resolved)
            val bytes = (result as SshAddressResult.Resolved).addresses.single().address
            assertEquals(16, bytes.size)
            assertEquals(0x20.toByte(), bytes[0]); assertEquals(0x01.toByte(), bytes[1])
            assertEquals(7.toByte(), bytes.last())
        }
    }

    @Test fun explicitInterfaceScopeFailsWithoutNameLookupOrScopeStripping() {
        SshAddressResolver(context).use { resolver ->
            val result = await { callback -> resolver.resolve(SshPeerIdentity.create("fe80::1%cloudy-nonexistent", 22), callback = callback) }
            assertEquals(SshAddressFailure.UNSUPPORTED_SCOPE, (result as SshAddressResult.Failed).code)
            assertFalse(result.toString().contains("cloudy-nonexistent"))
        }
    }

    @Test fun closedOwnerRejectsNumericAndHostnameRequestsWithoutDns() {
        val resolver = SshAddressResolver(context)
        resolver.close(); resolver.close()
        for (host in listOf("192.0.2.7", "closed-owner.invalid")) {
            val result = await { callback -> resolver.resolve(SshPeerIdentity.create(host, 22), callback = callback) }
            assertEquals(SshAddressFailure.CLOSED, (result as SshAddressResult.Failed).code)
        }
    }

    @Test fun repeatedCancellationAndCloseSelectExactlyOneTerminalResult() {
        // Completion may legitimately win before cancellation; never relabel that race a cancel pass.
        repeat(8) {
            val resolver = SshAddressResolver(context)
            val result = await { callback ->
                resolver.resolve(SshPeerIdentity.create("192.0.2.7", 22), callback = callback).also {
                    it.cancel(); it.cancel(); resolver.close()
                }
            }
            assertTrue(result is SshAddressResult.Resolved ||
                result is SshAddressResult.Failed && result.code == SshAddressFailure.CANCELLED)
        }
    }

    @Test fun deadlineInputIsBoundedBeforeAnyRequestAdmission() {
        SshAddressResolver(context).use { resolver ->
            for (timeout in listOf(0L, -1L, 30_001L, Long.MAX_VALUE)) {
                try {
                    resolver.resolve(SshPeerIdentity.create("192.0.2.7", 22), timeout) {
                        fail("Invalid deadline must not admit work")
                    }
                    fail("Deadline must be bounded")
                } catch (_: IllegalArgumentException) { /* No platform request admitted. */ }
            }
        }
    }

    private fun await(start: ((SshAddressResult) -> Unit) -> SshResolutionHandle): SshAddressResult {
        val latch = CountDownLatch(1)
        val count = AtomicInteger()
        val result = AtomicReference<SshAddressResult>()
        val callbackOnMain = AtomicReference<Boolean>()
        start {
            callbackOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            result.set(it); count.incrementAndGet(); latch.countDown()
        }
        assertTrue("Numeric completion deadline", latch.await(3, TimeUnit.SECONDS))
        // Bounded duplicate observation, not a claim about arbitrary future provider callbacks.
        SystemClock.sleep(75)
        assertEquals(1, count.get())
        assertEquals(true, callbackOnMain.get())
        assertNotNull(result.get())
        return checkNotNull(result.get())
    }
}
