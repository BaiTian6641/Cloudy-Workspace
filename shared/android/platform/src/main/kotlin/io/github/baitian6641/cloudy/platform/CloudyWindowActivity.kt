package io.github.baitian6641.cloudy.platform

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowNameProblem
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.domain.WindowRestore
import io.github.baitian6641.cloudy.domain.WindowState
import java.util.UUID

/** A document task owns local metadata; no app process, network operation or secret is shared. */
abstract class CloudyWindowActivity : ComponentActivity() {
    protected abstract val windowOwner: WindowOwner
    protected abstract val appNameResource: Int
    protected abstract val defaultWindowNameResource: Int

    /** A concrete app-owned destination; absence keeps the workspace entry unavailable. */
    protected open val remoteDesktopActivity: Class<out ComponentActivity>? = null

    /** Console's retained terminal destination; Desktop keeps the session-linked files route. */
    protected open val sshTerminalActivity: Class<out ComponentActivity>? = null

    private var state by mutableStateOf<WindowState?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var systemNotice by mutableStateOf<String?>(null)
    private lateinit var home: WorkspaceHomeController

    /** Ephemeral owner identity: a recreated Activity never inherits a live callback generation. */
    val activityGeneration: String = UUID.randomUUID().toString()

    /** Read-only metadata for platform adapters; never a durable operation or authorization handle. */
    val windowContext: WindowState
        get() = checkNotNull(state)

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only framework-supplied saved state is eligible. Launcher intent data/extras are ignored.
        val restored = WindowBundles.restore(savedInstanceState, windowOwner)
        state = when (restored) {
            is WindowRestore.Restored -> restored.state
            else -> WindowState.create(windowOwner, getString(defaultWindowNameResource))
        }
        if (restored is WindowRestore.Rejected) notice = getString(R.string.cloudy_restore_rejected)
        home = WorkspaceHomeController(applicationContext, windowOwner)
        updateSystemLabel()
        setContent {
            CloudyAppTheme {
                val current = windowContext
                val homeState by home.state
                WorkspaceHomeScreen(
                    appName = getString(appNameResource),
                    owner = windowOwner,
                    windowId = current.windowId,
                    currentName = current.name,
                    notice = listOfNotNull(notice, systemNotice).joinToString("\n").ifEmpty { null },
                    state = homeState,
                    actions = WorkspaceHomeActions(
                        onToggleFavorite = home::toggleFavorite,
                        onConnect = ::connectHost,
                        onRetry = home::refresh,
                        onBrowseFiles = ::openSshFiles,
                        onAddHost = ::openProfiles,
                        onNewWindow = ::newWindow,
                        onClose = ::closeWindow,
                        onRename = ::renameWindow,
                        onProfiles = ::openProfiles,
                        onSshTrust = ::openSshTrust,
                        onSshAuthenticate = ::openSshAuthenticate,
                        onFilesLocations = ::openFilesLocations,
                        onCredentials = ::openCredentials,
                        onDisplaySettings = ::openDisplaySettings,
                    ),
                )
            }
        }
    }

    final override fun onStart() {
        super.onStart()
        // Foregrounding re-reads the stores; an in-flight read never replays a mutation.
        home.refresh()
    }

    final override fun onStop() {
        home.leave()
        super.onStop()
    }

    final override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A system reuse must not turn an external intent into a local-context reset or import.
    }

    final override fun onSaveInstanceState(outState: Bundle) {
        WindowBundles.save(outState, windowContext)
        // Compose's saveable editor draft and the framework hierarchy have their own state.
        super.onSaveInstanceState(outState)
    }

    /** Connect routes only to a qualified operation; anything else is an honest typed state. */
    private fun connectHost(row: HomeRow) {
        when (row.profile.protocol) {
            ConnectionProtocol.SSH, ConnectionProtocol.TELNET -> {
                val terminal = sshTerminalActivity
                if (terminal != null) {
                    home.report(null)
                    home.recordUse(row, System.currentTimeMillis() / 1000) {
                        // A stopped/background window never replays a deferred navigation request.
                        if (isFinishing || isDestroyed || !hasWindowFocus()) return@recordUse
                        try {
                            startActivity(Intent(this, terminal).putExtra(
                                CloudyTerminalActivity.EXTRA_PROFILE_ID, row.profile.id))
                        } catch (_: RuntimeException) {
                            notice = getString(R.string.cloudy_window_launch_failed)
                        }
                    }
                } else if (windowOwner == WindowOwner.DESKTOP && row.profile.protocol == ConnectionProtocol.SSH) {
                    // The qualified Desktop SSH surface today is the session-linked file browser.
                    home.report(null)
                    openSshFiles()
                } else {
                    home.report(HomeMessage.TERMINAL_NOT_INTEGRATED)
                }
            }
            ConnectionProtocol.VNC -> {
                val destination = remoteDesktopActivity
                if (destination != null) {
                    home.report(null)
                    home.recordUse(row, System.currentTimeMillis() / 1000) {
                        if (!isFinishing && !isDestroyed && hasWindowFocus()) openRemoteDesktop(destination)
                    }
                } else {
                    home.report(HomeMessage.ROUTE_UNAVAILABLE)
                }
            }
            ConnectionProtocol.RDP -> home.report(HomeMessage.RDP_NOT_INTEGRATED)
            ConnectionProtocol.STREAM -> home.report(HomeMessage.STREAM_NOT_INTEGRATED)
        }
    }

    private fun renameWindow(name: String): String? {
        val error = when (WindowState.nameProblem(name)) {
            WindowNameProblem.BLANK -> R.string.cloudy_name_blank
            WindowNameProblem.TOO_LONG -> R.string.cloudy_name_long
            WindowNameProblem.CONTROL_CHARACTER,
            WindowNameProblem.INVALID_UNICODE -> R.string.cloudy_name_invalid
            null -> null
        }
        if (error != null) return getString(error)
        state = windowContext.renamed(name)
        notice = getString(R.string.cloudy_name_updated)
        updateSystemLabel()
        return null
    }

    private fun updateSystemLabel() {
        val label = getString(R.string.cloudy_window_title, windowContext.name, getString(appNameResource))
        title = label
        // The manifest requests a root document task even for external explicit launches.
        // Never relabel a caller's task if a platform delivers an unexpected non-root instance.
        if (!isTaskRoot) return
        try {
            setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(label).build())
            systemNotice = null
        } catch (_: RuntimeException) {
            systemNotice = getString(R.string.cloudy_system_title_failed)
        }
    }

    private fun newWindow() {
        val request = Intent(this, javaClass).addFlags(
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
        )
        try {
            // No copied state or intent extras: the new document receives a fresh logical ID.
            startActivity(request)
            notice = null
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun closeWindow() {
        // Task removal is scoped to an owned root, never an unexpected caller task.
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    private fun openRemoteDesktop(destination: Class<out ComponentActivity>) {
        try {
            // Explicit local Activity only; no secret/profile/authorization extras.
            startActivity(Intent(this, destination))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openCredentials() {
        try {
            // Explicit app-private destination; no profile, secret or authorization extras.
            startActivity(Intent(this, CloudyVaultActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openProfiles() {
        try {
            startActivity(Intent(this, CloudyProfilesActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openSshTrust() {
        try {
            // The manager reads this package's ledger; launch data confers no trust or approval.
            startActivity(Intent(this, CloudySshTrustActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openDisplaySettings() {
        try {
            // Appearance is local to this package; the destination accepts no preference extras.
            startActivity(Intent(this, CloudyDisplaySettingsActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openSshAuthenticate() {
        try {
            // The private destination loads its owning app's profiles; no credential or trust
            // assertion crosses this navigation Intent, and launch never starts authentication.
            startActivity(Intent(this, CloudySshAuthenticateActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openFilesLocations() {
        try {
            startActivity(Intent(this, CloudyFilesLocationsActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }

    private fun openSshFiles() {
        try {
            // The private screen selects this package's profile and trust. Navigation carries
            // no credentials or retained authority and never connects without an explicit action.
            startActivity(Intent(this, CloudySshFilesActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }
}
