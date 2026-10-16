package io.github.baitian6641.cloudy.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class CloudyDensityMetrics(
    val controlMinHeight: Dp,
    val rowMinHeight: Dp,
    val spaceSmall: Dp,
    val spaceMedium: Dp,
    val spaceLarge: Dp,
)

private val comfortable = CloudyDensityMetrics(
    CloudyTokens.Density.Comfortable.dp, CloudyTokens.Density.Comfortable.dp,
    CloudyTokens.Space8.dp, CloudyTokens.Space12.dp, CloudyTokens.Space16.dp,
)
private val standardPointer = CloudyDensityMetrics(
    CloudyTokens.Density.StandardPointer.dp, CloudyTokens.Density.StandardPointer.dp,
    CloudyTokens.Space8.dp, CloudyTokens.Space8.dp, CloudyTokens.Space12.dp,
)
private val compactPointer = CloudyDensityMetrics(
    CloudyTokens.Density.CompactPointer.dp, CloudyTokens.Density.CompactPointer.dp,
    CloudyTokens.Space4.dp, CloudyTokens.Space8.dp, CloudyTokens.Space8.dp,
)

internal val LocalCloudyDensityMetrics = staticCompositionLocalOf { comfortable }
internal fun metricsFor(density: CloudyDensity): CloudyDensityMetrics = when (density) {
    CloudyDensity.COMFORTABLE -> comfortable
    CloudyDensity.STANDARD_POINTER -> standardPointer
    CloudyDensity.COMPACT_POINTER -> compactPointer
}

/** Canonical geometry; the theme scopes density without changing text scale or platform density. */
object CloudyControlMetrics {
    val fieldRadius: Dp = CloudyTokens.Radii.Field.dp
    val buttonRadius: Dp = CloudyTokens.Radii.Button.dp
    val chipRadius: Dp = CloudyTokens.Radii.Chip.dp
    val dialogRadius: Dp = CloudyTokens.Radii.Dialog.dp
    val comfortableMinHeight: Dp = CloudyTokens.Density.Comfortable.dp

    // Nominal visual minimums, not fixed rendered heights. Material retains its minimum interactive
    // sizing; text/content may grow. Multi-line cards must not be clamped to a nominal row height.
    val controlMinHeight: Dp
        @Composable get() = LocalCloudyDensityMetrics.current.controlMinHeight
    val rowMinHeight: Dp
        @Composable get() = LocalCloudyDensityMetrics.current.rowMinHeight
    val spaceSmall: Dp
        @Composable get() = LocalCloudyDensityMetrics.current.spaceSmall
    val spaceMedium: Dp
        @Composable get() = LocalCloudyDensityMetrics.current.spaceMedium
    val spaceLarge: Dp
        @Composable get() = LocalCloudyDensityMetrics.current.spaceLarge
}
