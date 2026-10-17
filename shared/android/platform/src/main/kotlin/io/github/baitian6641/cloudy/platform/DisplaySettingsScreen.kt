package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.baitian6641.cloudy.design.CloudyAppearance
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.domain.TerminalRightClick

@Composable
internal fun DisplaySettingsScreen(
    state: CloudyDisplayState,
    onAppearance: (CloudyAppearance) -> Unit,
    onDensity: (CloudyDensity) -> Unit,
    onTerminalRightClick: (TerminalRightClick) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val enabled = state.ready && !state.loading && !state.saving
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState()).padding(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
        ) {
            Text(stringResource(R.string.display_heading), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.display_local_only))
            Text(stringResource(R.string.display_native_limit))
            if (state.loading) Text(stringResource(R.string.display_loading))
            if (state.saving) Text(stringResource(R.string.display_saving))
            state.notice?.let { notice ->
                val text = when (notice) {
                    CloudyDisplayNotice.SAVED -> R.string.display_saved
                    CloudyDisplayNotice.RECOVERED_VALUES -> R.string.display_recovered
                    CloudyDisplayNotice.WRITE_UNCONFIRMED -> R.string.display_write_unconfirmed
                    CloudyDisplayNotice.UNAVAILABLE -> R.string.display_unavailable
                    CloudyDisplayNotice.BUSY -> R.string.display_busy
                }
                // Only the outcome is live: progress and the persistent recovery explanation
                // remain readable without generating competing announcements.
                Text(stringResource(text), modifier = Modifier.testTag("display_message")
                    .semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (state.recoveredValues && state.notice != CloudyDisplayNotice.RECOVERED_VALUES) {
                Text(stringResource(R.string.display_recovered))
            }
            Text(stringResource(R.string.display_appearance_heading), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                CloudyAppearance.entries.forEach { appearance ->
                    val label = when (appearance) {
                        CloudyAppearance.SYSTEM -> R.string.display_appearance_system
                        CloudyAppearance.LIGHT -> R.string.display_appearance_light
                        CloudyAppearance.DARK -> R.string.display_appearance_dark
                    }
                    DisplayChoice(stringResource(label), state.preferences.appearance == appearance, enabled,
                        "display_appearance_${appearance.name}") { onAppearance(appearance) }
                }
            }
            Text(stringResource(R.string.display_density_heading), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.display_density_accessibility))
            Column(Modifier.selectableGroup()) {
                CloudyDensity.entries.forEach { density ->
                    val label = when (density) {
                        CloudyDensity.COMFORTABLE -> R.string.display_density_comfortable
                        CloudyDensity.STANDARD_POINTER -> R.string.display_density_standard
                        CloudyDensity.COMPACT_POINTER -> R.string.display_density_compact
                    }
                    DisplayChoice(stringResource(label), state.preferences.density == density, enabled,
                        "display_density_${density.name}") { onDensity(density) }
                }
            }
            Text(stringResource(R.string.display_terminal_heading), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.display_terminal_scope))
            Column(Modifier.selectableGroup()) {
                TerminalRightClick.entries.forEach { action ->
                    val label = when (action) {
                        TerminalRightClick.PASTE -> R.string.display_terminal_paste
                        TerminalRightClick.CONTEXT_MENU -> R.string.display_terminal_menu
                    }
                    DisplayChoice(stringResource(label), state.terminalRightClick == action, enabled,
                        "display_terminal_${action.name}") { onTerminalRightClick(action) }
                }
            }
            Button(onClick = onRefresh, enabled = !state.loading && !state.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                    .testTag("display_refresh"), shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius)) {
                Text(stringResource(R.string.display_refresh))
            }
            Button(onClick = onClose,
                modifier = Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                    .testTag("display_close"), shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius)) {
                Text(stringResource(R.string.display_close))
            }
        }
    }
}

@Composable
private fun DisplayChoice(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    // Material's radio keeps its interactive minimum; density changes nominal spacing rather
    // than shrinking the target or replacing native selectable/keyboard semantics.
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.comfortableMinHeight)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .testTag(tag).padding(horizontal = CloudyControlMetrics.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}
