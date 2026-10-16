package io.github.baitian6641.cloudy.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue

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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                // Includes system caption bounds and IME, using their union rather
                // than double-counting overlapping bottom insets.
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
        ) {
            // Committed context remains visible independently of the editable draft;
            // OEM captions may display only the application's label.
            Text(currentName, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(appName, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (notice != null) Text(notice, style = MaterialTheme.typography.bodyMedium)
            if (onCredentials != null) {
                OutlinedButton(onClick = onCredentials,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_saved_credentials))
                }
            }
            if (onProfiles != null) {
                OutlinedButton(onClick = onProfiles,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_connection_profiles))
                }
            }
            if (onSshTrust != null) {
                OutlinedButton(onClick = onSshTrust,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_ssh_host_keys))
                }
            }
            if (onSshAuthenticate != null) {
                OutlinedButton(onClick = onSshAuthenticate,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_ssh_authenticate))
                }
            }
            if (onSshFiles != null) {
                OutlinedButton(onClick = onSshFiles,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_ssh_files))
                }
            }
            if (onFilesLocations != null) {
                OutlinedButton(onClick = onFilesLocations,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_files_locations))
                }
            }
            if (onDisplaySettings != null) {
                OutlinedButton(onClick = onDisplaySettings,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_display_settings))
                }
            }
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
            ) {
                Button(onClick = {
                    renameError = onRename(draft.text)
                }, enabled = !retained.tooLong,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_save_name))
                }
                OutlinedButton(onClick = onNewWindow,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_new_window))
                }
                OutlinedButton(onClick = onClose,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)) {
                    Text(stringResource(R.string.cloudy_close_window))
                }
            }
        }
    }
}
