package dev.cloudy.qualification.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.RejectedExecutionException

class CleanupAdmissionTest {
    @Test fun newCloseIsAcceptedAfterWorkerFinishesBeforeUiCallback() {
        val admission = CleanupAdmission()
        val tasks = ArrayDeque<() -> Unit>()
        val callbacks = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val execute: (() -> Unit) -> Unit = { tasks.addLast(it) }
        assertTrue(admission.submit(execute) { events += "old-close"; callbacks.add { events += "ui" } })
        assertFalse(admission.submit(execute) { fail("duplicate close") })
        tasks.removeFirst().invoke()
        events += "new-handle-opened"
        // The old completion callback has not run. A new cancellation still closes the new lease.
        assertTrue(admission.submit(execute) { events += "new-close" })
        tasks.removeFirst().invoke()
        assertEquals(listOf("old-close", "new-handle-opened", "new-close"), events)
        assertEquals(1, callbacks.size)
    }
    @Test fun cleanupExceptionDoesNotPermanentlySuppressLaterClose() {
        val admission = CleanupAdmission(); val tasks = ArrayDeque<() -> Unit>()
        val execute: (() -> Unit) -> Unit = { tasks.addLast(it) }
        admission.submit(execute) { error("device gone") }
        try { tasks.removeFirst().invoke(); fail("must throw") } catch (_: IllegalStateException) { }
        assertTrue(admission.submit(execute) { })
    }
    @Test fun executorRejectionReleasesAdmission() {
        val admission = CleanupAdmission()
        try { admission.submit({ throw RejectedExecutionException() }) { }; fail("must throw") }
        catch (_: RejectedExecutionException) { }
        assertTrue(admission.submit({ it() }) { })
    }
}
