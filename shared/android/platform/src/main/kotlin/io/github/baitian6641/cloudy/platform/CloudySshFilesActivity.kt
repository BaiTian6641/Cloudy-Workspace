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
import io.github.baitian6641.cloudy.ssh.SshFileEntry
import io.github.baitian6641.cloudy.ssh.SshPasswordCredentials

/** Owning-app read-only remote files. Credential fields are transient and never enter saved state. */
class CloudySshFilesActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private lateinit var model: SshFilesSessionModel
    private val attachment = Any()
    private var initialRefresh = false
    private var initialLocationTicket: String? = null
    private var interactive by mutableStateOf(false)
    private var username by mutableStateOf(TextFieldValue())
    private var password by mutableStateOf(TextFieldValue())
    private var credentialEpoch by mutableLongStateOf(0L)
    private var notice by mutableStateOf<String?>(null)
    private var pendingDownload: SshFileEntry? = null
    private var pickerInFlight = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (::model.isInitialized && !isDestroyed) {
            clearFields()
            model.permissionResult()
            notice = getString(R.string.ssh_files_permission_return)
        }
    }
    private val uploadPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerInFlight = false
        if (uri != null && ::model.isInitialized && !isDestroyed && !isFinishing) {
            // The result can arrive before onStart reattaches the stopped owner.
            model.onOwnerStarted(attachment)
            model.upload(uri.toString())
        }
    }
    private val downloadPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        pickerInFlight = false
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null && ::model.isInitialized && !isDestroyed && !isFinishing) {
            model.onOwnerStarted(attachment)
            model.download(entry, uri.toString())
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
        FilesPositiveObservation.start(this, FilesPositiveObservation.Event.RECEIVER_CREATED, this, model)
        // Existing ViewModel state is the actual live owner on configuration recreation. A new
        // model after process loss receives only an interruption notice, never a restored command.
        if (created) {
            val restored = restorePending(savedInstanceState)
            if (restored.session) notice = getString(R.string.ssh_files_interrupted)
            if (restored.registration) {
                model.restoreRegistrationUncertainty()
                notice = getString(R.string.ssh_location_unknown)
            }
        }
        initialRefresh = created
        // A launch ticket is one-use process memory, never restoration authority. A recreated
        // model after process loss asks for a fresh selection instead of replaying the Intent.
        if (created && savedInstanceState == null) {
            initialLocationTicket = try {
                if (intent.hasExtra(LOCATION_TICKET)) intent.getStringExtra(LOCATION_TICKET) ?: "" else null
            } catch (_: RuntimeException) { "" }
        }
        try { intent.removeExtra(LOCATION_TICKET) } catch (_: RuntimeException) { initialLocationTicket = "" }
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
                LaunchedEffect(interactive, windowFocused) {
                    model.onOwnerForeground(attachment, interactive && windowFocused && hasWindowFocus())
                }
                val displayedUsername = state.location?.accountName?.let { account ->
                    username.takeIf { it.text == account } ?: TextFieldValue(account)
                } ?: username
                SshFilesScreen(state, displayedUsername, password, epoch, interactive && windowFocused, notice,
                    onUsername = { value ->
                        if (epoch == credentialEpoch && canEdit()) {
                            val boundAccount = model.state.location?.accountName
                            // Read-only still emits native selection changes. Preserve selection
                            // without allowing edits to the account used by registered auth.
                            if (boundAccount != null) {
                                if (value.text == boundAccount) username = value
                            } else if (value.text.length <= SshPasswordCredentials.MAX_USERNAME_UNITS) username = value
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
                    onCreateDirectory = { name -> if (interactive && hasWindowFocus()) { notice = null; model.createDirectory(name) } },
                    onRename = { entry, name ->
                        if (interactive && hasWindowFocus()) { notice = null; model.rename(entry, name) }
                    },
                    onRequestDelete = { entry ->
                        if (interactive && hasWindowFocus()) { notice = null; model.requestDelete(entry) }
                    },
                    onConfirmMutation = {
                        if (interactive && hasWindowFocus()) {
                            // Recheck the opaque owner's foreground state before consuming consent.
                            model.onOwnerForeground(attachment, interactive && hasWindowFocus() && !isFinishing)
                            notice = null
                            model.confirmMutation()
                        }
                    },
                    onCancelMutation = model::cancelMutation,
                    onEdit = { entry -> if (interactive && hasWindowFocus()) { notice = null; model.edit(entry) } },
                    onEditorText = { text -> if (interactive && hasWindowFocus()) model.updateEditor(text) },
                    onRequestSave = { if (interactive && hasWindowFocus()) model.requestSave() },
                    onCancelSave = model::cancelSaveConsent,
                    onConfirmSave = { if (interactive && hasWindowFocus()) model.confirmSave() },
                    onConfirmConflict = { if (interactive && hasWindowFocus()) model.confirmConflict() },
                    onDiscardStaged = { if (interactive && hasWindowFocus()) model.discardStaged() },
                    onRequestCloseEditor = model::requestCloseEditor,
                    onCancelCloseEditor = model::cancelCloseEditor,
                    onConfirmCloseEditor = { if (interactive && hasWindowFocus()) model.confirmCloseEditor() },
                    onUpload = {
                        if (interactive && hasWindowFocus()) {
                            notice = null
                            pickerInFlight = true
                            uploadPicker.launch(arrayOf("*/*"))
                        }
                    },
                    onDownload = { entry ->
                        if (interactive && hasWindowFocus()) {
                            notice = null
                            pendingDownload = entry
                            pickerInFlight = true
                            downloadPicker.launch(entry.name)
                        }
                    },
                    onCancelTransfer = { if (interactive && hasWindowFocus()) model.cancelTransfer() },
                    onConfirmUploadOverwrite = { if (interactive && hasWindowFocus()) model.confirmUploadOverwrite() },
                    onConfirmUploadCopy = { if (interactive && hasWindowFocus()) model.confirmUploadCopy() },
                    onCancelUploadConsent = { if (interactive && hasWindowFocus()) model.cancelUploadConsent() },
                    onDisconnect = { clearFields(); notice = null; model.disconnect() },
                    onClose = { pendingDownload = null; clearFields(); model.disconnect(); finish() },
                    onLocations = {
                        if (canEdit() && !model.state.registrationPending) {
                            clearFields()
                            startActivity(Intent(this, CloudyFilesLocationsActivity::class.java))
                        }
                    },
                    onRegister = { candidate, label ->
                        // Refresh the opaque owner's foreground gate synchronously; an enabled
                        // Compose callback may have been queued before an external focus change.
                        model.onOwnerForeground(attachment, interactive && hasWindowFocus() && !isFinishing)
                        model.register(candidate, label)
                    },
                    onCancelRegistration = model::cancelRegistration,
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
                val ticket = initialLocationTicket
                initialLocationTicket = null
                if (ticket != null) model.selectLocation(ticket)
                else if (model.state.phase == SshFilesPhase.IDLE) model.refreshProfiles()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        interactive = true
        pickerInFlight = false
        if (::model.isInitialized) model.onOwnerForeground(attachment, hasWindowFocus())
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::model.isInitialized) model.onOwnerForeground(attachment, interactive && hasFocus)
        if (!hasFocus) {
            clearFields()
            if (::model.isInitialized) model.pauseAuthentication(attachment)
        }
    }
    override fun onPause() {
        interactive = false
        clearFields()
        if (::model.isInitialized) {
            model.onOwnerForeground(attachment, false)
            model.pauseAuthentication(attachment)
        }
        super.onPause()
    }
    override fun onStop() {
        // Model-owned configuration handoff has a bounded lease and token fencing. The model
        // retains no old Activity and an absent replacement cannot keep the session indefinitely.
        if (::model.isInitialized) model.onOwnerStopped(attachment, isChangingConfigurations, pickerInFlight)
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        interactive = false
        clearFields()
        if (::model.isInitialized) {
            model.onOwnerForeground(attachment, false)
            val pending = model.state.phase in setOf(SshFilesPhase.CONNECTING, SshFilesPhase.CONNECTED,
                SshFilesPhase.READING, SshFilesPhase.RETIRING)
            model.pauseAuthentication(attachment)
            outState.putBundle(STATE_KEY, Bundle().apply {
                putInt("schema", 2); putString("owner", owner.wireId); putBoolean("pending", pending)
                putBoolean("registrationUncertain", model.state.registrationPending || model.state.registrationUncertain)
            })
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (::model.isInitialized) FilesPositiveObservation.record(
            FilesPositiveObservation.Event.RECEIVER_DESTROYED, this, model)
        clearFields(); super.onDestroy()
    }

    // Recheck framework focus at action admission, even if a previously enabled UI callback queues.
    private fun canEdit() = interactive && hasWindowFocus() && !isFinishing && !model.state.registrationPending &&
        model.state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED)
    private fun clearFields() {
        // Rotate the native field composition to dispose undo history, and fence queued callbacks.
        credentialEpoch++
        username = TextFieldValue()
        password = TextFieldValue()
    }
    private fun connect() {
        if (!canEdit() || model.state.selected == null) return
        val user = (model.state.location?.accountName ?: username.text).toCharArray()
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
    private fun restorePending(source: Bundle?): PendingState {
        if (source == null) return PendingState(false, false)
        return try {
            if (!source.containsKey(STATE_KEY)) PendingState(false, false) else {
                val record = source.get(STATE_KEY) as? Bundle ?: return PendingState(true, true)
                val schema = record.get("schema")
                val keys = if (schema == 1) setOf("schema", "owner", "pending")
                    else setOf("schema", "owner", "pending", "registrationUncertain")
                if (schema !in setOf(1, 2) || record.keySet() != keys ||
                    record.get("owner") != owner.wireId || record.get("pending") !is Boolean ||
                    (schema == 2 && record.get("registrationUncertain") !is Boolean)) PendingState(true, true)
                else PendingState(record.getBoolean("pending"), schema == 2 && record.getBoolean("registrationUncertain"))
            }
        } catch (_: RuntimeException) { PendingState(true, true) }
    }
    private class PendingState(val session: Boolean, val registration: Boolean)
    companion object {
        private const val STATE_KEY = "io.github.baitian6641.cloudy.ssh-files.v1"
        internal const val LOCATION_TICKET = "io.github.baitian6641.cloudy.FILES_LOCATION_TICKET"
    }
}
