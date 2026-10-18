package io.github.baitian6641.cloudy.terminal

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real packaged xterm/addon-search -> authored bridge -> native listener.
 * Synthetic DOM key events are not physical-keyboard, Android-clipboard, SSH or pixel evidence.
 */
@RunWith(AndroidJUnit4::class)
class TerminalRendererInteractionTest {
    @Test fun nativePasteUsesXtermLineEndingNormalizationOnce() = withRenderer { scenario, host ->
        var accepted = 0
        scenario.onActivity {
            accepted = it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.PASTE, "a\r\nb\nc\rd".toByteArray()))
        }
        assertTrue("native paste command was refused", accepted != 0)
        // xterm.paste normalizes CRLF/LF to CR and appends no Enter.
        expectBytes(host, "a\rb\rc\rd".toByteArray())
        assertTrue("renderer failure: ${host.failures}", host.failures.isEmpty())
    }

    @Test fun bracketedPasteWithinBoundUsesXtermTransformationOnce() = withRenderer { scenario, host ->
        awaitBracketedPasteMode(scenario, host)
        scenario.onActivity {
            assertTrue(it.renderer.sendUiCommand(TerminalUiCommand(
                TerminalUiCommandAction.PASTE, "a\r\nb\u001b[2J".toByteArray())) != 0)
        }
        // The real xterm.paste brackets, normalizes and replaces ESC inside the bracket.
        expectBytes(host, "\u001b[200~a\rb\u241b[2J\u001b[201~".toByteArray())
        assertTrue("renderer failure: ${host.failures}", host.failures.isEmpty())
    }

    @Test fun transformedBracketedPasteOverflowIsRefusedWholeWithVisibleFeedback() = withRenderer { scenario, host ->
        awaitBracketedPasteMode(scenario, host)
        var accepted = 0
        scenario.onActivity {
            accepted = it.renderer.sendUiCommand(TerminalUiCommand(
                TerminalUiCommandAction.PASTE, "\u001b".repeat(22_000).toByteArray()))
        }
        assertTrue("60 KiB source paste was refused before the renderer preflight", accepted != 0)
        awaitVisibleWarning(scenario)
        assertTrue("overflow sent remote bytes", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
        assertTrue("renderer failure: ${host.failures}", host.failures.isEmpty())
    }

    @Test fun localCopyShortcutWithNoSelectionIsATypedRefusalWithoutRemoteBytes() = withRenderer { scenario, host ->
        dispatchShortcut(scenario, key = "C", code = "KeyC", ctrl = true, shift = true)
        val request = nextUiRequest(host) { it.action == TerminalUiRequestAction.NOTICE }
        assertEquals(TerminalNotice.NO_SELECTION,
            (request.payload as TerminalUiPayload.Notice).code)
        assertTrue("local copy sent remote bytes", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
        assertTrue("renderer failure: ${host.failures}", host.failures.isEmpty())
    }

    @Test fun searchDrivesRealAddonAndReportsSelectedIndex() = withRenderer { scenario, host ->
        val skipped = mutableListOf<TerminalUiRequest>()
        // Write the fixture text together with DECSET 1000: the resulting real MOUSE_TRACKING
        // request proves xterm finished parsing the buffer before the first search command.
        write(scenario, "\u001b[?1000halpha beta alpha\r\n")
        nextUiRequest(host, skipped) {
            it.action == TerminalUiRequestAction.MOUSE_TRACKING &&
                (it.payload as? TerminalUiPayload.MouseTracking)?.active == true
        }
        var firstCommand = 0
        scenario.onActivity {
            firstCommand = it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.FIND_NEXT, "alpha".toByteArray()))
            assertTrue(firstCommand != 0)
        }
        val first = nextUiRequest(host, skipped) { it.action == TerminalUiRequestAction.SEARCH_RESULT }
            .payload as TerminalUiPayload.SearchResult
        assertEquals(firstCommand, first.commandSequence)
        assertEquals(2, first.resultCount)
        assertEquals(0, first.resultIndex)
        assertTrue(first.found)

        var secondCommand = 0
        scenario.onActivity {
            secondCommand = it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.FIND_NEXT, "alpha".toByteArray()))
            assertTrue(secondCommand != 0)
        }
        val second = nextUiRequest(host, skipped) { it.action == TerminalUiRequestAction.SEARCH_RESULT }
            .payload as TerminalUiPayload.SearchResult
        assertEquals(secondCommand, second.commandSequence)
        assertEquals(2, second.resultCount)
        assertEquals(1, second.resultIndex)

        var previousCommand = 0
        scenario.onActivity {
            previousCommand = it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.FIND_PREVIOUS, "alpha".toByteArray()))
            assertTrue(previousCommand != 0)
        }
        val previous = nextUiRequest(host, skipped) { it.action == TerminalUiRequestAction.SEARCH_RESULT }
            .payload as TerminalUiPayload.SearchResult
        assertEquals(previousCommand, previous.commandSequence)
        assertEquals(2, previous.resultCount)
        assertEquals(0, previous.resultIndex)

        var missingCommand = 0
        scenario.onActivity {
            missingCommand = it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.FIND_NEXT, "missing".toByteArray()))
            assertTrue(missingCommand != 0)
        }
        val missing = nextUiRequest(host, skipped) { it.action == TerminalUiRequestAction.SEARCH_RESULT }
            .payload as TerminalUiPayload.SearchResult
        assertEquals(missingCommand, missing.commandSequence)
        assertEquals(0, missing.resultCount)
        assertEquals(-1, missing.resultIndex)
        assertFalse(missing.found)

        // Searching itself must never request a clipboard copy; only the explicit COPY does.
        assertTrue("search triggered a clipboard copy",
            (skipped + host.uiRequests.toList()).none { it.action == TerminalUiRequestAction.COPY_SELECTION })
        scenario.onActivity {
            assertTrue(it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.FIND_NEXT, "alpha".toByteArray())) != 0)
        }
        nextUiRequest(host, skipped) { it.action == TerminalUiRequestAction.SEARCH_RESULT }
        scenario.onActivity {
            assertTrue(it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.COPY)) != 0)
        }
        val copy = nextUiRequest(host, skipped) { it.action == TerminalUiRequestAction.COPY_SELECTION }
            .payload as TerminalUiPayload.Text
        assertEquals("alpha", copy.bytes.toString(Charsets.UTF_8))
        assertTrue("renderer failure: ${host.failures}", host.failures.isEmpty())
    }

    @Test fun outputAloneNeverRequestsAClipboardAction() = withRenderer { scenario, host ->
        write(scenario, "remote output with no pointer action\r\n")
        assertTrue(host.uiRequests.none { it.action == TerminalUiRequestAction.COPY_SELECTION })
        assertTrue("renderer failure: ${host.failures}", host.failures.isEmpty())
    }

    @Test fun closedAttachmentRefusesLaterNativeCommandsWithoutReplay() = withRenderer { scenario, host ->
        scenario.onActivity { it.renderer.close() }
        var accepted = 1
        scenario.onActivity {
            accepted = it.renderer.sendUiCommand(
                TerminalUiCommand(TerminalUiCommandAction.PASTE, "must-not-replay".toByteArray()))
        }
        assertEquals(0, accepted)
        assertTrue("closed attachment accepted input", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
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

    private fun write(scenario: ActivityScenario<TerminalRendererTestActivity>, text: String) {
        lateinit var renderer: TerminalWebViewBridge
        scenario.onActivity { renderer = it.renderer }
        val failure = AtomicReference<Throwable?>()
        val writer = Thread({
            try {
                renderer.sendOutput(text.toByteArray(), isStderr = false)
                renderer.finishOutput()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }, "cloudy-renderer-output-test").apply { isDaemon = true }
        writer.start()
        writer.join(10_000)
        if (writer.isAlive) {
            renderer.close()
            writer.join(3_000)
        }
        assertFalse("output worker did not retire", writer.isAlive)
        failure.get()?.let { throw AssertionError("renderer output failed", it) }
    }

    private fun dispatchShortcut(
        scenario: ActivityScenario<TerminalRendererTestActivity>,
        key: String,
        code: String,
        ctrl: Boolean,
        shift: Boolean,
    ) {
        val latch = CountDownLatch(1)
        var result: String? = null
        scenario.onActivity { activity ->
            val keyCode = if (key == "C") 67 else 86
            activity.renderer.view.evaluateJavascript("""
                (() => {
                  const textarea = document.querySelector('.xterm-helper-textarea');
                  if (!textarea) return 'missing';
                  textarea.focus();
                  textarea.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true,
                    key: '$key', code: '$code', keyCode: $keyCode, which: $keyCode,
                    ctrlKey: $ctrl, shiftKey: $shift }));
                  return 'dispatched';
                })()
            """.trimIndent()) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue("shortcut script did not run", latch.await(5, TimeUnit.SECONDS))
        assertEquals("\"dispatched\"", result)
    }

    /** Enables bracketed paste and waits for the real parse signal from the same xterm write. */
    private fun awaitBracketedPasteMode(
        scenario: ActivityScenario<TerminalRendererTestActivity>,
        host: TerminalRendererTestActivity,
    ) {
        write(scenario, "\u001b[?1000h\u001b[?2004h")
        // onWriteParsed posts MOUSE_TRACKING, proving DECSET 2004 in the same write was parsed
        // before the native PASTE command is delivered.
        nextUiRequest(host) {
            it.action == TerminalUiRequestAction.MOUSE_TRACKING &&
                (it.payload as? TerminalUiPayload.MouseTracking)?.active == true
        }
    }

    private fun awaitVisibleWarning(scenario: ActivityScenario<TerminalRendererTestActivity>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val latch = CountDownLatch(1)
            var visible = false
            scenario.onActivity { activity ->
                activity.renderer.view.evaluateJavascript("""
                    (() => {
                      const feedback = document.getElementById('feedback');
                      return !!feedback && feedback.dataset.warning === 'true' &&
                        !feedback.hidden && feedback.getBoundingClientRect().height > 0;
                    })()
                """.trimIndent()) { result ->
                    visible = result == "true"
                    latch.countDown()
                }
            }
            assertTrue("feedback observation did not complete", latch.await(5, TimeUnit.SECONDS))
            if (visible) return
            Thread.sleep(100)
        }
        throw AssertionError("refused input had no visible warning")
    }

    private fun nextUiRequest(
        host: TerminalRendererTestActivity,
        skipped: MutableList<TerminalUiRequest>? = null,
        predicate: (TerminalUiRequest) -> Boolean,
    ): TerminalUiRequest {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val request = host.uiRequests.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (predicate(request)) return request
            skipped?.add(request)
        }
        throw AssertionError("expected renderer request; failures=${host.failures}")
    }

    private fun expectBytes(host: TerminalRendererTestActivity, expected: ByteArray) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var observed = ByteArray(0)
        while (observed.size < expected.size && System.nanoTime() < deadline) {
            host.inputs.poll(100, TimeUnit.MILLISECONDS)?.let { observed += it }
        }
        assertArrayEquals("committed bytes differ; failures=${host.failures}", expected, observed)
        assertTrue("input duplicated", host.inputs.poll(250, TimeUnit.MILLISECONDS) == null)
        assertFalse(observed.isEmpty())
    }
}
