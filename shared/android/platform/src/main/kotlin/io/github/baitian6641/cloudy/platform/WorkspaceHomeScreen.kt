package io.github.baitian6641.cloudy.platform

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.design.WindowDraft
import io.github.baitian6641.cloudy.design.WindowDraftCodec
import io.github.baitian6641.cloudy.design.R as designR
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.text.DateFormat
import java.util.Date

/** Callback bundle: every action is supplied by the owning window activity; none runs here. */
internal class WorkspaceHomeActions(
    val onSelectSection: (HomeSelection) -> Unit = {},
    val onSelectHost: (String?) -> Unit = {},
    val onToggleFavorite: (HomeRow) -> Unit = {},
    val onConnect: (HomeRow) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBrowseFiles: () -> Unit = {},
    val onAddHost: () -> Unit = {},
    val onNewWindow: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onRename: (String) -> String? = { null },
    val onProfiles: () -> Unit = {},
    val onSshTrust: () -> Unit = {},
    val onSshAuthenticate: () -> Unit = {},
    val onFilesLocations: () -> Unit = {},
    val onCredentials: () -> Unit = {},
    val onDisplaySettings: () -> Unit = {},
)

private val selectionSaver = Saver<HomeSelection, String>(
    save = { selection ->
        when (selection) {
            HomeSelection.Favorites -> "favorites"
            HomeSelection.Recents -> "recents"
            HomeSelection.All -> "all"
            is HomeSelection.Tag -> "tag:${selection.tag}"
        }
    },
    restore = { value ->
        when {
            value == "favorites" -> HomeSelection.Favorites
            value == "recents" -> HomeSelection.Recents
            value == "all" -> HomeSelection.All
            value.startsWith("tag:") && value.length > 4 -> HomeSelection.Tag(value.substring(4))
            else -> HomeSelection.All
        }
    },
)

/** The shared Termius-style workspace home for both apps; every state is rendered honestly. */
@Composable
internal fun WorkspaceHomeScreen(
    appName: String,
    owner: WindowOwner,
    windowId: String,
    currentName: String,
    notice: String?,
    state: WorkspaceHomeState,
    actions: WorkspaceHomeActions,
) {
    var selection by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<HomeSelection>(HomeSelection.All) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showPlanned by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
        ) {
            // Reflow earlier for scaled text: narrow tiled desktop windows keep every tool.
            val sideNavigation = maxWidth >= 960.dp && LocalDensity.current.fontScale <= 1.5f
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
            ) {
                HomeHeader(appName, currentName, actions)
                if (notice != null) {
                    Text(notice, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = CloudyControlMetrics.spaceLarge))
                }
                state.message?.let { message ->
                    Text(homeMessageText(message), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("home_message")
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .padding(horizontal = CloudyControlMetrics.spaceLarge))
                }
                when {
                    state.loading -> Text(stringResource(R.string.home_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = CloudyControlMetrics.spaceLarge))
                    state.storeUnavailable -> HomeUnavailable(actions)
                    else -> {
                        val sectionRows = WorkspaceHomeOrganizer.filter(state.rows, selection)
                        val selected = WorkspaceHomeOrganizer.resolve(state.rows, selectedId)
                        if (sideNavigation) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = CloudyControlMetrics.spaceLarge),
                                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceLarge),
                            ) {
                                Column(Modifier.width(224.dp)) {
                                    HomeNavigation(state.rows, selection, showAdvanced, showPlanned, owner, actions,
                                        windowId, currentName,
                                        onSelection = { selection = it; actions.onSelectSection(it) },
                                        onToggleAdvanced = { showAdvanced = !showAdvanced },
                                        onTogglePlanned = { showPlanned = !showPlanned })
                                }
                                Column(Modifier.weight(1f)) {
                                    HomeMain(state.rows, sectionRows, selected, owner, actions,
                                        onSelect = { id ->
                                            selectedId = if (selectedId == id) null else id
                                            actions.onSelectHost(selectedId)
                                        })
                                }
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = CloudyControlMetrics.spaceLarge),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceLarge),
                            ) {
                                HomeNavigation(state.rows, selection, showAdvanced, showPlanned, owner, actions,
                                    windowId, currentName,
                                    onSelection = { selection = it; actions.onSelectSection(it) },
                                    onToggleAdvanced = { showAdvanced = !showAdvanced },
                                    onTogglePlanned = { showPlanned = !showPlanned })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                HomeMain(state.rows, sectionRows, selected, owner, actions,
                                    onSelect = { id ->
                                        selectedId = if (selectedId == id) null else id
                                        actions.onSelectHost(selectedId)
                                    })
                            }
                        }
                    }
                }
                Text(stringResource(R.string.home_status), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge))
            }
        }
    }
}

