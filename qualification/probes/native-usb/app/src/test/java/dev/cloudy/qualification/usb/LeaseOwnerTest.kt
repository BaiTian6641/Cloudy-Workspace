package dev.cloudy.qualification.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LeaseOwnerTest {
    private class Fixture {
        val events = mutableListOf<String>()
        var wrapping: () -> Unit = {}
        var describing: () -> Unit = {}
        var failWrap = false
        val native = object : LeaseOwner.NativePort {
            override fun open(fd: Int): Long { events += "wrap:$fd"; wrapping(); if (failWrap) error("wrap failed"); return 23 }
            override fun describe(handle: Long): String { events += "describe:$handle"; describing(); return "real descriptor boundary" }
            override fun close(handle: Long) { events += "native-close:$handle" }
        }
        val owner = LeaseOwner(native)
        fun connection(fd: Int = 7) = object : LeaseOwner.Connection {
            override val fd = fd
            override fun close() { events += "android-close:$fd" }
        }
    }
    @Test fun closesNativeBeforeBorrowedFdAndOnlyOnce() {
        val f = Fixture(); val ticket = f.owner.invalidate()
        assertEquals("real descriptor boundary", f.owner.inspect(ticket) { f.connection() })
        assertEquals(listOf("wrap:7", "describe:23"), f.events)
        f.owner.close(); f.owner.close()
        assertEquals(listOf("wrap:7", "describe:23", "native-close:23", "android-close:7"), f.events)
    }
    @Test fun staleSelectionDoesNotOpen() {
        val f = Fixture(); val ticket = f.owner.invalidate(); f.owner.invalidate()
        assertFalse(f.owner.current(ticket))
        assertEquals("Cancelled before open", f.owner.inspect(ticket) { fail("must not open"); null })
        assertTrue(f.events.isEmpty())
    }
    @Test fun cancelDuringOpenClosesWithoutWrapping() {
        val f = Fixture(); val ticket = f.owner.invalidate()
        assertEquals("Cancelled after open", f.owner.inspect(ticket) { f.owner.invalidate(); f.connection() })
        assertEquals(listOf("android-close:7"), f.events)
    }
    @Test fun cancelDuringWrapDoesNotDescribe() {
        val f = Fixture(); val ticket = f.owner.invalidate(); f.wrapping = { f.owner.invalidate() }
        assertEquals("Cancelled after native wrap", f.owner.inspect(ticket) { f.connection() })
        assertEquals(listOf("wrap:7", "native-close:23", "android-close:7"), f.events)
    }
    @Test fun deviceLossDuringDescribeCleansSameGeneration() {
        val f = Fixture(); val ticket = f.owner.invalidate(); f.describing = { f.owner.invalidate() }
        assertEquals("Cancelled during descriptor inspection", f.owner.inspect(ticket) { f.connection() })
        assertEquals(listOf("wrap:7", "describe:23", "native-close:23", "android-close:7"), f.events)
    }
    @Test fun wrapFailureStillClosesAndroidOwner() {
        val f = Fixture(); f.failWrap = true
        try { f.owner.inspect(f.owner.invalidate()) { f.connection() }; fail("must throw") }
        catch (_: IllegalStateException) { assertEquals(listOf("wrap:7", "android-close:7"), f.events) }
        f.owner.close(); assertEquals(2, f.events.size)
    }
    @Test fun invalidFdNeverReachesNative() {
        val f = Fixture()
        try { f.owner.inspect(f.owner.invalidate()) { f.connection(-1) }; fail("must throw") }
        catch (_: IllegalStateException) { assertEquals(listOf("android-close:-1"), f.events) }
    }
    @Test fun missingDeviceReportsUnavailableWithoutNativeCall() {
        val f = Fixture()
        assertEquals("Device unavailable: open returned no connection", f.owner.inspect(f.owner.invalidate()) { null })
        assertTrue(f.events.isEmpty())
    }
    @Test fun replacingLeaseClosesPriorHandleBeforeNewOpen() {
        val f = Fixture()
        f.owner.inspect(f.owner.invalidate()) { f.connection(7) }
        f.owner.inspect(f.owner.invalidate()) { f.connection(8) }
        assertEquals(listOf("wrap:7", "describe:23", "native-close:23", "android-close:7", "wrap:8", "describe:23"), f.events)
        f.owner.close()
    }
    @Test fun descriptorFailureClosesNativeThenAndroid() {
        val f = Fixture(); f.describing = { error("device gone") }
        try { f.owner.inspect(f.owner.invalidate()) { f.connection() }; fail("must throw") }
        catch (_: IllegalStateException) {
            assertEquals(listOf("wrap:7", "describe:23", "native-close:23", "android-close:7"), f.events)
        }
    }
}
