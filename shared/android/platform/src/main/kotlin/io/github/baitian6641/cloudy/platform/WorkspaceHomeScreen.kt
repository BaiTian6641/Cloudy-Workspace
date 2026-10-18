package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val onToggleFavorite: (HomeRow) -> Unit,
    val onConnect: (HomeRow) -> Unit,
    val onRetry: () -> Unit,
    val onBrowseFiles: () -> Unit,
    val onAddHost: () -> Unit,
    val onNewWindow: () -> Unit,
    val onClose: () -> Unit,
    val onRename: (String) -> String?,
    val onProfiles: () -> Unit,
    val onSshTrust: () -> Unit,
    val onSshAuthenticate: () -> Unit,
    val onFilesLocations: () -> Unit,
    val onCredentials: () -> Unit,
    val onDisplaySettings: () -> Unit,
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
    var search by rememberSaveable { mutableStateOf("") }
    // One logical window owns one unsaved edit. It lives above the responsive branches so a
    // width/font-scale reflow cannot discard text, selection or a typed refusal.
    val windowNameDraft = rememberWindowNameDraftState(windowId, currentName)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
        ) {
            val fontScale = LocalDensity.current.fontScale
            // Reflow earlier for scaled text: narrow tiled desktop windows keep every tool.
            val stackedToolbar = maxWidth < 640.dp || fontScale > 1.6f
            val sideNavigation = maxWidth >= 960.dp && fontScale <= 1.5f
            // Wide windows put the selected host in its own pane; narrower ones expand it in
            // place under the selected row so selection and its actions stay together.
            val threePane = maxWidth >= 1280.dp && fontScale <= 1.35f
            Column(Modifier.fillMaxSize()) {
                HomeToolbar(appName, currentName, actions, stackedToolbar)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                notice?.let { HomeNotice(it) }
                state.message?.let { HomeMessageBanner(it) }
                when {
                    state.loading -> HomeLoading()
                    state.storeUnavailable -> Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    ) {
                        HomeUnavailable(actions)
                    }
                    else -> {
                        val sectionRows = remember(state.rows, selection) { WorkspaceHomeOrganizer.filter(state.rows, selection) }
                        val visibleRows = remember(sectionRows, search) { filterRows(sectionRows, search) }
                        val selected = WorkspaceHomeOrganizer.resolve(visibleRows, selectedId)
                        val onSelect: (String) -> Unit = { id ->
                            selectedId = if (selectedId == id) null else id
                        }
                        if (sideNavigation) {
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                Column(
                                    Modifier.width(224.dp).fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    HomeNavigation(
                                        rows = state.rows,
                                        selection = selection,
                                        showAdvanced = showAdvanced,
                                        showPlanned = showPlanned,
                                        owner = owner,
                                        actions = actions,
                                        nameDraft = windowNameDraft,
                                        onSelection = { selection = it },
                                        onToggleAdvanced = { showAdvanced = !showAdvanced },
                                        onTogglePlanned = { showPlanned = !showPlanned },
                                    )
                                }
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                HomeListPane(
                                    selection = selection,
                                    hasHosts = state.rows.isNotEmpty(),
                                    sectionCount = sectionRows.size,
                                    visibleRows = visibleRows,
                                    selected = selected,
                                    search = search,
                                    onSearchChange = { search = it },
                                    owner = owner,
                                    actions = actions,
                                    onSelect = onSelect,
                                    fillHeight = true,
                                    inlineDetail = !threePane,
                                    modifier = Modifier.weight(1f),
                                )
                                if (threePane) {
                                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HomeDetailPane(selected, owner, actions)
                                }
                            }
                            HomeStatus()
                        } else {
                            Column(
                                Modifier.fillMaxWidth().weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                HomeCompactNavigation(
                                    rows = state.rows,
                                    selection = selection,
                                    showAdvanced = showAdvanced,
                                    showPlanned = showPlanned,
                                    owner = owner,
                                    actions = actions,
                                    nameDraft = windowNameDraft,
                                    onSelection = { selection = it },
                                    onToggleAdvanced = { showAdvanced = !showAdvanced },
                                    onTogglePlanned = { showPlanned = !showPlanned },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                HomeListPane(
                                    selection = selection,
                                    hasHosts = state.rows.isNotEmpty(),
                                    sectionCount = sectionRows.size,
                                    visibleRows = visibleRows,
                                    selected = selected,
                                    search = search,
                                    onSearchChange = { search = it },
                                    owner = owner,
                                    actions = actions,
                                    onSelect = onSelect,
                                    fillHeight = false,
                                    inlineDetail = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                HomeStatus()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeToolbar(
    appName: String,
    currentName: String,
    actions: WorkspaceHomeActions,
    stacked: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        if (stacked) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = CloudyControlMetrics.spaceLarge,
                        vertical = CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            ) {
                HomeToolbarIdentity(appName, currentName, Modifier.fillMaxWidth())
                HomeToolbarActions(actions)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .padding(horizontal = CloudyControlMetrics.spaceLarge,
                        vertical = CloudyControlMetrics.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
            ) {
                HomeToolbarIdentity(appName, currentName, Modifier.weight(1f))
                HomeToolbarActions(actions)
            }
        }
    }
}

@Composable
private fun HomeToolbarIdentity(appName: String, currentName: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(appName, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Committed context stays separate from the editable window-name draft.
        Text(currentName, style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun HomeToolbarActions(actions: WorkspaceHomeActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        HomeAction(stringResource(R.string.home_add_host), actions.onAddHost)
        HomeToolbarAction(stringResource(designR.string.cloudy_new_window), actions.onNewWindow)
        HomeToolbarAction(stringResource(designR.string.cloudy_close_window), actions.onClose)
    }
}

@Composable
private fun HomeToolbarAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HomeNotice(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = CloudyControlMetrics.spaceLarge,
                vertical = CloudyControlMetrics.spaceSmall))
    }
}

@Composable
private fun HomeMessageBanner(message: HomeMessage) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(homeMessageText(message), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("home_message")
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = CloudyControlMetrics.spaceLarge,
                    vertical = CloudyControlMetrics.spaceSmall))
    }
}

