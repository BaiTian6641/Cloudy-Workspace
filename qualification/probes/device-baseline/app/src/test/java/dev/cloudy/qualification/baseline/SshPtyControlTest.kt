package dev.cloudy.qualification.baseline

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic ownership/control tests; no worker here communicates with SSH or an Android device. */
class SshPtyControlTest {
    @Test fun earlyActionWaitsForDurablePhaseReleaseAndCanOnlyBeTakenOnce() {
        val control = SshPtyControl()
        assertFalse(control.offer(SshPtyAction.RESIZE))
        control.arm(SshPtyAction.RESIZE)
        assertTrue(control.offer(SshPtyAction.RESIZE))
        assertFalse(control.offer(SshPtyAction.RESIZE))
        val task = FutureTask { control.take() }
        val thread = Thread(task)
        thread.start()
        try {
            assertThrows(TimeoutException::class.java) { task.get(30, TimeUnit.MILLISECONDS) }
            control.release(SshPtyAction.RESIZE)
            assertEquals(SshPtyAction.RESIZE, task.get(1, TimeUnit.SECONDS))
            control.applied(SshPtyAction.RESIZE)
            assertTrue(control.awaitApplied(SshPtyAction.RESIZE, 100))
            assertFalse(control.offer(SshPtyAction.RESIZE))
        } finally {
            control.close()
            thread.interrupt()
            thread.join(1_000)
            assertFalse(thread.isAlive)
        }
    }

    @Test fun finishCannotBeAdmittedUntilResizeHasActuallyBeenApplied() {
        val control = SshPtyControl()
        control.arm(SshPtyAction.RESIZE)
        assertFalse(control.offer(SshPtyAction.FINISH))
        assertThrows(IllegalStateException::class.java) { control.arm(SshPtyAction.FINISH) }
        control.release(SshPtyAction.RESIZE)
        assertTrue(control.offer(SshPtyAction.RESIZE))
        assertEquals(SshPtyAction.RESIZE, control.take())
        assertFalse(control.awaitApplied(SshPtyAction.RESIZE, 1))
        control.applied(SshPtyAction.RESIZE)
        control.arm(SshPtyAction.FINISH)
        control.release(SshPtyAction.FINISH)
        assertFalse(control.offer(SshPtyAction.RESIZE))
        assertTrue(control.offer(SshPtyAction.FINISH))
        assertEquals(SshPtyAction.FINISH, control.take())
        control.applied(SshPtyAction.FINISH)
        assertTrue(control.awaitApplied(SshPtyAction.FINISH, 100))
        control.close()
        assertFalse(control.offer(SshPtyAction.FINISH))
    }

    @Test fun failedPublicationCloseDiscardsAnAcceptedButUnreleasedAction() {
        val control = SshPtyControl()
        control.arm(SshPtyAction.RESIZE)
        assertTrue(control.offer(SshPtyAction.RESIZE))
        control.close()
        assertNull(control.take())
        assertFalse(control.awaitApplied(SshPtyAction.RESIZE, 100))
        assertFalse(control.offer(SshPtyAction.RESIZE))
        assertThrows(IllegalStateException::class.java) { control.release(SshPtyAction.RESIZE) }
    }

    @Test fun unsolicitedResponseDoesNotCountAsAnAppliedControl() {
        val control = SshPtyControl()
        control.arm(SshPtyAction.RESIZE)
        control.release(SshPtyAction.RESIZE)
        assertFalse(control.awaitApplied(SshPtyAction.RESIZE, 100))
        assertThrows(IllegalStateException::class.java) { control.applied(SshPtyAction.RESIZE) }
        control.close()
    }

    @Test fun stickyCloseReleasesAnIdleControlThreadAndPreventsReuse() {
        val control = SshPtyControl()
        val task = FutureTask { control.take() }
        val thread = Thread(task)
        thread.start()
        try {
            control.close()
            assertNull(task.get(1, TimeUnit.SECONDS))
            assertFalse(control.offer(SshPtyAction.RESIZE))
            assertThrows(IllegalStateException::class.java) { control.arm(SshPtyAction.RESIZE) }
        } finally {
            thread.interrupt()
            thread.join(1_000)
            assertFalse(thread.isAlive)
        }
    }
}