@Composable
private fun HomeHeader(appName: String, currentName: String, actions: WorkspaceHomeActions) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(CloudyControlMetrics.spaceLarge),
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
                HomeAction(stringResource(designR.string.cloudy_new_window), actions.onNewWindow)
                HomeAction(stringResource(designR.string.cloudy_close_window), actions.onClose)
            }
        }
    }
}

@Composable
private fun HomeNavigation(
    rows: List<HomeRow>,
    selection: HomeSelection,
    showAdvanced: Boolean,
    showPlanned: Boolean,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    windowId: String,
    currentName: String,
    onSelection: (HomeSelection) -> Unit,
    onToggleAdvanced: () -> Unit,
    onTogglePlanned: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        Text(stringResource(R.string.home_hosts_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            HomeNavEntry(stringResource(R.string.home_favorites),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.Favorites),
                selection == HomeSelection.Favorites, "home_nav_favorites") { onSelection(HomeSelection.Favorites) }
            HomeNavEntry(stringResource(R.string.home_recents),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.Recents),
                selection == HomeSelection.Recents, "home_nav_recents") { onSelection(HomeSelection.Recents) }
            HomeNavEntry(stringResource(R.string.home_all_hosts),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.All),
                selection == HomeSelection.All, "home_nav_all") { onSelection(HomeSelection.All) }
        }
        val tags = WorkspaceHomeOrganizer.tags(rows)
        if (tags.isNotEmpty()) {
            Text(stringResource(R.string.home_groups), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                tags.forEach { (tag, count) ->
                    HomeNavEntry(tag, count, selection == HomeSelection.Tag(tag),
                        "home_nav_tag") { onSelection(HomeSelection.Tag(tag)) }
                }
            }
        }
        TextButton(onClick = onToggleAdvanced, modifier = Modifier.testTag("home_advanced")) {
            Text(stringResource(if (showAdvanced) R.string.home_advanced_hide else R.string.home_advanced_show))
        }
        if (showAdvanced) {
            Text(stringResource(R.string.home_advanced_scope), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            ) {
                HomeAction(stringResource(designR.string.cloudy_connection_profiles), actions.onProfiles)
                HomeAction(stringResource(designR.string.cloudy_ssh_files), actions.onBrowseFiles)
                HomeAction(stringResource(designR.string.cloudy_files_locations), actions.onFilesLocations)
                HomeAction(stringResource(designR.string.cloudy_ssh_host_keys), actions.onSshTrust)
                HomeAction(stringResource(designR.string.cloudy_ssh_authenticate), actions.onSshAuthenticate)
                HomeAction(stringResource(designR.string.cloudy_saved_credentials), actions.onCredentials)
                HomeAction(stringResource(designR.string.cloudy_display_settings), actions.onDisplaySettings)
            }
            WindowNameEditor(windowId, currentName, actions.onRename)
        }
        TextButton(onClick = onTogglePlanned, modifier = Modifier.testTag("home_planned")) {
            Text(stringResource(if (showPlanned) designR.string.cloudy_hide_upcoming else designR.string.cloudy_show_upcoming))
        }
        if (showPlanned) {
            PlannedCapabilities(owner)
        }
    }
}

@Composable
private fun HomeNavEntry(label: String, count: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.rowMinHeight)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .testTag(tag)
                .padding(horizontal = CloudyControlMetrics.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeMain(
    allRows: List<HomeRow>,
    sectionRows: List<HomeRow>,
    selected: HomeRow?,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
        if (allRows.isEmpty()) {
            HomeEmpty(actions)
        } else if (sectionRows.isEmpty()) {
            Text(stringResource(R.string.home_section_empty), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                sectionRows.forEach { row -> HomeHostRow(row, selected?.profile?.id == row.profile.id,
                    onSelect = { onSelect(row.profile.id) }, onToggleFavorite = { actions.onToggleFavorite(row) }) }
            }
        }
        selected?.let { HomeDetail(it, owner, actions) }
    }
}

