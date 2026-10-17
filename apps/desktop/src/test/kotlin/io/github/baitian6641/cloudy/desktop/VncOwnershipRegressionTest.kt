package io.github.baitian6641.cloudy.desktop

import io.github.baitian6641.cloudy.desktop.vnc.VncEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** App-owned transfer/render-authority regressions. Delayed consumer is test-only; no JNI claim. */
class VncOwnershipRegressionTest {
    @Test fun callerFinallyCannotWipeTransferredAsynchronousCredentials() {
        val user = byteArrayOf(117, 115, 114)
        val password = byteArrayOf(112, 119)
        val pin = ByteArray(32) { 7 }
        val admission = VncAdmissionBuffers(pin, user, password)
        val transferred = admission.transferToEngine()
        val releaseConsumer = CountDownLatch(1)
        val consumer = Executors.newSingleThreadExecutor()
        try {
            val consumed = consumer.submit<Boolean> {
                try {
                    check(releaseConsumer.await(2, TimeUnit.SECONDS))
                    transferred.username.contentEquals(byteArrayOf(117, 115, 114)) &&
                        transferred.password.contentEquals(byteArrayOf(112, 119))
                } finally {
                    // This delayed fixture owns consumption/wiping, matching engine's public contract.
                    transferred.username.fill(0)
                    transferred.password.fill(0)
                }
            }
            // These are the production caller-finally/cancel paths, deliberately before consumption.
            admission.wipeOwned()
            admission.cancel()
            assertTrue(pin.all { it == 0.toByte() })
            releaseConsumer.countDown()
            assertTrue(consumed.get(3, TimeUnit.SECONDS))
            assertTrue(user.all { it == 0.toByte() })
            assertTrue(password.all { it == 0.toByte() })
        } finally {
            releaseConsumer.countDown()
            consumer.shutdownNow()
            assertTrue(consumer.awaitTermination(3, TimeUnit.SECONDS))
        }
    }
    @Test fun cancellationBeforeTransferWipesAndCannotAdmit() {
        val user = byteArrayOf(117)
        val password = byteArrayOf(112)
        val pin = ByteArray(32) { 9 }
        val admission = VncAdmissionBuffers(pin, user, password)
        admission.cancel()
        assertTrue(user.all { it == 0.toByte() })
        assertTrue(password.all { it == 0.toByte() })
        assertTrue(pin.all { it == 0.toByte() })
        var refused = false
        try { admission.transferToEngine() } catch (_: IllegalStateException) { refused = true }
        assertTrue(refused)
    }
    @Test fun autonomousTerminalStateRevokesRenderWithoutAnotherFrame() {
        assertTrue(VncRenderEligibility.allows(VncEngine.State.FIRST_FRAME, true, true, false))
        // No new frame or explicit local stop accompanies these engine-state transitions.
        for (state in VncEngine.State.values()) {
            if (state != VncEngine.State.FIRST_FRAME) {
                assertFalse(VncRenderEligibility.allows(state, true, true, false))
            }
        }
        assertFalse(VncRenderEligibility.allows(VncEngine.State.FIRST_FRAME, false, true, false))
        assertFalse(VncRenderEligibility.allows(VncEngine.State.FIRST_FRAME, true, false, false))
        assertFalse(VncRenderEligibility.allows(VncEngine.State.FIRST_FRAME, true, true, true))
    }
    @Test fun foregroundRevocationBeforeNextFrameRequiresAndAllowsAnotherRealDraw() {
        val receipt = VncPresentationReceipt()
        assertTrue(receipt.acknowledge())
        assertFalse(receipt.acknowledge())
        // Model foreground(false) revokes, foreground(true) does not itself acknowledge a draw.
        receipt.revoke()
        assertFalse(receipt.presented)
        assertTrue(VncRenderEligibility.allows(VncEngine.State.FIRST_FRAME, true, true, false))
        assertTrue(!receipt.presented) // Actual needsPresentation predicate requests onDraw again.
        assertTrue(receipt.acknowledge())
        assertTrue(receipt.presented)
        assertFalse(receipt.acknowledge())
    }
}
