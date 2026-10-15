package dev.cloudy.qualification.rdp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
        status.text = getString(R.string.session_status, snapshot.state, snapshot.detail, snapshot.nativeVersion,
            snapshot.width, snapshot.height, snapshot.frameUpdates, snapshot.inputEvents)
        permission.isEnabled = !hasLanPermission()
        permission.setText(if (hasLanPermission()) R.string.lan_available else R.string.request_lan)
        connect.isEnabled = hasLanPermission() && !snapshot.busy && !snapshot.connected
        disconnect.isEnabled = snapshot.busy || snapshot.connected
        disconnect.setText(if (snapshot.busy && !snapshot.connected) R.string.cancel else R.string.disconnect)
        send.isEnabled = snapshot.connected
        frame.update(snapshot)
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
        frame.releaseInput()
        session.close()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_LAN = 71 }
}
