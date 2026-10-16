package io.github.baitian6641.cloudy.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic output-boundary tests only; never acquire UI automation or launch products. */
@RunWith(AndroidJUnit4::class)
class FilesHandoffControllerDiagnosticTest {
    @Test fun hostileExceptionDetailsAndUntrustedFramesStayPrivate() {
        val secret = "SECRET_SENTINEL:/private/path:credential"
        val error = object : RuntimeException(secret, IllegalArgumentException(secret)) { }.apply {
            addSuppressed(SecurityException(secret))
            stackTrace = arrayOf(
                StackTraceElement("untrusted.$secret", "method", "FilesHandoffPositiveControllerTest.kt", 44),
                StackTraceElement("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest", "method", secret, 44),
                StackTraceElement("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest", "method", "FilesHandoffPositiveControllerTest.kt", -1),
                StackTraceElement("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest", "method", "FilesHandoffPositiveControllerTest.kt", 100001))
        }
        val failure = ControllerFailure.capture(ControllerStage.ASSERT_SELECTED_CARD, error)
        assertEquals("FILES_POSITIVE_CONTROLLER_FAILED_PRESERVE_STATE:CONTROLLER:ASSERT_SELECTED_CARD:OTHER:UNKNOWN:0", failure.message)
        assertFalse(failure.toString().contains(secret))
        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
    }
    @Test fun onlyFirstExactProjectFrameSuppliesBoundedLine() {
        val error = IllegalStateException("SECRET_SENTINEL").apply {
            stackTrace = arrayOf(
                StackTraceElement("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTestPretender", "method", "FilesHandoffPositiveControllerTest.kt", 40),
                StackTraceElement("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest\$Controls", "method", "FilesHandoffPositiveControllerTest.kt", 198),
                StackTraceElement("io.github.baitian6641.cloudy.platform.FilesHandoffPositiveControllerTest", "method", "FilesHandoffPositiveControllerTest.kt", 72))
        }
        assertEquals("FILES_POSITIVE_CONTROLLER_FAILED_PRESERVE_STATE:CONTROLLER:ASSERT_SELECTED_CARD:STATE:CONTROLLER:198",
            ControllerFailure.capture(ControllerStage.ASSERT_SELECTED_CARD, error).message)
    }
    @Test fun alreadySanitizedFailureKeepsOriginalIdentityAndStage() {
        val primary = ControllerFailure.capture(ControllerStage.SELECT_ROW,
            SecurityException("SECRET_SENTINEL").apply { stackTrace = emptyArray() })
        assertSame(primary, ControllerFailure.capture(ControllerStage.FINAL_STATUS, primary))
        assertNull(primary.cause)
        assertEquals(0, primary.suppressed.size)
    }
    @Test fun selectorTimeoutNeverSerializesHostileCauseOrText() {
        val secret = "SECRET_SENTINEL:/private/credential"
        val error = ControllerSelectorTimeout(ControllerSelectorSnapshot.unavailable()).apply {
            initCause(IllegalStateException(secret))
            addSuppressed(SecurityException(secret))
            stackTrace = emptyArray()
        }
        val result = ControllerFailure.capture(ControllerStage.ASSERT_SELECTED_CARD, error)
        assertEquals("FILES_POSITIVE_CONTROLLER_FAILED_PRESERVE_STATE:CONTROLLER:ASSERT_SELECTED_CARD:STATE:UNKNOWN:0:" +
            "SELECTOR_V1:W=0,0,0,0,0:T=0,0,0:A=NONE:S=UNAVAILABLE", result.message)
        assertFalse(result.toString().contains(secret))
        assertNull(result.cause)
        assertEquals(0, result.suppressed.size)
        assertSame(result, ControllerFailure.capture(ControllerStage.FINAL_STATUS, result))
    }
    @Test fun selectorCountersAndAncestorDepthAreBounded() {
        val snapshot = ControllerSelectorSnapshot.create(List(100) { Int.MAX_VALUE }, -1, Int.MAX_VALUE, Int.MIN_VALUE,
            List(100) { ControllerSelectorAncestor(true, false, Int.MAX_VALUE) }, ControllerSelectorStop.DEPTH_LIMIT)
        assertEquals("SELECTOR_V1:W=2048,2048,2048,2048,2048:T=0,2048,0:A=" +
            (0 until 8).joinToString(";") { "$it,1,0,2048" } + ":S=DEPTH_LIMIT", snapshot.encode())
    }
    @Test fun selectorSnapshotDistinguishesHeadingAndAncestorEvidence() {
        val snapshot = ControllerSelectorSnapshot.create(listOf(2, 1, 1, 1, 1), 1, 0, 2,
            listOf(ControllerSelectorAncestor(false, true, 1)), ControllerSelectorStop.TARGET_FOUND)
        assertEquals("SELECTOR_V1:W=2,1,1,1,1:T=1,0,2:A=0,0,1,1:S=TARGET_FOUND", snapshot.encode())
        assertEquals("SELECTOR_V1:W=0,0,0,0,0:T=0,0,0:A=NONE:S=ROOT_INELIGIBLE",
            ControllerSelectorSnapshot.create(emptyList(), 0, 0, 0, emptyList(),
                ControllerSelectorStop.ROOT_INELIGIBLE).encode())
    }

}
