package io.github.baitian6641.cloudy.desktop

import org.junit.Assert.*
import org.junit.Test

/** Pure admission/pairing tests, not native delivery, Android event or physical-device proof. */
class VncInputPolicyTest {
    private class Sink : VncInputSink {
        val calls = mutableListOf<String>()
        var reject = false
        var rejectPointerCall = 0
        private var pointerCalls = 0
        override fun pointer(x: Int, y: Int, mask: Int): Boolean {
            calls += "p:$x:$y:$mask"; pointerCalls++
            return !reject && pointerCalls != rejectPointerCall
        }
        override fun key(symbol: Int, down: Boolean): Boolean { calls += "k:$symbol:$down"; return !reject }
        override fun release() { calls += "release" }
    }
    @Test fun letterboxRejectsMarginsAndClampsOwnedDrag() {
        assertNull(VncInputPolicy.point(5f, 5f, 200, 200, 100, 50, false))
        assertEquals(VncRemotePoint(0, 0), VncInputPolicy.point(0f, 50f, 200, 200, 100, 50, false))
        assertEquals(VncRemotePoint(50, 25), VncInputPolicy.point(100f, 100f, 200, 200, 100, 50, false))
        assertNull(VncInputPolicy.point(200f, 150f, 200, 200, 100, 50, false))
        assertEquals(VncRemotePoint(99, 49), VncInputPolicy.point(500f, 500f, 200, 200, 100, 50, true))
        assertNull(VncInputPolicy.point(Float.NaN, 5f, 200, 200, 100, 50, true))
        assertNull(VncInputPolicy.point(1f, 1f, 0, 1, 1, 1, false))
    }
    @Test fun buttonsRequireFreshPressAndSuppressDuplicate() {
        val sink = Sink(); val policy = VncInputPolicy(sink); val p = VncRemotePoint(10, 20)
        policy.move(p, 1); assertEquals("p:10:20:0", sink.calls.last())
        policy.press(p, 1, 1); policy.press(p, 1, 1)
        assertEquals(listOf("p:10:20:0", "p:10:20:1"), sink.calls)
        policy.press(p, 2, 3); assertEquals("p:10:20:5", sink.calls.last())
        policy.buttonsUp(p, 1); assertEquals("p:10:20:1", sink.calls.last())
        policy.buttonsUp(p, 0); assertEquals("p:10:20:0", sink.calls.last())
        assertFalse(policy.hasButtons())
        policy.move(p, 7); assertEquals("p:10:20:0", sink.calls.last())
    }
    @Test fun fractionalWheelsPreserveDragAndReleaseEachPulse() {
        val sink = Sink(); val policy = VncInputPolicy(sink); val p = VncRemotePoint(2, 3)
        policy.press(p, 1, 1); policy.scroll(p, .4f, 0f); policy.scroll(p, .4f, 0f)
        assertEquals(1, sink.calls.size)
        policy.scroll(p, .4f, -1f)
        assertEquals(listOf("p:2:3:1", "p:2:3:9", "p:2:3:1", "p:2:3:33", "p:2:3:1"), sink.calls)
        val before = sink.calls.toList()
        assertFalse(policy.scroll(p, Float.POSITIVE_INFINITY, 0f))
        assertFalse(policy.scroll(p, 17f, 0f)); assertEquals(before, sink.calls)
    }
    @Test fun keyUpUsesAcceptedSymbolAndDeviceIdentity() {
        val sink = Sink(); val policy = VncInputPolicy(sink)
        val a = VncInputPolicy.keyIdentity(1, 30); val b = VncInputPolicy.keyIdentity(2, 30)
        policy.down(a, 65); policy.down(a, 97); policy.down(b, 98)
        policy.up(a); policy.up(b)
        assertEquals(listOf("k:65:true", "k:65:true", "k:98:true", "k:65:false", "k:98:false"), sink.calls)
        assertFalse(policy.up(a))
    }
    @Test fun rejectionReleasesAndClearsOwnership() {
        val sink = Sink(); val policy = VncInputPolicy(sink)
        policy.down(1, 65); policy.press(VncRemotePoint(1, 2), 1, 1)
        sink.reject = true; assertFalse(policy.move(VncRemotePoint(3, 4), 1))
        assertEquals("release", sink.calls.last()); assertFalse(policy.hasButtons()); assertFalse(policy.ownsKey(1))
        sink.reject = false; policy.move(VncRemotePoint(3, 4), 1)
        assertEquals("p:3:4:0", sink.calls.last())
    }
    @Test fun releaseClearsFractionalRemainderAndKeys() {
        val sink = Sink(); val policy = VncInputPolicy(sink); val p = VncRemotePoint(0, 0)
        policy.scroll(p, .9f, .9f); policy.down(1, 65); policy.release()
        val count = sink.calls.size
        policy.scroll(p, .2f, .2f)
        assertEquals(count, sink.calls.size); assertFalse(policy.up(1))
    }
    @Test fun boundedKeysAndUnicodeNeverFabricateControlText() {
        val sink = Sink(); val policy = VncInputPolicy(sink)
        policy.press(VncRemotePoint(1, 1), 1, 1)
        repeat(64) { assertTrue(policy.down(it.toLong(), 65)) }
        assertFalse(policy.down(65, 65)); assertEquals("release", sink.calls.last())
        repeat(64) { assertFalse(policy.ownsKey(it.toLong())) }
        assertFalse(policy.hasButtons())
        assertNull(VncInputPolicy.unicode(0)); assertNull(VncInputPolicy.unicode(0xd800))
        assertNull(VncInputPolicy.unicode(0x110000)); assertEquals(65, VncInputPolicy.unicode(65))
        assertEquals(0x0101f600, VncInputPolicy.unicode(0x1f600))
    }
    @Test fun newPressReconcilesMissingPhysicalRelease() {
        val sink = Sink(); val policy = VncInputPolicy(sink); val p = VncRemotePoint(1, 2)
        policy.press(p, 1, 1) // Left UP is deliberately absent from the event stream.
        policy.press(p, 2, 2) // Current physical state proves only right is down.
        assertEquals(listOf("p:1:2:1", "p:1:2:4"), sink.calls)
        policy.buttonsUp(p, 0)
        assertEquals("p:1:2:0", sink.calls.last())
    }
    @Test fun sharedKeysymReleasesOnlyAfterLastPhysicalOwner() {
        val sink = Sink(); val policy = VncInputPolicy(sink)
        val first = VncInputPolicy.keyIdentity(1, 30); val second = VncInputPolicy.keyIdentity(2, 30)
        policy.down(first, 65); policy.down(second, 65)
        assertTrue(policy.up(first)); assertTrue(policy.ownsKey(second))
        assertEquals(listOf("k:65:true", "k:65:true"), sink.calls)
        assertTrue(policy.up(second))
        assertEquals(listOf("k:65:true", "k:65:true", "k:65:false"), sink.calls)
    }

