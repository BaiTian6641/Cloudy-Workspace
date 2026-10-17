package io.github.baitian6641.cloudy.desktop

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.desktop.vnc.VncEngine
import io.github.baitian6641.cloudy.domain.ConnectionProfile

/** Local Cloudy controls only. Native remote pixels never enter Compose state or a UI bridge. */
@Composable
internal fun VncScreen(
    state: VncUiState, model: VncSessionModel, attachment: Any, selectedProfile: ConnectionProfile?,
    username: TextFieldValue, password: TextFieldValue, fingerprint: TextFieldValue, pinCompared: Boolean,
    fieldEpoch: Long, preferFullscreen: Boolean, interactive: Boolean, localGranted: Boolean,
    notice: String?, fullscreenLabel: String, fullscreenPending: Boolean, releaseEpoch: Long,
    onSelect: (ConnectionProfile) -> Unit,
    onUsername: (TextFieldValue) -> Unit, onPassword: (TextFieldValue) -> Unit,
    onFingerprint: (TextFieldValue) -> Unit, onPinCompared: (Boolean) -> Unit,
    onPreferFullscreen: (Boolean) -> Unit, onRefresh: () -> Unit, onPermission: () -> Unit,
    onConnect: () -> Unit, onDisconnect: () -> Unit, onRetryCleanup: () -> Unit,
    onReleaseInput: () -> Unit, onLocalControls: () -> Unit, onEnableInput: () -> Unit, onEnterFullscreen: () -> Unit, onExitFullscreen: () -> Unit, onClose: () -> Unit,
) {
    val comparisonLabel = stringResource(R.string.vnc_pin_comparison)
    val preferenceLabel = stringResource(R.string.vnc_fullscreen_preference)
    val livePane = state.attempt != null && state.engineState !in
        setOf(null, VncEngine.State.FAILED, VncEngine.State.RETIRED)
    val canEdit = interactive && !state.loading && state.engineState in
        setOf(null, VncEngine.State.FAILED, VncEngine.State.RETIRED)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (livePane) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                AndroidView(factory = { context -> VncFrameView(context).apply { bind(model, attachment) } },
                    modifier = Modifier.fillMaxSize().testTag("vnc_native_view"),
                    onReset = null, onRelease = { it.unbind() }, update = { it.bind(model, attachment); if (state.inputEnabled) it.focusRemoteInput() })
                SessionOverlay(state, selectedProfile, fullscreenLabel, fullscreenPending, interactive, notice,
                    releaseEpoch, onReleaseInput, onLocalControls, onEnableInput, onEnterFullscreen, onExitFullscreen,
                    onDisconnect, onRetryCleanup, onClose)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)).testTag("vnc_connect_form"),
                contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                item {
                    Text(stringResource(R.string.vnc_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.vnc_session_scope))
                    Status(state, notice)
                    Text(fullscreenLabel)
                    if (fullscreenPending) Text(stringResource(R.string.vnc_window_pending))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Action(R.string.vnc_release_input, "vnc_release_input", true, onReleaseInput)
                        Action(R.string.vnc_refresh, "vnc_refresh", canEdit, onRefresh)
                        if (!localGranted) Action(R.string.vnc_permission, "vnc_permission", canEdit, onPermission)
                        if (state.engineState == VncEngine.State.CLEANUP_UNCERTAIN) {
                            Action(R.string.vnc_retry_cleanup, "vnc_retry_cleanup", interactive &&
                                (state.attempt != null || state.canRetryAbandonedCleanup), onRetryCleanup)
                        }
                        Action(R.string.vnc_exit_fullscreen, "vnc_exit_fullscreen", interactive, onExitFullscreen)
                        Action(R.string.vnc_close, "vnc_close", true, onClose)
                    }
                }
                selectedProfile?.let { profile ->
                    item(key = "selected") {
                        Surface(shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
                            color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                Text(stringResource(R.string.vnc_selected), style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.semantics { heading() })
                                Text(profile.label)
                                Text(stringResource(R.string.vnc_endpoint_revision, endpoint(profile), profile.revision))
                                Text(stringResource(R.string.vnc_security_policy))
                            }
                        }
                    }
                    item(key = "fields") {
                        key(fieldEpoch) {
                            Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
                                OutlinedTextField(value = fingerprint, onValueChange = onFingerprint, enabled = canEdit,
                                    label = { Text(stringResource(R.string.vnc_fingerprint)) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                    modifier = Modifier.fillMaxWidth().testTag("vnc_fingerprint"))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = pinCompared, onCheckedChange = onPinCompared, enabled = canEdit,
                                        modifier = Modifier.testTag("vnc_pin_compared").semantics { contentDescription = comparisonLabel })
                                    Text(comparisonLabel)
                                }
                                OutlinedTextField(value = username, onValueChange = onUsername, enabled = canEdit,
                                    label = { Text(stringResource(R.string.vnc_username)) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
                                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                    modifier = Modifier.fillMaxWidth().testTag("vnc_username"))
                                OutlinedTextField(value = password, onValueChange = onPassword, enabled = canEdit,
                                    label = { Text(stringResource(R.string.vnc_password)) }, singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                                    modifier = Modifier.fillMaxWidth().testTag("vnc_password"))
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = preferFullscreen, onCheckedChange = onPreferFullscreen, enabled = canEdit,
                                modifier = Modifier.semantics { contentDescription = preferenceLabel })
                            Text(preferenceLabel)
                        }
                        Action(R.string.vnc_connect, "vnc_connect", canEdit && localGranted && pinCompared &&
                            fingerprint.text.length == 64 && username.text.isNotEmpty() && password.text.isNotEmpty(), onConnect)
                    }
                }
                item {
                    Text(stringResource(R.string.vnc_profiles), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    if (state.profiles.isEmpty()) Text(stringResource(R.string.vnc_no_profiles))
                }
                items(state.profiles, key = { it.id }) { profile ->
                    OutlinedButton(onClick = { onSelect(profile) }, enabled = canEdit,
                        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                        modifier = Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                            .testTag("vnc_profile_${profile.id}").semantics {
                                selected = selectedProfile?.let { it.id == profile.id && it.revision == profile.revision } == true
                            }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(profile.label, style = MaterialTheme.typography.titleMedium)
                            Text(endpoint(profile), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SessionOverlay(
    state: VncUiState, profile: ConnectionProfile?, fullscreenLabel: String, fullscreenPending: Boolean,
    interactive: Boolean, notice: String?, releaseEpoch: Long, onReleaseInput: () -> Unit,
    onLocalControls: () -> Unit, onEnableInput: () -> Unit, onEnterFullscreen: () -> Unit, onExitFullscreen: () -> Unit, onDisconnect: () -> Unit,
    onRetryCleanup: () -> Unit, onClose: () -> Unit,
) {
    var expanded by remember(state.attempt) { mutableStateOf(true) }
    var pinned by remember(state.attempt) { mutableStateOf(false) }
    var settings by remember(state.attempt) { mutableStateOf(false) }
    var left by remember(state.attempt) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var activityEpoch by remember { mutableLongStateOf(0L) }
    var revealEpoch by remember { mutableLongStateOf(0L) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val releaseFocus = remember { FocusRequester() }
    val latestLocalControls by rememberUpdatedState(onLocalControls)
    val focusManager = LocalFocusManager.current
    val accessibility = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    var exploration by remember { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    LaunchedEffect(releaseEpoch) {
        if (releaseEpoch > 0) { expanded = true; revealEpoch++ }
    }
    LaunchedEffect(revealEpoch) {
        if (revealEpoch > 0) { withFrameNanos { }; releaseFocus.requestFocus() }
    }
    DisposableEffect(expanded, pinned, focused, hovered, exploration, settings, state.presented,
        state.issue, notice, activityEpoch) {
        val handler = Handler(Looper.getMainLooper())
        val hide = Runnable { expanded = false }
        if (expanded && !pinned && !focused && !hovered && !exploration && !settings && state.presented &&
            state.issue == VncIssue.NONE && notice == null) handler.postDelayed(hide, 5000)
        onDispose { handler.removeCallbacks(hide) }
    }
    val alignment = if (left) Alignment.TopStart else Alignment.TopEnd
    Surface(modifier = Modifier.align(alignment).padding(CloudyControlMetrics.spaceSmall)
        .widthIn(max = CloudyControlMetrics.comfortableMinHeight * 10)
        .pointerInput(state.attempt) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Enter || event.type == PointerEventType.Press ||
                        event.type == PointerEventType.Scroll) latestLocalControls()
                    // Observe before descendants; never consume or mirror local events remotely.
                }
            }
        }.hoverable(interaction).onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onLocalControls() }.focusGroup(),
        shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius), color = MaterialTheme.colorScheme.surfaceContainer) {
        if (!expanded) {
            OutlinedButton(onClick = { onLocalControls(); expanded = true; revealEpoch++ },
                modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight)
                    .onFocusChanged { if (it.isFocused) { onLocalControls(); expanded = true; revealEpoch++ } }
                    .testTag("vnc_overlay_reveal")) { Text(stringResource(R.string.vnc_controls_collapsed)) }
        } else {
            Column(Modifier.padding(CloudyControlMetrics.spaceMedium),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                // Escape controls precede scrollable context, including large-font/short-window states.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Action(R.string.vnc_release_input, "vnc_release_input", true,
                        { activityEpoch++; onReleaseInput() }, Modifier.focusRequester(releaseFocus))
                    Action(R.string.vnc_exit_fullscreen, "vnc_exit_fullscreen", interactive,
                        { activityEpoch++; onExitFullscreen() })
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Text(profile?.label ?: stringResource(R.string.vnc_original_session), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.vnc_context, profile?.let(::endpoint) ?: stringResource(R.string.vnc_original_session)))
                Status(state, notice)
                Text(fullscreenLabel)
                    if (fullscreenPending) Text(stringResource(R.string.vnc_window_pending))
                Text(stringResource(if (state.inputEnabled) R.string.vnc_input_enabled else R.string.vnc_view_only))
                Action(R.string.vnc_enable_input, "vnc_enable_input", interactive && state.presented && !state.inputEnabled, {
                    focusManager.clearFocus()
                    onEnableInput()
                })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Action(R.string.vnc_enter_fullscreen, "vnc_enter_fullscreen", interactive && state.presented && !fullscreenPending,
                        { activityEpoch++; onEnterFullscreen() })
                    Action(R.string.vnc_settings, "vnc_settings", true, { onLocalControls(); activityEpoch++; settings = true })
                    if (state.engineState == VncEngine.State.CLEANUP_UNCERTAIN) {
                        Action(R.string.vnc_retry_cleanup, "vnc_retry_cleanup", interactive, onRetryCleanup)
                    }
                    Action(R.string.vnc_disconnect, "vnc_disconnect", true, { activityEpoch++; onDisconnect() })
                    Action(R.string.vnc_close, "vnc_close", true, onClose)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    FilterChip(selected = pinned, onClick = { pinned = !pinned }, label = { Text(stringResource(R.string.vnc_pin_controls)) })
                    Action(R.string.vnc_move_controls, "vnc_move_controls", true, { left = !left })
                    Action(R.string.vnc_collapse, "vnc_collapse", state.presented, {
                        focusManager.clearFocus(); expanded = false
                    })
                }
                }
            }
        }
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false; activityEpoch++ },
            title = { Text(stringResource(R.string.vnc_settings)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                Text(stringResource(R.string.vnc_security_policy))
                Text(stringResource(R.string.vnc_settings_scope))
            } },
            confirmButton = { TextButton(onClick = { settings = false; activityEpoch++ }) { Text(stringResource(R.string.vnc_done)) } })
    }
}

