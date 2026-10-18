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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowState
import io.github.baitian6641.cloudy.ssh.SshFileEntry
import io.github.baitian6641.cloudy.ssh.SshFileKind
import io.github.baitian6641.cloudy.ssh.SshFiles

/** Native presentation only. The model owns real remote operations; no remembered credential state. */
@Composable
internal fun SshFilesScreen(
    state: SshFilesSessionState, username: TextFieldValue, password: TextFieldValue,
    credentialEpoch: Long, interactive: Boolean, notice: String?,
    onUsername: (TextFieldValue) -> Unit, onPassword: (TextFieldValue) -> Unit,
    onSelect: (ConnectionProfile) -> Unit, onConnect: () -> Unit, onRefreshProfiles: () -> Unit,
    onList: (String) -> Unit, onRead: (SshFileEntry) -> Unit,
    onCreateDirectory: (String) -> Unit, onRename: (SshFileEntry, String) -> Unit,
    onRequestDelete: (SshFileEntry) -> Unit, onConfirmMutation: () -> Unit, onCancelMutation: () -> Unit,
    onEdit: (SshFileEntry) -> Unit, onEditorText: (String) -> Unit, onRequestSave: () -> Unit,
    onCancelSave: () -> Unit, onConfirmSave: () -> Unit, onConfirmConflict: () -> Unit,
    onDiscardStaged: () -> Unit, onRequestCloseEditor: () -> Unit, onCancelCloseEditor: () -> Unit,
    onConfirmCloseEditor: () -> Unit,
    onDisconnect: () -> Unit, onClose: () -> Unit,
    onLocations: () -> Unit, onRegister: (FilesListingCandidate, String) -> Unit,
    onCancelRegistration: (FilesListingCandidate) -> Unit,
    offerLocalPermission: Boolean, onPermission: () -> Unit,
) {
    val canConnect = interactive && !state.registrationPending && state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED)
    val canBrowse = interactive && !state.registrationPending && state.phase == SshFilesPhase.CONNECTED
    val canNavigate = canBrowse && state.editor == null
    val canMutate = canNavigate && state.mutationConsent == null
    // This proposal belongs to one completed listing only. No rememberSaveable, Intent or
    // restored field can resurrect its authority after focus/lifecycle invalidation.
    var confirming by remember(state.registrationCandidate, interactive) { mutableStateOf(false) }
    var locationLabel by remember(state.registrationCandidate, interactive) { mutableStateOf(TextFieldValue()) }
    var locationLabelRejected by remember(state.registrationCandidate, interactive) { mutableStateOf(false) }
    var pathDraft by remember(state.path) { mutableStateOf(TextFieldValue(state.path)) }
    var pathTooLong by remember(state.path) { mutableStateOf(false) }
    var folderDraft by remember(state.path) { mutableStateOf(TextFieldValue()) }
    var folderInvalid by remember(state.path) { mutableStateOf(false) }
    var renamingName by remember(state.path) { mutableStateOf<String?>(null) }
    var renameDraft by remember(state.path) { mutableStateOf(TextFieldValue()) }
    var renameInvalid by remember(state.path) { mutableStateOf(false) }
    LaunchedEffect(state.path) { renamingName = null }
    val scroll = rememberLazyListState()
    LaunchedEffect(state.selected) { if (state.selected != null) scroll.scrollToItem(0) }
    val hasForm = state.selected != null && state.phase in setOf(SshFilesPhase.IDLE, SshFilesPhase.FAILED, SshFilesPhase.CONNECTING)
    val hasPermission = state.permissionNeeded || (offerLocalPermission && canConnect)
    val hasBrowser = state.phase in setOf(SshFilesPhase.CONNECTED, SshFilesPhase.READING, SshFilesPhase.MUTATING)
    val pathIndex = 2 + (if (state.selected != null) 1 else 0) + (if (hasForm) 3 else 0) + (if (hasPermission) 1 else 0)
    val previewIndex = pathIndex + if (hasBrowser) 5 + state.entries.size +
        (if (state.entries.isEmpty() && state.phase == SshFilesPhase.CONNECTED) 1 else 0) +
        (if (state.mutationConsent != null) 1 else 0) + (if (state.editor != null) 1 else 0) +
        (if (state.transfers.isNotEmpty()) 1 else 0) else 0
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
                FilesButton(R.string.ssh_location_manage, "ssh_location_manage", canConnect, onLocations)
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
                        // Metadata writes can finish after Disconnect. Their outcome must remain
                        // visible even when there is no connected browser to display it beside.
                        if (state.registrationPending) Text(stringResource(R.string.ssh_location_saving))
                        if (state.registrationUncertain) Text(stringResource(R.string.ssh_location_unknown),
                            modifier = Modifier.testTag("ssh_location_unknown").semantics { liveRegion = LiveRegionMode.Polite })
                        state.registrationCode?.let { Text(stringResource(R.string.ssh_files_code, it),
                            modifier = Modifier.testTag("ssh_location_result").semantics { liveRegion = LiveRegionMode.Polite }) }
                        state.registrationSaved?.let { Text(stringResource(R.string.ssh_location_saved, it.label),
                            modifier = Modifier.testTag("ssh_location_saved").semantics { liveRegion = LiveRegionMode.Polite }) }
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
                                    state.location?.let { location ->
                                        Text(stringResource(R.string.ssh_location_selected, location.label))
                                        Text(stringResource(R.string.ssh_location_root, location.navigationRoot))
                                    }
                                }
                            }
                        }
                    }
                } }
                if (hasForm) {
                    item(key = "username") { key(credentialEpoch) {
                        OutlinedTextField(username, onUsername, enabled = canConnect, singleLine = true,
                            readOnly = state.location != null,
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
                            pathTooLong = it.text.length > SshFiles.MAX_PATH_UNITS
                            if (!pathTooLong) pathDraft = it
                        },
                            enabled = canBrowse, singleLine = true, label = { Text(stringResource(R.string.ssh_files_path)) },
                            isError = pathTooLong,
                            supportingText = { if (pathTooLong) Text(stringResource(R.string.ssh_files_path_limit)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                            modifier = Modifier.fillMaxWidth().testTag("ssh_files_path"))
                    }
                    item(key = "folder") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            OutlinedTextField(folderDraft, { value ->
                                folderInvalid = value.text.isEmpty() || value.text.length > SshFiles.MAX_NAME_UNITS ||
                                    '/' in value.text || '\\' in value.text || value.text == "." || value.text == ".."
                                if (value.text.length <= SshFiles.MAX_NAME_UNITS) folderDraft = value
                            }, enabled = canMutate, singleLine = true, isError = folderInvalid,
                                label = { Text(stringResource(R.string.ssh_files_folder_name)) },
                                supportingText = { if (folderInvalid) Text(stringResource(R.string.ssh_files_name_invalid)) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                modifier = Modifier.fillMaxWidth().testTag("ssh_files_folder_name"))
                            FilesButton(R.string.ssh_files_create_folder, "ssh_files_create_folder",
                                canMutate && folderDraft.text.isNotEmpty() && !folderInvalid) {
                                onCreateDirectory(folderDraft.text)
                                folderDraft = TextFieldValue()
                            }
                        }
                    }
                    item(key = "browse-actions") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            FilesButton(R.string.ssh_files_list_path, "ssh_files_list_path", canNavigate && pathDraft.text.isNotEmpty()) { onList(pathDraft.text) }
                            FilesButton(R.string.ssh_files_refresh, "ssh_files_refresh", canNavigate) { onList(state.path) }
                            FilesButton(R.string.ssh_files_parent, "ssh_files_parent", canNavigate && state.path != "/") { onList(remoteParent(state.path)) }
                        }
                    }
                    state.editor?.let { view -> item(key = "editor") {
                        Surface(Modifier.fillMaxWidth().testTag("ssh_files_editor"),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius)) {
                            Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                Text(stringResource(R.string.ssh_files_editor_heading), style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.semantics { heading() })
                                SelectionContainer { Text(view.path) }
                                OutlinedTextField(view.text, onEditorText,
                                    enabled = !view.busy && !view.saveConsent && !view.discardConsent && !view.conflict,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    minLines = 8, maxLines = 20,
                                    shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                    modifier = Modifier.fillMaxWidth().testTag("ssh_files_editor_text"))
                                if (view.dirty) Text(stringResource(R.string.ssh_files_editor_unsaved),
                                    modifier = Modifier.testTag("ssh_files_editor_dirty"))
                                view.code?.let { Text(stringResource(R.string.ssh_files_code, it),
                                    modifier = Modifier.testTag("ssh_files_editor_code").semantics { liveRegion = LiveRegionMode.Polite }) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                    FilesButton(R.string.ssh_files_editor_save, "ssh_files_editor_save",
                                        view.dirty && !view.busy && !view.saveConsent && !view.discardConsent && !view.conflict) { onRequestSave() }
                                    FilesButton(R.string.ssh_files_editor_close, "ssh_files_editor_close", !view.busy) { onRequestCloseEditor() }
                                }
                                if (view.saveConsent) Surface(Modifier.fillMaxWidth().testTag("ssh_files_save_confirmation"),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius)) {
                                    Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                                        Text(stringResource(R.string.ssh_files_save_confirm_heading), style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.semantics { heading() })
                                        Text(stringResource(R.string.ssh_files_save_confirm_body, view.path))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                            FilesButton(R.string.ssh_files_editor_replace, "ssh_files_save_confirm", !view.busy) { onConfirmSave() }
                                            FilesButton(R.string.ssh_files_cancel, "ssh_files_save_cancel", true) { onCancelSave() }
                                        }
                                    }
                                }
                                if (view.conflict) Surface(Modifier.fillMaxWidth().testTag("ssh_files_conflict"),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius)) {
                                    Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                                        Text(stringResource(R.string.ssh_files_conflict_heading), style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.semantics { heading() })
                                        Text(stringResource(R.string.ssh_files_conflict_body))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                            FilesButton(R.string.ssh_files_editor_replace, "ssh_files_conflict_replace", !view.busy) { onConfirmConflict() }
                                            FilesButton(R.string.ssh_files_editor_discard_staged, "ssh_files_conflict_discard", !view.busy) { onDiscardStaged() }
                                        }
                                    }
                                }
                                if (view.discardConsent) Surface(Modifier.fillMaxWidth().testTag("ssh_files_discard_confirmation"),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius)) {
                                    Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                                        Text(stringResource(R.string.ssh_files_editor_discard_heading), style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.semantics { heading() })
                                        Text(stringResource(R.string.ssh_files_editor_discard_body))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                            FilesButton(R.string.ssh_files_editor_discard, "ssh_files_discard_confirm", !view.busy) { onConfirmCloseEditor() }
                                            FilesButton(R.string.ssh_files_cancel, "ssh_files_discard_cancel", true) { onCancelCloseEditor() }
                                        }
                                    }
                                }
                            }
                        }
                    } }
                    if (state.transfers.isNotEmpty()) item(key = "transfers") {
                        Column(Modifier.fillMaxWidth().testTag("ssh_files_transfers"),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(stringResource(R.string.ssh_files_transfers_heading), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.ssh_files_transfers_scope))
                            state.transfers.forEach { record ->
                                Text(stringResource(transferLabel(record.state), record.path),
                                    modifier = Modifier.testTag("ssh_files_transfer_${record.id}"))
                            }
                        }
                    }
                    item(key = "registration") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            val candidate = state.registrationCandidate
                            FilesButton(R.string.ssh_location_register, "ssh_location_register", canBrowse && candidate != null && !confirming) {
                                locationLabel = TextFieldValue(candidate?.profile?.profile?.label.orEmpty())
                                locationLabelRejected = false
                                confirming = true
                            }
                            if (confirming && candidate != null) Surface(Modifier.fillMaxWidth().testTag("ssh_location_confirmation"),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius)) {
                                Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                                    Text(stringResource(R.string.ssh_location_confirm_heading), style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.semantics { heading() })
                                    Text(stringResource(R.string.ssh_location_private_scope))
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                            Text(peerText(candidate.profile.profile))
                                            Text(stringResource(R.string.ssh_location_account, candidate.accountName))
                                            Text(stringResource(R.string.ssh_location_root, candidate.navigationRoot))
                                            Text((candidate.acceptedTrust.status as io.github.baitian6641.cloudy.domain.SshTrustStatus.Accepted).key.fingerprint)
                                        }
                                    }
                                    val labelInvalid = locationLabelRejected || WindowState.nameProblem(locationLabel.text) != null
                                    OutlinedTextField(locationLabel, {
                                        locationLabelRejected = it.text.length > 160
                                        if (!locationLabelRejected) locationLabel = it
                                    },
                                        label = { Text(stringResource(R.string.ssh_location_label)) },
                                        enabled = canBrowse, singleLine = true,
                                        isError = labelInvalid,
                                        supportingText = { if (labelInvalid) Text(stringResource(R.string.ssh_location_label_invalid)) },
                                        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                        modifier = Modifier.fillMaxWidth().testTag("ssh_location_label"))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                        FilesButton(R.string.ssh_location_confirm, "ssh_location_confirm",
                                            canBrowse && !labelInvalid) {
                                            confirming = false
                                            onRegister(candidate, locationLabel.text)
                                        }
                                        FilesButton(R.string.ssh_location_cancel, "ssh_location_cancel", true) {
                                            confirming = false
                                            onCancelRegistration(candidate)
                                        }
                                    }
                                }
                            }
                            if (candidate == null && !state.registrationPending && state.registrationSaved == null)
                                Text(stringResource(R.string.ssh_location_refresh_required))
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
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                    when (entry.kind) {
                                        SshFileKind.DIRECTORY -> FilesButton(R.string.ssh_files_open, "ssh_files_open_$index", canBrowse) {
                                            onList(remoteChild(state.path, entry.name))
                                        }
                                        SshFileKind.REGULAR -> {
                                            FilesButton(R.string.ssh_files_read, "ssh_files_read_$index", canBrowse && state.editor == null) { onRead(entry) }
                                            FilesButton(R.string.ssh_files_edit, "ssh_files_edit_$index", canMutate) { onEdit(entry) }
                                        }
                                        SshFileKind.OTHER -> Text(stringResource(R.string.ssh_files_other_unsupported))
                                    }
                                    FilesButton(R.string.ssh_files_rename, "ssh_files_rename_$index", canMutate) {
                                        renamingName = entry.name
                                        renameDraft = TextFieldValue(entry.name)
                                        renameInvalid = false
                                    }
                                    FilesButton(R.string.ssh_files_delete, "ssh_files_delete_$index", canMutate) { onRequestDelete(entry) }
                                }
                                if (renamingName == entry.name) {
                                    OutlinedTextField(renameDraft, { value ->
                                        renameInvalid = value.text.isEmpty() || value.text.length > SshFiles.MAX_NAME_UNITS ||
                                            '/' in value.text || '\\' in value.text || value.text == "." || value.text == ".."
                                        if (value.text.length <= SshFiles.MAX_NAME_UNITS) renameDraft = value
                                    }, enabled = canMutate, singleLine = true, isError = renameInvalid,
                                        label = { Text(stringResource(R.string.ssh_files_new_name)) },
                                        supportingText = { if (renameInvalid) Text(stringResource(R.string.ssh_files_name_invalid)) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                        modifier = Modifier.fillMaxWidth().testTag("ssh_files_rename_name_$index"))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                        FilesButton(R.string.ssh_files_rename_confirm, "ssh_files_rename_confirm_$index",
                                            canMutate && renameDraft.text.isNotEmpty() && !renameInvalid) {
                                            renamingName = null
                                            onRename(entry, renameDraft.text)
                                        }
                                        FilesButton(R.string.ssh_files_cancel, "ssh_files_rename_cancel_$index", true) {
                                            renamingName = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                state.mutationConsent?.let { consent -> item(key = "mutation-consent") {
                    Surface(Modifier.fillMaxWidth().testTag("ssh_files_mutation_confirmation"),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius)) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                            Text(stringResource(R.string.ssh_files_confirmation_heading), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                            SelectionContainer { Text(consent.entry.name) }
                            when (consent.kind) {
                                SshFilesMutationKind.OVERWRITE_RENAME -> {
                                    Text(stringResource(R.string.ssh_files_overwrite_confirm,
                                        consent.entry.name, consent.newName.orEmpty()))
                                    Text(stringResource(R.string.ssh_files_overwrite_note))
                                }
                                SshFilesMutationKind.DELETE_FILE -> Text(stringResource(R.string.ssh_files_delete_file_confirm,
                                    consent.entry.name))
                                SshFilesMutationKind.DELETE_DIRECTORY -> {
                                    Text(stringResource(R.string.ssh_files_delete_directory_confirm, consent.entry.name))
                                    Text(stringResource(R.string.ssh_files_delete_directory_note))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                FilesButton(if (consent.kind == SshFilesMutationKind.OVERWRITE_RENAME)
                                    R.string.ssh_files_replace_action else R.string.ssh_files_delete_action,
                                    "ssh_files_mutation_confirm", canBrowse) { onConfirmMutation() }
                                FilesButton(R.string.ssh_files_cancel, "ssh_files_mutation_cancel", true) { onCancelMutation() }
                            }
                        }
                    }
                } }
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
    SshFilesPhase.MUTATING -> R.string.ssh_files_mutating
    SshFilesPhase.RETIRING -> R.string.ssh_files_retiring
    SshFilesPhase.FAILED -> R.string.ssh_files_failed
}
private fun transferLabel(state: FilesTransferState): Int = when (state) {
    FilesTransferState.STAGED -> R.string.ssh_files_transfer_staged
    FilesTransferState.COMMITTING -> R.string.ssh_files_transfer_committing
    FilesTransferState.COMMITTED -> R.string.ssh_files_transfer_committed
    FilesTransferState.CONFLICT -> R.string.ssh_files_transfer_conflict
    FilesTransferState.FAILED -> R.string.ssh_files_transfer_failed
    FilesTransferState.UNCERTAIN -> R.string.ssh_files_transfer_uncertain
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
