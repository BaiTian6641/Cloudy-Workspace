package dev.cloudy.qualification.rdp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.WindowInsets
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

/** Finite native-engine qualification UI; no background session owner or reconnect. */
class RdpActivity : Activity() {
    private lateinit var session: RdpSession
    private lateinit var frame: RdpFrameView
    private lateinit var status: TextView
    private lateinit var permission: Button
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var composer: EditText
    private lateinit var send: Button
    private lateinit var capture: Button
    private lateinit var release: Button
    private lateinit var captureStatus: TextView
    private val statusPolicy = RdpStatusPolicy()
    private val renderedLabels = mutableMapOf<TextView, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = RdpSession(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 247))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            view.setPadding(dp(16) + safe.left, dp(16) + safe.top, dp(16) + safe.right, dp(16) + safe.bottom)
            insets
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(controls)
        }
        fun label(resource: Int) = TextView(this).apply {
            setText(resource)
            setTextColor(Color.rgb(24, 36, 39))
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
            controls.addView(this)
        }
        label(R.string.probe_title).textSize = 20f
        label(R.string.probe_scope)
        label(R.string.configuration_help)
        status = label(R.string.state_initial).apply { accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        permission = Button(this).apply {
            setText(R.string.request_lan)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 37) requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), REQUEST_LAN)
            }
        }
        controls.addView(permission)
        connect = Button(this).apply {
            setText(R.string.connect)
            setOnClickListener { session.connect() }
        }
        controls.addView(connect)
        disconnect = Button(this).apply {
            setText(R.string.disconnect)
            setOnClickListener { session.releaseInput(); session.disconnect("operator") }
        }
        controls.addView(disconnect)
        label(R.string.input_help)
        label(R.string.relative_help)
        captureStatus = label(R.string.relative_disconnected).apply {
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        capture = Button(this).apply {
            setText(R.string.capture_relative)
            setOnClickListener {
                if (frame.isCaptureRequested || frame.isCaptureConfirmed) frame.releaseInput()
                else frame.requestRelativeCapture()
            }
        }
        controls.addView(capture)
        release = Button(this).apply {
            setText(R.string.release_input)
            setOnClickListener { frame.releaseInput() }
        }
        controls.addView(release)
        controls.addView(Button(this).apply {
            setText(R.string.input_menu)
            setOnClickListener { anchor ->
                val popup = PopupMenu(this@RdpActivity, anchor)
                popup.menu.add(Menu.NONE, RELEASE_INPUT_MENU, Menu.NONE, R.string.release_input)
                popup.setOnMenuItemClickListener { item ->
                    if (item.itemId == RELEASE_INPUT_MENU) { frame.releaseInput(); true } else false
                }
                popup.show()
            }
        })
        composer = EditText(this).apply {
            hint = getString(R.string.composer_hint)
            contentDescription = getString(R.string.composer_label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 1
            maxLines = 4
            // Remote text must not enter framework saved-instance state or autofill.
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        controls.addView(composer)
        send = Button(this).apply {
            setText(R.string.send_text)
            setOnClickListener {
                val value = composer.text.toString()
                if (value.isNotEmpty() && session.snapshot().connected) {
                    // Delivery is asynchronous and cannot be confirmed here. Keep the
                    // submitted draft visible when validation or remote delivery fails.
                    session.text(value)
                }
            }
        }
        controls.addView(send)
        controls.addView(Button(this).apply {
            setText(R.string.native_notices)
            setOnClickListener { showNotices() }
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        frame = RdpFrameView(this, session)
        frame.captureStateListener = { renderCapture() }
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 2f))
        setContentView(root)
        root.requestApplyInsets()
        session.listener = { render(it) }
        render(session.snapshot())
    }

    private fun showNotices() {
        val names = assets.list("").orEmpty().filter { it.endsWith(".txt") }.sorted()
        val content = names.joinToString("\n\n") { name ->
            "$name\n" + assets.open(name).bufferedReader().use { it.readText() }
        }
        val text = TextView(this).apply {
            this.text = content
            setTextIsSelectable(true)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(text) }
        AlertDialog.Builder(this).setTitle(R.string.native_notices).setView(scroll)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun render(snapshot: RdpSnapshot) {
        val identity = RdpStatusPolicy.Identity(snapshot.attemptId, snapshot.state, snapshot.detail,
            snapshot.nativeVersion, snapshot.width, snapshot.height, snapshot.busy, snapshot.connected)
        if (statusPolicy.shouldRender(identity, snapshot.frameUpdates, snapshot.inputEvents, SystemClock.elapsedRealtime())) {
            status.text = getString(R.string.session_status, snapshot.state, snapshot.detail, snapshot.nativeVersion,
                snapshot.width, snapshot.height, snapshot.frameUpdates, snapshot.inputEvents)
        }
        // Read once per refresh, including resume/grant; cached setters never cache permission itself.
        val lanAvailable = hasLanPermission()
        permission.enableIfChanged(!lanAvailable)
        permission.labelIfChanged(if (lanAvailable) R.string.lan_available else R.string.request_lan)
        connect.enableIfChanged(lanAvailable && !snapshot.busy && !snapshot.connected)
        disconnect.enableIfChanged(snapshot.busy || snapshot.connected)
        disconnect.labelIfChanged(if (snapshot.busy && !snapshot.connected) R.string.cancel else R.string.disconnect)
        send.enableIfChanged(snapshot.connected)
        // Status pacing must not suppress framebuffer/cursor updates or capture transitions.
        frame.update(snapshot)
        renderCapture(snapshot)
    }

    private fun renderCapture(snapshot: RdpSnapshot = session.snapshot()) {
        val owned = frame.isCaptureRequested || frame.isCaptureConfirmed
        captureStatus.labelIfChanged(frame.captureStatusResource)
        capture.labelIfChanged(if (owned) R.string.release_relative else R.string.capture_relative)
        capture.enableIfChanged(snapshot.connected && (snapshot.relativeSupported || owned))
        release.enableIfChanged(snapshot.connected || owned)
    }

    private fun TextView.labelIfChanged(resource: Int) {
        if (renderedLabels[this] != resource) {
            setText(resource)
            renderedLabels[this] = resource
        }
    }

    private fun Button.enableIfChanged(enabled: Boolean) {
        if (isEnabled != enabled) isEnabled = enabled
    }

    override fun onResume() {
        super.onResume()
        if (::frame.isInitialized) render(session.snapshot())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::frame.isInitialized && frame.handleLocalReleaseChord(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, RELEASE_INPUT_MENU, Menu.NONE, R.string.release_input)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == RELEASE_INPUT_MENU) { frame.releaseInput(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun hasLanPermission() = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LAN) render(session.snapshot())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::frame.isInitialized) frame.releaseInput()
    }

    override fun onStop() {
        frame.releaseInput()
        session.disconnect("not-visible")
        super.onStop()
    }

    override fun onDestroy() {
        session.listener = null
        frame.captureStateListener = null
        frame.releaseInput()
        session.close()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_LAN = 71; private const val RELEASE_INPUT_MENU = 1 }
}
