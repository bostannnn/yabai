package com.example.unifiedreader.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppSettingsGroup

/** One remote source: its server, its library actions, and its per-library reading defaults. */
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
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        AppSettingsGroup(label = "Server") {
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
        SourceLibraryGroup(
            formState = formState,
            coverPreload = entry.connection.coverPreload,
            actions = entry.actions,
        )
        ReadingDefaultsGroup(
            rows = readingRows,
            onLibraryDirectionSelected = onLibraryDirectionSelected,
            onLibraryModeSelected = onLibraryReaderModeSelected,
        )
    }
}
