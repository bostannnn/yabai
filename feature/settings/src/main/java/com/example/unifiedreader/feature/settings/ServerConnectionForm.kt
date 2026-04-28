package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

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

@Composable
internal fun ServerConnectionForm(
    state: ServerConnectionFormState,
    connectLabel: String,
    syncLabel: String,
    onDisplayNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onBackupBaseUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    secondarySyncLabel: String? = null,
    onSecondarySync: (() -> Unit)? = null,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val actionState = buildServerConnectionActionState(state)

    BoxWithConstraints {
        val layout = resolveSettingsAdaptiveLayout(maxWidth.value.toInt())

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (layout.useTwoColumnServerForm) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = onDisplayNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isConnecting,
                    singleLine = true,
                    label = { Text("Name") },
                )
                ServerFormFieldRow(
                    start = {
                        OutlinedTextField(
                            value = state.baseUrl,
                            onValueChange = onBaseUrlChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isConnecting,
                            singleLine = true,
                            label = { Text("Server URL") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    },
                    end = {
                        OutlinedTextField(
                            value = state.backupBaseUrl,
                            onValueChange = onBackupBaseUrlChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isConnecting,
                            singleLine = true,
                            label = { Text("Backup URL (optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    },
                )
                ServerFormFieldRow(
                    start = {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isConnecting,
                            singleLine = true,
                            label = { Text("Username") },
                        )
                    },
                    end = {
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
            } else {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = onDisplayNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isConnecting,
                    singleLine = true,
                    label = { Text("Name") },
                )
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isConnecting,
                    singleLine = true,
                    label = { Text("Server URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = state.backupBaseUrl,
                    onValueChange = onBackupBaseUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isConnecting,
                    singleLine = true,
                    label = { Text("Backup URL (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isConnecting,
                    singleLine = true,
                    label = { Text("Username") },
                )
            }
            if (!layout.useTwoColumnServerForm) {
                ServerPasswordField(
                    password = state.password,
                    hasStoredSession = state.hasStoredSession,
                    isConnecting = state.isConnecting,
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChanged = { isPasswordVisible = it },
                    onPasswordChanged = onPasswordChanged,
                )
            }
            if (actionState.showSyncButton) {
                if (layout.useTwoColumnServerForm) {
                    ServerFormFieldRow(
                        start = {
                            Button(
                                onClick = onConnect,
                                enabled = state.canConnect,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = SettingsActionButtonMinHeight),
                            ) {
                                if (actionState.activeAction == ServerConnectionAction.Connect) {
                                    SettingsButtonProgressIndicator()
                                } else {
                                    Text(connectLabel)
                                }
                            }
                        },
                        end = {
                            ServerSyncButtons(
                                syncLabel = syncLabel,
                                secondarySyncLabel = secondarySyncLabel,
                                actionState = actionState,
                                onSync = onSync,
                                onSecondarySync = onSecondarySync,
                            )
                        },
                    )
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = state.canConnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SettingsActionButtonMinHeight),
                    ) {
                        if (actionState.activeAction == ServerConnectionAction.Connect) {
                            SettingsButtonProgressIndicator()
                        } else {
                            Text(connectLabel)
                        }
                    }
                    ServerSyncButtons(
                        syncLabel = syncLabel,
                        secondarySyncLabel = secondarySyncLabel,
                        actionState = actionState,
                        onSync = onSync,
                        onSecondarySync = onSecondarySync,
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = state.canConnect,
                    modifier = if (layout.useTwoColumnServerForm) {
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 240.dp)
                            .heightIn(min = SettingsActionButtonMinHeight)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = SettingsActionButtonMinHeight)
                    },
                ) {
                    if (actionState.activeAction == ServerConnectionAction.Connect) {
                        SettingsButtonProgressIndicator()
                    } else {
                        Text(connectLabel)
                    }
                }
            }
            if (state.isConnecting && state.syncProgressLabel != null) {
                LinearProgressIndicator(
                    progress = { state.syncProgressFraction ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.syncProgressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
internal fun SettingsButtonProgressIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(SettingsButtonProgressIndicatorSize),
        strokeWidth = 2.dp,
    )
}

@Composable
private fun ServerSyncButtons(
    syncLabel: String,
    secondarySyncLabel: String?,
    actionState: ServerConnectionActionState,
    onSync: () -> Unit,
    onSecondarySync: (() -> Unit)?,
) {
    if (secondarySyncLabel == null || onSecondarySync == null) {
        Button(
            onClick = onSync,
            enabled = actionState.canSync,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsActionButtonMinHeight),
        ) {
            if (actionState.activeAction == ServerConnectionAction.Sync) {
                SettingsButtonProgressIndicator()
            } else {
                Text(syncLabel)
            }
        }
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onSync,
            enabled = actionState.canSync,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SettingsActionButtonMinHeight),
        ) {
            if (actionState.activeAction == ServerConnectionAction.Sync) {
                SettingsButtonProgressIndicator()
            } else {
                Text(syncLabel)
            }
        }
        Button(
            onClick = onSecondarySync,
            enabled = actionState.canSync,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SettingsActionButtonMinHeight),
        ) {
            if (actionState.activeAction == ServerConnectionAction.FullSync) {
                SettingsButtonProgressIndicator()
            } else {
                Text(secondarySyncLabel)
            }
        }
    }
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
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isConnecting,
        singleLine = true,
        label = { Text(if (hasStoredSession) "Password (optional)" else "Password") },
        visualTransformation = if (isPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(
                onClick = { onPasswordVisibilityChanged(!isPasswordVisible) },
                enabled = password.isNotEmpty() && !isConnecting,
            ) {
                Text(if (isPasswordVisible) "Hide" else "Show")
            }
        },
    )
}

@Composable
private fun ServerFormFieldRow(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            start()
        }
        Box(modifier = Modifier.weight(1f)) {
            end()
        }
    }
}