@Composable
private fun HomeLoading() {
    Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge))
}

@Composable
private fun HomeStatus() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(stringResource(R.string.home_status), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
            .padding(horizontal = CloudyControlMetrics.spaceLarge,
                vertical = CloudyControlMetrics.spaceSmall))
}

@Composable
private fun HomeNavigation(
    rows: List<HomeRow>,
    selection: HomeSelection,
    showAdvanced: Boolean,
    showPlanned: Boolean,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    nameDraft: WindowNameDraftState,
    onSelection: (HomeSelection) -> Unit,
    onToggleAdvanced: () -> Unit,
    onTogglePlanned: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = CloudyControlMetrics.spaceMedium,
                vertical = CloudyControlMetrics.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
    ) {
        Text(stringResource(R.string.home_hosts_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HomeNavEntry(stringResource(R.string.home_favorites),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.Favorites),
                selection == HomeSelection.Favorites, "home_nav_favorites",
                Modifier.fillMaxWidth()) { onSelection(HomeSelection.Favorites) }
            HomeNavEntry(stringResource(R.string.home_recents),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.Recents),
                selection == HomeSelection.Recents, "home_nav_recents",
                Modifier.fillMaxWidth()) { onSelection(HomeSelection.Recents) }
            HomeNavEntry(stringResource(R.string.home_all_hosts),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.All),
                selection == HomeSelection.All, "home_nav_all",
                Modifier.fillMaxWidth()) { onSelection(HomeSelection.All) }
        }
        val tags = WorkspaceHomeOrganizer.tags(rows)
        if (tags.isNotEmpty()) {
            Text(stringResource(R.string.home_groups), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tags.forEach { (tag, count) ->
                    HomeNavEntry(tag, count, selection == HomeSelection.Tag(tag),
                        "home_nav_tag", Modifier.fillMaxWidth()) {
                        onSelection(HomeSelection.Tag(tag))
                    }
                }
            }
        }
        AdvancedTools(actions, nameDraft, showAdvanced, onToggleAdvanced, compact = false)
        PlannedSection(owner, showPlanned, onTogglePlanned)
    }
}

