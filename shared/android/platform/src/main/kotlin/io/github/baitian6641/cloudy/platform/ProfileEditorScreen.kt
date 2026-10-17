package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner

/** Local-profile presentation. The Activity owns drafts, validation, confirmations and all I/O. */
@Composable
internal fun ProfileEditorScreen(
    appName: String,
    owner: WindowOwner,
    catalog: ConnectionProfileCatalog?,
    draft: ProfileEditorDraft?,
    busy: Boolean,
    message: String?,
    outcomeUnknown: Boolean,
    reviewed: ConnectionProfileEntry?,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ConnectionProfile) -> Unit,
    onDraftChange: (ProfileEditorDraft) -> Unit,
    onSave: () -> Unit,
    onCloseEditor: () -> Unit,
    onDelete: () -> Unit,
    onReview: () -> Unit,
    onReapply: () -> Unit,
    onReload: () -> Unit,
    onFinish: () -> Unit,
) {
    val listState = rememberLazyListState()
    val editorId = draft?.editorId
    var revealedEditorId by rememberSaveable { mutableStateOf(editorId) }
    LaunchedEffect(editorId) {
        if (editorId != revealedEditorId) {
            if (editorId != null) {
                // Reveal an accepted new/replaced editor, including a normalized saved/reloaded
                // draft. Field/selection changes retain its ID and must not steal the viewport.
                val editorIndex = 2 + (if (message != null) 1 else 0) + (if (outcomeUnknown) 1 else 0)
                listState.scrollToItem(editorIndex)
            }
            revealedEditorId = editorId
        }
    }
    // Both the list and reveal marker survive recreation. A restored draft is not a new
    // navigation request, and revealing an editor never requests focus or authorizes a write.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .testTag("profile_screen"),
            contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
        ) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text(stringResource(R.string.profile_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(appName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.profile_local_only), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (message != null) {
                item(key = "message") {
                    Text(message, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("profile_message").semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (outcomeUnknown) {
                item(key = "unknown") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_unknown), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(CloudyControlMetrics.spaceMedium).testTag("profile_unknown"))
                    }
                }
            }
            item(key = "actions") {
                ProfileActions {
                    ProfileButton(R.string.profile_refresh, "profile_refresh", !busy, onRefresh)
                    ProfileButton(R.string.profile_new, "profile_new", !busy && !outcomeUnknown && catalog != null, onNew)
                    ProfileButton(R.string.profile_finish, "profile_finish", !busy, onFinish)
                }
            }
            if (draft != null) {
                item(key = "editor-${draft.editorId}") {
                    ProfileDraftEditor(owner, draft, busy, outcomeUnknown, reviewed, onDraftChange,
                        onSave, onCloseEditor, onDelete, onReview, onReapply, onReload)
                }
            }
            item(key = "saved-heading") {
                Text(stringResource(R.string.profile_saved_profiles), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            when {
                catalog == null -> item(key = "unavailable") {
                    Text(stringResource(if (busy) R.string.profile_loading else R.string.profile_catalog_unavailable),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("profile_catalog_unavailable"))
                }
                catalog.profiles.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.profile_no_profiles), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("profile_empty"))
                }
                else -> items(catalog.profiles, key = { it.id }) { profile ->
                    SavedProfileRow(profile, busy, onEdit)
                }
            }
        }
    }
}

