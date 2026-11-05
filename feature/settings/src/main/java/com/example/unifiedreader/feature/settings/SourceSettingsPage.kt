package com.example.unifiedreader.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.navigation.navigationIcon
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppStatusPill
import com.example.unifiedreader.ui.components.AppStatusTone
import com.example.unifiedreader.ui.components.AppTonalButton

/**
 * One remote source. Connected: the server (address, account, state, Edit opening the form in
 * place), then Sync and the cover cache, then the reading defaults per library. Not connected, it
 * is the sign-in form alone.
 */
@Composable
internal fun SourceSettingsPage(
    entry: SourceSettingsEntry,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryReaderModeSelected: (String, ComicReaderMode) -> Unit,
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val formState = entry.connection.toFormState()
    val readingRows = remember(entry.readingDirectionLibraries, entry.readerModeLibraries) {
        buildReadingDefaultsRows(
            directionLibraries = entry.readingDirectionLibraries,
            modeLibraries = entry.readerModeLibraries,
        )
    }
    val form = @Composable {
        ServerConnectionForm(
            state = formState,
            onDisplayNameChanged = entry.actions.onDisplayNameChanged,
            onBaseUrlChanged = entry.actions.onBaseUrlChanged,
            onBackupBaseUrlChanged = entry.actions.onBackupBaseUrlChanged,
            onUsernameChanged = entry.actions.onUsernameChanged,
            onPasswordChanged = entry.actions.onPasswordChanged,
            onConnect = entry.actions.onConnect,
        )
    }
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        if (!entry.connection.hasStoredSession) {
            SettingsPageLine("Sign in to read your ${entry.sourceType.displayName()} libraries here.")
            AppSettingsGroup { form() }
            SettingsPageLine("Sync, cached covers and reading defaults appear once you are connected.")
            return@SettingsPageContainer
        }
        // A sync or a connection that went wrong says so here, above everything it affects.
        entry.connection.message?.takeIf { entry.connection.isError }?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        var editing by rememberSaveable(entry.sourceType) { mutableStateOf(false) }
        ServerGroup(entry = entry, editing = editing, onEditingChange = { editing = it }, form = form)
        SourceLibraryGroup(
            formState = formState,
            coverPreload = entry.connection.coverPreload,
            actions = entry.actions,
            lastSyncedAtEpochMillis = entry.lastSyncedAtEpochMillis,
        )
        ReadingDefaultsGroup(
            rows = readingRows,
            onLibraryDirectionSelected = onLibraryDirectionSelected,
            onLibraryModeSelected = onLibraryReaderModeSelected,
        )
    }
}

/** A connected server's state as the source page shows it: Connected, Offline or Checking. */
internal fun serverConnectionStatus(connectivity: SourceConnectivityState): SourceHubStatus = when (connectivity) {
    // Offline is a state, not an error: the same quiet tone as Checking (design system, AppStatusPill).
    SourceConnectivityState.Offline -> SourceHubStatus("Offline", AppStatusTone.Neutral)
    SourceConnectivityState.Checking -> SourceHubStatus("Checking", AppStatusTone.Neutral)
    else -> SourceHubStatus("Connected", AppStatusTone.Good)
}

/** "andrew · backup 100.84.32.88": who is signed in, and the second address if there is one. */
internal fun serverAccountLine(connection: RemoteSourceConnectionUiState): String? = listOfNotNull(
    connection.username.takeIf(String::isNotBlank),
    connection.backupBaseUrl.takeIf(String::isNotBlank)?.let { "backup ${settingsHostLabel(it)}" },
).joinToString(" · ").ifEmpty { null }

/**
 * The page's first group: the server's address with the account under it, its state and Edit,
 * which opens the sign-in form in place. Narrow, the state moves into the line under the address.
 */
@Composable
private fun ServerGroup(
    entry: SourceSettingsEntry,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    form: @Composable () -> Unit,
) {
    val connection = entry.connection
    val status = serverConnectionStatus(entry.connectivity)
    val account = serverAccountLine(connection)
    AppSettingsGroup(label = "Server") {
        BoxWithConstraints {
            val wide = resolveSettingsAdaptiveLayout(maxWidth.value.toInt()).useInlineChoiceControls
            AppSettingsRow(
                title = settingsHostLabel(connection.baseUrl).ifBlank { entry.sourceType.displayName() },
                icon = entry.sourceType.navigationIcon(),
                supporting = if (wide) account else listOfNotNull(status.label, account).joinToString(" · "),
                supportingTone = if (wide) null else status.tone,
                trailing = {
                    if (wide) AppStatusPill(label = status.label, tone = status.tone)
                    AppTonalButton(onClick = { onEditingChange(!editing) }) {
                        Text(text = if (editing) "Done" else "Edit")
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = editing,
            enter = expandVertically(AppMotion.spatial()) + fadeIn(AppMotion.effects()),
            exit = shrinkVertically(AppMotion.spatial()) + fadeOut(AppMotion.effects()),
        ) {
            Column { form() }
        }
    }
}

/** The state of a connected server on first launch: a pill, then the address and the account. */
@Composable
internal fun ServerStatus(entry: SourceSettingsEntry) {
    val scheme = MaterialTheme.colorScheme
    val connection = entry.connection
    val status = serverConnectionStatus(entry.connectivity)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppStatusPill(label = status.label, tone = status.tone)
            Text(
                text = listOfNotNull(
                    settingsHostLabel(connection.baseUrl).takeIf(String::isNotBlank),
                    connection.username.takeIf(String::isNotBlank)?.let { "signed in as $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        connection.message?.takeIf { connection.isError }?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
        }
    }
}

/** A line of the page's own, aligned with the group headings. */
@Composable
internal fun SettingsPageLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
