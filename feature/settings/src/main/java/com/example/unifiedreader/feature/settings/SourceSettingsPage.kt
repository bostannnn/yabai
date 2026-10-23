package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.navigation.navigationIcon
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * One remote source. Connected, it leads with what you use: its state, Sync and the cover cache,
 * the reading defaults per library, and the address and account folded into one row whose Edit
 * opens the form. Not connected, it is the sign-in form alone.
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
        ServerStatus(entry = entry)
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
        var editing by rememberSaveable(entry.sourceType) { mutableStateOf(false) }
        AppSettingsGroup(label = "Server") {
            AppSettingsRow(
                title = "Address and account",
                icon = entry.sourceType.navigationIcon(),
                supporting = entry.connection.backupBaseUrl.takeIf(String::isNotBlank)
                    ?.let { backup -> "Backup address ${settingsHostLabel(backup)}" }
                    ?: "No backup address",
                trailing = {
                    AppTonalButton(onClick = { editing = !editing }) {
                        Text(text = if (editing) "Done" else "Edit")
                    }
                },
            )
            if (editing) form()
        }
    }
}

/** The state of a connected server: a pill, then the address and the account. */
@Composable
private fun ServerStatus(entry: SourceSettingsEntry) {
    val semantic = UnifiedReaderThemeTokens.colors
    val scheme = MaterialTheme.colorScheme
    val connection = entry.connection
    val (label, container, content) = when (entry.connectivity) {
        SourceConnectivityState.Offline -> Triple("Offline", scheme.errorContainer, scheme.onErrorContainer)
        SourceConnectivityState.Checking -> Triple("Checking", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        else -> Triple("Connected", semantic.syncAccentContainer, semantic.onSyncAccentContainer)
    }
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppBadge(shape = CircleShape, color = container, contentColor = content) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.connectivity != SourceConnectivityState.Offline && entry.connectivity != SourceConnectivityState.Checking) {
                        Icon(imageVector = AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
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
        // A sync or a connection that went wrong says so here, above everything it affects.
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
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