@Composable
private fun HomeCompactNavigation(
    rows: List<HomeRow>,
    selection: HomeSelection,
    showAdvanced: Boolean,
    showPlanned: Boolean,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    nameDraft: WindowNameDraftState,
    onSelection: (HomeSelection) -> Unit,
    onToggleAdvanced: () -> Unit,
    onTogglePlanned: () -> Unit,
) {
    // Compact windows keep the same labeled sections as wrapping chips, never a bottom bar.
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = CloudyControlMetrics.spaceLarge,
                vertical = CloudyControlMetrics.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
    ) {
        Text(stringResource(R.string.home_hosts_heading), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
        ) {
            HomeNavChip(stringResource(R.string.home_favorites),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.Favorites),
                selection == HomeSelection.Favorites, "home_nav_favorites") {
                onSelection(HomeSelection.Favorites)
            }
            HomeNavChip(stringResource(R.string.home_recents),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.Recents),
                selection == HomeSelection.Recents, "home_nav_recents") {
                onSelection(HomeSelection.Recents)
            }
            HomeNavChip(stringResource(R.string.home_all_hosts),
                WorkspaceHomeOrganizer.count(rows, HomeSelection.All),
                selection == HomeSelection.All, "home_nav_all") {
                onSelection(HomeSelection.All)
            }
        }
        val tags = WorkspaceHomeOrganizer.tags(rows)
        if (tags.isNotEmpty()) {
            Text(stringResource(R.string.home_groups), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                modifier = Modifier.selectableGroup(),
            ) {
                tags.forEach { (tag, count) ->
                    HomeNavChip(tag, count, selection == HomeSelection.Tag(tag),
                        "home_nav_tag") { onSelection(HomeSelection.Tag(tag)) }
                }
            }
        }
        AdvancedTools(actions, nameDraft, showAdvanced, onToggleAdvanced, compact = true)
        PlannedSection(owner, showPlanned, onTogglePlanned)
    }
}

