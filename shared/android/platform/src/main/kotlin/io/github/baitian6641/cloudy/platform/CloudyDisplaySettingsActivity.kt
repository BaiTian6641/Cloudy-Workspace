package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue

/** Native owning-app presentation settings. Admitted commits belong to the process owner. */
class CloudyDisplaySettingsActivity : ComponentActivity() {
    private lateinit var settings: CloudyDisplaySettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = try {
            CloudyDisplaySettings.get(this)
        } catch (_: IllegalArgumentException) {
            finish()
            return
        }
        title = getString(R.string.display_heading)
        setContent {
            CloudyAppTheme {
                val state by settings.state
                DisplaySettingsScreen(state = state, onAppearance = settings::setAppearance,
                    onDensity = settings::setDensity, onRefresh = settings::refresh, onClose = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Initial loading and any admitted save already own their queue position. Refresh never
        // replays a mutation or clears a failed commit merely by reading the process cache.
        if (::settings.isInitialized) settings.refresh()
    }
}
