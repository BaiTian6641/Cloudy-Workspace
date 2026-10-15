package dev.cloudy.qualification.baseline

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
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

/** The screen observes process-owned work. Recreating or returning never starts it. */
class TransferActivity : Activity() {
    private lateinit var runtime: TransferRuntime
    private lateinit var start: Button
    private lateinit var cancel: Button
    private lateinit var lan: Button
    private lateinit var notifications: Button
    private lateinit var permissions: TextView
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private var visible = false
    private var visibilityEpoch = 0L
    private var latest: TransferUiState? = null
    private val generation = UUID.randomUUID().toString()
    private val listener = TransferRuntime.Listener { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.transfer_heading)
        runtime = TransferRuntime.get(applicationContext)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            setText(R.string.transfer_heading)
            textSize = 20f
            isAccessibilityHeading = true
        })
        content.addView(TextView(this).apply { setText(R.string.transfer_scope) })
        content.addView(TextView(this).apply {
            id = R.id.transfer_window_identity
            text = getString(R.string.transfer_window_identity, generation, taskId)
            setTextIsSelectable(true)
        })
        lan = button(R.id.transfer_request_lan, R.string.transfer_request_lan) {
            if (Build.VERSION.SDK_INT >= 37 && !hasLanPermission()) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), REQUEST_LAN)
            }
        }
        content.addView(lan)
        notifications = button(R.id.transfer_request_notifications, R.string.transfer_request_notifications) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
        content.addView(notifications)
        start = button(R.id.transfer_start, R.string.transfer_start) {
            if (visible && hasLanPermission()) {
                // Preparation can outlive this screen. The completion gate owns
                // only a weak reference and this exact visible-lifecycle epoch.
                val owner = WeakReference(this)
                val epoch = visibilityEpoch
                runtime.start {
                    owner.get()?.let {
                        it.visible && it.visibilityEpoch == epoch && !it.isFinishing &&
                            !it.isDestroyed && it.hasLanPermission()
                    } == true
                }
            }
        }
        content.addView(start)
        cancel = button(R.id.transfer_cancel_button, R.string.transfer_cancel) { runtime.cancelCurrent() }
        content.addView(cancel)
        content.addView(button(R.id.transfer_recreate, R.string.transfer_recreate) { recreate() })
        content.addView(button(R.id.transfer_refresh, R.string.transfer_refresh) {
            runtime.refresh()
            renderPermissions()
        })
        permissions = TextView(this).apply { id = R.id.transfer_permissions }
        content.addView(permissions)
        status = TextView(this).apply {
            id = R.id.transfer_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setText(R.string.transfer_initializing)
        }
        content.addView(status)
        summary = TextView(this).apply { id = R.id.transfer_summary; setTextIsSelectable(true) }
        content.addView(summary)
        val padding = (12 * resources.displayMetrics.density).toInt()
        val root = ScrollView(this).apply {
            addView(content)
            setPadding(padding, padding, padding, padding)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(padding + safe.left, padding + safe.top, padding + safe.right, padding + safe.bottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        cancel.isEnabled = false
        renderPermissions()
    }

    override fun onStart() {
        super.onStart()
        visible = true
        visibilityEpoch++
        runtime.addListener(listener)
        renderPermissions()
    }

    override fun onResume() {
        super.onResume()
        renderPermissions()
    }

    override fun onStop() {
        visible = false
        visibilityEpoch++
        runtime.removeListener(listener)
        super.onStop()
    }

    override fun onDestroy() {
        runtime.removeListener(listener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Grant/denial updates readiness only; scheduling needs a fresh Start action.
        renderPermissions()
    }

    private fun hasLanPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun render(state: TransferUiState) {
        latest = state
        status.text = state.status
        summary.text = state.summary
        cancel.isEnabled = state.canCancel
        renderPermissions()
    }

    private fun renderPermissions() {
        val localAccess = hasLanPermission()
        val notificationGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notificationVisible = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        lan.isEnabled = Build.VERSION.SDK_INT >= 37 && !localAccess
        notifications.isEnabled = !notificationGranted
        start.isEnabled = visible && localAccess && latest?.canStart == true
        permissions.text = getString(R.string.transfer_permission_state,
            if (localAccess) getString(R.string.transfer_granted) else getString(R.string.transfer_denied),
            if (notificationGranted) getString(R.string.transfer_granted) else getString(R.string.transfer_denied),
            notificationVisible.toString())
    }

    private fun button(resourceId: Int, label: Int, action: () -> Unit): Button = Button(this).apply {
        id = resourceId
        setText(label)
        setOnClickListener { action() }
    }

    companion object {
        private const val REQUEST_LAN = 41
        private const val REQUEST_NOTIFICATIONS = 42
    }
}
