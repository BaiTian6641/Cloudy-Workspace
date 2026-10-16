package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.FilesSharedDescriptor

/** Labels are the only disclosed location content shown here; references remain opaque in memory. */
@Composable
internal fun SharedLocationsScreen(
    counterpartName: String, page: FilesDiscoveryReply?, selected: FilesSharedDescriptor?,
    busy: Boolean, uncertain: Boolean, message: String?, interactive: Boolean,
    onRefresh: () -> Unit, onNext: () -> Unit, onSelect: (FilesSharedDescriptor) -> Unit,
    onOpen: () -> Unit, onClose: () -> Unit,
) {
    val scroll = rememberLazyListState()
    val selectedIndex = 2 + (if (message != null) 1 else 0) + (if (uncertain) 1 else 0)
    LaunchedEffect(selected) {
        if (selected != null) scroll.animateScrollToItem(selectedIndex)
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("shared_locations_list"), contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text(stringResource(R.string.shared_locations_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(counterpartName, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("shared_locations_counterpart"))
                    Text(stringResource(R.string.shared_locations_scope), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (message != null) item(key = "message") {
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("shared_locations_message").semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (uncertain) item(key = "unknown") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shared_locations_unknown), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(CloudyControlMetrics.spaceMedium).testTag("shared_locations_unknown"))
                }
            }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    SharedLocationButton(R.string.shared_locations_refresh, "shared_locations_refresh", interactive && !busy, onRefresh)
                    SharedLocationButton(R.string.shared_locations_next, "shared_locations_next",
                        interactive && !busy && !uncertain && page?.nextAfterResourceId?.isNotEmpty() == true, onNext)
                    SharedLocationButton(R.string.shared_locations_close, "shared_locations_close", true, onClose)
                }
            }
            if (selected != null) item(key = "selected") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
                    .testTag("shared_locations_selection")) {
                    Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                        Text(stringResource(R.string.shared_locations_selected), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(selected.label, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("shared_locations_selected_label"))
                        Text(stringResource(R.string.shared_locations_open_scope, counterpartName),
                            style = MaterialTheme.typography.bodyMedium)
                        SharedLocationButton(R.string.shared_locations_open, "shared_locations_open",
                            interactive && !busy && !uncertain, onOpen)
                    }
                }
            }
            item(key = "page-heading") {
                Text(stringResource(R.string.shared_locations_page), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            when {
                page == null -> item(key = "not-loaded") {
                    Text(stringResource(if (busy) R.string.shared_locations_waiting else R.string.shared_locations_explicit_refresh),
                        style = MaterialTheme.typography.bodyMedium)
                }
                page.locations.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.shared_locations_empty), style = MaterialTheme.typography.bodyMedium)
                }
                else -> itemsIndexed(page.locations, key = { index, _ -> "shared-location-$index" }) { index, descriptor ->
                    Surface(color = if (selected === descriptor) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
                        .testTag("shared_locations_row_$index")) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(descriptor.label, style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag("shared_locations_label_$index"))
                            SharedLocationButton(R.string.shared_locations_select, "shared_locations_select_$index",
                                interactive && !busy && !uncertain) { onSelect(descriptor) }
                        }
                    }
                }
            }
            item(key = "page-scope") {
                Text(stringResource(if (page?.nextAfterResourceId?.isNotEmpty() == true)
                    R.string.shared_locations_more else R.string.shared_locations_paging_scope),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SharedLocationButton(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) {
        Text(stringResource(label))
    }
}
