package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.direction.ComicReadingDirectionSettingsSection
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    kavitaReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    stumpReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    kavitaConnection: KavitaConnectionUiState,
    stumpConnection: StumpConnectionUiState,
    grimmoryConnection: GrimmoryConnectionUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    extraSections: LazyListScope.() -> Unit = {},
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onKavitaDisplayNameChanged: (String) -> Unit,
    onKavitaBaseUrlChanged: (String) -> Unit,
    onKavitaBackupBaseUrlChanged: (String) -> Unit,
    onKavitaUsernameChanged: (String) -> Unit,
    onKavitaPasswordChanged: (String) -> Unit,
    onConnectKavita: () -> Unit,
    onStumpDisplayNameChanged: (String) -> Unit,
    onStumpBaseUrlChanged: (String) -> Unit,
    onStumpBackupBaseUrlChanged: (String) -> Unit,
    onStumpUsernameChanged: (String) -> Unit,
    onStumpPasswordChanged: (String) -> Unit,
    onConnectStump: () -> Unit,
    onGrimmoryDisplayNameChanged: (String) -> Unit,
    onGrimmoryBaseUrlChanged: (String) -> Unit,
    onGrimmoryBackupBaseUrlChanged: (String) -> Unit,
    onGrimmoryUsernameChanged: (String) -> Unit,
    onGrimmoryPasswordChanged: (String) -> Unit,
    onConnectGrimmory: () -> Unit,
) {
    val upcomingSettings = listOf(
        "Reading preferences" to "Font, spacing, and page density controls can slot in after the shared app theme is stable.",
        "Offline behavior" to "Cache size, download quality, and cleanup rules can become persistent settings next.",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Appearance is the first persisted app setting. It updates the full Material 3 shell immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard(
                title = "Theme",
                body = "Choose how the app chrome should render across the library and settings surfaces.",
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ThemeMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == themeMode,
                            onClick = { onThemeModeSelected(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                            label = {
                                Text(text = option.label)
                            },
                        )
                    }
                }
                Text(
                    text = themeMode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        extraSections()

        item {
            SettingsCard(
                title = "Kavita",
                body = "Connect a Kavita server and sync comic libraries into the Kavita tab.",
            ) {
                ServerConnectionForm(
                    state = kavitaConnection.toFormState(),
                    connectLabel = "Connect Kavita",
                    syncLabel = "Sync Kavita",
                    onDisplayNameChanged = onKavitaDisplayNameChanged,
                    onBaseUrlChanged = onKavitaBaseUrlChanged,
                    onBackupBaseUrlChanged = onKavitaBackupBaseUrlChanged,
                    onUsernameChanged = onKavitaUsernameChanged,
                    onPasswordChanged = onKavitaPasswordChanged,
                    onConnect = onConnectKavita,
                )
                ComicReadingDirectionSettingsSection(
                    libraries = kavitaReadingDirectionLibraries,
                    onLibraryDirectionSelected = onLibraryDirectionSelected,
                )
            }
        }

        item {
            SettingsCard(
                title = "Grimmory",
                body = "Connect a Grimmory server and sync book libraries into the Grimmory tab.",
            ) {
                ServerConnectionForm(
                    state = grimmoryConnection.toFormState(),
                    connectLabel = "Connect Grimmory",
                    syncLabel = "Sync Grimmory",
                    onDisplayNameChanged = onGrimmoryDisplayNameChanged,
                    onBaseUrlChanged = onGrimmoryBaseUrlChanged,
                    onBackupBaseUrlChanged = onGrimmoryBackupBaseUrlChanged,
                    onUsernameChanged = onGrimmoryUsernameChanged,
                    onPasswordChanged = onGrimmoryPasswordChanged,
                    onConnect = onConnectGrimmory,
                )
            }
        }

        item {
            SettingsCard(
                title = "Stump",
                body = "Connect a Stump server and sync comics or manga grouped by series into the Stump tab.",
            ) {
                ServerConnectionForm(
                    state = stumpConnection.toFormState(),
                    connectLabel = "Connect Stump",
                    syncLabel = "Sync Stump",
                    onDisplayNameChanged = onStumpDisplayNameChanged,
                    onBaseUrlChanged = onStumpBaseUrlChanged,
                    onBackupBaseUrlChanged = onStumpBackupBaseUrlChanged,
                    onUsernameChanged = onStumpUsernameChanged,
                    onPasswordChanged = onStumpPasswordChanged,
                    onConnect = onConnectStump,
                )
                ComicReadingDirectionSettingsSection(
                    libraries = stumpReadingDirectionLibraries,
                    onLibraryDirectionSelected = onLibraryDirectionSelected,
                )
            }
        }

        item {
            Text(
                text = "Planned next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        items(upcomingSettings) { (title, body) ->
            SettingsCard(
                title = title,
                body = body,
            )
        }
    }
}

private data class ServerConnectionFormState(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String,
    val username: String,
    val password: String,
    val hasStoredSession: Boolean,
    val isConnecting: Boolean,
    val syncProgressFraction: Float?,
    val syncProgressLabel: String?,
    val message: String?,
    val isError: Boolean,
    val canSubmit: Boolean,
    val usesSyncLabelForStoredSessionAction: Boolean,
)

@Composable
private fun ServerConnectionForm(
    state: ServerConnectionFormState,
    connectLabel: String,
    syncLabel: String,
    onDisplayNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onBackupBaseUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isConnecting,
            singleLine = true,
            label = { Text(if (state.hasStoredSession) "Password (optional)" else "Password") },
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    enabled = state.password.isNotEmpty() && !state.isConnecting,
                ) {
                    Text(if (isPasswordVisible) "Hide" else "Show")
                }
            },
        )
        Button(
            onClick = onConnect,
            enabled = state.canSubmit,
        ) {
            if (state.isConnecting) {
                CircularProgressIndicator()
            } else if (
                state.usesSyncLabelForStoredSessionAction &&
                state.hasStoredSession &&
                state.password.isBlank()
            ) {
                Text(syncLabel)
            } else {
                Text(connectLabel)
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

@Composable
private fun SettingsCard(
    title: String,
    body: String,
    content: @Composable () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

data class GrimmoryConnectionUiState(
    val displayName: String = "Grimmory",
    val baseUrl: String = "",
    val backupBaseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val hasStoredSession: Boolean = false,
    val isConnecting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

data class KavitaConnectionUiState(
    val displayName: String = "Kavita",
    val baseUrl: String = "",
    val backupBaseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val availableLibraries: List<Library> = emptyList(),
    val hasStoredSession: Boolean = false,
    val isConnecting: Boolean = false,
    val syncProgressFraction: Float? = null,
    val syncProgressLabel: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

data class StumpConnectionUiState(
    val displayName: String = "Stump",
    val baseUrl: String = "",
    val backupBaseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val availableLibraries: List<Library> = emptyList(),
    val hasStoredSession: Boolean = false,
    val isConnecting: Boolean = false,
    val syncProgressFraction: Float? = null,
    val syncProgressLabel: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

private fun GrimmoryConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    syncProgressFraction = null,
    syncProgressLabel = null,
    message = message,
    isError = isError,
    canSubmit = canSubmit,
    usesSyncLabelForStoredSessionAction = true,
)

private fun KavitaConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    syncProgressFraction = syncProgressFraction,
    syncProgressLabel = syncProgressLabel,
    message = message,
    isError = isError,
    canSubmit = canSubmit,
    usesSyncLabelForStoredSessionAction = false,
)

private fun StumpConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    syncProgressFraction = syncProgressFraction,
    syncProgressLabel = syncProgressLabel,
    message = message,
    isError = isError,
    canSubmit = canSubmit,
    usesSyncLabelForStoredSessionAction = false,
)
