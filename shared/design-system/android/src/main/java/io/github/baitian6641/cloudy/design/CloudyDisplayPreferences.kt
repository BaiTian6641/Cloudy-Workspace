package io.github.baitian6641.cloudy.design

import androidx.compose.runtime.Immutable

enum class CloudyAppearance { SYSTEM, LIGHT, DARK }
enum class CloudyDensity { COMFORTABLE, STANDARD_POINTER, COMPACT_POINTER }

/** App-owned presentation preferences only; storage and operation state remain outside the theme. */
@Immutable
data class CloudyDisplayPreferences(
    val appearance: CloudyAppearance = CloudyAppearance.SYSTEM,
    val density: CloudyDensity = CloudyDensity.COMFORTABLE,
)
