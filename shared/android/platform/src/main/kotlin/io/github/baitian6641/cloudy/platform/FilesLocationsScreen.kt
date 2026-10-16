package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.FilesResourceState
import io.github.baitian6641.cloudy.domain.SshTrustStatus

/** Stateless own-location presentation. Inline consent shares the foreground Activity window. */
@Composable
internal fun FilesLocationsScreen(
    appName: String, counterpartName: String, catalog: FilesResourceCatalog?, selected: FilesResourceRegistration?,
    confirmation: FilesLocationConfirmation?, busy: Boolean, uncertain: Boolean, message: String?,
    onInspect: (FilesResourceRegistration) -> Unit, onShare: () -> Unit, onRevoke: () -> Unit,
    onConfirm: (FilesLocationConfirmation) -> Unit, onCancel: () -> Unit, onOpen: () -> Unit,
    onRefresh: () -> Unit, onShared: () -> Unit, onClose: () -> Unit,
) {
    val scroll = rememberLazyListState()
    val detailIndex = 2 + (if (message != null) 1 else 0) + (if (uncertain) 1 else 0)
    LaunchedEffect(selected?.reference?.resourceId, confirmation) {
        // Reveal a deliberate selection/proposal, without resetting scroll during unrelated reads.
        if (selected != null || confirmation != null) scroll.animateScrollToItem(detailIndex)
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = scroll,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .testTag("locations_list"),
            contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text(stringResource(R.string.files_locations_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(appName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.files_locations_local), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (message != null) item(key = "message") {
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("locations_message").semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (uncertain) item(key = "uncertain") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.files_locations_unknown), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(CloudyControlMetrics.spaceMedium).testTag("locations_unknown"))
                }
            }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    LocationButton(R.string.files_locations_refresh, "locations_refresh", !busy, onRefresh)
                    LocationButton(R.string.files_locations_other_app, "locations_other_app", !busy, onShared)
                    LocationButton(R.string.files_locations_close, "locations_close", true, onClose)
                }
            }
            if (confirmation != null) item(key = "confirmation") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
                    .testTag("locations_confirmation")) {
                    Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                        Text(stringResource(if (confirmation.action == FilesLocationAction.SHARE)
                            R.string.files_locations_share_title else R.string.files_locations_revoke_title),
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(if (confirmation.action == FilesLocationAction.SHARE)
                            stringResource(R.string.files_locations_share_body, counterpartName)
                            else stringResource(R.string.files_locations_revoke_body),
                            style = MaterialTheme.typography.bodyMedium)
                        FilesLocationDetails(confirmation.registration, "locations_confirmation")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            LocationButton(R.string.files_locations_confirm, "locations_confirm", !busy && !uncertain) {
                                onConfirm(confirmation)
                            }
                            LocationButton(R.string.files_locations_cancel, "locations_cancel", !busy, onCancel)
                        }
                    }
                }
            } else if (selected != null) item(key = "details") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
                    .testTag("locations_details")) {
                    Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                        Text(stringResource(R.string.files_locations_details), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        FilesLocationDetails(selected, "locations_detail")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            if (selected.state != FilesResourceState.REVOKED) {
                                LocationButton(R.string.files_locations_open, "locations_open", !busy && !uncertain, onOpen)
                                if (selected.state == FilesResourceState.PRIVATE) {
                                    LocationButton(R.string.files_locations_share, "locations_share", !busy && !uncertain, onShare)
                                }
                                LocationButton(R.string.files_locations_revoke, "locations_revoke", !busy && !uncertain, onRevoke)
                            }
                        }
                        Text(stringResource(if (selected.state == FilesResourceState.REVOKED)
                            R.string.files_locations_revoked_retained else R.string.files_locations_fresh_auth),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item(key = "saved-heading") {
                Text(stringResource(R.string.files_locations_saved), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            when {
                catalog == null -> item(key = "not-loaded") {
                    Text(stringResource(if (busy) R.string.files_locations_loading else R.string.files_locations_not_loaded),
                        style = MaterialTheme.typography.bodyMedium)
                }
                catalog.registrations.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.files_locations_empty), style = MaterialTheme.typography.bodyMedium)
                }
                else -> itemsIndexed(catalog.registrations, key = { _, row -> row.reference.resourceId }) { index, row ->
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
                        .testTag("locations_row_$index")) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(row.label, style = MaterialTheme.typography.bodyLarge)
                            Text(locationState(row.state), style = MaterialTheme.typography.bodyMedium)
                            LocationButton(R.string.files_locations_inspect, "locations_inspect_$index",
                                !busy && confirmation == null) { onInspect(row) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesLocationDetails(row: FilesResourceRegistration, prefix: String) {
    val key = when (val status = row.acceptedTrust.status) {
        is SshTrustStatus.Accepted -> status.key
        is SshTrustStatus.Revoked -> status.lastKey
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
            LocationField(R.string.files_locations_label, row.label, "${prefix}_label")
            LocationField(R.string.files_locations_host, row.profile.peer.host, "${prefix}_host")
            LocationField(R.string.files_locations_port, row.profile.peer.port.toString(), "${prefix}_port")
            LocationField(R.string.files_locations_account, row.accountName, "${prefix}_account")
            LocationField(R.string.files_locations_root, row.navigationRoot, "${prefix}_root")
            LocationField(R.string.files_locations_key_format, key.keyFormat, "${prefix}_key_format")
            LocationField(R.string.files_locations_fingerprint, key.fingerprint, "${prefix}_fingerprint")
            LocationField(R.string.files_locations_state, locationState(row.state), "${prefix}_state")
            LocationField(R.string.files_locations_revision, row.revision.toString(), "${prefix}_revision")
            Text(stringResource(R.string.files_locations_navigation_only), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LocationField(label: Int, value: String, tag: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun locationState(state: FilesResourceState): String = stringResource(when (state) {
    FilesResourceState.PRIVATE -> R.string.files_locations_private
    FilesResourceState.SHARED -> R.string.files_locations_shared
    FilesResourceState.REVOKED -> R.string.files_locations_revoked
})

@Composable
private fun LocationButton(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) {
        Text(stringResource(label))
    }
}