@Composable
private fun ProfileDraftEditor(
    owner: WindowOwner,
    draft: ProfileEditorDraft,
    busy: Boolean,
    outcomeUnknown: Boolean,
    reviewed: ConnectionProfileEntry?,
    onDraftChange: (ProfileEditorDraft) -> Unit,
    onSave: () -> Unit,
    onCloseEditor: () -> Unit,
    onDelete: () -> Unit,
    onReview: () -> Unit,
    onReapply: () -> Unit,
    onReload: () -> Unit,
) {
    val editable = !busy && !outcomeUnknown
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("profile_editor")) {
        Column(Modifier.padding(CloudyControlMetrics.spaceMedium), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
            Text(stringResource(if (draft.version == null) R.string.profile_editor_new else R.string.profile_editor_edit),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            // Preserve TextFieldValue selection and live IME composition. The controller rejects
            // oversized edits as a whole and owns their error message; this view never truncates.
            OutlinedTextField(value = draft.label, onValueChange = { onDraftChange(draft.copy(label = it)) },
                label = { Text(stringResource(R.string.profile_name)) }, enabled = editable, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                modifier = Modifier.fillMaxWidth().testTag("profile_label"))
            Text(stringResource(R.string.profile_protocol), style = MaterialTheme.typography.labelLarge)
            ProfileActions {
                val protocols = if (owner == WindowOwner.CONSOLE) listOf(ConnectionProtocol.SSH) else ConnectionProtocol.entries
                for (protocol in protocols) {
                    FilterChip(selected = draft.protocol == protocol, enabled = editable,
                        onClick = { onDraftChange(draft.copy(protocol = protocol)) },
                        label = { Text(protocolLabel(protocol)) },
                        shape = RoundedCornerShape(CloudyControlMetrics.chipRadius),
                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag("profile_protocol_${protocol.wireId}"))
                }
            }
            OutlinedTextField(value = draft.host, onValueChange = { onDraftChange(draft.copy(host = it)) },
                label = { Text(stringResource(R.string.profile_host)) }, enabled = editable, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                modifier = Modifier.fillMaxWidth().testTag("profile_host"))
            OutlinedTextField(value = draft.port, onValueChange = { onDraftChange(draft.copy(port = it)) },
                label = { Text(stringResource(R.string.profile_port)) }, enabled = editable, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                modifier = Modifier.fillMaxWidth().testTag("profile_port"))
            OutlinedTextField(value = draft.tags, onValueChange = { onDraftChange(draft.copy(tags = it)) },
                label = { Text(stringResource(R.string.profile_tags)) }, enabled = editable, singleLine = true,
                supportingText = { Text(stringResource(R.string.profile_tags_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                modifier = Modifier.fillMaxWidth().testTag("profile_tags"))
            Row(Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                .selectable(selected = draft.favorite, enabled = editable, role = Role.Checkbox,
                    onClick = { onDraftChange(draft.copy(favorite = !draft.favorite)) })
                .testTag("profile_favorite")
                .padding(horizontal = CloudyControlMetrics.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Checkbox(checked = draft.favorite, onCheckedChange = null, enabled = editable)
                Text(stringResource(R.string.profile_favorite), modifier = Modifier.weight(1f))
            }
            ProfileActions {
                ProfileButton(R.string.profile_save, "profile_save", editable, onSave, primary = true)
                ProfileButton(R.string.profile_close_editor, "profile_close_editor", !busy, onCloseEditor)
                if (draft.version != null) {
                    ProfileButton(R.string.profile_delete, "profile_delete", editable, onDelete)
                    ProfileButton(R.string.profile_review, "profile_review", !busy, onReview)
                    ProfileButton(R.string.profile_reload, "profile_reload", !busy, onReload)
                }
            }
            if (reviewed != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CloudyControlMetrics.spaceMedium), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.profile_current_saved), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        ProfileDetails(reviewed.profile)
                        Text(stringResource(R.string.profile_draft_preserved), style = MaterialTheme.typography.bodyMedium)
                        ProfileButton(R.string.profile_reapply, "profile_reapply", !busy, onReapply)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedProfileRow(profile: ConnectionProfile, busy: Boolean, onEdit: (ConnectionProfile) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("profile_row_${profile.id}")) {
        Column(Modifier.padding(CloudyControlMetrics.spaceMedium), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
            ProfileDetails(profile)
            val description = stringResource(R.string.profile_edit_named, profile.label)
            // An active draft never disables row selection; the controller requests discard
            // confirmation before changing editor identity or losing unsaved native field state.
            OutlinedButton(onClick = { onEdit(profile) }, enabled = !busy,
                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).semantics { contentDescription = description }
                    .testTag("profile_edit_${profile.id}")) {
                Text(stringResource(R.string.profile_edit))
            }
        }
    }
}

@Composable
private fun ProfileDetails(profile: ConnectionProfile) {
    Text(profile.label, style = MaterialTheme.typography.bodyLarge)
    if (profile.favorite) {
        Text(stringResource(R.string.profile_favorite_saved), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (profile.tags.isNotEmpty()) {
        Text(profile.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val host = if (':' in profile.host && !profile.host.startsWith('[')) "[${profile.host}]" else profile.host
    Text(stringResource(R.string.profile_endpoint, protocolLabel(profile.protocol), host, profile.port),
        style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.profile_revision, profile.revision), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun protocolLabel(protocol: ConnectionProtocol): String = stringResource(when (protocol) {
    ConnectionProtocol.SSH -> R.string.profile_protocol_ssh
    ConnectionProtocol.RDP -> R.string.profile_protocol_rdp
    ConnectionProtocol.VNC -> R.string.profile_protocol_vnc
    ConnectionProtocol.STREAM -> R.string.profile_protocol_stream
})

@Composable
private fun ProfileActions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) { content() }
}

@Composable
private fun ProfileButton(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit, primary: Boolean = false) {
    // Control geometry comes from the canonical token facade; typography comes from
    // the owning app theme.
    val modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)
    val shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius)
    if (primary) {
        Button(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) { Text(stringResource(label)) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) { Text(stringResource(label)) }
    }
}