@Composable
private fun HomeHostRow(row: HomeRow, isSelected: Boolean, onSelect: () -> Unit, onToggleFavorite: () -> Unit) {
    val profile = row.profile
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.rowMinHeight)
                .selectable(selected = isSelected, role = Role.Button, onClick = onSelect)
                .testTag("home_row")
                .padding(CloudyControlMetrics.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
        ) {
            val favoriteLabel = favoriteDescription(profile.favorite)
            TextButton(onClick = onToggleFavorite,
                modifier = Modifier.testTag("home_favorite")
                    .semantics { contentDescription = favoriteLabel }) {
                Text(text = if (profile.favorite) "★" else "☆",
                    style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.label, style = MaterialTheme.typography.bodyLarge)
                    ProtocolBadge(profile.protocol)
                }
                Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (profile.tags.isNotEmpty()) {
                    Text(profile.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(lastUsedText(profile.lastUsedAt), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeDetail(row: HomeRow, owner: WindowOwner, actions: WorkspaceHomeActions) {
    val profile = row.profile
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius)) {
        Column(
            Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge)
                .testTag("home_detail"),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Text(profile.label, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                ProtocolBadge(profile.protocol)
            }
            Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace)
            Text(trustText(row), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(credentialText(row), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lastUsedText(profile.lastUsedAt), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            ) {
                Button(onClick = { actions.onConnect(row) },
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)
                        .testTag("home_connect")) {
                    Text(stringResource(connectLabel(owner, profile.protocol)))
                }
                if (profile.protocol == ConnectionProtocol.SSH) {
                    HomeAction(stringResource(designR.string.cloudy_ssh_files), actions.onBrowseFiles)
                }
                HomeAction(stringResource(R.string.home_manage_profiles), actions.onProfiles)
            }
        }
    }
}

@Composable
private fun HomeEmpty(actions: WorkspaceHomeActions) {
    Column(
        Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
    ) {
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = actions.onAddHost,
            shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
            modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)
                .testTag("home_add_host")) {
            Text(stringResource(R.string.home_add_host))
        }
    }
}

@Composable
private fun HomeUnavailable(actions: WorkspaceHomeActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = CloudyControlMetrics.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
    ) {
        Text(stringResource(R.string.home_store_unavailable), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error)
        // The retry affordance is a real reload through the owning stores.
        OutlinedButton(onClick = actions.onRetry,
            shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
            modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)
                .testTag("home_retry")) {
            Text(stringResource(R.string.home_retry))
        }
    }
}

@Composable
private fun WindowNameEditor(windowId: String, currentName: String, onRename: (String) -> String?) {
    // The saver survives recreation; the envelope independently binds the accepted window ID.
    val saver = remember(windowId) {
        Saver<WindowDraft, Any>(
            save = { WindowDraftCodec.save(windowId, it) },
            restore = { WindowDraftCodec.restore(windowId, it) },
        )
    }
    var retained by rememberSaveable(windowId, stateSaver = saver) {
        mutableStateOf(WindowDraftCodec.initial(currentName))
    }
    var draft by remember(windowId) {
        mutableStateOf(TextFieldValue(retained.text, TextRange(retained.selectionStart, retained.selectionEnd)))
    }
    var renameError by remember(windowId) { mutableStateOf<String?>(null) }
    val error = if (retained.tooLong) stringResource(designR.string.cloudy_name_too_long) else renameError
    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        Text(stringResource(designR.string.cloudy_workspace_window), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = draft,
            singleLine = true,
            onValueChange = { next ->
                // Reject the whole oversized edit; never truncate a paste or split a surrogate pair.
                val tooLong = next.text.length > WindowDraftCodec.MaximumUnits
                if (!tooLong) {
                    draft = next
                    retained = WindowDraft(next.text, next.selection.start, next.selection.end)
                } else {
                    retained = retained.copy(tooLong = true)
                }
                renameError = null
            },
            label = { Text(stringResource(designR.string.cloudy_window_name)) },
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
        )
        FlowRow {
            Button(onClick = { renameError = onRename(draft.text) }, enabled = !retained.tooLong,
                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                Text(stringResource(designR.string.cloudy_save_name))
            }
        }
    }
}

