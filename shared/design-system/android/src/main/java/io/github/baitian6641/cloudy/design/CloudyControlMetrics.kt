package io.github.baitian6641.cloudy.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Canonical control geometry shared by native product surfaces across module boundaries. */
object CloudyControlMetrics {
    val buttonRadius: Dp = CloudyTokens.Radii.Button.dp
    val chipRadius: Dp = CloudyTokens.Radii.Chip.dp
    val dialogRadius: Dp = CloudyTokens.Radii.Dialog.dp
    val comfortableMinHeight: Dp = CloudyTokens.Density.Comfortable.dp
    val spaceSmall: Dp = CloudyTokens.Space8.dp
    val spaceMedium: Dp = CloudyTokens.Space12.dp
    val spaceLarge: Dp = CloudyTokens.Space16.dp
}
