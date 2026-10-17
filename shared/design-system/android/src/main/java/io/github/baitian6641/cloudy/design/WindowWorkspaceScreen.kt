package io.github.baitian6641.cloudy.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun WindowWorkspaceScreen(
    appName: String,
    windowId: String,
    currentName: String,
    notice: String?,
    onRename: (String) -> String?,
    onNewWindow: () -> Unit,
    onClose: () -> Unit,
    onCredentials: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onSshTrust: (() -> Unit)? = null,
    onDisplaySettings: (() -> Unit)? = null,
    onSshAuthenticate: (() -> Unit)? = null,
    onSshFiles: (() -> Unit)? = null,
    onFilesLocations: (() -> Unit)? = null,
    isDesktop: Boolean = false,
    onVnc: (() -> Unit)? = null,
) {
    // Inputs reset live composition, but rememberSaveable does not validate the
    // prior inputs on restoration. The envelope independently binds the accepted ID.
    val saver = remember(windowId) {
        Saver<WindowDraft, Any>(
            save = { WindowDraftCodec.save(windowId, it) },
            restore = { WindowDraftCodec.restore(windowId, it) },
        )
    }
    var retained by rememberSaveable(windowId, stateSaver = saver) {
        mutableStateOf(WindowDraftCodec.initial(currentName))
    }
    // Selection is durable; IME composition and localized operation outcomes are
    // transient. Keep TextFieldValue intact during live edits, not across restore.
    var draft by remember(windowId) {
        mutableStateOf(TextFieldValue(retained.text,
            TextRange(retained.selectionStart, retained.selectionEnd)))
    }
    var renameError by remember(windowId) { mutableStateOf<String?>(null) }
    val error = if (retained.tooLong) stringResource(R.string.cloudy_name_too_long) else renameError
    var showUpcoming by rememberSaveable(windowId) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                // Preserve system captions and native IME; overlapping insets count once.
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
        ) {
            // Reflow earlier for scaled text: narrow tiled desktop windows keep every tool.
            val sideNavigation = maxWidth >= 960.dp && LocalDensity.current.fontScale <= 1.5f
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
            ) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .padding(CloudyControlMetrics.spaceLarge),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    ) {
                        Text(appName, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Committed context stays separate from the editable window-name draft.
                        Text(currentName, style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() })
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                        ) {
                            WorkspaceAction(stringResource(R.string.cloudy_new_window), onNewWindow)
                            WorkspaceAction(stringResource(R.string.cloudy_close_window), onClose)
                        }
                    }
                }
                if (notice != null) {
                    Text(notice, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = CloudyControlMetrics.spaceLarge))
                }
                val tools: @Composable () -> Unit = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    ) {
                        WorkspaceHeading(stringResource(R.string.cloudy_workspace_tools))
                        Text(stringResource(R.string.cloudy_workspace_tools_scope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                        ) {
                            if (onProfiles != null) WorkspaceAction(stringResource(R.string.cloudy_connection_profiles), onProfiles)
                            if (onSshFiles != null) WorkspaceAction(stringResource(R.string.cloudy_ssh_files), onSshFiles)
                            if (onFilesLocations != null) WorkspaceAction(stringResource(R.string.cloudy_files_locations), onFilesLocations)
                            if (onSshTrust != null) WorkspaceAction(stringResource(R.string.cloudy_ssh_host_keys), onSshTrust)
                            if (onSshAuthenticate != null) WorkspaceAction(stringResource(R.string.cloudy_ssh_authenticate), onSshAuthenticate)
                            if (onCredentials != null) WorkspaceAction(stringResource(R.string.cloudy_saved_credentials), onCredentials)
                            if (onDisplaySettings != null) WorkspaceAction(stringResource(R.string.cloudy_display_settings), onDisplaySettings)
                        }
                    }
                }
                val workspace: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceLarge)) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Column(
                                Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
                            ) {
                                WorkspaceHeading(stringResource(R.string.cloudy_workspace_overview))
                                Text(stringResource(when {
                                    !isDesktop -> R.string.cloudy_console_scope
                                    onVnc != null -> R.string.cloudy_desktop_scope
                                    else -> R.string.cloudy_desktop_files_scope
                                }),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.cloudy_workspace_local_state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isDesktop && onVnc != null) {
                                    Button(onClick = onVnc,
                                        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                                        Text(stringResource(R.string.cloudy_open_vnc))
                                    }
                                    Text(stringResource(R.string.cloudy_vnc_entry_scope), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        // The expander changes only local disclosure. Missing features remain
                        // noninteractive text and never imply a session or backend observation.
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(
                                Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
                            ) {
                                WorkspaceHeading(stringResource(R.string.cloudy_workspace_reserved))
                                OutlinedButton(
                                    onClick = { showUpcoming = !showUpcoming },
                                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight),
                                ) {
                                    Text(stringResource(if (showUpcoming) R.string.cloudy_hide_upcoming else R.string.cloudy_show_upcoming))
                                }
                                if (showUpcoming) {
                                    if (isDesktop) {
                                        if (onVnc == null) ReservedWorkspaceRegion(R.string.cloudy_gap_vnc, R.string.cloudy_gap_vnc_detail)
                                        ReservedWorkspaceRegion(R.string.cloudy_gap_rdp, R.string.cloudy_gap_rdp_detail)
                                        ReservedWorkspaceRegion(R.string.cloudy_gap_stream, R.string.cloudy_gap_stream_detail)
                                        ReservedWorkspaceRegion(R.string.cloudy_gap_usb, R.string.cloudy_gap_usb_detail)
                                    } else {
                                        ReservedWorkspaceRegion(R.string.cloudy_gap_terminal, R.string.cloudy_gap_terminal_detail)
                                        ReservedWorkspaceRegion(R.string.cloudy_gap_sessions, R.string.cloudy_gap_sessions_detail)
                                        ReservedWorkspaceRegion(R.string.cloudy_gap_x11, R.string.cloudy_gap_x11_detail)
                                    }
                                    ReservedWorkspaceRegion(R.string.cloudy_gap_transfers, R.string.cloudy_gap_transfers_detail)
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            WorkspaceHeading(stringResource(R.string.cloudy_workspace_window))
                            OutlinedTextField(
                                value = draft,
                                singleLine = true,
                                onValueChange = { next ->
                                    // Reject the whole oversized edit; never truncate a paste or
                                    // split a UTF-16 surrogate pair. The retained saver is bounded.
                                    val tooLong = next.text.length > WindowDraftCodec.MaximumUnits
                                    if (!tooLong) {
                                        draft = next
                                        retained = WindowDraft(next.text, next.selection.start, next.selection.end)
                                    } else {
                                        retained = retained.copy(tooLong = true)
                                    }
                                    renameError = null
                                },
                                label = { Text(stringResource(R.string.cloudy_window_name)) },
                                isError = error != null,
                                supportingText = { if (error != null) Text(error) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                            )
                            FlowRow {
                                Button(onClick = {
                                    renameError = onRename(draft.text)
                                }, enabled = !retained.tooLong,
                                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                                    Text(stringResource(R.string.cloudy_save_name))
                                }
                            }
                        }
                    }
                }
                if (sideNavigation) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = CloudyControlMetrics.spaceLarge),
                        horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceLarge),
                    ) {
                        Column(Modifier.width(224.dp)) { tools() }
                        Column(Modifier.weight(1f)) { workspace() }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = CloudyControlMetrics.spaceLarge),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceLarge),
                    ) {
                        tools()
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        workspace()
                    }
                }
                Text(stringResource(R.string.cloudy_workspace_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge))
            }
        }
    }
}

@Composable
private fun WorkspaceHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun WorkspaceAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
        Text(label)
    }
}

@Composable
private fun ReservedWorkspaceRegion(title: Int, detail: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.cloudy_not_integrated), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium)
    }
}