@Composable
private fun PlannedCapabilities(owner: WindowOwner) {
    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        if (owner == WindowOwner.CONSOLE) {
            PlannedRow(designR.string.cloudy_gap_terminal, designR.string.cloudy_gap_terminal_detail)
            PlannedRow(designR.string.cloudy_gap_sessions, designR.string.cloudy_gap_sessions_detail)
            PlannedRow(designR.string.cloudy_gap_x11, designR.string.cloudy_gap_x11_detail)
        } else {
            PlannedRow(designR.string.cloudy_gap_rdp, designR.string.cloudy_gap_rdp_detail)
            PlannedRow(designR.string.cloudy_gap_stream, designR.string.cloudy_gap_stream_detail)
            PlannedRow(designR.string.cloudy_gap_usb, designR.string.cloudy_gap_usb_detail)
        }
        PlannedRow(designR.string.cloudy_gap_transfers, designR.string.cloudy_gap_transfers_detail)
    }
}

@Composable
private fun PlannedRow(title: Int, detail: Int) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(detail), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
        Text(label)
    }
}

@Composable
private fun ProtocolBadge(protocol: ConnectionProtocol) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(CloudyControlMetrics.chipRadius)) {
        Text(protocolLabel(protocol), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = CloudyControlMetrics.spaceSmall, vertical = 2.dp))
    }
}

private fun protocolLabel(protocol: ConnectionProtocol): String = when (protocol) {
    ConnectionProtocol.SSH -> "SSH"
    ConnectionProtocol.RDP -> "RDP"
    ConnectionProtocol.VNC -> "VNC"
    ConnectionProtocol.STREAM -> "Stream"
}

private fun connectLabel(owner: WindowOwner, protocol: ConnectionProtocol): Int = when (protocol) {
    ConnectionProtocol.SSH -> if (owner == WindowOwner.DESKTOP) designR.string.cloudy_ssh_files else R.string.home_connect
    else -> R.string.home_connect
}

@Composable
private fun favoriteDescription(favorite: Boolean): String =
    stringResource(if (favorite) R.string.home_favorite_clear else R.string.home_favorite_mark)

@Composable
private fun trustText(row: HomeRow): String {
    if (row.profile.protocol != ConnectionProtocol.SSH) return stringResource(R.string.home_trust_session)
    return stringResource(when (row.trust) {
        HomeTrust.ACCEPTED -> R.string.home_trust_accepted
        HomeTrust.REVOKED -> R.string.home_trust_revoked
        HomeTrust.NONE -> R.string.home_trust_none
        HomeTrust.UNKNOWN -> R.string.home_trust_unknown
    })
}

@Composable
private fun credentialText(row: HomeRow): String = stringResource(when (row.hasCredential) {
    true -> R.string.home_credential_saved
    false -> R.string.home_credential_none
    null -> R.string.home_credential_unknown
})

@Composable
private fun lastUsedText(lastUsedAt: Long): String = if (lastUsedAt > 0) {
    stringResource(R.string.home_last_used,
        remember(lastUsedAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(lastUsedAt * 1000)) })
} else {
    stringResource(R.string.home_last_used_never)
}

@Composable
private fun homeMessageText(message: HomeMessage): String = stringResource(when (message) {
    HomeMessage.TERMINAL_NOT_INTEGRATED -> R.string.home_msg_terminal_pending
    HomeMessage.RDP_NOT_INTEGRATED -> R.string.home_msg_rdp_pending
    HomeMessage.STREAM_NOT_INTEGRATED -> R.string.home_msg_stream_pending
    HomeMessage.STORE_UNAVAILABLE -> R.string.home_msg_store_unavailable
    HomeMessage.BUSY -> R.string.home_msg_busy
    HomeMessage.CONFLICT_REFRESHED -> R.string.home_msg_conflict
    HomeMessage.ROUTE_UNAVAILABLE -> R.string.home_msg_route_unavailable
})
