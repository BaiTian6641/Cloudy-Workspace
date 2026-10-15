package dev.cloudy.qualification.baseline

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.UUID

/** Native trust/permission controls surround a local terminal page with no credential access. */
class TerminalActivity : Activity() {
    private lateinit var runtime: TerminalRuntime
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var trust: Button
    private lateinit var lan: Button
    private lateinit var status: TextView
    private lateinit var surface: FrameLayout
    private var latest: TerminalUiState? = null
    private var bridge: TerminalWebViewBridge? = null
    private var identity: TerminalIdentity? = null
    private var pending: TerminalIdentity? = null
    private var localFailure: String? = null
    @Volatile private var visible = false
    @Volatile private var epoch = 0L
    private val listener = TerminalRuntime.Listener { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.terminal_heading)
        runtime = (application as ProbeApplication).terminal
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(TextView(this).apply {
            setText(R.string.terminal_heading); textSize = 18f; isAccessibilityHeading = true
        })
        header.addView(TextView(this).apply { setText(R.string.terminal_scope) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lan = button(R.id.terminal_lan, R.string.terminal_lan) {
            if (Build.VERSION.SDK_INT >= 37 && !hasLanPermission()) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), 61)
            }
        }
        controls.addView(lan)
        connect = button(R.id.terminal_connect, R.string.terminal_connect) { connectExplicitly() }
        controls.addView(connect)
        trust = button(R.id.terminal_trust, R.string.terminal_trust) {
            val snapshot = latest ?: return@button
            val ticket = snapshot.trustTicket ?: return@button
            if (snapshot.canTrust) AlertDialog.Builder(this)
                .setTitle(R.string.terminal_trust).setMessage(snapshot.trustSummary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.terminal_trust_confirm) { _, _ -> runtime.acceptTrust(ticket) }.show()
        }
        controls.addView(trust)
        disconnect = button(R.id.terminal_disconnect, R.string.terminal_disconnect) {
            pending = null
            identity?.let { runtime.stop(it, "user-disconnect") }
            bridge?.close()
            latest?.let(::render)
        }
        controls.addView(disconnect)
        controls.addView(button(R.id.terminal_details, R.string.terminal_details) { details() })
        controls.addView(button(R.id.terminal_refresh, R.string.terminal_refresh) {
            localFailure = null; runtime.refresh()
        })
        controls.addView(button(R.id.terminal_recreate, R.string.terminal_recreate) { recreate() })
        header.addView(HorizontalScrollView(this).apply { addView(controls); isFillViewport = false })
        status = TextView(this).apply {
            id = R.id.terminal_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        header.addView(status)
        val headerScroll = ScrollView(this).apply { addView(header) }
        root.addView(headerScroll, LinearLayout.LayoutParams(-1, dp(140)))
        surface = FrameLayout(this).apply { id = R.id.terminal_surface }
        surface.addView(TextView(this).apply { setText(R.string.terminal_empty) })
        root.addView(surface, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                // The native controls scroll at large fonts/narrow tiles; they cannot consume the whole terminal.
                val available = (bottom - top - root.paddingTop - root.paddingBottom).coerceAtLeast(1)
                val height = minOf(dp(140), (available * 0.45f).toInt()).coerceAtLeast(1)
                if (headerScroll.layoutParams.height != height) {
                    headerScroll.layoutParams = (headerScroll.layoutParams as LinearLayout.LayoutParams).apply { this.height = height }
                }
            }
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            val padding = dp(8)
            view.setPadding(safe.left + padding, safe.top + padding, safe.right + padding, safe.bottom + padding)
            insets
        }
        setContentView(root); root.requestApplyInsets()
        connect.isEnabled = false; disconnect.isEnabled = false; trust.isEnabled = false
    }

    override fun onStart() { super.onStart(); visible = true; epoch++; runtime.addListener(listener) }
    override fun onResume() { super.onResume(); latest?.let(::render) }
    override fun onStop() {
        visible = false; epoch++; pending = null
        identity?.let { runtime.stop(it, if (isChangingConfigurations) "activity-recreated" else "owner-no-longer-visible") }
        bridge?.close(); runtime.removeListener(listener)
        super.onStop()
    }
    override fun onDestroy() { bridge?.dispose(); bridge = null; super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        latest?.let(::render) // Granting permission never creates a terminal or starts a connection.
    }

    private fun connectExplicitly() {
        if (!visible || !hasLanPermission() || latest?.canConnect != true || pending != null) return
        bridge?.dispose(); surface.removeAllViews()
        val next = TerminalIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val capturedEpoch = epoch; val owner = WeakReference(this)
        identity = next; pending = next; localFailure = null
        val renderer = try { TerminalWebViewBridge(this, next, object : TerminalWebViewBridge.Listener {
            override fun onReady(size: TerminalSize) {
                val activity = owner.get() ?: return
                val current = activity.bridge ?: return
                if (activity.pending != next || activity.identity != next || !activity.isCurrent(capturedEpoch)) {
                    current.close(); return
                }
                activity.pending = null
                val accepted = runtime.connect(next, size, current.qualificationDetails,
                    { owner.get()?.isCurrent(capturedEpoch) == true }, current::sendOutput,
                    current::finishOutput, current::close) {
                    owner.get()?.takeIf { it.identity == next }?.let {
                        it.bridge?.close(); it.latest?.let(it::render)
                    }
                }
                if (!accepted) {
                    activity.localFailure = "Session admission unavailable; use Connect explicitly after the current operation finishes."
                    current.close(); activity.latest?.let(activity::render)
                }
            }
            override fun onInput(bytes: ByteArray): TerminalAdmission =
                if (owner.get()?.isCurrent(capturedEpoch) == true) runtime.input(next, bytes) else TerminalAdmission.STOPPED
            override fun onResize(size: TerminalSize): TerminalAdmission =
                if (owner.get()?.isCurrent(capturedEpoch) == true) runtime.resize(next, size) else TerminalAdmission.STOPPED
            override fun onFailure(reason: String) {
                runtime.stop(next, reason, true)
                owner.get()?.takeIf { it.identity == next }?.let {
                    it.pending = null; it.localFailure = "Renderer stopped: $reason"; it.latest?.let(it::render)
                }
            }
        }) } catch (failure: Exception) {
            pending = null
            localFailure = "Renderer unavailable: ${failure.javaClass.simpleName.take(80)}"
            latest?.let(::render)
            return
        }
        bridge = renderer
        surface.addView(renderer.view, FrameLayout.LayoutParams(-1, -1))
        latest?.let(::render)
        renderer.start()
    }

    private fun render(state: TerminalUiState) {
        latest = state
        status.text = localFailure ?: if (pending != null) "Preparing local terminal…" else state.status
        connect.isEnabled = visible && hasLanPermission() && state.canConnect && pending == null
        trust.isEnabled = visible && state.canTrust && pending == null
        disconnect.isEnabled = pending != null || (state.canDisconnect && state.identity == identity)
        lan.isEnabled = Build.VERSION.SDK_INT >= 37 && !hasLanPermission()
    }
    private fun details() {
        val text = latest?.let { "${it.trustSummary}\n\n${it.summary}\n\n${bridge?.providerSummary ?: "Renderer not prepared"}" }
            ?: "Terminal state is loading."
        val value = TextView(this).apply { this.text = text; setTextIsSelectable(true); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        AlertDialog.Builder(this).setTitle(R.string.terminal_details)
            .setView(ScrollView(this).apply { addView(value) }).setPositiveButton(android.R.string.ok, null).show()
    }
    private fun isCurrent(expectedEpoch: Long) = visible && epoch == expectedEpoch && !isFinishing && !isDestroyed && hasLanPermission()
    private fun hasLanPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun button(idValue: Int, label: Int, action: () -> Unit) = Button(this).apply {
        id = idValue; setText(label); setOnClickListener { action() }
    }
}
