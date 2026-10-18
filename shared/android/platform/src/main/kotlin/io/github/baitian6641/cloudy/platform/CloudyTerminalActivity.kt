package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.TerminalRightClick
import io.github.baitian6641.cloudy.ssh.SshKeyCredentials
import io.github.baitian6641.cloudy.ssh.SshKeyboardInteractiveCredentials
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.terminal.TerminalAdmission
import io.github.baitian6641.cloudy.terminal.TerminalIdentity
import io.github.baitian6641.cloudy.terminal.TerminalNotice
import io.github.baitian6641.cloudy.terminal.TerminalSize
import io.github.baitian6641.cloudy.terminal.TerminalText
import io.github.baitian6641.cloudy.terminal.TerminalUiCommand
import io.github.baitian6641.cloudy.terminal.TerminalUiCommandAction
import io.github.baitian6641.cloudy.terminal.TerminalUiLimits
import io.github.baitian6641.cloudy.terminal.TerminalUiPayload
import io.github.baitian6641.cloudy.terminal.TerminalUiRequest
import io.github.baitian6641.cloudy.terminal.TerminalUiRequestAction
import io.github.baitian6641.cloudy.terminal.TerminalWebViewBridge
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Foreground terminal construction, not background persistence or full desktop-input acceptance.
 * Each attempt captures its own transport and renderer. No late callback acquires a newer target.
 */
class CloudyTerminalActivity : ComponentActivity() {
    private enum class Phase { AUTH, CONNECTING, TERMINAL, RETIRING, BLOCKED }
    private enum class AuthMethod { PASSWORD, PRIVATE_KEY, KEYBOARD_INTERACTIVE }
    private class Attempt {
        val renderer = AtomicReference<TerminalWebViewBridge?>()
        lateinit var session: TerminalSession
    }
    /** Preview identity is the actual attempt plus renderer; focus/close/supersession drops it. */
    private class PendingPaste(
        val attempt: Attempt,
        val renderer: TerminalWebViewBridge,
        val text: String,
        val utf8: ByteArray,
        val token: Long,
    )

