package io.github.baitian6641.cloudy.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials

/** Owning-app read-only remote files. Credential fields are transient and never enter saved state. */
class CloudySshFilesActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private lateinit var model: SshFilesSessionModel
    private val attachment = Any()
    private var initialRefresh = false
    private var interactive by mutableStateOf(false)
    private var username by mutableStateOf(TextFieldValue())
    private var password by mutableStateOf(TextFieldValue())
    private var credentialEpoch by mutableLongStateOf(0L)
    private var notice by mutableStateOf<String?>(null)

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (::model.isInitialized && !isDestroyed) {
            clearFields()
            model.permissionResult()
            notice = getString(R.string.ssh_files_permission_return)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        var created = false
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == SshFilesSessionModel::class.java)
                created = true
                @Suppress("UNCHECKED_CAST")
                return SshFilesSessionModel(application) as T
            }
        })[SshFilesSessionModel::class.java]
        // Existing ViewModel state is the actual live owner on configuration recreation. A new
        // model after process loss receives only an interruption notice, never a restored command.
        if (created && restorePending(savedInstanceState)) notice = getString(R.string.ssh_files_interrupted)
        initialRefresh = created
        enableEdgeToEdge()
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        title = getString(R.string.ssh_files_heading)
        setContent {
            CloudyAppTheme {
                val state = model.state
                val epoch = credentialEpoch
                // A recreated Activity can inherit an already-focused window without replaying
                // its focus callback. Observe the Compose owner's current window, not a cache.
                val windowFocused = LocalWindowInfo.current.isWindowFocused
                LaunchedEffect(state.selected) { clearFields() }
                SshFilesScreen(state, username, password, epoch, interactive && windowFocused, notice,
                    onUsername = { value ->
                        if (epoch == credentialEpoch && canEdit()) {
                            if (value.text.length <= SshPasswordCredentials.MAX_USERNAME_UNITS) username = value
                            else notice = getString(R.string.ssh_files_invalid)
                        }
                    },
                    onPassword = { value ->
                        if (epoch == credentialEpoch && canEdit()) {
                            if (value.text.length <= SshPasswordCredentials.MAX_PASSWORD_UNITS) password = value
                            else notice = getString(R.string.ssh_files_invalid)
                        }
                    },
                    onSelect = { profile ->
                        if (canEdit()) { clearFields(); notice = null; model.selectProfile(profile) }
                    },
                    onConnect = ::connect,
                    onRefreshProfiles = { if (canEdit()) { clearFields(); notice = null; model.refreshProfiles() } },
                    onList = { path -> if (interactive && hasWindowFocus()) { notice = null; model.list(path) } },
                    onRead = { entry -> if (interactive && hasWindowFocus()) { notice = null; model.read(entry) } },
                    onDisconnect = { clearFields(); notice = null; model.disconnect() },
                    onClose = { clearFields(); model.disconnect(); finish() },
                    offerLocalPermission = Build.VERSION.SDK_INT >= 37 && !hasLocalPermission(),
                    onPermission = ::requestLocalPermission)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::model.isInitialized) {
            model.onOwnerStarted(attachment)
            if (initialRefresh) {
                initialRefresh = false
                if (model.state.phase == SshFilesPhase.IDLE) model.refreshProfiles()
            }
        }
    }
    override fun onResume() { super.onResume(); interactive = true }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            clearFields()
            if (::model.isInitialized) model.pauseAuthentication(attachment)
        }
    }
    override fun onPause() {
        interactive = false
        clearFields()
        if (::model.isInitialized) model.pauseAuthentication(attachment)
        super.onPause()
    }
    override fun onStop() {
        // Model-owned configuration handoff has a bounded lease and token fencing. The model
        // retains no old Activity and an absent replacement cannot keep the session indefinitely.
        if (::model.isInitialized) model.onOwnerStopped(attachment, isChangingConfigurations)
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        interactive = false
        clearFields()
        if (::model.isInitialized) {
            val pending = model.state.phase in setOf(SshFilesPhase.CONNECTING, SshFilesPhase.CONNECTED,
                SshFilesPhase.READING, SshFilesPhase.RETIRING)
            model.pauseAuthentication(attachment)
            outState.putBundle(STATE_KEY, Bundle().apply {
                putInt("schema", 1); putString("owner", owner.wireId); putBoolean("pending", pending)
            })
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { clearFields(); super.onDestroy() }

    // Recheck framework focus at action admission, even if a previously enabled UI callback queues.
    private fun canEdit() = interactive && hasWindowFocus() && !isFinishing && model.state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED)
    private fun clearFields() {
        // Rotate the native field composition to dispose undo history, and fence queued callbacks.
        credentialEpoch++
        username = TextFieldValue()
        password = TextFieldValue()
    }
    private fun connect() {
        if (!canEdit() || model.state.selected == null) return
        val user = username.text.toCharArray()
        val secret = password.text.toCharArray()
        val credentials = try { SshPasswordCredentials.create(user, secret) }
        catch (_: IllegalArgumentException) { notice = getString(R.string.ssh_files_invalid); return }
        finally { user.fill('\u0000'); secret.fill('\u0000') }
        clearFields()
        notice = null
        // Model consumes the holder on every path, including Busy/permission/lifecycle rejection.
        model.connect(credentials)
    }
    private fun hasLocalPermission() = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    private fun requestLocalPermission() {
        if (!canEdit()) return
        clearFields()
        model.pauseAuthentication(attachment)
        notice = getString(R.string.ssh_files_permission_needed)
        if (Build.VERSION.SDK_INT >= 37) permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    @Suppress("DEPRECATION")
    private fun restorePending(source: Bundle?): Boolean {
        if (source == null) return false
        return try {
            if (!source.containsKey(STATE_KEY)) false else {
                val record = source.get(STATE_KEY) as? Bundle ?: return true
                if (record.keySet() != setOf("schema", "owner", "pending") || record.get("schema") != 1 ||
                    record.get("owner") != owner.wireId || record.get("pending") !is Boolean) true
                else record.get("pending") as Boolean
            }
        } catch (_: RuntimeException) { true }
    }
    companion object { private const val STATE_KEY = "io.github.baitian6641.cloudy.ssh-files.v1" }
}
