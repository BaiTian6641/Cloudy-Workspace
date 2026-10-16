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
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.SshTrustStatus

/** Stateless saved-pin presentation; callbacks neither enroll a key nor connect to an SSH host. */
@Composable
internal fun SshTrustScreen(
    appName: String,
    catalog: SshTrustCatalog?,
    selected: SshTrustSnapshot?,
    busy: Boolean,
    message: String?,
    uncertain: Boolean,
    onInspect: (SshTrustSnapshot) -> Unit,
    onRevoke: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onCopyFingerprint: () -> Unit,
    onCheckServer: () -> Unit,
) {
    val listState = rememberLazyListState()
    val detailsIndex = 2 + (if (message != null) 1 else 0) + (if (uncertain) 1 else 0)
    LaunchedEffect(selected?.peer) {
        // Inspecting a row near the list's end must reveal the selected details, not leave the
        // user at an unrelated scroll offset after inserting content above the viewport.
        if (selected != null) listState.animateScrollToItem(detailsIndex)
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .testTag("trust_list"),
            contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
        ) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text(stringResource(R.string.ssh_trust_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(appName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.ssh_trust_local_only), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (message != null) item(key = "message") {
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("trust_message").semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (uncertain) item(key = "unknown") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ssh_trust_unknown), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(CloudyControlMetrics.spaceMedium).testTag("trust_unknown"))
                }
            }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    TrustButton(R.string.ssh_trust_refresh, "trust_refresh", !busy, onRefresh)
                    TrustButton(R.string.ssh_trust_check_server, "trust_check_server", !busy && !uncertain, onCheckServer)
                    // Closing does not cancel or replay an already admitted database mutation.
                    TrustButton(R.string.ssh_trust_close, "trust_close", true, onClose)
                }
            }
            if (selected != null) item(key = "details") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().testTag("trust_details")) {
                    Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                        Text(stringResource(R.string.ssh_trust_details_heading), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        SshTrustDetails(selected)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            TrustButton(R.string.ssh_trust_copy_fingerprint, "trust_copy_fingerprint", !busy, onCopyFingerprint)
                            if (selected.status is SshTrustStatus.Accepted) {
                                TrustButton(R.string.ssh_trust_revoke, "trust_revoke", !busy && !uncertain, onRevoke)
                            }
                        }
                        if (selected.status is SshTrustStatus.Revoked) {
                            Text(stringResource(R.string.ssh_trust_revoked_retained), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item(key = "saved-heading") {
                Text(stringResource(R.string.ssh_trust_list_heading), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            when {
                catalog == null -> item(key = "unavailable") {
                    Text(stringResource(if (busy) R.string.ssh_trust_loading else R.string.ssh_trust_not_loaded),
                        style = MaterialTheme.typography.bodyMedium)
                }
                catalog.snapshots.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.ssh_trust_empty), style = MaterialTheme.typography.bodyMedium)
                }
                else -> itemsIndexed(catalog.snapshots, key = { _, snapshot ->
                    "${snapshot.peer.host}/${snapshot.peer.port}/${snapshot.peer.route.wireId}"
                }) { index, snapshot ->
                    Surface(color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().testTag("trust_row_$index")) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            val host = if (':' in snapshot.peer.host) "[${snapshot.peer.host}]" else snapshot.peer.host
                            Text(stringResource(R.string.ssh_trust_endpoint, host, snapshot.peer.port),
                                style = MaterialTheme.typography.bodyLarge)
                            Text(statusLabel(snapshot.status), style = MaterialTheme.typography.bodyMedium)
                            TrustButton(R.string.ssh_trust_inspect, "trust_open_$index", !busy) { onInspect(snapshot) }
                        }
                    }
                }
            }
        }
    }
}

/** Exact immutable fields are also rendered in the confirmation; only selected details get tags. */
@Composable
internal fun SshTrustDetails(snapshot: SshTrustSnapshot, fieldTags: Boolean = true) {
    val key = trustKey(snapshot.status)
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
            TrustField(R.string.ssh_trust_host_label, snapshot.peer.host, "trust_host", fieldTags)
            TrustField(R.string.ssh_trust_port_label, snapshot.peer.port.toString(), "trust_port", fieldTags)
            TrustField(R.string.ssh_trust_route_label, stringResource(R.string.ssh_trust_route_direct), "trust_route", fieldTags)
            TrustField(R.string.ssh_trust_key_format_label, key.keyFormat, "trust_key_format", fieldTags)
            TrustField(R.string.ssh_trust_fingerprint_label, key.fingerprint, "trust_fingerprint", fieldTags)
            TrustField(R.string.ssh_trust_status_label, statusLabel(snapshot.status), "trust_status", fieldTags)
            TrustField(R.string.ssh_trust_revision_label, snapshot.revision.toString(), "trust_revision", fieldTags)
        }
    }
}

@Composable
private fun TrustField(label: Int, value: String, tag: String, tagged: Boolean) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            modifier = if (tagged) Modifier.testTag(tag) else Modifier)
    }
}

@Composable
private fun statusLabel(status: SshTrustStatus): String = stringResource(when (status) {
    is SshTrustStatus.Accepted -> R.string.ssh_trust_accepted
    is SshTrustStatus.Revoked -> R.string.ssh_trust_revoked
})

internal fun trustKey(status: SshTrustStatus): SshHostKeyIdentity = when (status) {
    is SshTrustStatus.Accepted -> status.key
    is SshTrustStatus.Revoked -> status.lastKey
}

@Composable
private fun TrustButton(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) {
        Text(stringResource(label))
    }
}
