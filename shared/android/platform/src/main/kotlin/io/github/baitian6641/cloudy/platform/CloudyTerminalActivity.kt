package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials
import io.github.baitian6641.cloudy.terminal.TerminalAdmission
import io.github.baitian6641.cloudy.terminal.TerminalIdentity
import io.github.baitian6641.cloudy.terminal.TerminalSize
import io.github.baitian6641.cloudy.terminal.TerminalWebViewBridge
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Foreground terminal construction, not background persistence or full desktop-input acceptance.
 * Each attempt captures its own transport and renderer. No late callback acquires a newer target.
 */
class CloudyTerminalActivity : ComponentActivity() {
    private enum class Phase { AUTH, CONNECTING, TERMINAL, RETIRING, BLOCKED }
    private class Attempt {
        val renderer = AtomicReference<TerminalWebViewBridge?>()
        lateinit var session: TerminalSession
    }

    private var catalog by mutableStateOf<ConnectionProfileCatalog?>(null)
    private var selected by mutableStateOf<SshObservationProfile?>(null)
    private var username by mutableStateOf(TextFieldValue())
    private var password by mutableStateOf(TextFieldValue())
    private var phase by mutableStateOf(Phase.AUTH)
    private var message by mutableStateOf<String?>(null)
    private var renderer by mutableStateOf<TerminalWebViewBridge?>(null)
    private var credentialEpoch by mutableLongStateOf(0L)
    private var attempt: Attempt? = null
    private var interactive = false
    private var disposed = false
    private var profileRead = 0L
    private var preselectId: String? = null
    private val busy get() = phase != Phase.AUTH || attempt != null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!disposed) message = getString(R.string.terminal_permission_return)
        // Grant return never replays authentication or sends any input.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (packageName != "io.github.baitian6641.cloudy.console" &&
            packageName != "io.github.baitian6641.cloudy.desktop") { finish(); return }
        // A launch hint has no authority and is never restored as a pending operation.
        if (savedInstanceState == null) preselectId = intent.getStringExtra(EXTRA_PROFILE_ID)
        title = getString(R.string.terminal_heading)
        enableEdgeToEdge()
        setContent {
            CloudyAppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = renderer
                    if (current != null && phase == Phase.TERMINAL) TerminalSurface(current) else ConnectSurface()
                }
            }
        }
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
        super.onPause()
    }

    override fun onStop() {
        // No background lifetime is claimed yet. Configuration changes retire, never reconnect.
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
    }

    private fun connect() {
        if (!interactive || busy || disposed) return
        val profile = selected ?: return
        if (SshLocalNetworkPolicy.shouldRequestPermission(applicationContext, profile.peer) && !hasLocalPermission()) {
            requestLocalPermission()
            return
        }
        if (username.text.isEmpty() || password.text.isEmpty()) return
        val user = username.text.toCharArray()
        val secret = password.text.toCharArray()
        clearSecretFields()
        profileRead++
        phase = Phase.CONNECTING
        message = getString(R.string.terminal_status_connecting)
        val current = Attempt()
        current.session = TerminalSession(applicationContext, sessionListener(current, profile))
        attempt = current
        current.session.connect(profile, user, secret)
    }

    private fun sessionListener(current: Attempt, profile: SshObservationProfile) = object : TerminalSession.Listener {
        override fun onAuthenticated(host: String) {
            if (disposed || !interactive || attempt !== current || phase != Phase.CONNECTING) {
                current.session.close("OWNER_UNAVAILABLE")
                return
            }
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
            current.renderer.getAndSet(null)?.dispose()
            if (disposed || attempt !== current) return
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
        override fun onFailure(reason: String) {
            if (attempt === current) retire("RENDERER_$reason")
        }
    }

    private fun acceptsInput(current: Attempt) = !disposed && interactive && hasWindowFocus() &&
        attempt === current && phase == Phase.TERMINAL && current.renderer.get()?.view?.hasFocus() == true

    private fun retire(reason: String) {
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
        username = TextFieldValue()
        password = TextFieldValue()
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
                    Button(::connect, enabled = !busy && chosen != null && username.text.isNotEmpty() && password.text.isNotEmpty(),
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
                item("username") {
                    key(credentialEpoch) {
                        OutlinedTextField(username, { if (!busy && it.text.length <= SshPasswordCredentials.MAX_USERNAME_UNITS) username = it },
                            enabled = !busy, label = { Text(stringResource(R.string.terminal_username)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().testTag("terminal_username"))
                    }
                }
                item("password") {
                    key(credentialEpoch) {
                        OutlinedTextField(password, { if (!busy && it.text.length <= SshPasswordCredentials.MAX_PASSWORD_UNITS) password = it },
                            enabled = !busy, label = { Text(stringResource(R.string.terminal_password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().testTag("terminal_password"))
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
                TextButton({ retire("USER_DISCONNECTED") }, modifier = Modifier.testTag("terminal_disconnect")) {
                    Text(stringResource(R.string.terminal_disconnect))
                }
                TextButton(::closeWindow) { Text(stringResource(R.string.terminal_close)) }
            }
            Text(message ?: "", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("terminal_live_status").padding(horizontal = CloudyControlMetrics.spaceSmall))
            key(current) {
                AndroidView(factory = { current.view }, modifier = Modifier.fillMaxWidth().weight(1f))
                DisposableEffect(current) {
                    current.start()
                    onDispose { current.dispose() }
                }
            }
        }
    }

    companion object {
        /** Navigation only; the destination re-reads exact current profile/trust before admission. */
        const val EXTRA_PROFILE_ID = "io.github.baitian6641.cloudy.terminal.PROFILE_ID"
    }
}
