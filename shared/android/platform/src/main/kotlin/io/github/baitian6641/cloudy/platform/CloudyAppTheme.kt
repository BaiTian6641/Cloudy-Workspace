package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.baitian6641.cloudy.design.CloudyAppearance
import io.github.baitian6641.cloudy.design.CloudyTheme

/** App-private content appearance/density only; native captions, splash and IME remain OS-owned. */
@Composable
fun CloudyAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { CloudyDisplaySettings.get(context) }
    val state by settings.state
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.preferences.appearance) {
        CloudyAppearance.SYSTEM -> systemDark
        CloudyAppearance.LIGHT -> false
        CloudyAppearance.DARK -> true
    }
    val activity = context.componentActivity()
    SideEffect {
        // Change only this window's edge-to-edge/icon contrast. Do not mutate app/global uiMode,
        // recreate Activities, remove secure flags or affect keyboard/permission ownership.
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        )
    }
    CloudyTheme(preferences = state.preferences, content = content)
}

private fun Context.componentActivity(): ComponentActivity? {
    var current: Context = this
    repeat(16) {
        if (current is ComponentActivity) return current as ComponentActivity
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
