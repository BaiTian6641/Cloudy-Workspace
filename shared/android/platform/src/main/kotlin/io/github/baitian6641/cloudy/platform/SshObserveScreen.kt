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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import io.github.baitian6641.cloudy.domain.SshObservationChallenge
import io.github.baitian6641.cloudy.domain.SshObservationProfile
import io.github.baitian6641.cloudy.domain.SshTrustChallengeReason

/** Stateless observed-key presentation: callbacks belong to the foreground attempt controller. */
@Composable
internal fun SshObserveScreen(
    catalog: ConnectionProfileCatalog?,
    selected: SshObservationProfile?,
    challenge: SshObservationChallenge?,
    busy: Boolean,
    pending: Boolean,
    message: String?,
    onSelect: (ConnectionProfile) -> Unit,
    onCheck: () -> Unit,
    onCancel: () -> Unit,
    onSave: (SshObservationChallenge) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    offerLocalPermission: Boolean,
    onLocalPermission: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected?.profile?.id) {
        if (selected != null) listState.scrollToItem(2 + (if (message != null) 1 else 0) + (if (pending) 1 else 0))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("observe_profiles"), contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text(stringResource(R.string.ssh_observe_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.ssh_observe_scope))
                }
            }
            if (message != null) item(key = "message") {
                Text(message, modifier = Modifier.testTag("observe_message").semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (pending) item(key = "pending") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ssh_observe_unknown), color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(CloudyControlMetrics.spaceMedium).testTag("observe_unknown"))
                }
            }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    ObserveButton(R.string.ssh_observe_check, "observe_check", !busy && !pending && selected != null, onCheck)
                    ObserveButton(R.string.ssh_observe_cancel, "observe_cancel", true, onCancel)
                    ObserveButton(R.string.ssh_observe_refresh, "observe_refresh", !busy, onRefresh)
                    ObserveButton(R.string.ssh_observe_close, "observe_close", true, onClose)
                    if (offerLocalPermission) ObserveButton(R.string.ssh_observe_allow_local,
                        "observe_allow_local", !busy && !pending, onLocalPermission)
                }
            }
            selected?.let { value -> item(key = "selected") {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.ssh_observe_selected), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(value.profile.label)
                        ObservePeer(value, "observe_selected_")
                    }
                }
            } }
            item(key = "profiles-heading") {
                Text(stringResource(R.string.ssh_observe_profiles_heading), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            when {
                catalog == null -> item(key = "unavailable") {
                    Text(stringResource(if (busy) R.string.ssh_observe_loading else R.string.ssh_observe_profiles_unavailable))
                }
                catalog.profiles.none { it.protocol == ConnectionProtocol.SSH } -> item(key = "empty") {
                    Text(stringResource(R.string.ssh_observe_no_profiles))
                }
                else -> itemsIndexed(catalog.profiles.filter { it.protocol == ConnectionProtocol.SSH }, key = { _, value -> value.id }) { index, profile ->
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(profile.label, style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.ssh_observe_endpoint, displayHost(profile.host), profile.port))
                            ObserveButton(R.string.ssh_observe_select, "observe_select_$index", !busy && !pending) { onSelect(profile) }
                        }
                    }
                }
            }
        }
    }
    challenge?.let { value ->
        AlertDialog(onDismissRequest = onCancel, modifier = Modifier.testTag("observe_dialog"),
            shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            text = {
                key(value.challenge.presentationGeneration) {
                    // Title and exact identities share the available viewport; fixed actions do
                    // not compete with a separately fixed scaled title in short desktop windows.
                    Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                        Text(stringResource(when (value.challenge.reason) {
                            SshTrustChallengeReason.FIRST_USE -> R.string.ssh_observe_first_use
                            SshTrustChallengeReason.REPLACEMENT -> R.string.ssh_observe_replacement
                            SshTrustChallengeReason.REENROLLMENT -> R.string.ssh_observe_reenrollment
                        }), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.ssh_observe_comparison))
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                ObservePeer(value.profile, "observe_")
                                ObserveKey(value.challenge.observedKey, "observe_", R.string.ssh_observe_observed_key)
                                value.challenge.previousKey?.let { previous ->
                                    ObserveKey(previous, "observe_previous_", R.string.ssh_observe_previous_key)
                                }
                                ObserveField(R.string.ssh_observe_revision_label,
                                    value.challenge.token.expectedRevision.toString(), "observe_expected_revision")
                            }
                        }
                        Text(stringResource(R.string.ssh_observe_save_scope))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(value) }, enabled = !busy && !pending,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag("observe_confirm")) {
                    Text(stringResource(R.string.ssh_observe_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag("observe_dialog_cancel")) {
                    Text(stringResource(R.string.ssh_observe_cancel))
                }
            })
    }
}

@Composable
private fun ObservePeer(profile: SshObservationProfile, prefix: String) {
    ObserveField(R.string.ssh_observe_host_label, profile.peer.host, "${prefix}host")
    ObserveField(R.string.ssh_observe_port_label, profile.peer.port.toString(), "${prefix}port")
    ObserveField(R.string.ssh_observe_route_label, stringResource(R.string.ssh_observe_direct), "${prefix}route")
}

@Composable
private fun ObserveKey(key: SshHostKeyIdentity, prefix: String, heading: Int) {
    Text(stringResource(heading), style = MaterialTheme.typography.labelLarge)
    ObserveField(R.string.ssh_observe_key_format_label, key.keyFormat, "${prefix}key_format")
    ObserveField(R.string.ssh_observe_fingerprint_label, key.fingerprint, "${prefix}fingerprint")
}

@Composable
private fun ObserveField(label: Int, value: String, tag: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ObserveButton(label: Int, tag: String, enabled: Boolean, click: () -> Unit) {
    OutlinedButton(onClick = click, enabled = enabled, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) { Text(stringResource(label)) }
}

private fun displayHost(host: String): String = if (':' in host) "[$host]" else host
