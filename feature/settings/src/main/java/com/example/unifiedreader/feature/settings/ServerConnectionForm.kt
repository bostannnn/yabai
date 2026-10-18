package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppLinearProgress
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField

internal data class ServerConnectionFormState(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String,
    val username: String,
    val password: String,
    val hasStoredSession: Boolean,
    val isConnecting: Boolean,
    val activeAction: ServerConnectionAction?,
    val syncProgressFraction: Float?,
    val syncProgressLabel: String?,
    val message: String?,
    val isError: Boolean,
    val canConnect: Boolean,
)

enum class ServerConnectionAction {
    Connect,
    Sync,
    FastSync,
    FullSync,
}

internal data class ServerConnectionActionState(
    val showSyncButton: Boolean,
    val activeAction: ServerConnectionAction?,
    val isBusy: Boolean,
) {
    val canSync: Boolean
        get() = showSyncButton && !isBusy
}

internal fun buildServerConnectionActionState(
    state: ServerConnectionFormState,
): ServerConnectionActionState {
    return ServerConnectionActionState(
        showSyncButton = state.hasStoredSession,
        activeAction = state.activeAction.takeIf { state.isConnecting },
        isBusy = state.isConnecting,
    )
}

/** The "Server" group of a source page: address and credentials, then one Connect button. */
@Composable
internal fun ServerConnectionForm(
    state: ServerConnectionFormState,
    onDisplayNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onBackupBaseUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val actionState = buildServerConnectionActionState(state)
    val fieldsEnabled = !state.isConnecting

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        run {
            val layout = resolveSettingsAdaptiveLayout(maxWidth.value.toInt())
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTextField(
                    value = state.displayName,
                    onValueChange = onDisplayNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = fieldsEnabled,
                    singleLine = true,
                    label = { Text("Name") },
                )
                ServerFormFields(
                    twoColumns = layout.useTwoColumnServerForm,
                    first = {
                        AppTextField(
                            value = state.baseUrl,
                            onValueChange = onBaseUrlChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = fieldsEnabled,
                            singleLine = true,
                            label = { Text("Server URL") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    },
                    second = {
                        AppTextField(
                            value = state.backupBaseUrl,
                            onValueChange = onBackupBaseUrlChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = fieldsEnabled,
                            singleLine = true,
                            label = { Text("Backup URL") },
                            placeholder = { Text("Optional") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    },
                )
                ServerFormFields(
                    twoColumns = layout.useTwoColumnServerForm,
                    first = {
                        AppTextField(
                            value = state.username,
                            onValueChange = onUsernameChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = fieldsEnabled,
                            singleLine = true,
                            label = { Text("Username") },
                        )
                    },
                    second = {
                        ServerPasswordField(
                            password = state.password,
                            hasStoredSession = state.hasStoredSession,
                            isConnecting = state.isConnecting,
                            isPasswordVisible = isPasswordVisible,
                            onPasswordVisibilityChanged = { isPasswordVisible = it },
                            onPasswordChanged = onPasswordChanged,
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val connecting = actionState.activeAction == ServerConnectionAction.Connect
                        if (connecting && state.syncProgressLabel != null) {
                            AppLinearProgress(
                                progress = { state.syncProgressFraction ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = state.syncProgressLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = state.message ?: if (state.hasStoredSession) {
                                    "Signed in. Reconnect after changing the address or account."
                                } else {
                                    "Sign in to sync this server's libraries."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.isError && state.message != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    AppButton(
                        onClick = onConnect,
                        enabled = state.canConnect,
                        modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                    ) {
                        if (actionState.activeAction == ServerConnectionAction.Connect) {
                            SettingsButtonProgressIndicator()
                        } else {
                            Text(if (state.hasStoredSession) "Reconnect" else "Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsButtonProgressIndicator() {
    AppCircularProgress(
        modifier = Modifier.size(SettingsButtonProgressIndicatorSize),
        strokeWidth = 2.dp,
    )
}

internal val SettingsActionButtonMinHeight = 40.dp
internal val SettingsButtonProgressIndicatorSize = 18.dp

@Composable
private fun ServerPasswordField(
    password: String,
    hasStoredSession: Boolean,
    isConnecting: Boolean,
    isPasswordVisible: Boolean,
    onPasswordVisibilityChanged: (Boolean) -> Unit,
    onPasswordChanged: (String) -> Unit,
) {
    AppTextField(
        value = password,
        onValueChange = onPasswordChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isConnecting,
        singleLine = true,
        label = { Text("Password") },
        placeholder = { if (hasStoredSession) Text("Saved") },
        visualTransformation = if (isPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            AppTextButton(
                onClick = { onPasswordVisibilityChanged(!isPasswordVisible) },
                enabled = password.isNotEmpty() && !isConnecting,
            ) {
                Text(if (isPasswordVisible) "Hide" else "Show")
            }
        },
    )
}

@Composable
private fun ServerFormFields(
    twoColumns: Boolean,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    if (twoColumns) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) { first() }
            Box(modifier = Modifier.weight(1f)) { second() }
        }
    } else {
        first()
        second()
    }
}