    @Test fun wheelReleaseRejectionClearsOwnershipAndObservableRemainder() {
        val sink = Sink(); val policy = VncInputPolicy(sink); val p = VncRemotePoint(0, 0)
        policy.press(p, 1, 1); policy.down(1, 65)
        policy.scroll(p, .75f, 0f)
        sink.rejectPointerCall = 3 // Press pulse is admitted at2; its release fails at3.
        assertFalse(policy.scroll(p, .5f, 0f))
        assertEquals(listOf("p:0:0:1", "k:65:true", "p:0:0:9", "p:0:0:1", "release"), sink.calls)
        assertFalse(policy.hasButtons()); assertFalse(policy.ownsKey(1))
        sink.rejectPointerCall = 0
        val before = sink.calls.size
        policy.scroll(p, .8f, 0f)
        assertEquals(before, sink.calls.size) // If stale .25 remained, this would emit a pulse.
        policy.scroll(p, .3f, 0f)
        assertEquals(listOf("p:0:0:8", "p:0:0:0"), sink.calls.takeLast(2))
    }
    @Test fun failedKeyUpReleasesOtherHeldInputs() {
        val sink = Sink(); val policy = VncInputPolicy(sink)
        policy.down(1, 65); policy.down(2, 66); policy.press(VncRemotePoint(1, 1), 1, 1)
        sink.reject = true
        assertTrue(policy.up(1)) // Consume original UP even when admission failed.
        assertEquals("release", sink.calls.last())
        assertFalse(policy.ownsKey(2)); assertFalse(policy.hasButtons())
    }

}
