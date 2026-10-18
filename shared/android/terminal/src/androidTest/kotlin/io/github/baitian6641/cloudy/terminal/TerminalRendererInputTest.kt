package io.github.baitian6641.cloudy.terminal

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.testing.ime.ImeFixtureResult
import io.github.baitian6641.cloudy.testing.ime.ImeQualificationSession
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real OS-served IME -> packaged xterm -> binary native admission.
 * Not physical keyboard, a particular system IME, SSH, TalkBack or paint-latency qualification.
 */
@RunWith(AndroidJUnit4::class)
class TerminalRendererInputTest {
    @Test fun plainCommittedTextReachesNativeAdmission() = withRenderer { session, scenario, host ->
        assertSubmitted(session.commit(focusEditor(scenario), "Cloudy"))
        expectBytes(host, "Cloudy".toByteArray())
    }

    @Test fun nativeEnterAfterCommittedTextReachesTerminalOnce() = withRenderer { session, scenario, host ->
        assertSubmitted(session.commit(focusEditor(scenario), "Cloudy command"))
        expectBytes(host, "Cloudy command".toByteArray(), checkNoDuplicate = false)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        expectBytes(host, byteArrayOf(13))
    }

    @Test fun committedUnicodeArrivesOnceWithoutPreeditOrReplay() = withRenderer { session, scenario, host ->
        val view = focusEditor(scenario)
        assertSubmitted(session.compose(view, "uncommitted"))
        assertSubmitted(session.compose(view, ""))
        assertSubmitted(session.finishComposition(view))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue("preedit leaked", host.inputs.isEmpty())
        val committed = "中文 e\u0301 🙂\nsecond line"
        assertSubmitted(session.commit(view, committed))
        expectBytes(host, committed.toByteArray(Charsets.UTF_8))
        scenario.onActivity { it.renderer.close() }
        // A closed renderer may still have an OS connection that accepts the API call and
        // asynchronously drops it. The criterion is zero subsequent native bytes, not a refusal.
        val refusal = session.commit(view, "must-not-replay")
        assertTrue(
            "closed attachment was ${refusal.outcome}/${refusal.reason?.name ?: "NONE"}",
            refusal.submitted || typedRefusal(refusal),
        )
        assertTrue("closed attachment sent input", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
    }

    @Test fun enterAndInterruptPreserveTerminalControlBytes() = withRenderer { session, scenario, host ->
        val view = focusEditor(scenario)
        assertSubmitted(session.enter(view))
        assertSubmitted(session.interrupt(view))
        expectBytes(host, byteArrayOf(13, 3))
    }

    private fun withRenderer(
        action: (
            ImeQualificationSession,
            ActivityScenario<TerminalRendererTestActivity>,
            TerminalRendererTestActivity,
        ) -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val allowDeviceChange = InstrumentationRegistry.getArguments()
            .getString("cloudyAllowDeviceImeChange")?.toBooleanStrictOrNull() == true
        val session = ImeQualificationSession.open(
            instrumentation,
            automation,
            UUID.randomUUID().toString(),
            allowDeviceChange = allowDeviceChange,
        )
        try {
            val intent = Intent(instrumentation.targetContext, TerminalRendererTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<TerminalRendererTestActivity>(intent).use { scenario ->
                lateinit var host: TerminalRendererTestActivity
                scenario.onActivity { host = it }
                assertTrue("renderer did not become ready", host.ready.await(15, TimeUnit.SECONDS))
                action(session, scenario, host)
            }
        } finally {
            session.close()
        }
        // Only reached on success; a primary assertion failure propagates first.
        assertTrue(
            "IME_CLEANUP_UNCONFIRMED_${session.closeReason?.name ?: "NONE"}",
            session.cleanupConfirmed,
        )
    }

    private fun focusEditor(scenario: ActivityScenario<TerminalRendererTestActivity>): WebView {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var web: WebView? = null
        val focused = AtomicBoolean(false)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!focused.get() && SystemClock.elapsedRealtime() < deadline) {
            val observed = CountDownLatch(1)
            scenario.onActivity { activity ->
                val view = activity.renderer.view
                web = view
                activity.renderer.focusRenderer()
                view.evaluateJavascript(
                    "document.hasFocus() && document.activeElement?.classList?.contains('xterm-helper-textarea') === true"
                ) { value ->
                    focused.set(value == "true")
                    observed.countDown()
                }
            }
            check(observed.await(5, TimeUnit.SECONDS))
            if (!focused.get()) SystemClock.sleep(10)
        }
        val editor = checkNotNull(web)
        assertTrue("terminal editor never became focused", focused.get())
        instrumentation.runOnMainSync {
            assertTrue("terminal editor lacks native focus", editor.hasWindowFocus() && editor.hasFocus())
        }
        return editor
    }

    private fun assertSubmitted(result: ImeFixtureResult) {
        assertTrue("IME_NOT_SUBMITTED_${result.reason?.name ?: "UNKNOWN"}", result.submitted)
    }

    private fun typedRefusal(result: ImeFixtureResult): Boolean {
        val reason = result.reason
        if (reason == null) return false
        return when (result.outcome) {
            ImeFixtureResult.Outcome.REJECTED -> reason in setOf(
                ImeFixtureResult.Reason.NO_ACTIVE_INPUT,
                ImeFixtureResult.Reason.STALE_INSTANCE,
                ImeFixtureResult.Reason.STALE_GENERATION,
                ImeFixtureResult.Reason.TARGET_MISMATCH,
            )
            ImeFixtureResult.Outcome.API_REFUSED -> reason == ImeFixtureResult.Reason.COMMIT_REFUSED
            ImeFixtureResult.Outcome.SUBMITTED -> false
        }
    }

    private fun expectBytes(host: TerminalRendererTestActivity, expected: ByteArray, checkNoDuplicate: Boolean = true) {
        val observed = ByteArrayOutputStream()
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (observed.size() < expected.size && System.nanoTime() < end) {
            host.inputs.poll(100, TimeUnit.MILLISECONDS)?.let { observed.write(it) }
        }
        assertArrayEquals("committed bytes differ; failures=${host.failures}", expected, observed.toByteArray())
        if (checkNoDuplicate) assertTrue("input duplicated", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
    }
}
