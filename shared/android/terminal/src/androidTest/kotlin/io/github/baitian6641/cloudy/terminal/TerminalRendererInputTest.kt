package io.github.baitian6641.cloudy.terminal

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android InputConnection -> packaged xterm -> binary native admission.
 * Not physical keyboard, a particular system IME, SSH, TalkBack or paint-latency qualification.
 */
@RunWith(AndroidJUnit4::class)
class TerminalRendererInputTest {
    @Test fun plainCommittedTextReachesNativeAdmission() = withRenderer { scenario, host ->
        input(scenario) { assertTrue(it.commitText("Cloudy", 1)) }
        expectBytes(host, "Cloudy".toByteArray())
    }

    @Test fun committedUnicodeArrivesOnceWithoutPreeditOrReplay() = withRenderer { scenario, host ->
        val connection = connection(scenario)
        input(connection) {
            connection.beginBatchEdit()
            connection.setComposingText("uncommitted", 1)
            connection.setComposingText("", 1)
            connection.finishComposingText()
            connection.endBatchEdit()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue("preedit leaked", host.inputs.isEmpty())
        // Text newlines are LF at Android's textarea boundary; Enter is tested as a key below.
        val committed = "中文 e\u0301 🙂\nsecond line"
        input(connection) { assertTrue(it.commitText(committed, 1)) }
        expectBytes(host, committed.toByteArray(Charsets.UTF_8))
        scenario.onActivity { it.renderer.close() }
        input(connection) { it.commitText("must-not-replay", 1) }
        assertTrue("closed attachment sent input", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
    }

    @Test fun enterAndInterruptPreserveTerminalControlBytes() = withRenderer { scenario, host ->
        input(scenario) { connection ->
            val now = SystemClock.uptimeMillis()
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                connection.sendKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_ENTER, 0))
            }
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                connection.sendKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON))
            }
        }
        expectBytes(host, byteArrayOf(13, 3))
    }

    private fun withRenderer(action: (ActivityScenario<TerminalRendererTestActivity>, TerminalRendererTestActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.context, TerminalRendererTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<TerminalRendererTestActivity>(intent).use { scenario ->
            lateinit var host: TerminalRendererTestActivity
            scenario.onActivity { host = it }
            assertTrue("renderer did not become ready", host.ready.await(15, TimeUnit.SECONDS))
            action(scenario, host)
        }
    }

    private fun expectBytes(host: TerminalRendererTestActivity, expected: ByteArray) {
        val observed = ByteArrayOutputStream()
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (observed.size() < expected.size && System.nanoTime() < end) {
            host.inputs.poll(100, TimeUnit.MILLISECONDS)?.let { observed.write(it) }
        }
        assertArrayEquals("committed bytes differ; failures=${host.failures}", expected, observed.toByteArray())
        assertTrue("input duplicated", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
    }

    /** WebView declares a dedicated IME handler, not the UI thread. */
    private fun input(scenario: ActivityScenario<TerminalRendererTestActivity>, action: (InputConnection) -> Unit) =
        input(connection(scenario), action)

    private fun connection(scenario: ActivityScenario<TerminalRendererTestActivity>): InputConnection {
        var pending: InputConnection? = null
        val deadline = SystemClock.uptimeMillis() + 5_000
        // READY precedes the asynchronous DOM-focus/IME registration, not native edit readiness.
        while (pending == null && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                activity.renderer.focusRenderer()
                pending = activity.renderer.view.onCreateInputConnection(EditorInfo())
            }
            if (pending == null) SystemClock.sleep(10)
        }
        return checkNotNull(pending) { "WebView input connection never became ready" }
    }

    private fun input(connection: InputConnection, action: (InputConnection) -> Unit) {
        val task = FutureTask { action(connection) }
        assertTrue(checkNotNull(connection.handler).post(task))
        task.get(5, TimeUnit.SECONDS)
    }
}
