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
import io.github.baitian6641.cloudy.design.CloudyTheme
import io.github.baitian6641.cloudy.design.WindowWorkspaceScreen
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

    private var state by mutableStateOf<WindowState?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var systemNotice by mutableStateOf<String?>(null)

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
        updateSystemLabel()
        setContent {
            CloudyTheme {
                val current = windowContext
                WindowWorkspaceScreen(
                    appName = getString(appNameResource),
                    windowId = current.windowId,
                    currentName = current.name,
                    notice = listOfNotNull(notice, systemNotice).joinToString("\n").ifEmpty { null },
                    onRename = ::renameWindow,
                    onNewWindow = ::newWindow,
                    onClose = ::closeWindow,
                    onCredentials = ::openCredentials,
                )
            }
        }
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

    private fun openCredentials() {
        try {
            // Explicit app-private destination; no profile, secret or authorization extras.
            startActivity(Intent(this, CloudyVaultActivity::class.java))
        } catch (_: RuntimeException) {
            notice = getString(R.string.cloudy_window_launch_failed)
        }
    }
}