@Composable
private fun HomeNavEntry(
    label: String,
    count: Int,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius)
    Row(
        modifier.heightIn(min = CloudyControlMetrics.rowMinHeight)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = CloudyControlMetrics.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeNavChip(
    label: String,
    count: Int,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius)
    Row(
        Modifier.heightIn(min = CloudyControlMetrics.rowMinHeight)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = CloudyControlMetrics.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdvancedTools(
    actions: WorkspaceHomeActions,
    nameDraft: WindowNameDraftState,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    compact: Boolean,
) {
    TextButton(onClick = onToggleAdvanced, modifier = Modifier.testTag("home_advanced")) {
        Text(stringResource(if (showAdvanced) R.string.home_advanced_hide
            else R.string.home_advanced_show))
    }
    if (showAdvanced) {
        Text(stringResource(R.string.home_advanced_scope), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (compact) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            ) {
                AdvancedActions(actions)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AdvancedNavActions(actions)
            }
        }
        WindowNameEditor(nameDraft, actions.onRename)
    }
}

@Composable
private fun AdvancedActions(actions: WorkspaceHomeActions) {
    HomeAction(stringResource(designR.string.cloudy_connection_profiles), actions.onProfiles)
    HomeAction(stringResource(designR.string.cloudy_ssh_files), actions.onBrowseFiles)
    HomeAction(stringResource(designR.string.cloudy_files_locations), actions.onFilesLocations)
    HomeAction(stringResource(designR.string.cloudy_ssh_host_keys), actions.onSshTrust)
    HomeAction(stringResource(designR.string.cloudy_ssh_authenticate), actions.onSshAuthenticate)
    HomeAction(stringResource(designR.string.cloudy_saved_credentials), actions.onCredentials)
    HomeAction(stringResource(designR.string.cloudy_display_settings), actions.onDisplaySettings)
}

@Composable
private fun AdvancedNavActions(actions: WorkspaceHomeActions) {
    HomeNavAction(stringResource(designR.string.cloudy_connection_profiles), actions.onProfiles)
    HomeNavAction(stringResource(designR.string.cloudy_ssh_files), actions.onBrowseFiles)
    HomeNavAction(stringResource(designR.string.cloudy_files_locations), actions.onFilesLocations)
    HomeNavAction(stringResource(designR.string.cloudy_ssh_host_keys), actions.onSshTrust)
    HomeNavAction(stringResource(designR.string.cloudy_ssh_authenticate), actions.onSshAuthenticate)
    HomeNavAction(stringResource(designR.string.cloudy_saved_credentials), actions.onCredentials)
    HomeNavAction(stringResource(designR.string.cloudy_display_settings), actions.onDisplaySettings)
}

@Composable
private fun PlannedSection(owner: WindowOwner, showPlanned: Boolean, onTogglePlanned: () -> Unit) {
    TextButton(onClick = onTogglePlanned, modifier = Modifier.testTag("home_planned")) {
        Text(stringResource(if (showPlanned) designR.string.cloudy_hide_upcoming
            else designR.string.cloudy_show_upcoming))
    }
    if (showPlanned) PlannedCapabilities(owner)
}

@Composable
private fun HomeListPane(
    selection: HomeSelection,
    hasHosts: Boolean,
    sectionCount: Int,
    visibleRows: List<HomeRow>,
    selected: HomeRow?,
    search: String,
    onSearchChange: (String) -> Unit,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    onSelect: (String) -> Unit,
    fillHeight: Boolean,
    inlineDetail: Boolean,
    modifier: Modifier = Modifier,
) {
    if (fillHeight) {
        Column(modifier.fillMaxHeight()) {
            HomeListHeader(selection, sectionCount)
            if (hasHosts) HomeSearch(search, visibleRows.size, sectionCount, onSearchChange)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                HomeListBody(
                    hasHosts = hasHosts,
                    visibleRows = visibleRows,
                    selected = selected,
                    search = search,
                    owner = owner,
                    actions = actions,
                    onSelect = onSelect,
                    inlineDetail = inlineDetail,
                )
            }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            HomeListHeader(selection, sectionCount)
            if (hasHosts) HomeSearch(search, visibleRows.size, sectionCount, onSearchChange)
            HomeListBody(
                hasHosts = hasHosts,
                visibleRows = visibleRows,
                selected = selected,
                search = search,
                owner = owner,
                actions = actions,
                onSelect = onSelect,
                inlineDetail = inlineDetail,
            )
        }
    }
}

