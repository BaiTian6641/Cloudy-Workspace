package io.github.baitian6641.cloudy.platform

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Non-secret SSH-config preview/import. It never connects or stores credentials. */
class CloudySshConfigImportActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private var disposed = false

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !disposed && !isFinishing) loadFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        enableEdgeToEdge()
        title = getString(R.string.ssh_import_heading)
        setContent {
            CloudyAppTheme {
                var text by remember { mutableStateOf(TextFieldValue()) }
                var preview by remember { mutableStateOf<SshConfigImporter.Preview?>(null) }
                var message by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                fun parseNow() {
                    if (busy || text.text.isBlank()) return
                    busy = true
                    message = null
                    val candidate = text.text
                    Thread({
                        val loaded = runCatching { ConnectionProfiles(applicationContext).load() }.getOrNull()
                        val labels = (loaded as? ConnectionProfilesResult.Success)?.value?.profiles?.map { it.label }?.toSet()
                            ?: emptySet()
                        val result = SshConfigImporter.parse(candidate, owner, labels)
                        runOnUiThread {
                            busy = false
                            if (!disposed) { preview = result; message = null }
                        }
                    }, "cloudy-ssh-config-preview").apply { isDaemon = true }.start()
                }
                fun importNow() {
                    val plan = preview ?: return
                    if (busy || plan.entries.isEmpty()) return
                    busy = true
                    message = null
                    Thread({
                        val store = ConnectionProfiles(applicationContext)
                        val result = SshConfigImporter.importProfiles(store, owner, plan)
                        runOnUiThread {
                            busy = false
                            if (disposed) return@runOnUiThread
                            message = if (result.failure != null)
                                getString(R.string.ssh_import_stopped, result.created, result.failure)
                            else getString(R.string.ssh_import_done, result.created)
                            if (result.failure == null) { preview = null; text = TextFieldValue() }
                        }
                    }, "cloudy-ssh-config-import").apply { isDaemon = true }.start()
                }
                SshConfigImportScreen(
                    text = text, preview = preview, message = message, busy = busy,
                    onText = { value -> if (!busy) { if (value.text.length <= SshConfigImporter.MAX_TEXT_UNITS) text = value } },
                    onOpenFile = { if (!busy) picker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                    onPreview = ::parseNow, onImport = ::importNow,
                    onClose = { if (!busy) finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        disposed = true
        super.onDestroy()
    }

    private fun loadFile(uri: Uri) {
        val input = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
        if (input == null) { return }
        Thread({
            val bytes = try {
                input.use { stream ->
                    val buffer = ByteArray(SshConfigImporter.MAX_TEXT_UNITS + 1)
                    var size = 0
                    while (size < buffer.size) {
                        val count = stream.read(buffer, size, buffer.size - size)
                        if (count <= 0) break
                        size += count
                    }
                    buffer.copyOf(size)
                }
            } catch (_: Exception) { null }
            val decoded = bytes?.let {
                try {
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(it)).toString()
                } catch (_: Exception) { null }
            }
            bytes?.fill(0)
            runOnUiThread { if (!disposed && decoded != null) _fileText.value = TextFieldValue(decoded) }
        }, "cloudy-ssh-config-file").apply { isDaemon = true }.start()
    }

    // Set by the file worker. The Compose state reads this on the next frame; the import screen
    // remains a normal editable paste field.
    private val _fileText = androidx.compose.runtime.mutableStateOf(TextFieldValue())

    @Composable
    private fun SshConfigImportScreen(
        text: TextFieldValue, preview: SshConfigImporter.Preview?, message: String?, busy: Boolean,
        onText: (TextFieldValue) -> Unit, onOpenFile: () -> Unit, onPreview: () -> Unit,
        onImport: () -> Unit, onClose: () -> Unit,
    ) {
        // The file worker publishes through _fileText; fold it into the visible field once.
        LaunchedEffect(_fileText.value) {
            val loaded = _fileText.value
            if (loaded.text.isNotEmpty() && loaded != text) onText(loaded)
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                    .testTag("ssh_import_screen"),
                contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium),
            ) {
                item("heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(stringResource(R.string.ssh_import_heading), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.ssh_import_scope), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.ssh_import_subset), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item("actions") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                        verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        OutlinedButton(onOpenFile, enabled = !busy, modifier = Modifier.testTag("ssh_import_file")) {
                            Text(stringResource(R.string.ssh_import_open_file))
                        }
                        Button(onPreview, enabled = !busy && text.text.isNotBlank(),
                            modifier = Modifier.testTag("ssh_import_preview")) {
                            Text(stringResource(R.string.ssh_import_preview_action))
                        }
                        Button(onImport, enabled = !busy && preview?.entries?.isNotEmpty() == true,
                            modifier = Modifier.testTag("ssh_import_apply")) {
                            Text(stringResource(R.string.ssh_import_apply))
                        }
                        TextButton(onClose, enabled = !busy, modifier = Modifier.testTag("ssh_import_close")) {
                            Text(stringResource(R.string.ssh_import_close))
                        }
                    }
                }
                item("text") {
                    OutlinedTextField(text, onText, enabled = !busy, minLines = 6, maxLines = 14,
                        label = { Text(stringResource(R.string.ssh_import_text)) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                        modifier = Modifier.fillMaxWidth().testTag("ssh_import_text"))
                }
                message?.let { status ->
                    item("message") {
                        Text(status, modifier = Modifier.testTag("ssh_import_message")
                            .semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                preview?.let { plan ->
                    item("entries-heading") {
                        Text(stringResource(R.string.ssh_import_entries, plan.entries.size),
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    itemsIndexed(plan.entries, key = { _, entry -> "entry-${entry.alias}" }) { index, entry ->
                        Text(stringResource(R.string.ssh_import_entry, entry.alias, entry.host, entry.port),
                            modifier = Modifier.testTag("ssh_import_entry_$index"))
                    }
                    if (plan.issues.isNotEmpty()) item("issues-heading") {
                        Text(stringResource(R.string.ssh_import_issues, plan.issues.size),
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    itemsIndexed(plan.issues, key = { index, _ -> "issue-$index" }) { index, issue ->
                        Text(stringResource(R.string.ssh_import_issue, issue.line, issue.directive, issue.reason),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("ssh_import_issue_$index"))
                    }
                    if (plan.truncated) item("truncated") {
                        Text(stringResource(R.string.ssh_import_truncated),
                            modifier = Modifier.testTag("ssh_import_truncated"))
                    }
                }
            }
        }
    }
}
