package io.github.baitian6641.cloudy.testing.ime

import android.app.UiAutomation
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual framework binding and editor effects, not a mock IC, SSH or Googlebook qualification. */
@RunWith(AndroidJUnit4::class)
class ImeQualificationSessionTest {
    @Test fun realEditorReceivesFirstCommitAndNativeEnterOnce() = withEditor { session, view ->
        val text = "Cloudy 中文 e\u0301 🙂"
        assertSubmitted(session.commit(view, text))
        awaitValue(view, text)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        awaitValue(view, text + "\n")
    }

    @Test fun firstSoftwareEnterReachesTheEditor() = withEditor { session, view ->
        assertSubmitted(session.enter(view))
        awaitValue(view, "\n")
    }

    @Test fun pointerFocusedEditorReceivesNativeEnter() = withEditor { _, view ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val location = IntArray(2)
        var x = 0f
        var y = 0f
        instrumentation.runOnMainSync {
            check(view.isShown && view.hasWindowFocus() && view.width > 0 && view.height > 0)
            view.getLocationOnScreen(location)
            // The fixed editor covers 80% of this viewport; this point is inside, not on a control.
            x = location[0] + view.width / 2f
            y = location[1] + view.height / 4f
        }
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; size = 1f })
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, button: Int) {
            coordinates[0].pressure = if (action == MotionEvent.ACTION_DOWN) 1f else 0f
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, properties,
                coordinates, 0, button, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            try { assertTrue("NATIVE_POINTER_REFUSED", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        try { inject(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY) }
        finally { inject(MotionEvent.ACTION_UP, 0) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        awaitValue(view, "\n")
    }

    @Test fun expiredWrongTargetAndConsumedRequestsNeverEdit() = withEditor { session, view ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertSubmitted(session.commit(view, "anchor"))
        awaitValue(view, "anchor")
        val lease = checkNotNull(ImeFixtureProtocol.readLease(context))
        val originalId = lease.requests.last()
        val original = checkNotNull(ImeFixtureProtocol.readResult(context, lease.run, originalId))
        val state = checkNotNull(ImeFixtureProtocol.readState(context))
        val target = checkNotNull(state.target)
        val request = FixtureRequest(
            schema = 1, run = lease.run, request = UUID.randomUUID().toString(),
            instance = state.instance, generation = state.generation,
            targetPackage = context.packageName, clientPid = Process.myPid(), fieldId = target.fieldId,
            expiresElapsedMillis = SystemClock.elapsedRealtime() + 10_000,
            operation = ImeFixtureOperation.COMMIT, text = "must-not-arrive",
        )
        val expired = request.copy(expiresElapsedMillis = SystemClock.elapsedRealtime() - 1)
        session.recordRequest(expired.request)
        assertRefused(session, expired, ImeFixtureResult.Reason.EXPIRED_REQUEST)
        val foreign = request.copy(request = UUID.randomUUID().toString(), clientPid = Process.myPid() + 1)
        session.recordRequest(foreign.request)
        assertRefused(session, foreign, ImeFixtureResult.Reason.TARGET_MISMATCH)
        // Model result-publication loss after a real delivered edit. Removing our own result
        // must not let a repeated, otherwise valid request execute again in this service instance.
        assertTrue(ImeFixtureProtocol.deleteRecord(ImeFixtureProtocol.resultFile(context, lease.run, originalId)))
        val replay = request.copy(request = originalId, instance = original.instance,
            generation = original.generation, expiresElapsedMillis = SystemClock.elapsedRealtime() + 10_000)
        assertRefused(session, replay, ImeFixtureResult.Reason.DUPLICATE_REQUEST)
        assertEquals("anchor", value(view))
    }

    @Test fun malformedRequestIdCannotBeCorrectedAndReplayed() = withEditor { session, view ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertSubmitted(session.commit(view, "anchor"))
        awaitValue(view, "anchor")
        val lease = checkNotNull(ImeFixtureProtocol.readLease(context))
        val state = checkNotNull(ImeFixtureProtocol.readState(context))
        val request = FixtureRequest(
            schema = 1, run = lease.run, request = UUID.randomUUID().toString(),
            instance = state.instance, generation = state.generation,
            targetPackage = context.packageName, clientPid = Process.myPid(),
            fieldId = checkNotNull(state.target).fieldId,
            expiresElapsedMillis = SystemClock.elapsedRealtime() + 10_000,
            operation = ImeFixtureOperation.COMMIT, text = null,
        )
        session.recordRequest(request.request)
        ImeFixtureProtocol.writeRequest(context, request) // Deliberately missing the required text.
        session.dispatch(request.request)
        assertTrue(ImeFixtureProtocol.deleteRecord(ImeFixtureProtocol.requestFile(context, request.run, request.request)))
        assertRefused(session, request.copy(text = "must-not-arrive"), ImeFixtureResult.Reason.DUPLICATE_REQUEST)
        assertEquals("anchor", value(view))
    }

    private fun assertRefused(
        session: ImeQualificationSession,
        request: FixtureRequest,
        reason: ImeFixtureResult.Reason,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ImeFixtureProtocol.writeRequest(context, request)
        session.dispatch(request.request)
        var result: ParsedResult? = null
        awaitCondition {
            result = ImeFixtureProtocol.readResult(context, request.run, request.request)
            result != null
        }
        assertEquals(ImeFixtureResult.Outcome.REJECTED, checkNotNull(result).outcome)
        assertEquals(reason, checkNotNull(result).reason)
    }

    private fun withEditor(action: (ImeQualificationSession, WebView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val session = ImeQualificationSession.open(instrumentation, automation, UUID.randomUUID().toString())
        try {
            val intent = Intent(instrumentation.targetContext, ImeFixtureTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<ImeFixtureTestActivity>(intent).use { scenario ->
                lateinit var activity: ImeFixtureTestActivity
                scenario.onActivity { activity = it }
                assertTrue("EDITOR_LOAD_TIMEOUT", activity.loaded.await(10, TimeUnit.SECONDS))
                val view = activity.editor
                instrumentation.runOnMainSync { view.requestFocus() }
                javascript(view, "document.getElementById('editor').focus()")
                awaitCondition {
                    javascript(view, "document.hasFocus() && document.activeElement.id === 'editor'") == "true"
                }
                val drawn = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    view.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            val observer = view.viewTreeObserver
                            val listener = object : android.view.ViewTreeObserver.OnDrawListener {
                                private var signalled = false
                                override fun onDraw() {
                                    if (signalled) return
                                    signalled = true
                                    view.post {
                                        if (observer.isAlive) observer.removeOnDrawListener(this)
                                        drawn.countDown()
                                    }
                                }
                            }
                            observer.addOnDrawListener(listener)
                            view.invalidate()
                        }
                    })
                }
                assertTrue("EDITOR_NOT_DRAWN", drawn.await(10, TimeUnit.SECONDS))
                action(session, view)
            }
        } finally {
            session.close()
        }
        assertTrue("IME_CLEANUP_UNCONFIRMED_${session.closeReason?.name ?: "NONE"}", session.cleanupConfirmed)
    }

    private fun assertSubmitted(result: ImeFixtureResult) {
        assertTrue("IME_NOT_SUBMITTED_${result.reason?.name ?: "NONE"}", result.submitted)
    }

    private fun value(view: WebView): String =
        JSONTokener(javascript(view, "document.getElementById('editor').value")).nextValue() as String

    private fun awaitValue(view: WebView, expected: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var actual = value(view)
        while (actual != expected && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(10)
            actual = value(view)
        }
        // This editor contains only declared synthetic fixture input, never terminal/user content.
        assertEquals(expected, actual)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "EDITOR_EFFECT_TIMEOUT" }
            SystemClock.sleep(10)
        }
    }

    private fun javascript(view: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript(expression) { result.set(it); done.countDown() }
        }
        check(done.await(5, TimeUnit.SECONDS)) { "EDITOR_SCRIPT_TIMEOUT" }
        return checkNotNull(result.get())
    }
}