@Composable
private fun Status(state: VncUiState, notice: String?) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (state.loading) Text(stringResource(R.string.vnc_loading))
        state.engineState?.let { Text(stringResource(when (it) {
            VncEngine.State.NEW -> R.string.vnc_state_new
            VncEngine.State.STARTING -> R.string.vnc_state_starting
            VncEngine.State.AUTHENTICATING -> R.string.vnc_state_authenticating
            VncEngine.State.INITIALIZED -> R.string.vnc_state_waiting_frame
            VncEngine.State.FIRST_FRAME -> if (state.presented) R.string.vnc_state_presented else R.string.vnc_state_waiting_draw
            VncEngine.State.STOP_REQUESTED -> R.string.vnc_state_stopping
            VncEngine.State.RETIRED -> R.string.vnc_state_retired
            VncEngine.State.FAILED -> R.string.vnc_state_failed
            VncEngine.State.CLEANUP_UNCERTAIN -> R.string.vnc_state_uncertain
        })) }
        if (state.issue != VncIssue.NONE) Text(stringResource(when (state.issue) {
            VncIssue.NONE -> R.string.vnc_no_issue
            VncIssue.WRONG_APP -> R.string.vnc_wrong_app
            VncIssue.BUSY -> R.string.vnc_busy
            VncIssue.PROFILES_UNAVAILABLE -> R.string.vnc_profiles_unavailable
            VncIssue.STALE_PROFILE -> R.string.vnc_stale_profile
            VncIssue.NUMERIC_ENDPOINT_REQUIRED -> R.string.vnc_numeric_only
            VncIssue.PERMISSION_REQUIRED -> R.string.vnc_permission_required
            VncIssue.INVALID_PIN_OR_DURATION -> R.string.vnc_invalid_fields
            VncIssue.CONNECT_REJECTED -> R.string.vnc_connect_rejected
            VncIssue.ENGINE_FAILED -> R.string.vnc_state_failed
            VncIssue.RETIRING -> R.string.vnc_state_stopping
            VncIssue.CLEANUP_UNCERTAIN -> R.string.vnc_state_uncertain
            VncIssue.INTERRUPTED -> R.string.vnc_interrupted
        }))
        if (notice != null) Text(notice)
    }
}
private fun endpoint(profile: ConnectionProfile) =
    "${if (':' in profile.host) "[${profile.host}]" else profile.host}:${profile.port}"

@Composable
private fun Action(label: Int, tag: String, enabled: Boolean, action: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = action, enabled = enabled, shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) {
        Text(stringResource(label))
    }
}
