package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProfileValidationCode
import io.github.baitian6641.cloudy.domain.ConnectionProfileValidationException
import io.github.baitian6641.cloudy.domain.WindowOwner
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** App-private endpoint editor. A restored draft never authorizes another database mutation. */
class CloudyProfilesActivity : ComponentActivity() {
    private lateinit var owner: WindowOwner
    private var catalog by mutableStateOf<ConnectionProfileCatalog?>(null)
    private var draft by mutableStateOf<ProfileEditorDraft?>(null)
    private var reviewed by mutableStateOf<ConnectionProfileEntry?>(null)
    private var message by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var pendingMutation by mutableStateOf(false)
    private var confirmation by mutableStateOf<Confirmation?>(null)
    private var interactive = false
    private var disposed = false

    private class Confirmation(
        val title: Int, val body: Int, val accept: Int,
        val saved: ConnectionProfile? = null, val action: () -> Unit,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = when (packageName) {
            "io.github.baitian6641.cloudy.console" -> WindowOwner.CONSOLE
            "io.github.baitian6641.cloudy.desktop" -> WindowOwner.DESKTOP
            else -> { finish(); return }
        }
        enableEdgeToEdge()
        // Only framework saved state is eligible; Intent extras never populate this editor.
        when (val restored = ProfileEditorState.restore(savedInstanceState, owner)) {
            is ProfileEditorRestore.Restored -> {
                draft = restored.draft
                pendingMutation = restored.pendingMutation
                if (pendingMutation) message = getString(R.string.profile_restored_pending)
            }
            ProfileEditorRestore.Rejected -> message = getString(R.string.profile_restore_rejected)
            ProfileEditorRestore.Missing -> Unit
        }
        title = getString(R.string.profile_heading)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leaveEditor { finish() } }
        })
        setContent {
            CloudyAppTheme {
                ProfileEditorScreen(
                    appName = applicationInfo.loadLabel(packageManager).toString(), owner = owner,
                    catalog = catalog, draft = draft, busy = busy, message = message,
                    outcomeUnknown = pendingMutation && !busy, reviewed = reviewed,
                    onRefresh = { if (canInteract()) refresh() }, onNew = ::newProfile,
                    onEdit = ::editProfile, onDraftChange = ::changeDraft, onSave = ::save,
                    onCloseEditor = { leaveEditor {} }, onDelete = ::delete,
                    onReview = ::reviewCurrent, onReapply = ::reapply,
                    onReload = ::reloadDraft, onFinish = { leaveEditor { finish() } },
                )
                confirmation?.let { request ->
                    AlertDialog(
                        onDismissRequest = { confirmation = null },
                        modifier = Modifier.testTag("profile_dialog"),
                        shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        text = {
                            key(request) {
                                // In a short scaled window, a fixed title can consume the whole
                                // body viewport. Scroll all explanatory content together while
                                // keeping both actions fixed and this exact confirmation frozen.
                                Column(Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                    Text(getString(request.title), style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.semantics { heading() })
                                    Text(getString(request.body), style = MaterialTheme.typography.bodyMedium)
                                    request.saved?.let { saved ->
                                        Text(saved.label, style = MaterialTheme.typography.bodyLarge)
                                        val host = if (':' in saved.host) "[${saved.host}]" else saved.host
                                        Text("${saved.protocol.wireId.uppercase()} · $host:${saved.port}",
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text(getString(R.string.profile_revision, saved.revision),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmation = null
                                    if (canInteract()) request.action()
                                },
                                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)
                                    .testTag("profile_confirm"),
                            ) { Text(getString(request.accept)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmation = null },
                                shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)
                                    .testTag("profile_cancel")) { Text(getString(R.string.profile_cancel)) }
                        },
                    )
                }
            }
        }
        // The single application queue orders this read after an older Activity's admitted write.
        refresh(preserveMessage = true)
    }

    override fun onResume() { super.onResume(); interactive = true }
    override fun onPause() {
        interactive = false
        confirmation = null // Approval is tied to this visible presentation, never restored.
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        // No post-snapshot action can bypass the pending bit persisted before admission.
        interactive = false
        ProfileEditorState.save(outState, owner, draft, pendingMutation)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { disposed = true; super.onDestroy() }

    private fun canInteract(): Boolean = interactive && !disposed && !busy

    private fun refresh(preserveMessage: Boolean = false, complete: ((ConnectionProfileCatalog) -> Unit)? = null) {
        if (busy) return
        val store = ConnectionProfiles(applicationContext)
        dispatch(mutation = false, operation = { store.load() }) { result ->
            when (result) {
                is ConnectionProfilesResult.Success -> {
                    catalog = result.value
                    if (!preserveMessage) message = getString(R.string.profile_refreshed)
                    complete?.invoke(result.value)
                }
                is ConnectionProfilesResult.Failure -> {
                    catalog = null // Unavailability must never appear as a successfully empty list.
                    reviewed = null
                    message = getString(errorResource(result.code))
                }
            }
        }
    }

    private fun newProfile() {
        if (!canInteract() || pendingMutation) return
        val current = catalog ?: return
        leaveEditor { draft = ProfileEditorState.newDraft(owner, current.generation) }
    }

    private fun editProfile(profile: ConnectionProfile) {
        if (!canInteract()) return
        val current = catalog ?: return
        if (current.profiles.none { it.id == profile.id && it.revision == profile.revision }) return
        leaveEditor { draft = ProfileEditorState.editDraft(owner, current.generation, profile) }
    }

    private fun leaveEditor(after: () -> Unit) {
        if (!canInteract()) return
        if (draft == null && !pendingMutation) { after(); return }
        // Matching endpoint text cannot identify which uncertain create was committed. Leaving
        // must still work if storage is unavailable; this explicit discard never retries a write.
        confirmation = Confirmation(R.string.profile_discard_title,
            if (pendingMutation) R.string.profile_discard_unknown_body else R.string.profile_discard_body,
            R.string.profile_discard_confirm) {
            draft = null
            reviewed = null
            pendingMutation = false
            message = null
            after()
        }
    }

    private fun changeDraft(next: ProfileEditorDraft) {
        val old = draft ?: return
        if (!canInteract() || pendingMutation || next.editorId != old.editorId || next.owner != old.owner ||
            next.generation != old.generation || next.version !== old.version) return
        if (!ProfileEditorState.acceptsField(next.label, 160) || !ProfileEditorState.acceptsField(next.host, 1024) ||
            !ProfileEditorState.acceptsField(next.port, 5) || !ProfileEditorState.acceptsField(next.tags, 1024)) {
            message = getString(R.string.profile_invalid_field)
            return
        }
        draft = next
        reviewed = null
        message = null
    }

    private fun validate(value: ProfileEditorDraft): ConnectionProfileDraft? {
        val port = value.port.text.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toIntOrNull()
        if (port == null) { message = getString(R.string.profile_invalid_port); return null }
        // Comma-separated tags are user organization metadata; empty entries drop, domain
        // validation rejects what remains malformed, and dedupe keeps the first spelling.
        val tags = value.tags.text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .toCollection(java.util.LinkedHashSet())
        return try {
            ConnectionProfileDraft.create(owner, value.label.text, value.protocol, value.host.text, port,
                tags, value.favorite)
        } catch (error: ConnectionProfileValidationException) {
            message = getString(when (error.code) {
                ConnectionProfileValidationCode.INVALID_LABEL -> R.string.profile_invalid_label
                ConnectionProfileValidationCode.INVALID_PROTOCOL -> R.string.profile_invalid_protocol
                ConnectionProfileValidationCode.INVALID_HOST -> R.string.profile_invalid_host
                ConnectionProfileValidationCode.INVALID_PORT -> R.string.profile_invalid_port
                ConnectionProfileValidationCode.INVALID_TAGS -> R.string.profile_invalid_tags
                else -> R.string.profile_invalid_input
            })
            null
        }
    }

    private fun save() {
        if (!canInteract() || pendingMutation) return
        val current = draft ?: return
        val fields = validate(current) ?: return
        saveSnapshot(current, fields, current.version)
    }

    private fun saveSnapshot(value: ProfileEditorDraft, fields: ConnectionProfileDraft, version: ConnectionProfileVersion?) {
        val store = ConnectionProfiles(applicationContext)
        reviewed = null
        dispatch(mutation = true, operation = {
            if (version == null) store.create(value.generation, fields) else store.update(version, fields)
        }) { result ->
            when (result) {
                is ConnectionProfilesResult.Success -> {
                    draft = ProfileEditorState.editDraft(owner, result.value.version.generation, result.value.profile)
                    message = getString(if (version == null) R.string.profile_saved else R.string.profile_updated)
                }
                is ConnectionProfilesResult.Failure -> message = getString(errorResource(result.code))
            }
            refresh(preserveMessage = true)
        }
    }

    private fun currentEntry(current: ConnectionProfileCatalog, value: ProfileEditorDraft): ConnectionProfileEntry? {
        val version = value.version ?: return null
        if (current.generation != version.generation) {
            message = getString(R.string.profile_stale_generation); return null
        }
        val profile = current.profiles.singleOrNull { it.id == version.id }
        if (profile == null) { message = getString(R.string.profile_not_found); return null }
        return ConnectionProfileEntry(profile, ConnectionProfileVersion.create(current.generation, profile.id, profile.revision))
    }

    private fun reviewCurrent() {
        if (!canInteract()) return
        val current = draft ?: return
        if (current.version == null) return
        reviewed = null
        refresh { saved ->
            // Merely reading the current token does not replace the editor's original token.
            if (draft === current) reviewed = currentEntry(saved, current)
        }
    }

    private fun reapply() {
        if (!canInteract()) return
        val value = draft ?: return
        val target = reviewed ?: return
        val fields = validate(value) ?: return
        confirmation = Confirmation(R.string.profile_reapply_title, R.string.profile_reapply_body,
            R.string.profile_reapply_confirm, target.profile) {
            // This exact reviewed token is submitted; another edit still yields a conflict.
            if (draft === value && reviewed === target) saveSnapshot(value, fields, target.version)
        }
    }

    private fun reloadDraft() {
        if (!canInteract()) return
        val value = draft ?: return
        if (value.version == null) return
        confirmation = Confirmation(R.string.profile_discard_title,
            if (pendingMutation) R.string.profile_discard_unknown_body else R.string.profile_discard_body,
            R.string.profile_discard_confirm) {
            refresh { saved ->
                currentEntry(saved, value)?.let { entry ->
                    draft = ProfileEditorState.editDraft(owner, entry.version.generation, entry.profile)
                    pendingMutation = false
                    reviewed = null
                }
            }
        }
    }

    private fun delete() {
        if (!canInteract() || pendingMutation) return
        val value = draft ?: return
        val version = value.version ?: return
        val current = catalog ?: return
        val target = currentEntry(current, value) ?: return
        if (target.version.revision != version.revision) {
            message = getString(R.string.profile_conflict); return
        }
        confirmation = Confirmation(R.string.profile_delete_title, R.string.profile_delete_body,
            R.string.profile_delete_confirm, target.profile) {
            val store = ConnectionProfiles(applicationContext)
            reviewed = null
            dispatch(mutation = true, operation = { store.delete(version) }) { result ->
                when (result) {
                    is ConnectionProfilesResult.Success -> {
                        draft = null
                        message = getString(R.string.profile_deleted)
                    }
                    is ConnectionProfilesResult.Failure -> message = getString(errorResource(result.code))
                }
                refresh(preserveMessage = true)
            }
        }
    }

    private fun <T> dispatch(mutation: Boolean, operation: () -> ConnectionProfilesResult<T>,
        complete: (ConnectionProfilesResult<T>) -> Unit) {
        val previouslyPending = pendingMutation
        busy = true
        // Synchronous before queue admission. If the Activity dies, its snapshot is uncertainty,
        // never a command. Worker closures capture only immutable inputs and application storage.
        if (mutation) pendingMutation = true
        try {
            operations.execute {
                val result = try { operation() } catch (_: Exception) {
                    ConnectionProfilesResult.Failure(if (mutation) ConnectionProfilesCode.COMPLETION_UNKNOWN
                        else ConnectionProfilesCode.STORE_UNAVAILABLE)
                }
                main.post {
                    if (!disposed) {
                        busy = false
                        if (mutation) pendingMutation = result is ConnectionProfilesResult.Failure &&
                            (previouslyPending || result.code == ConnectionProfilesCode.COMPLETION_UNKNOWN)
                        complete(result)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            busy = false
            if (mutation) pendingMutation = previouslyPending // This new write was never admitted.
            message = getString(R.string.profile_busy)
        }
    }

    private fun errorResource(code: ConnectionProfilesCode): Int = when (code) {
        ConnectionProfilesCode.INVALID_INPUT -> R.string.profile_invalid_input
        ConnectionProfilesCode.WRONG_OWNER -> R.string.profile_wrong_owner
        ConnectionProfilesCode.BUSY -> R.string.profile_busy
        ConnectionProfilesCode.CONFLICT -> R.string.profile_conflict
        ConnectionProfilesCode.NOT_FOUND -> R.string.profile_not_found
        ConnectionProfilesCode.STALE_GENERATION -> R.string.profile_stale_generation
        ConnectionProfilesCode.CAPACITY -> R.string.profile_capacity
        ConnectionProfilesCode.REVISION_EXHAUSTED -> R.string.profile_revision_exhausted
        ConnectionProfilesCode.COMPLETION_UNKNOWN -> R.string.profile_unknown
        ConnectionProfilesCode.STORE_UNAVAILABLE, ConnectionProfilesCode.WRONG_THREAD -> R.string.profile_unavailable
    }

    companion object {
        // One bounded executor per owning app process, including reads across Activity recreation.
        // It expires when idle and requires neither a service nor a sibling process.
        private val operations = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(16), { work -> Thread(work, "cloudy-profiles").apply { isDaemon = true } })
            .apply { allowCoreThreadTimeOut(true) }
        private val main = Handler(Looper.getMainLooper())
    }
}
