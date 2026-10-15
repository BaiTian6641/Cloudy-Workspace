package dev.cloudy.qualification.baseline

import android.app.Activity
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

/** Real platform observations only; this Activity is not either product shell. */
class BaselineActivity : Activity() {
    private lateinit var recorder: ProbeRecorder
    private lateinit var refreshButton: Button
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private val main = Handler(Looper.getMainLooper())
    private val probeListener = ProbeRecorder.Listener { state -> render(state) }
    private val captureWindow = Runnable {
        if (!isDestroyed) recorder.window(FrameworkCollectors.window(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorder = (application as ProbeApplication).recorder
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val padding = (12 * resources.displayMetrics.density).toInt()
        root.setPadding(padding, padding, padding, padding)
        root.addView(TextView(this).apply {
            setText(R.string.probe_heading)
            textSize = 20f
            isAccessibilityHeading = true
        })
        root.addView(TextView(this).apply { setText(R.string.probe_scope) })
        refreshButton = Button(this).apply {
            setText(R.string.refresh)
            setOnClickListener {
                observeWindowSoon()
                recorder.refresh()
            }
        }
        root.addView(refreshButton)
        status = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setText(R.string.status_pending)
        }
        root.addView(status)
        summary = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(summary) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Leave the OS caption untouched. Apply observed insets to the content;
        // caption rectangles are measured, never used as guessed control geometry.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(padding + safe.left, padding + safe.top,
                padding + safe.right, padding + safe.bottom)
            observeWindowSoon()
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                observeWindowSoon()
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        recorder.event("activity-created", "savedInstanceStatePresent=${savedInstanceState != null}")
        recorder.ensureInitialCollection()
        observeWindowSoon()
    }

    override fun onStart() {
        super.onStart()
        recorder.addListener(probeListener)
        recorder.event("activity-started")
    }

    override fun onResume() {
        super.onResume()
        recorder.event("activity-resumed")
        observeWindowSoon()
    }

    override fun onPause() {
        recorder.event("activity-paused")
        super.onPause()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        // Multiple desktop activities can be resumed; top-resumed is a separate fact.
        recorder.event("activity-top-resumed", "topResumed=$isTopResumedActivity")
        observeWindowSoon()
    }

    override fun onStop() {
        recorder.event("activity-stopped")
        recorder.removeListener(probeListener)
        super.onStop()
    }

    override fun onDestroy() {
        main.removeCallbacks(captureWindow)
        recorder.removeListener(probeListener)
        recorder.event("activity-destroyed", "changingConfigurations=$isChangingConfigurations")
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        recorder.event("activity-save-instance-state")
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::recorder.isInitialized && !isDestroyed) {
            recorder.event("window-focus", "focused=$hasFocus")
            observeWindowSoon()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recorder.event("configuration-changed")
        observeWindowSoon()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        recorder.event("multi-window-mode", "multiWindow=$isInMultiWindowMode")
        observeWindowSoon()
    }

    private fun render(state: ProbeUiState) {
        refreshButton.isEnabled = !state.collecting
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

    private fun observeWindowSoon() {
        main.removeCallbacks(captureWindow)
        main.postDelayed(captureWindow, 120)
    }
}
