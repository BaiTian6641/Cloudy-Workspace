package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.ssh.SshFileEntry
import io.github.baitian6641.cloudy.ssh.SshFileKind
import io.github.baitian6641.cloudy.ssh.SshReadOnlyFiles

/** Native presentation only. The model owns real remote operations; no remembered credential state. */
@Composable
internal fun SshFilesScreen(
    state: SshFilesSessionState, username: TextFieldValue, password: TextFieldValue,
    credentialEpoch: Long, interactive: Boolean, notice: String?,
    onUsername: (TextFieldValue) -> Unit, onPassword: (TextFieldValue) -> Unit,
    onSelect: (ConnectionProfile) -> Unit, onConnect: () -> Unit, onRefreshProfiles: () -> Unit,
    onList: (String) -> Unit, onRead: (SshFileEntry) -> Unit,
    onDisconnect: () -> Unit, onClose: () -> Unit,
    offerLocalPermission: Boolean, onPermission: () -> Unit,
) {
    val canConnect = interactive && state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED)
    val canBrowse = interactive && state.phase == SshFilesPhase.CONNECTED
    var pathDraft by remember(state.path) { mutableStateOf(TextFieldValue(state.path)) }
    var pathTooLong by remember(state.path) { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(state.selected) { if (state.selected != null) scroll.scrollToItem(0) }
    val hasForm = state.selected != null && state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED, SshFilesPhase.CONNECTING)
    val hasPermission = state.permissionNeeded || (offerLocalPermission && canConnect)
    val hasBrowser = state.phase in setOf(SshFilesPhase.CONNECTED, SshFilesPhase.READING)
    val pathIndex = 2 + (if (state.selected != null) 1 else 0) + (if (hasForm) 3 else 0) + (if (hasPermission) 1 else 0)
    val previewIndex = pathIndex + if (hasBrowser) 3 + state.entries.size +
        (if (state.entries.isEmpty() && state.phase == SshFilesPhase.CONNECTED) 1 else 0) else 0
    // Reveal completed navigation/preview once; never move focus/scroll on a field keystroke.
    LaunchedEffect(state.path, hasBrowser, state.preview) {
        if (state.preview != null) scroll.scrollToItem(previewIndex)
        else if (hasBrowser) scroll.scrollToItem(pathIndex)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            // Only the essential exit actions are fixed, leaving the identity/form/preview scrollable
            // even when scaled text and native IME leave a short desktop window.
            FlowRow(Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge),
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                FilesButton(R.string.ssh_files_disconnect, "ssh_files_disconnect", true, onDisconnect)
                FilesButton(R.string.ssh_files_close, "ssh_files_close", true, onClose)
            }
            LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth().testTag("ssh_files_list"),
                contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                item(key = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.ssh_files_heading), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.ssh_files_scope), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "status") {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(phaseLabel(state.phase)), modifier = Modifier.testTag("ssh_files_phase"))
                        val status = notice ?: state.code?.let { stringResource(R.string.ssh_files_code, it) }
                            ?: if (state.phase == SshFilesPhase.IDLE) stringResource(R.string.ssh_files_ready) else null
                        if (status != null) Text(status, modifier = Modifier.testTag("ssh_files_status")
                            .semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                state.selected?.let { selection -> item(key = "identity") {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(stringResource(R.string.ssh_files_selected), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                    Text(selection.profile.label, modifier = Modifier.testTag("ssh_files_selected_label"))
                                    Text(peerText(selection.profile), modifier = Modifier.testTag("ssh_files_peer"))
                                    Text(stringResource(R.string.ssh_files_revision, selection.profile.revision),
                                        modifier = Modifier.testTag("ssh_files_revision"))
                                    Text(stringResource(R.string.ssh_files_direct))
                                    state.fingerprint?.let { value ->
                                        Text(value, modifier = Modifier.testTag("ssh_files_fingerprint"))
                                    }
                                }
                            }
                        }
                    }
                } }
                if (hasForm) {
                    item(key = "username") { key(credentialEpoch) {
                        OutlinedTextField(username, onUsername, enabled = canConnect, singleLine = true,
                            label = { Text(stringResource(R.string.ssh_files_username)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                            modifier = Modifier.fillMaxWidth().testTag("ssh_files_username"))
                    } }
                    item(key = "password") { key(credentialEpoch) {
                        OutlinedTextField(password, onPassword, enabled = canConnect, singleLine = true,
                            label = { Text(stringResource(R.string.ssh_files_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                            modifier = Modifier.fillMaxWidth().testTag("ssh_files_password"))
                    } }
                    item(key = "connect") {
                        FilesButton(R.string.ssh_files_connect, "ssh_files_connect",
                            canConnect && username.text.isNotEmpty() && password.text.isNotEmpty(), onConnect)
                    }
                }
                if (hasPermission) item(key = "permission") {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.ssh_files_permission_needed))
                        FilesButton(R.string.ssh_files_permission, "ssh_files_permission", canConnect, onPermission)
                    }
                }
                if (hasBrowser) {
                    item(key = "path") {
                        OutlinedTextField(pathDraft, {
                            pathTooLong = it.text.length > SshReadOnlyFiles.MAX_PATH_UNITS
                            if (!pathTooLong) pathDraft = it
                        },
                            enabled = canBrowse, singleLine = true, label = { Text(stringResource(R.string.ssh_files_path)) },
                            isError = pathTooLong,
                            supportingText = { if (pathTooLong) Text(stringResource(R.string.ssh_files_path_limit)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                            modifier = Modifier.fillMaxWidth().testTag("ssh_files_path"))
                    }
                    item(key = "browse-actions") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            FilesButton(R.string.ssh_files_list_path, "ssh_files_list_path", canBrowse && pathDraft.text.isNotEmpty()) { onList(pathDraft.text) }
                            FilesButton(R.string.ssh_files_refresh, "ssh_files_refresh", canBrowse) { onList(state.path) }
                            FilesButton(R.string.ssh_files_parent, "ssh_files_parent", canBrowse && state.path != "/") { onList(remoteParent(state.path)) }
                        }
                    }
                    item(key = "entries-heading") {
                        Text(stringResource(R.string.ssh_files_entries), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                    }
                    if (state.entries.isEmpty() && state.phase == SshFilesPhase.CONNECTED) item(key = "empty-directory") {
                        Text(stringResource(R.string.ssh_files_empty_directory))
                    }
                    itemsIndexed(state.entries, key = { index, _ -> "entry-$index" }) { index, entry ->
                        Surface(Modifier.fillMaxWidth().testTag("ssh_files_entry_$index"),
                            shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                SelectionContainer { Text(entry.name, style = MaterialTheme.typography.titleMedium) }
                                Text(stringResource(kindLabel(entry.kind)))
                                entry.size?.let { Text(stringResource(R.string.ssh_files_size, it)) }
                                when (entry.kind) {
                                    SshFileKind.DIRECTORY -> FilesButton(R.string.ssh_files_open, "ssh_files_open_$index", canBrowse) {
                                        onList(remoteChild(state.path, entry.name))
                                    }
                                    SshFileKind.REGULAR -> FilesButton(R.string.ssh_files_read, "ssh_files_read_$index", canBrowse) { onRead(entry) }
                                    SshFileKind.OTHER -> Text(stringResource(R.string.ssh_files_other_unsupported))
                                }
                            }
                        }
                    }
                }
                state.preview?.let { preview -> item(key = "preview") {
                    Column(Modifier.fillMaxWidth().testTag("ssh_files_preview"),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.ssh_files_preview_heading), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        SelectionContainer { Text(preview.name) }
                        Text(stringResource(R.string.ssh_files_preview_bound, preview.byteCount))
                        Text(stringResource(if (preview.binary) R.string.ssh_files_hex else R.string.ssh_files_text))
                        SelectionContainer { Text(preview.text, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("ssh_files_preview_content")) }
                    }
                } }
                item(key = "profiles-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.ssh_files_profiles), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.testTag("ssh_files_profiles").semantics { heading() })
                        FilesButton(R.string.ssh_files_refresh_profiles, "ssh_files_refresh_profiles", canConnect, onRefreshProfiles)
                    }
                }
                val profiles = state.catalog?.profiles?.filter { it.protocol == ConnectionProtocol.SSH }
                if (profiles == null) item(key = "unavailable") { Text(stringResource(R.string.ssh_files_catalog_unavailable)) }
                else if (profiles.isEmpty()) item(key = "no-profiles") { Text(stringResource(R.string.ssh_files_no_profiles)) }
                else itemsIndexed(profiles, key = { _, profile -> profile.id }) { _, profile ->
                    OutlinedButton(onClick = { onSelect(profile) }, enabled = canConnect,
                        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                        modifier = Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                            .testTag("ssh_files_profile_${profile.id}")) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(profile.label, style = MaterialTheme.typography.titleMedium)
                            Text(peerText(profile), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun peerText(profile: ConnectionProfile) = "${if (':' in profile.host) "[${profile.host}]" else profile.host}:${profile.port}"
// These construct remote protocol paths only. No java.io.File/local normalization or destination.
private fun remoteChild(parent: String, name: String) = if (parent == "/") "/$name" else "${parent.trimEnd('/')}/$name"
private fun remoteParent(path: String): String = if (path == "/") "/" else "${path.trimEnd('/')}/.."
private fun phaseLabel(phase: SshFilesPhase): Int = when (phase) {
    SshFilesPhase.IDLE -> R.string.ssh_files_idle
    SshFilesPhase.LOADING -> R.string.ssh_files_loading
    SshFilesPhase.CONNECTING -> R.string.ssh_files_connecting
    SshFilesPhase.CONNECTED -> R.string.ssh_files_connected
    SshFilesPhase.READING -> R.string.ssh_files_reading
    SshFilesPhase.RETIRING -> R.string.ssh_files_retiring
    SshFilesPhase.FAILED -> R.string.ssh_files_failed
}
private fun kindLabel(kind: SshFileKind): Int = when (kind) {
    SshFileKind.REGULAR -> R.string.ssh_files_regular
    SshFileKind.DIRECTORY -> R.string.ssh_files_directory
    SshFileKind.OTHER -> R.string.ssh_files_other
}
@Composable private fun FilesButton(label: Int, tag: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(action, enabled = enabled, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) { Text(stringResource(label)) }
}
