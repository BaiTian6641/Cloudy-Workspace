package dev.cloudy.qualification.baseline

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/** Each task owns its Bundle-restored state; the recorder is observation-only. */
class BaselineActivity : Activity() {
    private lateinit var recorder: ProbeRecorder
    private lateinit var engagement: EngagementController<Activity>
    private lateinit var identity: WindowIdentity
    private lateinit var localState: WindowLocalState
    private lateinit var restorationSource: String
    private lateinit var refreshButton: Button
    private lateinit var newWindowButton: Button
    private lateinit var markerButton: Button
    private lateinit var recreateButton: Button
    private lateinit var status: TextView
    private lateinit var tracking: TextView
    private lateinit var windowLabel: TextView
    private lateinit var actionStatus: TextView
    private lateinit var summary: TextView
    private val main = Handler(Looper.getMainLooper())
    private val probeListener = ProbeRecorder.Listener { state -> render(state) }
    private val captureWindow = Runnable {
        if (!isDestroyed) recorder.window(identity, FrameworkCollectors.window(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val restored = WindowLocalState.restore(readSavedFields(savedInstanceState))
        localState = restored.state
        restorationSource = restored.source
        identity = WindowIdentity(localState.logicalWindowId, taskId, UUID.randomUUID().toString())
        val probeApplication = application as ProbeApplication
        recorder = probeApplication.recorder
        recorder.register(identity, restored)
        // The report sink captures process data/identity, never this Activity.
        val reportRecorder = recorder
        val reportIdentity = identity
        engagement = probeApplication.engagement.controller { update ->
            reportRecorder.engagement(reportIdentity, update)
        }

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            setText(R.string.probe_heading)
            textSize = 20f
            isAccessibilityHeading = true
        })
        content.addView(TextView(this).apply { setText(R.string.probe_scope) })
        content.addView(button(R.id.open_transfer_probe, R.string.open_transfer_probe) {
            startActivity(Intent(this, TransferActivity::class.java))
        })
        windowLabel = TextView(this).apply { id = R.id.window_state; setTextIsSelectable(true) }
        content.addView(windowLabel)
        newWindowButton = button(R.id.new_window, R.string.new_window) { launchWindow() }
        content.addView(newWindowButton)
        markerButton = button(R.id.change_marker, R.string.change_marker) {
            localState = localState.changed()
            recorder.changeState(identity, localState)
            renderLocalState()
        }
        content.addView(markerButton)
        recreateButton = button(R.id.recreate_window, R.string.recreate_window) {
            recreateButton.isEnabled = false
            recorder.event(identity, "recreate-requested")
            recreate()
        }
        content.addView(recreateButton)
        refreshButton = button(R.id.refresh_diagnostics, R.string.refresh) {
            observeWindowSoon()
            engagement.refresh()
            recorder.refresh(identity)
        }
        content.addView(refreshButton)
        actionStatus = TextView(this).apply { id = R.id.action_status }
        content.addView(actionStatus)
        tracking = TextView(this).apply { id = R.id.window_tracking }
        content.addView(tracking)
        status = TextView(this).apply {
            id = R.id.save_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setText(R.string.status_pending)
        }
        content.addView(status)
        summary = TextView(this).apply {
            id = R.id.diagnostics_summary
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        content.addView(summary)
        // Scrolling includes every control so font scaling/narrow desktop tiles
        // cannot hide controls above a fixed-height diagnostics area.
        val root = ScrollView(this).apply { addView(content) }
        val padding = (12 * resources.displayMetrics.density).toInt()
        root.setPadding(padding, padding, padding, padding)
        root.setOnApplyWindowInsetsListener { view, insets ->
            // Preserve the native OS caption; only inset app content.
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(padding + safe.left, padding + safe.top, padding + safe.right, padding + safe.bottom)
            observeWindowSoon()
            insets
        }
        // Observe the viewport, not the summary's content height: rendering an
        // observation must not itself create a new observation indefinitely.
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) observeWindowSoon()
        }
        setContentView(root)
        root.requestApplyInsets()
        renderLocalState()
        recorder.ensureInitialCollection()
        observeWindowSoon()
    }

    override fun onStart() {
        super.onStart()
        recorder.addListener(identity, probeListener)
        recorder.event(identity, "activity-started")
        engagement.start(this, recorder.tracks(identity))
    }

    override fun onResume() {
        super.onResume()
        recorder.event(identity, "activity-resumed")
        observeWindowSoon()
    }

    override fun onPause() {
        recorder.event(identity, "activity-paused")
        super.onPause()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        // Multiple desktop activities can be resumed; top-resumed is separate.
        recorder.event(identity, "activity-top-resumed", "topResumed=$isTopResumedActivity")
        observeWindowSoon()
    }

    override fun onStop() {
        engagement.stop()
        recorder.event(identity, "activity-stopped")
        recorder.removeListener(probeListener)
        super.onStop()
    }

    override fun onDestroy() {
        engagement.destroy()
        main.removeCallbacks(captureWindow)
        recorder.removeListener(probeListener)
        recorder.event(identity, "activity-destroyed",
            "changingConfigurations=$isChangingConfigurations;finishing=$isFinishing", close = true)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val saved = localState.savedFields()
        outState.putInt(STATE_VERSION, saved.version)
        outState.putString(WINDOW_ID, saved.logicalWindowId)
        outState.putInt(MARKER, saved.markerIndex)
        outState.putLong(CHANGE_COUNT, saved.changeCount)
        recorder.event(identity, "activity-save-instance-state")
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::identity.isInitialized && !isDestroyed) {
            recorder.event(identity, "window-focus", "focused=$hasFocus")
            observeWindowSoon()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recorder.event(identity, "configuration-changed")
        observeWindowSoon()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        recorder.event(identity, "multi-window-mode", "multiWindow=$isInMultiWindowMode")
        observeWindowSoon()
    }

    private fun launchWindow() {
        // No identity or state extras: a new document creates fresh local state.
        // These flags request a distinct task, not a guaranteed screen position.
        val launch = Intent(this, BaselineActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )
        recorder.event(identity, "new-window-launch-requested")
        try {
            startActivity(launch)
            actionStatus.setText(R.string.launch_requested)
            recorder.event(identity, "new-window-launch-returned", "Inspect the new task/window; return is not qualification.")
        } catch (failure: RuntimeException) {
            actionStatus.text = getString(R.string.launch_failed, failure.javaClass.simpleName)
            recorder.event(identity, "new-window-launch-failed", failure.javaClass.simpleName)
        }
    }

    private fun renderLocalState() {
        title = getString(R.string.window_title, localState.marker, localState.changeCount)
        windowLabel.text = getString(R.string.window_identity, identity.logicalWindowId, identity.taskId,
            identity.generationId, localState.marker, localState.changeCount, restorationSource)
        markerButton.isEnabled = localState.canChange
    }

    private fun render(state: ProbeUiState) {
        refreshButton.isEnabled = !state.collecting
        newWindowButton.isEnabled = state.windowTracking == "current" && state.aliveWindowCount < state.windowCapacity
        tracking.text = getString(R.string.tracking_state, state.windowTracking, state.aliveWindowCount, state.windowCapacity)
        status.text = when {
            state.saveError != null -> getString(R.string.status_failed, state.saveError)
            state.collecting -> getString(R.string.status_collecting)
            !state.saved -> getString(R.string.status_pending)
            else -> getString(R.string.status_saved, getString(
                if (state.hasCollectorError) R.string.status_partial else R.string.status_reported
            ))
        }
        summary.text = state.summary
    }

    private fun button(viewId: Int, label: Int, action: () -> Unit): Button = Button(this).apply {
        id = viewId
        setText(label)
        setOnClickListener { action() }
    }

    private fun observeWindowSoon() {
        main.removeCallbacks(captureWindow)
        main.postDelayed(captureWindow, 120)
    }

    private fun readSavedFields(bundle: Bundle?): SavedWindowFields? {
        if (bundle == null) return null
        return try {
            SavedWindowFields(bundle.getInt(STATE_VERSION, -1), bundle.getString(WINDOW_ID),
                bundle.getInt(MARKER, -1), bundle.getLong(CHANGE_COUNT, -1))
        } catch (_: RuntimeException) {
            // A malformed Bundle is reported as reset, never silently restored.
            SavedWindowFields(-1, null, -1, -1)
        }
    }

    private companion object {
        const val STATE_VERSION = "cloudy.window.stateVersion"
        const val WINDOW_ID = "cloudy.window.logicalId"
        const val MARKER = "cloudy.window.markerIndex"
        const val CHANGE_COUNT = "cloudy.window.changeCount"
    }
}
