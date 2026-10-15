package dev.cloudy.qualification.baseline

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.UUID

/** Native controls observe one finite process owner; creating a window never opens a connection. */
class SshActivity : Activity() {
    private lateinit var runtime: SshRuntime
    private lateinit var run: Button
    private lateinit var runPty: Button
    private lateinit var resizePty: Button
    private lateinit var finishPty: Button
    private lateinit var trust: Button
    private lateinit var cancel: Button
    private lateinit var lan: Button
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var keySummary: TextView
    @Volatile private var visible = false
    @Volatile private var epoch = 0L
    private var latest: SshUiState? = null
    private val generation = UUID.randomUUID().toString()
    private val listener = SshRuntime.Listener { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.ssh_heading)
        runtime = (application as ProbeApplication).ssh
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            setText(R.string.ssh_heading); textSize = 20f; isAccessibilityHeading = true
        })
        content.addView(TextView(this).apply { setText(R.string.ssh_scope) })
        content.addView(TextView(this).apply {
            id = R.id.ssh_window_identity
            text = getString(R.string.ssh_window_identity, generation, taskId)
            setTextIsSelectable(true)
        })
        lan = button(R.id.ssh_request_lan, R.string.ssh_request_lan) {
            if (Build.VERSION.SDK_INT >= 37 && !hasLanPermission()) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), 51)
            }
        }
        content.addView(lan)
        run = button(R.id.ssh_run, R.string.ssh_run) { start(SshOperation.SFTP) }
        content.addView(run)
        runPty = button(R.id.ssh_run_pty, R.string.ssh_run_pty) { start(SshOperation.EXEC_PTY) }
        content.addView(runPty)
        keySummary = TextView(this).apply { id = R.id.ssh_key_summary; setTextIsSelectable(true) }
        content.addView(keySummary)
        trust = button(R.id.ssh_trust, R.string.ssh_trust) { runtime.acceptTrust() }
        content.addView(trust)
        cancel = button(R.id.ssh_cancel, R.string.ssh_cancel) { runtime.cancel() }
        content.addView(cancel)
        resizePty = button(R.id.ssh_resize_pty, R.string.ssh_resize_pty) { control(SshPtyAction.RESIZE) }
        content.addView(resizePty)
        finishPty = button(R.id.ssh_finish_pty, R.string.ssh_finish_pty) { control(SshPtyAction.FINISH) }
        content.addView(finishPty)
        content.addView(button(R.id.ssh_refresh, R.string.ssh_refresh) { runtime.refresh() })
        content.addView(button(R.id.ssh_recreate, R.string.ssh_recreate) { recreate() })
        content.addView(button(R.id.ssh_new_window, R.string.ssh_new_window) {
            if (runtime.canOpenWindow()) {
                startActivity(Intent(this, SshActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                })
            }
        })
        status = TextView(this).apply {
            id = R.id.ssh_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(status)
        summary = TextView(this).apply { id = R.id.ssh_summary; setTextIsSelectable(true) }
        content.addView(summary)
        val padding = (12 * resources.displayMetrics.density).toInt()
        val root = ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(padding + safe.left, padding + safe.top, padding + safe.right, padding + safe.bottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        run.isEnabled = false; runPty.isEnabled = false
        resizePty.isEnabled = false; finishPty.isEnabled = false
        trust.isEnabled = false; cancel.isEnabled = false
    }

    override fun onStart() {
        super.onStart(); visible = true; epoch++
        runtime.addListener(listener)
    }

    override fun onResume() { super.onResume(); latest?.let(::render) }

    override fun onStop() {
        visible = false; epoch++
        runtime.removeListener(listener, isChangingConfigurations)
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        latest?.let(::render) // A permission result never starts a connection.
    }

    private fun render(state: SshUiState) {
        latest = state
        status.text = state.status
        summary.text = state.summary
        keySummary.text = state.trustSummary
        run.isEnabled = visible && hasLanPermission() && state.canRun
        runPty.isEnabled = run.isEnabled
        resizePty.isEnabled = visible && hasLanPermission() && state.ptyAction == SshPtyAction.RESIZE
        finishPty.isEnabled = visible && hasLanPermission() && state.ptyAction == SshPtyAction.FINISH
        trust.isEnabled = visible && state.canTrust
        cancel.isEnabled = state.canCancel
        lan.isEnabled = Build.VERSION.SDK_INT >= 37 && !hasLanPermission()
    }

    private fun start(kind: SshOperation) {
        val owner = WeakReference(this)
        val capturedEpoch = epoch
        runtime.run(kind) { owner.get()?.let {
            it.visible && it.epoch == capturedEpoch && !it.isFinishing && !it.isDestroyed && it.hasLanPermission()
        } == true }
    }

    private fun control(action: SshPtyAction) {
        val attemptId = latest?.activeAttemptId ?: return
        val capturedEpoch = epoch
        if (runtime.control(attemptId, action) {
            visible && epoch == capturedEpoch && !isFinishing && !isDestroyed
        }) {
            // The worker publishes the next phase only after a real server response.
            resizePty.isEnabled = false
            finishPty.isEnabled = false
        }
    }

    private fun hasLanPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun button(resourceId: Int, label: Int, action: () -> Unit): Button = Button(this).apply {
        id = resourceId; setText(label); setOnClickListener { action() }
    }
}
