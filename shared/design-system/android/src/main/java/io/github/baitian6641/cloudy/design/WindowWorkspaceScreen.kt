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
                .padding(CloudyTokens.Space16.dp),
            verticalArrangement = Arrangement.spacedBy(CloudyTokens.Space12.dp),
        ) {
            Text(appName, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            if (notice != null) Text(notice, style = MaterialTheme.typography.bodyMedium)
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
                shape = RoundedCornerShape(CloudyTokens.Radii.Field.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CloudyTokens.Space8.dp),
                verticalArrangement = Arrangement.spacedBy(CloudyTokens.Space8.dp),
            ) {
                Button(onClick = {
                    renameError = onRename(draft.text)
                }, enabled = !retained.tooLong,
                    shape = RoundedCornerShape(CloudyTokens.Radii.Button.dp),
                    modifier = Modifier.heightIn(min = CloudyTokens.Density.Comfortable.dp)) {
                    Text(stringResource(R.string.cloudy_save_name))
                }
                OutlinedButton(onClick = onNewWindow,
                    shape = RoundedCornerShape(CloudyTokens.Radii.Button.dp),
                    modifier = Modifier.heightIn(min = CloudyTokens.Density.Comfortable.dp)) {
                    Text(stringResource(R.string.cloudy_new_window))
                }
                OutlinedButton(onClick = onClose,
                    shape = RoundedCornerShape(CloudyTokens.Radii.Button.dp),
                    modifier = Modifier.heightIn(min = CloudyTokens.Density.Comfortable.dp)) {
                    Text(stringResource(R.string.cloudy_close_window))
                }
            }
        }
    }
}