    private var catalog by mutableStateOf<ConnectionProfileCatalog?>(null)
    private var selected by mutableStateOf<SshObservationProfile?>(null)
    private var username by mutableStateOf(TextFieldValue())
    private var password by mutableStateOf(TextFieldValue())
    private var authMethod by mutableStateOf(AuthMethod.PASSWORD)
    private var key by mutableStateOf<ByteArray?>(null)
    private var keyPassphrase by mutableStateOf(TextFieldValue())
    private var interactiveResponse by mutableStateOf(TextFieldValue())
    private var keyBusy by mutableStateOf(false)
    private var keyRead = 0L
    /** Persisted content-URI reference for the selected profile; never key bytes. */
    private var keyUri by mutableStateOf<String?>(null)
    private var rememberKey by mutableStateOf(false)
    private var pickedKeyUri by mutableStateOf<android.net.Uri?>(null)
    private var keyPersistBusy by mutableStateOf(false)
    private var phase by mutableStateOf(Phase.AUTH)
    private var message by mutableStateOf<String?>(null)
    private var renderer by mutableStateOf<TerminalWebViewBridge?>(null)
    private var credentialEpoch by mutableLongStateOf(0L)
    private var attempt: Attempt? = null
    private var interactive = false
    private var disposed = false
    private var profileRead = 0L
    private var preselectId: String? = null
    private var menuOpen by mutableStateOf(false)
    private var searchOpen by mutableStateOf(false)
    private var searchQuery by mutableStateOf(TextFieldValue())
    private var searchStatus by mutableStateOf<String?>(null)
    private var searchCommand by mutableIntStateOf(0)
    private var interactionNotice by mutableStateOf<String?>(null)
    private var remoteMouse by mutableStateOf(false)
    private var pendingPaste by mutableStateOf<PendingPaste?>(null)
    /** One approved action awaiting removal of native controls; never persisted or retried. */
    private var approvedPaste by mutableStateOf<PendingPaste?>(null)
    private var pasteEpoch = 0L
    private var swallowNextEscapeUp = false
    private lateinit var clipboard: TerminalClipboardAccess
    private lateinit var displaySettings: CloudyDisplaySettings
    private val busy get() = phase != Phase.AUTH || attempt != null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!disposed) message = getString(R.string.terminal_permission_return)
        // Grant return never replays authentication or sends any input.
    }
    /** Session-only key choice: the returned read grant is consumed immediately, never persisted. */
    private val keyPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Activity results arrive between onStart and onResume, when onPause has already set
        // interactive=false. A granted document is still a valid response for this foreground
        // window; gate on actual destruction/retirement rather than the transient flag.
        if (uri != null && !disposed && !busy && !isFinishing && !isDestroyed) {
            pickedKeyUri = uri
            if (rememberKey) rememberPickedKey(uri)
            readKey(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (packageName != "io.github.baitian6641.cloudy.console" &&
            packageName != "io.github.baitian6641.cloudy.desktop") { finish(); return }
        clipboard = TerminalClipboardAccess(getSystemService(ClipboardManager::class.java))
        displaySettings = CloudyDisplaySettings.get(this)
        // A launch hint has no authority and is never restored as a pending operation.
        if (savedInstanceState == null) preselectId = intent.getStringExtra(EXTRA_PROFILE_ID)
        title = getString(R.string.terminal_heading)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Native Back closes the local surface before it may close the SSH window.
                if (!dismissLocalSurface(returnFocus = true)) closeWindow()
            }
        })
        setContent {
            CloudyAppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = renderer
                    if (current != null && phase == Phase.TERMINAL) TerminalSurface(current) else ConnectSurface()
                }
            }
        }
    }

    /** Publish the real focus-scoped terminal shortcuts through Android's public helper. */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        if (data == null || phase != Phase.TERMINAL) return
        val ctrlShift = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        data.add(KeyboardShortcutGroup(getString(R.string.terminal_shortcut_group), listOf(
            KeyboardShortcutInfo(getString(R.string.terminal_shortcut_copy), KeyEvent.KEYCODE_C, ctrlShift),
            KeyboardShortcutInfo(getString(R.string.terminal_shortcut_paste), KeyEvent.KEYCODE_V, ctrlShift),
            KeyboardShortcutInfo(getString(R.string.terminal_shortcut_paste_insert),
                KeyEvent.KEYCODE_INSERT, KeyEvent.META_SHIFT_ON),
            KeyboardShortcutInfo(getString(R.string.terminal_shortcut_find), KeyEvent.KEYCODE_F, ctrlShift),
            KeyboardShortcutInfo(getString(R.string.terminal_shortcut_menu), KeyEvent.KEYCODE_MENU, 0),
        )))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Escape is the only key handled here; it is not a character mapper or terminal shortcut.
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && localOverlayActive) {
            swallowNextEscapeUp = true
            dismissLocalSurface(returnFocus = true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && swallowNextEscapeUp) {
            swallowNextEscapeUp = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        interactive = true
        if (!busy) refresh()
    }

    override fun onPause() {
        interactive = false
        profileRead++
        clearSecretFields()
        // Pending preview and hidden local surfaces may not survive backgrounding or recreation.
        dismissLocalSurface(returnFocus = false)
        swallowNextEscapeUp = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            // A preview may not survive focus transfer; there is no replay after focus returns.
            // Remote-mouse routing is a property of the live session, not of local focus.
            dismissLocalSurface(returnFocus = false)
            swallowNextEscapeUp = false
        }
    }

    override fun onStop() {
        // No background lifetime is claimed yet. Configuration changes retire, never reconnect.
        clearInteractionState()
        retire("WINDOW_HIDDEN")
        super.onStop()
    }

    override fun onDestroy() {
        disposed = true
        retire("WINDOW_CLOSED")
        super.onDestroy()
    }

    private fun refresh() {
        if (!interactive || busy || disposed) return
        val token = ++profileRead
        val app = applicationContext
        Thread({
            val loaded = try { ConnectionProfiles(app).load() } catch (_: Exception) { null }
            runOnUiThread {
                if (disposed || !interactive || busy || token != profileRead) return@runOnUiThread
                when (loaded) {
                    is ConnectionProfilesResult.Success -> {
                        catalog = loaded.value
                        val id = selected?.profile?.id ?: preselectId
                        selected = loaded.value.profiles.singleOrNull {
                            it.id == id && it.protocol == ConnectionProtocol.SSH
                        }?.let { SshObservationProfile.create(loaded.value.generation, it) }
                        preselectId = null
                        message = getString(if (selected == null) R.string.terminal_choose else R.string.terminal_credentials_ready)
                        if (!keyPersistBusy) applyPersistedKey(selected?.profile?.keyUri)
                    }
                    is ConnectionProfilesResult.Failure -> {
                        catalog = null
                        selected = null
                        message = getString(R.string.terminal_status_failed, loaded.code.name)
                    }
                    null -> message = getString(R.string.terminal_status_failed, "STORE_UNAVAILABLE")
                }
            }
        }, "cloudy-terminal-profiles").apply { isDaemon = true }.start()
    }

    private fun select(profile: ConnectionProfile) {
        if (!interactive || busy) return
        val loaded = catalog ?: return
        val exact = loaded.profiles.singleOrNull { it.id == profile.id && it.revision == profile.revision } ?: return
        if (exact.protocol != ConnectionProtocol.SSH) return
        clearSecretFields()
        selected = SshObservationProfile.create(loaded.generation, exact)
        message = getString(R.string.terminal_credentials_ready)
        applyPersistedKey(exact.keyUri)
    }

    /** A saved reference is only a document URI; the key is re-read and never copied to storage. */
    private fun applyPersistedKey(saved: String?) {
        if (keyPersistBusy) return
        keyUri = saved
        rememberKey = saved != null
        if (saved != null) {
            authMethod = AuthMethod.PRIVATE_KEY
            key = null
            val uri = runCatching { android.net.Uri.parse(saved) }.getOrNull()
            if (uri != null) readKey(uri) else message = getString(R.string.terminal_key_unreadable)
        }
    }

    private fun rememberPickedKey(uri: android.net.Uri): Boolean {
        val taken = runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
        if (!taken) {
            rememberKey = false
            message = getString(R.string.terminal_key_persist_failed)
            return false
        }
        persistKeyReference(uri.toString())
        return true
    }

    /** Connect is fenced while this mutates the profile; updates invalidate live auth attempts. */
    private fun persistKeyReference(value: String?) {
        val loaded = catalog ?: return
        val current = selected?.profile ?: return
        val exact = loaded.profiles.singleOrNull { it.id == current.id } ?: return
        if (exact.keyUri == value) {
            keyUri = value
            return
        }
        keyPersistBusy = true
        val version = ConnectionProfileVersion.create(loaded.generation, exact.id, exact.revision)
        val draft = ConnectionProfileDraft.create(exact.owner, exact.label, exact.protocol, exact.host, exact.port,
            exact.tags, exact.favorite, exact.jumpProfileId, value)
        Thread({
            val result = runCatching { ConnectionProfiles(applicationContext).update(version, draft) }.getOrNull()
            runOnUiThread {
                keyPersistBusy = false
                if (disposed) return@runOnUiThread
                when (result) {
                    is ConnectionProfilesResult.Success -> {
                        val updated = result.value.profile
                        selected = SshObservationProfile.create(loaded.generation, updated)
                        catalog = ConnectionProfileCatalog(loaded.generation,
                            loaded.profiles.map { if (it.id == updated.id) updated else it })
                        keyUri = value
                        rememberKey = value != null
                        message = getString(if (value != null) R.string.terminal_key_remembered
                        else R.string.terminal_key_forgotten)
                    }
                    is ConnectionProfilesResult.Failure ->
                        message = getString(R.string.terminal_status_failed, result.code.name)
                    null -> message = getString(R.string.terminal_status_failed, "STORE_UNAVAILABLE")
                }
            }
        }, "cloudy-terminal-key-reference").apply { isDaemon = true }.start()
    }

    private fun connect() {
        if (!interactive || busy || disposed) return
        val profile = selected ?: return
        val loaded = catalog
        val jump = profile.profile.jumpProfileId?.let { id ->
            loaded?.profiles?.singleOrNull {
                it.id == id && it.protocol == ConnectionProtocol.SSH && it.jumpProfileId == null
            }?.let { SshObservationProfile.create(loaded.generation, it) }
        }
        if (profile.profile.jumpProfileId != null && jump == null) {
            message = getString(R.string.terminal_status_failed, "JUMP_UNAVAILABLE")
            return
        }
        // A jump target is contacted only through the jump; the session checks the actual host.
        if (jump == null && SshLocalNetworkPolicy.shouldRequestPermission(applicationContext, profile.peer) &&
            !hasLocalPermission()) {
            requestLocalPermission()
            return
        }
        if (username.text.isEmpty() || keyBusy || keyPersistBusy) return
        val pair = when (authMethod) {
            AuthMethod.PASSWORD -> {
                if (password.text.isEmpty()) return
                val user = username.text.toCharArray()
                val secret = password.text.toCharArray()
                try {
                    SshPasswordCredentials.create(user, secret) to
                        (if (jump != null) SshPasswordCredentials.create(user, secret) else null)
                } catch (_: IllegalArgumentException) {
                    message = getString(R.string.terminal_auth_invalid)
                    return
                } finally {
                    user.fill('\u0000'); secret.fill('\u0000')
                }
            }
            AuthMethod.PRIVATE_KEY -> {
                val material = key ?: return
                val user = username.text.toCharArray()
                val passphrase = keyPassphrase.text.toCharArray()
                try {
                    SshKeyCredentials.create(user, material, passphrase) to
                        (if (jump != null) SshKeyCredentials.create(user, material, passphrase) else null)
                } catch (_: IllegalArgumentException) {
                    message = getString(R.string.terminal_auth_invalid)
                    return
                } finally {
                    user.fill('\u0000'); passphrase.fill('\u0000')
                }
            }
            AuthMethod.KEYBOARD_INTERACTIVE -> {
                if (interactiveResponse.text.isEmpty()) return
                val user = username.text.toCharArray()
                val response = interactiveResponse.text.toCharArray()
                try {
                    SshKeyboardInteractiveCredentials.create(user, response) to
                        (if (jump != null) SshKeyboardInteractiveCredentials.create(user, response) else null)
                } catch (_: IllegalArgumentException) {
                    message = getString(R.string.terminal_auth_invalid)
                    return
                } finally {
                    user.fill('\u0000'); response.fill('\u0000')
                }
            }
        }
        clearSecretFields()
        profileRead++
        phase = Phase.CONNECTING
        message = getString(R.string.terminal_status_connecting)
        val current = Attempt()
        current.session = TerminalSession(applicationContext, sessionListener(current, profile))
        attempt = current
        current.session.connect(profile, pair.first, jump, pair.second)
    }

    /** One bounded session-only read; supersession and lifecycle changes wipe the bytes. */
    private fun readKey(uri: android.net.Uri) {
        val token = ++keyRead
        keyBusy = true
        message = getString(R.string.terminal_key_reading)
        Thread({
            var loaded: ByteArray? = null
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(SshKeyCredentials.MAX_KEY_BYTES + 1)
                    var size = 0
                    while (size < buffer.size) {
                        val read = input.read(buffer, size, buffer.size - size)
                        if (read <= 0) break
                        size += read
                    }
                    if (size in 1..SshKeyCredentials.MAX_KEY_BYTES) loaded = buffer.copyOf(size)
                    buffer.fill(0)
                }
            } catch (_: Exception) {
                // A failed read is a typed local refusal; no path or provider text is exposed.
            }
            runOnUiThread {
                if (disposed || token != keyRead) {
                    loaded?.fill(0)
                    return@runOnUiThread
                }
                keyBusy = false
                val body = loaded
                if (body == null) {
                    message = getString(R.string.terminal_key_unreadable)
                } else {
                    key?.fill(0)
                    key = body
                    message = getString(R.string.terminal_key_loaded)
                }
            }
        }, "cloudy-terminal-key").apply { isDaemon = true }.start()
    }

    private fun sessionListener(current: Attempt, profile: SshObservationProfile) = object : TerminalSession.Listener {
        override fun onAuthenticated(host: String) {
            if (disposed || !interactive || attempt !== current || phase != Phase.CONNECTING) {
                current.session.close("OWNER_UNAVAILABLE")
                return
            }
            clearInteractionState()
            val created = TerminalWebViewBridge(this@CloudyTerminalActivity,
                TerminalIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString()), rendererListener(current))
            current.renderer.set(created)
            renderer = created
            phase = Phase.TERMINAL
            message = getString(R.string.terminal_status_authenticated)
            title = getString(R.string.terminal_selected, profile.profile.label,
                "${profile.profile.host}:${profile.profile.port}")
        }

        override fun onSessionOpened(size: TerminalSize) {
            if (attempt === current && phase == Phase.TERMINAL) message = getString(R.string.terminal_status_ready)
        }

        override fun onOutput(bytes: ByteArray, stderr: Boolean) {
            // Never drop output silently and never consult another attempt's mutable renderer.
            checkNotNull(current.renderer.get()) { "RENDERER_RETIRED" }.sendOutput(bytes, stderr)
        }

        override fun onOutputEnded() {
            checkNotNull(current.renderer.get()) { "RENDERER_RETIRED" }.finishOutput()
        }

        override fun onEnded(code: String, authenticated: Boolean, cleanupCertain: Boolean) {
            // Retire only this callback's captured renderer, never a newer attempt's target.
            current.renderer.getAndSet(null)?.dispose()
            if (disposed || attempt !== current) return
            // Past the attempt/disposed fence this callback may clear the shared local UI state.
            clearInteractionState()
            renderer = null
            if (cleanupCertain) {
                attempt = null
                phase = Phase.AUTH
            } else {
                // Do not release this window's capacity on a timeout or uncertain worker cleanup.
                phase = Phase.BLOCKED
            }
            message = getString(if (authenticated) R.string.terminal_status_ended else R.string.terminal_status_failed,
                if (cleanupCertain) code else "CLEANUP_UNCERTAIN: $code")
        }
    }

    private fun rendererListener(current: Attempt) = object : TerminalWebViewBridge.Listener {
        override fun onReady(size: TerminalSize) {
            if (attempt === current && phase == Phase.TERMINAL) current.session.openPty(size)
        }
        override fun onInput(bytes: ByteArray): TerminalAdmission =
            if (acceptsInput(current)) current.session.offerInput(bytes) else TerminalAdmission.STALE
        override fun onResize(size: TerminalSize): TerminalAdmission =
            if (attempt === current && phase == Phase.TERMINAL) current.session.offerResize(size) else TerminalAdmission.STALE
        override fun onUiRequest(request: TerminalUiRequest) {
            if (disposed || attempt !== current || phase != Phase.TERMINAL) return
            handleUiRequest(current, request)
        }
        override fun onFailure(reason: String) {
            if (attempt === current) retire("RENDERER_$reason")
        }
    }

    private fun handleUiRequest(current: Attempt, request: TerminalUiRequest) {
        // Session mode changes remain relevant while unfocused; clipboard/actions do not.
        if (request.action == TerminalUiRequestAction.MOUSE_TRACKING) {
            val payload = request.payload as? TerminalUiPayload.MouseTracking ?: return
            remoteMouse = payload.active
            return
        }
        if (!ownsInteraction(current)) return
        when (request.action) {
            TerminalUiRequestAction.COPY_SELECTION -> {
                val payload = request.payload as? TerminalUiPayload.Text ?: return
                copyTerminalSelection(payload.bytes)
            }
            TerminalUiRequestAction.REQUEST_PASTE -> beginPaste(current)
            TerminalUiRequestAction.REQUEST_RIGHT_CLICK -> routeRightClick(current)
            TerminalUiRequestAction.REQUEST_MENU -> openMenu()
            TerminalUiRequestAction.REQUEST_FIND -> openSearch()
            TerminalUiRequestAction.SEARCH_RESULT -> {
                val payload = request.payload as? TerminalUiPayload.SearchResult ?: return
                showSearchResult(payload)
            }
            TerminalUiRequestAction.MOUSE_TRACKING -> return // Handled behind the caller's attempt fence.
            TerminalUiRequestAction.NOTICE -> {
                val payload = request.payload as? TerminalUiPayload.Notice ?: return
                interactionNotice = getString(when (payload.code) {
                    TerminalNotice.NO_SELECTION -> R.string.terminal_copy_empty
                    TerminalNotice.SELECTION_TOO_LARGE -> R.string.terminal_copy_too_large
                    TerminalNotice.SELECTION_INVALID -> R.string.terminal_copy_invalid
                })
            }
        }
    }

    /** Window/attempt focus, not DOM focus: a native menu button may own focus for one action. */
    private fun ownsInteraction(current: Attempt) = !disposed && interactive && hasWindowFocus() &&
        attempt === current && phase == Phase.TERMINAL && current.renderer.get()?.let { it === renderer } == true

    private fun copyTerminalSelection(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            interactionNotice = getString(R.string.terminal_copy_empty)
            return
        }
        val text = TerminalText.decodeUtf8(bytes)
        if (text == null) {
            interactionNotice = getString(R.string.terminal_copy_invalid)
            return
        }
        // The admitted string goes straight to ClipData; only a bounded validation scan runs here.
        val refusal = TerminalClipboardPolicy.refusal(text)
        if (refusal != null) {
            interactionNotice = getString(copyRefusalMessage(refusal))
            return
        }
        interactionNotice = getString(
            if (clipboard.copy(text)) R.string.terminal_copied else R.string.terminal_copy_failed)
    }

    private fun copyRefusalMessage(reason: TerminalClipboardRefusal): Int = when (reason) {
        TerminalClipboardRefusal.EMPTY -> R.string.terminal_copy_empty
        TerminalClipboardRefusal.TOO_LARGE -> R.string.terminal_copy_too_large
        TerminalClipboardRefusal.MALFORMED -> R.string.terminal_copy_invalid
        TerminalClipboardRefusal.UNSUPPORTED, TerminalClipboardRefusal.UNAVAILABLE ->
            R.string.terminal_copy_failed
    }

    private fun pasteRefusalMessage(reason: TerminalClipboardRefusal): Int = when (reason) {
        TerminalClipboardRefusal.EMPTY -> R.string.terminal_clipboard_empty
        TerminalClipboardRefusal.TOO_LARGE -> R.string.terminal_clipboard_too_large
        TerminalClipboardRefusal.MALFORMED -> R.string.terminal_clipboard_invalid
        TerminalClipboardRefusal.UNSUPPORTED -> R.string.terminal_clipboard_unsupported
        TerminalClipboardRefusal.UNAVAILABLE -> R.string.terminal_clipboard_unavailable
    }

    /** Every paste route converges here; clipboard reading happens only after an explicit action. */
    private fun beginPaste(current: Attempt) {
        if (!ownsInteraction(current)) return
        val bridge = current.renderer.get() ?: return
        val needsFocusTransfer = localOverlayActive || !bridge.view.hasFocus()
        // A new read supersedes any prior preview and any other local surface before it starts.
        dismissLocalSurface(returnFocus = false)
        val token = ++pasteEpoch
        when (val read = clipboard.read()) {
            is TerminalClipboardRead.Text -> {
                if (TerminalClipboardPolicy.requiresPreview(read.value)) {
                    pendingPaste = PendingPaste(current, bridge, read.value, read.utf8, token)
                    interactionNotice = null
                    clearRendererFocus()
                } else if (needsFocusTransfer) {
                    approvedPaste = PendingPaste(current, bridge, read.value, read.utf8, token)
                    interactionNotice = null
                } else {
                    // An already-focused shortcut keeps its immediate ordering; no extra queue.
                    interactionNotice = if (bridge.sendUiCommand(
                            TerminalUiCommand(TerminalUiCommandAction.PASTE, read.utf8)) != 0) null
                        else getString(R.string.terminal_interaction_unavailable)
                }
            }
            is TerminalClipboardRead.Refused -> interactionNotice = getString(pasteRefusalMessage(read.reason))
        }
    }

    private fun insertPendingPaste(expected: PendingPaste) {
        if (pendingPaste !== expected) return // A newer preview owns the surface.
        pendingPaste = null
        val current = attempt
        if (expected.token != pasteEpoch || current == null || current !== expected.attempt ||
            phase != Phase.TERMINAL || current.renderer.get() !== expected.renderer || !ownsInteraction(current)) {
            interactionNotice = getString(R.string.terminal_paste_stale)
            return
        }
        approvedPaste = expected
        interactionNotice = null
    }

    /** Called once after the overlay has left composition and AndroidView can own focus again. */
    private fun deliverApprovedPaste(expected: PendingPaste) {
        if (approvedPaste !== expected) return
        approvedPaste = null
        if (attempt !== expected.attempt || phase != Phase.TERMINAL) return
        if (expected.token != pasteEpoch || expected.attempt.renderer.get() !== expected.renderer ||
            !ownsInteraction(expected.attempt)) {
            interactionNotice = getString(R.string.terminal_paste_stale)
            return
        }
        expected.renderer.focusRenderer()
        interactionNotice = if (!expected.renderer.view.hasFocus() ||
            expected.renderer.sendUiCommand(TerminalUiCommand(
                TerminalUiCommandAction.PASTE, expected.utf8)) == 0) {
            getString(R.string.terminal_interaction_unavailable)
        } else null
    }

    private fun cancelPendingPaste(expected: PendingPaste) {
        if (pendingPaste !== expected) return
        pasteEpoch++
        pendingPaste = null
        interactionNotice = getString(R.string.terminal_paste_cancelled)
        focusRendererIfAvailable()
    }

    private fun routeRightClick(current: Attempt) {
        val state = displaySettings.state.value
        val policy = TerminalClipboardPolicy.confirmedRightClick(state.ready, state.loading, state.saving,
            state.recoveredValues, state.persistenceUncertain, state.terminalRightClick)
        when (policy) {
            TerminalRightClick.PASTE -> beginPaste(current)
            TerminalRightClick.CONTEXT_MENU -> openMenu()
            null -> {
                // No default is an approval while storage is loading, recovered or uncertain.
                openMenu()
                interactionNotice = getString(R.string.terminal_right_click_unconfirmed)
            }
        }
    }

    private fun sendCommand(current: Attempt, command: TerminalUiCommand): Int {
        if (!ownsInteraction(current)) return 0
        return current.renderer.get()?.sendUiCommand(command) ?: 0
    }

    private fun copyFromMenu() {
        dismissLocalSurface(returnFocus = false)
        val current = attempt ?: return
        if (sendCommand(current, TerminalUiCommand(TerminalUiCommandAction.COPY)) == 0) {
            interactionNotice = getString(R.string.terminal_interaction_unavailable)
        }
        focusRendererIfAvailable()
    }

    private fun pasteFromMenu() {
        attempt?.let(::beginPaste)
    }

    private fun selectAllFromMenu() {
        dismissLocalSurface(returnFocus = false)
        val current = attempt ?: return
        if (sendCommand(current, TerminalUiCommand(TerminalUiCommandAction.SELECT_ALL)) == 0) {
            interactionNotice = getString(R.string.terminal_interaction_unavailable)
        }
        focusRendererIfAvailable()
    }

    private fun openSearchFromMenu() {
        openSearch()
    }

    private fun focusTerminal() {
        dismissLocalSurface(returnFocus = false)
        focusRendererIfAvailable()
    }

    /** At most one local surface owns keys; opening one first closes the previous surface. */
    private fun openMenu() {
        if (!canHostLocalSurface()) return
        dismissLocalSurface(returnFocus = false)
        interactionNotice = null
        // Claim interaction before the renderer can emit a focus-driven byte on blur.
        menuOpen = true
        clearRendererFocus()
    }

    private fun openSearch() {
        if (!canHostLocalSurface()) return
        dismissLocalSurface(returnFocus = false)
        interactionNotice = null
        searchQuery = TextFieldValue()
        searchStatus = null
        searchCommand = 0
        searchOpen = true
        clearRendererFocus()
    }

    private fun closeSearch() {
        dismissLocalSurface(returnFocus = true)
    }

    /** Closing or replacing Find clears renderer decorations and the outstanding sequence. */
    private fun dismissLocalSurface(returnFocus: Boolean): Boolean {
        val closedPaste = pendingPaste != null || approvedPaste != null
        if (closedPaste) {
            pasteEpoch++
            pendingPaste = null
            approvedPaste = null
        }
        val closedMenu = menuOpen
        menuOpen = false
        val closedSearch = searchOpen
        if (closedSearch) {
            searchOpen = false
            searchQuery = TextFieldValue()
            searchStatus = null
            searchCommand = 0
            sendSearchClear()
        }
        val closed = closedPaste || closedMenu || closedSearch
        if (returnFocus && closed) focusRendererIfAvailable()
        return closed
    }

    private fun canHostLocalSurface() = !disposed && attempt != null && phase == Phase.TERMINAL

    private fun clearRendererFocus() {
        attempt?.renderer?.get()?.view?.clearFocus()
    }

    private fun focusRendererIfAvailable() {
        if (interactive && hasWindowFocus() && phase == Phase.TERMINAL) attempt?.renderer?.get()?.focusRenderer()
    }

    private fun sendSearchClear() {
        if (phase != Phase.TERMINAL) return
        attempt?.renderer?.get()?.sendUiCommand(TerminalUiCommand(TerminalUiCommandAction.SEARCH_CLEAR))
    }

    private fun updateSearchQuery(candidate: TextFieldValue) {
        if (!searchOpen) return // A retiring native field cannot clear a newer terminal selection.
        when {
            candidate.text.length > TerminalUiLimits.SEARCH_QUERY_UTF16_UNITS -> {
                searchStatus = getString(R.string.terminal_search_limit)
                return
            }
            TerminalText.utf8Size(candidate.text) == null -> {
                searchStatus = getString(R.string.terminal_search_invalid)
                return
            }
        }
        val changed = candidate.text != searchQuery.text
        searchQuery = candidate
        if (!changed) return // Cursor/selection/composition movement is not a new query.
        // Stop the old decoration work and refuse to display its delayed count.
        searchCommand = 0
        searchStatus = null
        sendSearchClear()
    }

    private fun runSearch(direction: Int) {
        val current = attempt ?: return
        if (!ownsInteraction(current)) {
            searchStatus = getString(R.string.terminal_search_unavailable)
            return
        }
        val query = searchQuery.text
        if (query.isEmpty()) {
            searchStatus = getString(R.string.terminal_search_empty)
            return
        }
        if (query.length > TerminalUiLimits.SEARCH_QUERY_UTF16_UNITS) {
            searchStatus = getString(R.string.terminal_search_limit)
            return
        }
        if (TerminalText.utf8Size(query) == null) {
            searchStatus = getString(R.string.terminal_search_invalid)
            return
        }
        val bytes = query.encodeToByteArray()
        val action = if (direction > 0) TerminalUiCommandAction.FIND_NEXT else TerminalUiCommandAction.FIND_PREVIOUS
        val sequence = sendCommand(current, TerminalUiCommand(action, bytes))
        if (sequence == 0) {
            searchStatus = getString(R.string.terminal_search_unavailable)
            return
        }
        searchCommand = sequence
        searchStatus = getString(R.string.terminal_search_pending)
    }

    private fun showSearchResult(payload: TerminalUiPayload.SearchResult) {
        if (!searchOpen || payload.commandSequence != searchCommand) return
        searchStatus = when {
            payload.resultCount == 0 -> getString(R.string.terminal_search_no_matches)
            payload.resultIndex < 0 -> getString(R.string.terminal_search_beyond)
            payload.atThreshold -> getString(R.string.terminal_search_result_threshold,
                payload.resultIndex + 1, payload.resultCount)
            else -> getString(R.string.terminal_search_result, payload.resultIndex + 1, payload.resultCount)
        }
    }

    private fun clearInteractionState() {
        pasteEpoch++
        pendingPaste = null
        approvedPaste = null
        menuOpen = false
        searchOpen = false
        searchQuery = TextFieldValue()
        searchStatus = null
        searchCommand = 0
        interactionNotice = null
        remoteMouse = false
        swallowNextEscapeUp = false
    }

    private val localOverlayActive: Boolean
        get() = menuOpen || searchOpen || pendingPaste != null

    private fun acceptsInput(current: Attempt) = !disposed && interactive && hasWindowFocus() &&
        !localOverlayActive && approvedPaste == null && attempt === current && phase == Phase.TERMINAL &&
        current.renderer.get()?.view?.hasFocus() == true

    private fun retire(reason: String) {
        clearInteractionState()
        val current = attempt ?: return
        current.renderer.getAndSet(null)?.dispose()
        renderer = null
        phase = Phase.RETIRING
        message = getString(R.string.terminal_retiring)
        current.session.close(reason)
    }

    private fun closeWindow() {
        clearSecretFields()
        retire("USER_CLOSED")
        finish()
    }

    private fun clearSecretFields() {
        credentialEpoch++
        keyRead++
        keyBusy = false
        key?.fill(0)
        key = null
        keyUri = null
        rememberKey = false
        pickedKeyUri = null
        username = TextFieldValue()
        password = TextFieldValue()
        keyPassphrase = TextFieldValue()
        interactiveResponse = TextFieldValue()
    }

    private fun hasLocalPermission() = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun requestLocalPermission() {
        if (!interactive || busy || disposed || Build.VERSION.SDK_INT < 37) return
        clearSecretFields()
        message = getString(R.string.terminal_status_local_permission)
        permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private fun openTrust() {
        if (!interactive || busy) return
        clearSecretFields()
        try { startActivity(Intent(this, CloudySshTrustActivity::class.java)) }
        catch (_: RuntimeException) { message = getString(R.string.terminal_status_failed, "TRUST_SCREEN_UNAVAILABLE") }
    }

    @Composable private fun ConnectSurface() {
        val loaded = catalog
        val chosen = selected
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("terminal_connect"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
            item("heading") {
                Text(stringResource(R.string.terminal_heading), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.terminal_scope))
            }
            item("message") {
                Text(message ?: stringResource(R.string.terminal_choose), modifier = Modifier.testTag("terminal_status")
                    .semantics { liveRegion = LiveRegionMode.Polite })
            }
            item("actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Button(::connect, enabled = !busy && !keyBusy && !keyPersistBusy && chosen != null &&
                        username.text.isNotEmpty() &&
                        ((authMethod == AuthMethod.PASSWORD && password.text.isNotEmpty()) ||
                            (authMethod == AuthMethod.PRIVATE_KEY && key != null) ||
                            (authMethod == AuthMethod.KEYBOARD_INTERACTIVE &&
                                interactiveResponse.text.isNotEmpty())),
                        modifier = Modifier.testTag("terminal_connect_button")) { Text(stringResource(R.string.terminal_connect)) }
                    TextButton(::openTrust, enabled = !busy) { Text(stringResource(R.string.terminal_trust)) }
                    TextButton(::refresh, enabled = !busy, modifier = Modifier.testTag("terminal_refresh")) {
                        Text(stringResource(R.string.terminal_refresh))
                    }
                    if (Build.VERSION.SDK_INT >= 37 && !hasLocalPermission()) {
                        TextButton(::requestLocalPermission, enabled = !busy,
                            modifier = Modifier.testTag("terminal_local_permission")) {
                            Text(stringResource(R.string.ssh_observe_allow_local))
                        }
                    }
                    TextButton(::closeWindow) { Text(stringResource(R.string.terminal_close)) }
                }
            }
            chosen?.let { binding ->
                item("selected") {
                    Text(stringResource(R.string.terminal_selected, binding.profile.label,
                        "${binding.profile.host}:${binding.profile.port}"), modifier = Modifier.testTag("terminal_selected"))
                }
                if (binding.profile.jumpProfileId != null) {
                    val hop = loaded?.profiles?.singleOrNull { it.id == binding.profile.jumpProfileId }
                    item("jump") {
                        Text(stringResource(R.string.terminal_jump,
                            hop?.label ?: getString(R.string.terminal_jump_missing),
                            hop?.let { "${it.host}:${it.port}" } ?: ""),
                            modifier = Modifier.testTag("terminal_jump"))
                        Text(stringResource(R.string.terminal_jump_scope), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item("username") {
                    key(credentialEpoch) {
                        OutlinedTextField(username, { if (!busy && it.text.length <= SshPasswordCredentials.MAX_USERNAME_UNITS) username = it },
                            enabled = !busy, label = { Text(stringResource(R.string.terminal_username)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().testTag("terminal_username"))
                    }
                }
                item("auth_method") {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.terminal_auth_method), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            FilterChip(authMethod == AuthMethod.PASSWORD,
                                { if (!busy && !keyBusy) authMethod = AuthMethod.PASSWORD },
                                label = { Text(stringResource(R.string.terminal_auth_password)) },
                                modifier = Modifier.testTag("terminal_auth_password_method"))
                            FilterChip(authMethod == AuthMethod.PRIVATE_KEY,
                                { if (!busy && !keyBusy) authMethod = AuthMethod.PRIVATE_KEY },
                                label = { Text(stringResource(R.string.terminal_auth_private_key)) },
                                modifier = Modifier.testTag("terminal_auth_key_method"))
                            FilterChip(authMethod == AuthMethod.KEYBOARD_INTERACTIVE,
                                { if (!busy && !keyBusy) authMethod = AuthMethod.KEYBOARD_INTERACTIVE },
                                label = { Text(stringResource(R.string.terminal_auth_keyboard_interactive)) },
                                modifier = Modifier.testTag("terminal_auth_interactive_method"))
                        }
                    }
                }
                if (authMethod == AuthMethod.PASSWORD) item("password") {
                    key(credentialEpoch) {
                        OutlinedTextField(password, { if (!busy && it.text.length <= SshPasswordCredentials.MAX_PASSWORD_UNITS) password = it },
                            enabled = !busy, label = { Text(stringResource(R.string.terminal_password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().testTag("terminal_password"))
                    }
                }
                if (authMethod == AuthMethod.KEYBOARD_INTERACTIVE) item("interactive_response") {
                    key(credentialEpoch) {
                        Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            OutlinedTextField(interactiveResponse,
                                { if (!busy && it.text.length <= SshKeyboardInteractiveCredentials.MAX_RESPONSE_UNITS) interactiveResponse = it },
                                enabled = !busy,
                                label = { Text(stringResource(R.string.terminal_interactive_response)) },
                                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth().testTag("terminal_interactive_response"))
                            Text(stringResource(R.string.terminal_interactive_scope),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (authMethod == AuthMethod.PRIVATE_KEY) {
                    item("key_choose") {
                        Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                Button({ keyPicker.launch(arrayOf("*/*")) }, enabled = !busy && !keyBusy,
                                    modifier = Modifier.testTag("terminal_key_choose")) {
                                    Text(stringResource(if (key == null) R.string.terminal_key_choose
                                    else R.string.terminal_key_replace))
                                }
                            }
                            Text(stringResource(if (key == null) R.string.terminal_key_none
                            else R.string.terminal_key_selected),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("terminal_key_status")
                                    .semantics { liveRegion = LiveRegionMode.Polite })
                            Text(stringResource(R.string.terminal_key_scope), style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                                .padding(horizontal = CloudyControlMetrics.spaceSmall),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                Checkbox(checked = rememberKey, onCheckedChange = { checked ->
                                    if (!busy && !keyBusy && !keyPersistBusy) {
                                        rememberKey = checked
                                        if (checked) {
                                            val uri = pickedKeyUri ?: keyUri?.let {
                                                runCatching { android.net.Uri.parse(it) }.getOrNull()
                                            }
                                            if (uri != null) rememberPickedKey(uri)
                                            else message = getString(R.string.terminal_key_choose_first)
                                        } else {
                                            persistKeyReference(null)
                                        }
                                    }
                                }, enabled = !busy && !keyBusy && !keyPersistBusy,
                                    modifier = Modifier.testTag("terminal_key_remember"))
                                Text(stringResource(R.string.terminal_key_remember), modifier = Modifier.weight(1f))
                            }
                            if (keyUri != null) {
                                Text(stringResource(R.string.terminal_key_remembered),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("terminal_key_reference"))
                            }
                        }
                    }
                    item("key_passphrase") {
                        key(credentialEpoch) {
                            OutlinedTextField(keyPassphrase,
                                { if (!busy && it.text.length <= SshKeyCredentials.MAX_PASSPHRASE_UNITS) keyPassphrase = it },
                                enabled = !busy, label = { Text(stringResource(R.string.terminal_key_passphrase)) },
                                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth().testTag("terminal_key_passphrase"))
                        }
                    }
                }
            }
            if (loaded?.profiles?.none { it.protocol == ConnectionProtocol.SSH } == true) {
                item("empty") { Text(stringResource(R.string.terminal_no_profiles)) }
            }
            items(loaded?.profiles?.filter { it.protocol == ConnectionProtocol.SSH } ?: emptyList(), key = { it.id }) { profile ->
                TextButton({ select(profile) }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                    .testTag("terminal_profile_${profile.id}")) { Text("${profile.label} · ${profile.host}:${profile.port}") }
            }
        }
    }

    @Composable private fun TerminalSurface(current: TerminalWebViewBridge) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            FlowRow(Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Text(title.toString(), style = MaterialTheme.typography.titleMedium)
                TextButton({
                    if (menuOpen) dismissLocalSurface(returnFocus = true) else openMenu()
                }, modifier = Modifier.testTag("terminal_edit")) { Text(stringResource(R.string.terminal_edit)) }
                TextButton({ retire("USER_DISCONNECTED") }, modifier = Modifier.testTag("terminal_disconnect")) {
                    Text(stringResource(R.string.terminal_disconnect))
                }
                TextButton(::closeWindow) { Text(stringResource(R.string.terminal_close)) }
            }
            Text(message ?: "", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("terminal_live_status").padding(horizontal = CloudyControlMetrics.spaceSmall))
            interactionNotice?.let { notice ->
                Text(notice, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("terminal_local_notice")
                        .padding(horizontal = CloudyControlMetrics.spaceSmall)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (remoteMouse) {
                Text(stringResource(R.string.terminal_remote_mouse), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("terminal_mouse_routing").padding(horizontal = CloudyControlMetrics.spaceSmall))
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val searchMaxHeight = maxHeight / 2
                Column(Modifier.fillMaxSize()) {
                    // Reserve visible terminal space: Find must not cover its selected match.
                    if (searchOpen) SearchPanel(Modifier.heightIn(max = searchMaxHeight))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        key(current) {
                            AndroidView(factory = { current.view }, modifier = Modifier.fillMaxSize()
                                .focusProperties { canFocus = !localOverlayActive })
                            DisposableEffect(current) {
                                current.start()
                                onDispose { current.dispose() }
                            }
                        }
                        if (searchOpen) {
                            Box(Modifier.matchParentSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                })
                        }
                        if (menuOpen) EditMenuOverlay()
                        pendingPaste?.let { pending -> PastePreviewOverlay(pending) }
                    }
                }
            }
        }
        // After an overlay leaves composition, restore the one keyboard recipient without sleeps.
        val delivery = approvedPaste
        LaunchedEffect(localOverlayActive, delivery) {
            if (!localOverlayActive) {
                if (delivery != null) deliverApprovedPaste(delivery)
                else if (interactive && hasWindowFocus() && phase == Phase.TERMINAL) {
                    attempt?.renderer?.get()?.focusRenderer()
                }
            }
        }
    }

    /**
     * One local surface owns the pointer, keys and focus above the renderer. Escape or Back closes
     * it before the SSH window; a failed focus request is reported rather than hidden.
     */
    @Composable private fun BoxScope.LocalOverlayPanel(
        tag: String,
        focus: FocusRequester,
        alignment: Alignment,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Box(Modifier.matchParentSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { change -> change.consume() }
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Escape) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        swallowNextEscapeUp = true
                        dismissLocalSurface(returnFocus = true)
                        true
                    }
                    KeyEventType.KeyUp -> if (swallowNextEscapeUp) {
                        swallowNextEscapeUp = false
                        true
                    } else false
                    else -> false
                }
            }) {
            Surface(Modifier.align(alignment).fillMaxWidth().padding(CloudyControlMetrics.spaceSmall),
                color = MaterialTheme.colorScheme.surface) {
                // Narrow/short/large-text windows scroll instead of clipping Insert, Cancel or Close.
                Column(Modifier.verticalScroll(rememberScrollState()).padding(CloudyControlMetrics.spaceSmall)
                        .testTag(tag),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) { content() }
            }
        }
        LaunchedEffect(Unit) {
            if (!focus.requestFocus()) interactionNotice = getString(R.string.terminal_overlay_focus_failed)
        }
    }

    @Composable private fun BoxScope.EditMenuOverlay() {
        val menuFocus = remember { FocusRequester() }
        LocalOverlayPanel("terminal_menu_overlay", menuFocus, Alignment.TopCenter) {
            Text(stringResource(R.string.terminal_edit_menu), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                // Local panels own keyboard focus even when opened with a pointer in touch mode.
                TextButton(::copyFromMenu, modifier = Modifier.focusProperties { canFocus = true }
                    .focusRequester(menuFocus).testTag("terminal_copy")) {
                    Text(stringResource(R.string.terminal_shortcut_copy))
                }
                TextButton(::pasteFromMenu, modifier = Modifier.testTag("terminal_paste")) {
                    Text(stringResource(R.string.terminal_shortcut_paste))
                }
                TextButton(::selectAllFromMenu, modifier = Modifier.testTag("terminal_select_all")) {
                    Text(stringResource(R.string.terminal_menu_select_all))
                }
                TextButton(::openSearchFromMenu, modifier = Modifier.testTag("terminal_find")) {
                    Text(stringResource(R.string.terminal_shortcut_find))
                }
                TextButton(::focusTerminal, modifier = Modifier.testTag("terminal_menu_focus")) {
                    Text(stringResource(R.string.terminal_menu_focus))
                }
                TextButton(::focusTerminal, modifier = Modifier.testTag("terminal_menu_close")) {
                    Text(stringResource(R.string.terminal_menu_close))
                }
            }
        }
    }

    @Composable private fun SearchPanel(modifier: Modifier) {
        val queryFocus = remember { FocusRequester() }
        Surface(modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceSmall),
            color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(CloudyControlMetrics.spaceSmall)
                    .testTag("terminal_search_overlay"),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                OutlinedTextField(searchQuery, ::updateSearchQuery, singleLine = true,
                    label = { Text(stringResource(R.string.terminal_search_heading)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search,
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(1) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(queryFocus).testTag("terminal_find_query"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    TextButton({ runSearch(1) }, modifier = Modifier.testTag("terminal_find_next")) {
                        Text(stringResource(R.string.terminal_search_next))
                    }
                    TextButton({ runSearch(-1) }, modifier = Modifier.testTag("terminal_find_previous")) {
                        Text(stringResource(R.string.terminal_search_previous))
                    }
                    TextButton(::closeSearch, modifier = Modifier.testTag("terminal_find_close")) {
                        Text(stringResource(R.string.terminal_search_close))
                    }
                    searchStatus?.let { status ->
                        Text(status, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterVertically).testTag("terminal_find_count")
                                .semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            if (!queryFocus.requestFocus()) interactionNotice = getString(R.string.terminal_overlay_focus_failed)
        }
    }

    @Composable private fun BoxScope.PastePreviewOverlay(pending: PendingPaste) {
        val insertFocus = remember { FocusRequester() }
        val preview = remember(pending) {
            TerminalClipboardPolicy.preview(pending.text, PREVIEW_CHARACTERS)
        }
        LocalOverlayPanel("terminal_paste_overlay", insertFocus, Alignment.BottomCenter) {
            Text(stringResource(R.string.terminal_paste_preview_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.terminal_paste_preview_warning), style = MaterialTheme.typography.bodySmall)
            Text(preview.display, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("terminal_paste_preview"))
            if (preview.truncated) {
                Text(pluralStringResource(R.plurals.terminal_paste_preview_truncated, preview.totalCharacters,
                    preview.shownCharacters, preview.totalCharacters),
                    style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Button({ insertPendingPaste(pending) }, modifier = Modifier.focusProperties { canFocus = true }
                    .focusRequester(insertFocus)
                    .testTag("terminal_paste_insert")) { Text(stringResource(R.string.terminal_paste_insert)) }
                TextButton({ cancelPendingPaste(pending) }, modifier = Modifier.testTag("terminal_paste_cancel")) {
                    Text(stringResource(R.string.terminal_paste_cancel))
                }
            }
        }
    }

    companion object {
        /** Navigation only; the destination re-reads exact current profile/trust before admission. */
        const val EXTRA_PROFILE_ID = "io.github.baitian6641.cloudy.terminal.PROFILE_ID"
        private const val PREVIEW_CHARACTERS = 2000
    }
}
