package io.github.baitian6641.cloudy.desktop

import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.CloudyWindowActivity

/** Each APK supplies its own owner and resources; no other app is required. */
class WorkspaceActivity : CloudyWindowActivity() {
    override val windowOwner = WindowOwner.DESKTOP
    override val appNameResource = R.string.app_name
    override val defaultWindowNameResource = R.string.default_window_name
}
