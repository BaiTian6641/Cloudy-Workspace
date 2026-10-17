package io.github.baitian6641.cloudy.desktop

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.baitian6641.cloudy.desktop.vnc.VncEngine
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.platform.CloudyAppTheme

/** Private Desktop absolute-input entry. Intent extras/data never supply an endpoint, secret or authority. */
class VncActivity : ComponentActivity() {
    private lateinit var model: VncSessionModel
    private val attachment = Any()
    private var attached = false
    private var initialRefresh = false
    private var resumed by mutableStateOf(false)
    private var focused by mutableStateOf(false)
    private var topResumed = false
    private var selectedId by mutableStateOf<String?>(null)
    private var selectedRevision by mutableLongStateOf(0L)
    private var username by mutableStateOf(TextFieldValue())
    private var password by mutableStateOf(TextFieldValue())
    private var fingerprint by mutableStateOf(TextFieldValue())
    private var pinCompared by mutableStateOf(false)
    private var fieldEpoch by mutableLongStateOf(0L)
    private var preferFullscreen by mutableStateOf(true)
    private var localGranted by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private var releaseEpoch by mutableLongStateOf(0L)
    private var waitingForReady by mutableStateOf(false)
    private var readyAttempt: VncAttempt? = null
    private var preexistingAttempt: VncAttempt? = null
    private var apiEntered = false
    private var fullscreenPending by mutableStateOf(false)
    private var fullscreenLabel by mutableIntStateOf(0)
    private var windowRequestSerial = 0L
    private var releaseChordHeld = false
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> observeWindow() }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        clearFields()
        cancelReadyIntent()
        localGranted = hasLocalPermission()
        notice = getString(if (localGranted) R.string.vnc_permission_granted else R.string.vnc_permission_denied)
        // A permission callback never resubmits credentials or starts an engine.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "io.github.baitian6641.cloudy.desktop") { finish(); return }
        // Public Window.Callback intercepts only the local release chord. Delegate all other
        // events to the original AndroidX callback so native fields and Compose keep their paths.
        val originalCallback = checkNotNull(window.callback)
        window.callback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                handleReleaseChord(event) || originalCallback.dispatchKeyEvent(event)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        enableEdgeToEdge()
        var created = false
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == VncSessionModel::class.java)
                created = true
                @Suppress("UNCHECKED_CAST")
                return VncSessionModel(application) as T
            }
        })[VncSessionModel::class.java]
        attached = model.attach(attachment)
        if (!attached) { finish(); return }
        // Only harmless local metadata is restored. A fresh process never restores connect/fullscreen intent.
        selectedId = savedInstanceState?.getString(STATE_PROFILE)?.takeIf { UUID.matches(it) }
        selectedRevision = savedInstanceState?.getLong(STATE_REVISION, 0L)?.takeIf { it > 0 } ?: 0L
        preferFullscreen = savedInstanceState?.getBoolean(STATE_PREFERENCE, true) ?: true
        apiEntered = !created && savedInstanceState?.getBoolean(STATE_API_ENTRY, false) == true
        if (created && savedInstanceState?.getBoolean(STATE_INTERRUPTED, false) == true) {
            notice = getString(R.string.vnc_interrupted)
        }
        initialRefresh = created
        if (!created && model.state.attempt != null && selectedProfile() == null) {
            model.disconnect(attachment)
            notice = getString(R.string.vnc_identity_unavailable)
        }
        localGranted = hasLocalPermission()
        fullscreenLabel = R.string.vnc_window_os_managed
        title = getString(R.string.vnc_heading)
        window.decorView.addOnLayoutChangeListener(layoutListener)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeSession()
        })
        setContent {
            CloudyAppTheme {
                val state = model.state
                val selected = selectedProfile()
                val epoch = fieldEpoch
                LaunchedEffect(selected?.id, selected?.revision, selected?.host, selected?.port) {
                    clearFields()
                    cancelReadyIntent()
                }
                LaunchedEffect(state.attempt, state.presented, state.engineState, state.loading, resumed, focused) {
                    tryReadyFullscreen()
                }
                VncScreen(state, model, attachment, selected, username, password, fingerprint, pinCompared,
                    epoch, preferFullscreen, canInteract(), localGranted, notice,
                    getString(fullscreenLabel), fullscreenPending, releaseEpoch,
                    onSelect = ::selectProfile,
                    onUsername = { if (epoch == fieldEpoch && canEdit() && it.text.length <= 256) username = it },
                    onPassword = { if (epoch == fieldEpoch && canEdit() && it.text.length <= 1024) password = it },
                    onFingerprint = {
                        if (epoch == fieldEpoch && canEdit() && it.text.length <= 64) {
                            fingerprint = it; pinCompared = false
                        }
                    },
                    onPinCompared = { if (epoch == fieldEpoch && canEdit()) pinCompared = it },
                    onPreferFullscreen = { if (canEdit()) preferFullscreen = it },
                    onRefresh = { if (canEdit()) { clearFields(); model.refresh(attachment) } },
                    onPermission = ::requestLocalPermission, onConnect = ::connect,
                    onDisconnect = ::disconnect, onRetryCleanup = {
                        if (canInteract()) { clearFields(); cancelReadyIntent(); model.retryCleanup(attachment) }
                    },
                    onReleaseInput = ::releaseLocalInput, onLocalControls = ::localControlOwnership,
                    onEnableInput = ::enableRemoteInput,
                    onEnterFullscreen = { requestWindow(true) }, onExitFullscreen = { requestWindow(false) },
                    onClose = ::closeSession)
            }
        }
    }
    private fun refreshInitially() {
        if (initialRefresh && canInteract()) { initialRefresh = false; model.refresh(attachment) }
    }
    private fun selectedProfile(): ConnectionProfile? = model.state.profiles.singleOrNull {
        it.id == selectedId && it.revision == selectedRevision
    }
    private fun canInteract() = attached && !isFinishing && !isDestroyed && resumed && focused
    private fun canEdit() = canInteract() && !model.state.loading &&
        model.state.engineState in setOf(null, VncEngine.State.FAILED, VncEngine.State.RETIRED)
    private fun fullscreenEligible() = canInteract() && topResumed
    private fun syncForeground() {
        if (attached) model.foreground(attachment, canInteract())
        if (!fullscreenEligible()) cancelReadyIntent()
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        focused = hasWindowFocus()
        localGranted = hasLocalPermission()
        syncForeground()
        refreshInitially()
        observeWindow()
    }
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        topResumed = isTopResumedActivity
        syncForeground()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
        if (!hasFocus) { clearFields(); releaseChordHeld = false }
        syncForeground()
        refreshInitially()
        observeWindow()
    }
    override fun onPause() {
        resumed = false
        clearFields()
        cancelReadyIntent()
        syncForeground()
        super.onPause()
    }
    override fun onStop() {
        if (attached) model.windowStopped(attachment, isChangingConfigurations)
        super.onStop()
    }
    override fun onDestroy() {
        if (attached) { model.detach(attachment); attached = false }
        window.decorView.removeOnLayoutChangeListener(layoutListener)
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (!::model.isInitialized) { super.onSaveInstanceState(outState); return }
        clearFields()
        cancelReadyIntent()
        selectedProfile()?.let {
            outState.putString(STATE_PROFILE, it.id)
            outState.putLong(STATE_REVISION, it.revision)
        }
        outState.putBoolean(STATE_PREFERENCE, preferFullscreen)
        outState.putBoolean(STATE_API_ENTRY, apiEntered)
        outState.putBoolean(STATE_INTERRUPTED, model.state.loading || model.state.attempt != null)
        super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        observeWindow()
    }
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        observeWindow()
    }
    private fun clearFields() {
        fieldEpoch++
        username = TextFieldValue(); password = TextFieldValue(); fingerprint = TextFieldValue()
        pinCompared = false
    }
    private fun cancelReadyIntent() {
        waitingForReady = false
        readyAttempt = null
        preexistingAttempt = null
    }
    private fun selectProfile(profile: ConnectionProfile) {
        if (!canEdit()) return
        val current = model.state.profiles.singleOrNull { it.id == profile.id && it.revision == profile.revision } ?: return
        clearFields(); cancelReadyIntent(); notice = null
        selectedId = current.id; selectedRevision = current.revision
    }
    private fun connect() {
        if (!canEdit()) return
        val selected = selectedProfile() ?: return
        if (!localGranted || !hasLocalPermission()) {
            clearFields(); notice = getString(R.string.vnc_permission_required); return
        }
        val pin = if (pinCompared) VncUiInputs.decodePin(fingerprint.text) else null
        if (pin == null) { clearFields(); notice = getString(R.string.vnc_invalid_fields); return }
        val user = VncUiInputs.encodeSecret(username.text, 256)
        val secret = VncUiInputs.encodeSecret(password.text, 1024)
        if (user == null || secret == null) {
            pin.fill(0); user?.fill(0); secret?.fill(0)
            clearFields(); notice = getString(R.string.vnc_invalid_fields); return
        }
        notice = null
        preexistingAttempt = model.state.attempt
        waitingForReady = preferFullscreen && fullscreenEligible()
        readyAttempt = null
        // From this call the real model owns the arrays, and later transfers them to the real Engine.
        // Caller never wipes them after handoff: engine consumption is asynchronous.
        try { model.connect(attachment, selected.id, selected.revision, pin, user, secret, PRIVATE_SECONDS) }
        finally { clearFields() }
    }
    private fun tryReadyFullscreen() {
        if (!waitingForReady) return
        if (!fullscreenEligible()) { cancelReadyIntent(); return }
        val state = model.state
        val current = state.attempt
        if (readyAttempt == null && current != null && current !== preexistingAttempt) readyAttempt = current
        if (!state.loading && (current === preexistingAttempt || state.engineState in
            setOf(VncEngine.State.FAILED, VncEngine.State.RETIRED, VncEngine.State.CLEANUP_UNCERTAIN))) {
            cancelReadyIntent(); return
        }
        if (current != null && current === readyAttempt && state.presented &&
            state.engineState == VncEngine.State.FIRST_FRAME) {
            cancelReadyIntent()
            requestWindow(true)
        }
    }
    private fun requestWindow(enter: Boolean) {
        if (!fullscreenEligible() || (enter && fullscreenPending)) {
            notice = getString(R.string.vnc_fullscreen_ineligible); return
        }
        if (enter && (!model.state.presented || model.state.engineState != VncEngine.State.FIRST_FRAME)) {
            notice = getString(R.string.vnc_fullscreen_wait); return
        }
        cancelReadyIntent()
        fullscreenPending = true
        // Explicit Exit remains callable during a pending Enter. It issues the actual OS exit;
        // serial fencing prevents the older callback from relabeling this newer window request.
        val serial = ++windowRequestSerial
        try {
            requestFullscreenMode(if (enter) FULLSCREEN_MODE_REQUEST_ENTER else FULLSCREEN_MODE_REQUEST_EXIT,
                object : OutcomeReceiver<Void, Throwable> {
                    override fun onResult(result: Void?) {
                        runOnUiThread {
                            if (!isDestroyed && serial == windowRequestSerial) {
                                apiEntered = enter
                                fullscreenPending = false
                                notice = null
                                observeWindow()
                            }
                        }
                    }
                    override fun onError(error: Throwable) {
                        runOnUiThread {
                            if (!isDestroyed && serial == windowRequestSerial) {
                                fullscreenPending = false
                                notice = getString(R.string.vnc_window_request_denied)
                                observeWindow()
                            }
                        }
                    }
                })
        } catch (_: RuntimeException) {
            fullscreenPending = false
            notice = getString(R.string.vnc_window_request_denied)
        }
    }
    private fun observeWindow() {
        if (isDestroyed) return
        fullscreenLabel = if (apiEntered && !isInMultiWindowMode &&
            windowManager.currentWindowMetrics.bounds == windowManager.maximumWindowMetrics.bounds) {
            R.string.vnc_window_fullscreen
        } else if (apiEntered) R.string.vnc_window_verifying else R.string.vnc_window_os_managed
        // Callback acceptance alone is never displayed as observed fullscreen completion.
    }
    private fun disconnect() {
        clearFields(); cancelReadyIntent(); notice = null
        if (attached) model.disconnect(attachment)
    }
    private fun closeSession() {
        disconnect()
        finish()
    }
    private fun requestLocalPermission() {
        if (!canEdit()) return
        clearFields(); cancelReadyIntent()
        if (Build.VERSION.SDK_INT >= 37 && !hasLocalPermission()) {
            try { permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }
            catch (_: RuntimeException) { notice = getString(R.string.vnc_permission_unavailable) }
        } else { localGranted = hasLocalPermission(); notice = getString(R.string.vnc_permission_granted) }
    }
    private fun hasLocalPermission() = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    private fun localControlOwnership() {
        if (attached) model.releaseInput(attachment)
        cancelReadyIntent()
        window.decorView.releasePointerCapture()
    }
    private fun enableRemoteInput() {
        if (!canInteract()) return
        val attempt = model.state.attempt ?: return
        // Clear local field focus before consent; no focus/hover callback may enable input.
        currentFocus?.clearFocus()
        if (model.enableInput(attachment, attempt)) {
            notice = null
        }
    }
    private fun releaseLocalInput() {
        if (attached) model.releaseInput(attachment)
        cancelReadyIntent()
        window.decorView.releasePointerCapture()
        currentFocus?.clearFocus()
        clearFields()
        releaseEpoch++
        notice = getString(R.string.vnc_input_local)
    }
    private fun handleReleaseChord(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_R) {
            val chord = event.isCtrlPressed && event.isAltPressed && event.isShiftPressed
            if (event.action == KeyEvent.ACTION_DOWN && chord) {
                releaseChordHeld = true
                if (event.repeatCount == 0) releaseLocalInput()
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && releaseChordHeld) { releaseChordHeld = false; return true }
        }
        return false
    }
    companion object {
        internal const val PRIVATE_SECONDS = 60
        private const val STATE_PROFILE = "vnc.local.profile"
        private const val STATE_REVISION = "vnc.local.revision"
        private const val STATE_PREFERENCE = "vnc.local.fullscreenPreference"
        private const val STATE_API_ENTRY = "vnc.local.apiEntryObserved"
        private const val STATE_INTERRUPTED = "vnc.local.interrupted"
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