@Composable
private fun HomeListHeader(selection: HomeSelection, count: Int) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = CloudyControlMetrics.spaceLarge, end = CloudyControlMetrics.spaceLarge,
                top = CloudyControlMetrics.spaceLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
    ) {
        Text(selectionLabel(selection), style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() })
        Text(pluralStringResource(R.plurals.home_host_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSearch(
    search: String,
    visibleCount: Int,
    sectionCount: Int,
    onSearchChange: (String) -> Unit,
) {
    val clearLabel = stringResource(R.string.home_search_clear)
    // Search only filters already-loaded, non-secret row metadata; it never reads the stores.
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = CloudyControlMetrics.spaceLarge,
                vertical = CloudyControlMetrics.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            singleLine = true,
            label = { Text(stringResource(R.string.home_search_label)) },
            placeholder = { Text(stringResource(R.string.home_search_hint)) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.semantics { contentDescription = clearLabel },
                    ) {
                        Text("\u00D7", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
            modifier = Modifier.fillMaxWidth().testTag("home_search"),
        )
        if (search.isNotBlank()) {
            Text(stringResource(R.string.home_search_results, visibleCount, sectionCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeListBody(
    hasHosts: Boolean,
    visibleRows: List<HomeRow>,
    selected: HomeRow?,
    search: String,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    onSelect: (String) -> Unit,
    inlineDetail: Boolean,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .padding(start = CloudyControlMetrics.spaceLarge, end = CloudyControlMetrics.spaceLarge,
                bottom = CloudyControlMetrics.spaceLarge),
    ) {
        // Trailing timestamps need room; otherwise they join the compact metadata lines.
        val trailingLastUsed = maxWidth >= 560.dp && LocalDensity.current.fontScale <= 1.5f
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!hasHosts) {
                HomeEmpty(actions)
            } else if (visibleRows.isEmpty()) {
                Text(
                    if (search.isNotBlank())
                        stringResource(R.string.home_search_empty, search.trim())
                    else stringResource(R.string.home_section_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                visibleRows.forEach { row ->
                    HomeHostRow(
                        row = row,
                        isSelected = selected?.profile?.id == row.profile.id,
                        trailingLastUsed = trailingLastUsed,
                        onSelect = { onSelect(row.profile.id) },
                        onToggleFavorite = { actions.onToggleFavorite(row) },
                    )
                    if (inlineDetail && selected?.profile?.id == row.profile.id) {
                        HomeDetail(row, owner, actions, Modifier.padding(top = CloudyControlMetrics.spaceSmall))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHostRow(
    row: HomeRow,
    isSelected: Boolean,
    trailingLastUsed: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val profile = row.profile
    val shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius)
    val primaryText = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.rowMinHeight)
            .clip(shape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
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
            Text(text = if (profile.favorite) "\u2605" else "\u2606",
                style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalAlignment = Alignment.CenterVertically) {
                Text(profile.label, style = MaterialTheme.typography.bodyLarge,
                    color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                ProtocolBadge(profile.protocol)
            }
            Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace, color = secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (profile.tags.isNotEmpty()) {
                Text(profile.tags.joinToString(" \u00B7 "), style = MaterialTheme.typography.bodySmall,
                    color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!trailingLastUsed) {
                Text(lastUsedText(profile.lastUsedAt), style = MaterialTheme.typography.labelMedium,
                    color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingLastUsed) {
            Text(lastUsedText(profile.lastUsedAt), style = MaterialTheme.typography.labelMedium,
                color = secondaryText, textAlign = TextAlign.End,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp))
        }
    }
}

@Composable
private fun HomeDetail(
    row: HomeRow,
    owner: WindowOwner,
    actions: WorkspaceHomeActions,
    modifier: Modifier = Modifier,
) {
    val profile = row.profile
    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
        modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(CloudyControlMetrics.spaceLarge)
                .testTag("home_detail"),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
        ) {
            Text(stringResource(R.string.home_detail_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Text(profile.label, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                ProtocolBadge(profile.protocol)
            }
            Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace)
            // The single prominent action sits above state metadata so it stays reachable when
            // a short window scrolls the detail pane.
            val filesAreConnect = connectOpensFiles(owner, profile.protocol)
            Button(onClick = { actions.onConnect(row) },
                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = CloudyControlMetrics.controlMinHeight)
                    .testTag("home_connect")) {
                Text(stringResource(connectLabel(owner, profile.protocol)))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            ) {
                if (profile.protocol == ConnectionProtocol.SSH && !filesAreConnect) {
                    HomeAction(stringResource(designR.string.cloudy_ssh_files), actions.onBrowseFiles)
                }
                HomeAction(stringResource(R.string.home_manage_profiles), actions.onProfiles)
            }
            Text(trustText(row), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(credentialText(row), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lastUsedText(profile.lastUsedAt), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (profile.tags.isNotEmpty()) {
                Text(profile.tags.joinToString(" \u00B7 "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeDetailPane(selected: HomeRow?, owner: WindowOwner, actions: WorkspaceHomeActions) {
    Column(
        Modifier.width(320.dp).fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(CloudyControlMetrics.spaceMedium),
    ) {
        if (selected != null) {
            HomeDetail(selected, owner, actions)
        } else {
            Text(stringResource(R.string.home_detail_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeEmpty(actions: WorkspaceHomeActions) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth(),
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
        Modifier.fillMaxWidth()
            .padding(horizontal = CloudyControlMetrics.spaceLarge,
                vertical = CloudyControlMetrics.spaceMedium),
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

/**
 * A single logical window's unsaved name edit. It is remembered above the responsive home
 * branches so width/font-scale reflow cannot discard the text, selection, refusal or rename
 * error, while the envelope still binds restoration to this window and never to the committed
 * window state owned by the activity.
 */
@Stable
private class WindowNameDraftState(initial: WindowDraft) {
    var retained by mutableStateOf(initial)
        private set
    var draft by mutableStateOf(
        TextFieldValue(initial.text, TextRange(initial.selectionStart, initial.selectionEnd)),
    )
        private set
    var renameError by mutableStateOf<String?>(null)
        private set

    val tooLong: Boolean get() = retained.tooLong

    fun edit(next: TextFieldValue) {
        // Reject the whole oversized edit; never truncate a paste or split a surrogate pair.
        if (next.text.length > WindowDraftCodec.MaximumUnits) {
            retained = retained.copy(tooLong = true)
        } else {
            draft = next
            retained = WindowDraft(next.text, next.selection.start, next.selection.end)
        }
        renameError = null
    }

    fun save(onRename: (String) -> String?) {
        // A refusal is display state only; the committed name lives in the owning window activity.
        renameError = onRename(draft.text)
    }
}

@Composable
private fun rememberWindowNameDraftState(windowId: String, currentName: String): WindowNameDraftState {
    // Reuse the shared codec: recreation restores text, selection and the oversized-edit flag
    // for this window only; a rename error is display state, exactly as before.
    val saver = remember(windowId) {
        Saver<WindowNameDraftState, Any>(
            save = { WindowDraftCodec.save(windowId, it.retained) },
            restore = { value -> WindowDraftCodec.restore(windowId, value)?.let(::WindowNameDraftState) },
        )
    }
    return rememberSaveable(windowId, saver = saver) {
        WindowNameDraftState(WindowDraftCodec.initial(currentName))
    }
}

@Composable
private fun WindowNameEditor(state: WindowNameDraftState, onRename: (String) -> String?) {
    val error = if (state.tooLong) stringResource(designR.string.cloudy_name_too_long) else state.renameError
    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
        Text(stringResource(designR.string.cloudy_workspace_window), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.draft,
            singleLine = true,
            onValueChange = state::edit,
            label = { Text(stringResource(designR.string.cloudy_window_name)) },
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            modifier = Modifier.fillMaxWidth().testTag("home_window_name"),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
        )
        FlowRow {
            Button(onClick = { state.save(onRename) }, enabled = !state.tooLong,
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
private fun HomeNavAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
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

@Composable
private fun selectionLabel(selection: HomeSelection): String = when (selection) {
    HomeSelection.Favorites -> stringResource(R.string.home_favorites)
    HomeSelection.Recents -> stringResource(R.string.home_recents)
    HomeSelection.All -> stringResource(R.string.home_all_hosts)
    is HomeSelection.Tag -> selection.tag
}

private fun filterRows(rows: List<HomeRow>, search: String): List<HomeRow> {
    val needle = search.trim()
    if (needle.isEmpty()) return rows
    return rows.filter { row ->
        val profile = row.profile
        profile.label.contains(needle, ignoreCase = true) ||
            profile.host.contains(needle, ignoreCase = true) ||
            profile.port.toString().contains(needle) ||
            profile.tags.any { it.contains(needle, ignoreCase = true) } ||
            protocolLabel(profile.protocol).contains(needle, ignoreCase = true)
    }
}

private fun protocolLabel(protocol: ConnectionProtocol): String = when (protocol) {
    ConnectionProtocol.SSH -> "SSH"
    ConnectionProtocol.RDP -> "RDP"
    ConnectionProtocol.VNC -> "VNC"
    ConnectionProtocol.STREAM -> "Stream"
}

/** Desktop's SSH connect route opens files, so the detail must not repeat that action. */
private fun connectOpensFiles(owner: WindowOwner, protocol: ConnectionProtocol): Boolean =
    protocol == ConnectionProtocol.SSH && owner == WindowOwner.DESKTOP

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
