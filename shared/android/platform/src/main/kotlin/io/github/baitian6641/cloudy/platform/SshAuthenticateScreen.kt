package io.github.baitian6641.cloudy.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baitian6641.cloudy.design.CloudyControlMetrics
import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshObservationProfile

/** Values remain owned by the Activity; this screen neither saves input nor performs operations. */
@Composable
internal fun SshAuthenticateScreen(
    catalog: ConnectionProfileCatalog?, selected: SshObservationProfile?,
    username: TextFieldValue, password: TextFieldValue, credentialEpoch: Long, busy: Boolean, message: String?,
    onSelect: (ConnectionProfile) -> Unit,
    onUsername: (TextFieldValue) -> Unit, onPassword: (TextFieldValue) -> Unit,
    onAuthenticate: () -> Unit, onCancel: () -> Unit, onRefresh: () -> Unit, onClose: () -> Unit,
    offerLocalPermission: Boolean, onLocalPermission: () -> Unit,
) {
    val scroll = rememberLazyListState()
    LaunchedEffect(selected?.profile?.id) { if (selected != null) scroll.scrollToItem(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(state = scroll,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .testTag("ssh_auth_list"),
            contentPadding = PaddingValues(CloudyControlMetrics.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceMedium)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    Text(stringResource(R.string.ssh_auth_heading), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.ssh_auth_scope), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item(key = "status") {
                Text(message ?: stringResource(R.string.ssh_auth_choose), modifier = Modifier.testTag("ssh_auth_status")
                    .semantics { liveRegion = LiveRegionMode.Polite })
            }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                    AuthButton(R.string.ssh_auth_cancel, "ssh_auth_cancel", true, onCancel)
                    AuthButton(R.string.ssh_auth_refresh, "ssh_auth_refresh", !busy, onRefresh)
                    AuthButton(R.string.ssh_auth_close, "ssh_auth_close", true, onClose)
                    if (offerLocalPermission) AuthButton(R.string.ssh_auth_local_permission,
                        "ssh_auth_local_permission", !busy, onLocalPermission)
                }
            }
            selected?.let { binding ->
                item(key = "identity") {
                    Surface(shape = RoundedCornerShape(CloudyControlMetrics.dialogRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CloudyControlMetrics.spaceLarge),
                            verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                            Text(stringResource(R.string.ssh_auth_selected), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                                    Text(binding.profile.label, modifier = Modifier.testTag("ssh_auth_selected_label"))
                                    Text(stringResource(R.string.ssh_auth_endpoint, endpoint(binding.profile)),
                                        modifier = Modifier.testTag("ssh_auth_selected_peer"))
                                    Text(stringResource(R.string.ssh_auth_direct))
                                    Text(stringResource(R.string.ssh_auth_revision, binding.profile.revision))
                                }
                            }
                        }
                    }
                }
                item(key = "username") {
                    key(credentialEpoch) {
                    OutlinedTextField(value = username, onValueChange = onUsername, enabled = !busy,
                        label = { Text(stringResource(R.string.ssh_auth_username)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                        modifier = Modifier.fillMaxWidth().testTag("ssh_auth_username"))
                    }
                }
                item(key = "password") {
                    key(credentialEpoch) {
                    OutlinedTextField(value = password, onValueChange = onPassword, enabled = !busy,
                        label = { Text(stringResource(R.string.ssh_auth_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        shape = RoundedCornerShape(CloudyControlMetrics.fieldRadius),
                        modifier = Modifier.fillMaxWidth().testTag("ssh_auth_password"))
                    }
                }
                item(key = "authenticate") {
                    AuthButton(R.string.ssh_auth_authenticate, "ssh_auth_authenticate",
                        !busy && username.text.isNotEmpty() && password.text.isNotEmpty(), onAuthenticate)
                }
            }
            item(key = "profiles-heading") {
                Text(stringResource(R.string.ssh_auth_profiles), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            val profiles = catalog?.profiles?.filter { it.protocol == ConnectionProtocol.SSH }
            if (profiles == null) item(key = "unavailable") { Text(stringResource(R.string.ssh_auth_catalog_unavailable)) }
            else if (profiles.isEmpty()) item(key = "empty") { Text(stringResource(R.string.ssh_auth_empty)) }
            else items(profiles, key = { it.id }) { profile ->
                OutlinedButton(onClick = { onSelect(profile) }, enabled = !busy,
                    shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
                    modifier = Modifier.fillMaxWidth().heightIn(min = CloudyControlMetrics.controlMinHeight)
                        .testTag("ssh_auth_profile_${profile.id}").semantics { this.selected = selected?.profile?.id == profile.id }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CloudyControlMetrics.spaceSmall)) {
                        Text(profile.label, style = MaterialTheme.typography.titleMedium)
                        Text(endpoint(profile), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun endpoint(profile: ConnectionProfile): String =
    "${if (':' in profile.host) "[${profile.host}]" else profile.host}:${profile.port}"

@Composable
private fun AuthButton(label: Int, tag: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled,
        shape = RoundedCornerShape(CloudyControlMetrics.buttonRadius),
        modifier = Modifier.heightIn(min = CloudyControlMetrics.controlMinHeight).testTag(tag)) {
        Text(stringResource(label))
    }
}
