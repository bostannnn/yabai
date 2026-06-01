package com.example.unifiedreader.feature.settings

import androidx.compose.runtime.Composable
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.direction.ComicReadingDirectionSettingsSection
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ComicReaderModeSettingsSection
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState

@Composable
internal fun RemoteSourceSettingsCard(
    title: String,
    body: String,
    formState: ServerConnectionFormState,
    connectLabel: String,
    syncLabel: String,
    secondarySyncLabel: String? = null,
    onDisplayNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onBackupBaseUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onSecondarySync: (() -> Unit)? = null,
    coverPreloadState: CoverPreloadUiState = CoverPreloadUiState(),
    preloadCoversLabel: String = "Preload covers",
    onPreloadCovers: () -> Unit = {},
    onRetryFailedCovers: () -> Unit = {},
    onCancelPreload: () -> Unit = {},
    onClearCachedCovers: () -> Unit = {},
    readingDirectionLibraries: List<ReadingDirectionLibraryUiState> = emptyList(),
    readerModeLibraries: List<ReaderModeLibraryUiState> = emptyList(),
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit = { _, _ -> },
    onLibraryModeSelected: (String, ComicReaderMode) -> Unit = { _, _ -> },
) {
    SettingsCard(
        title = title,
        body = body,
    ) {
        ServerConnectionForm(
            state = formState,
            connectLabel = connectLabel,
            syncLabel = syncLabel,
            secondarySyncLabel = secondarySyncLabel,
            onDisplayNameChanged = onDisplayNameChanged,
            onBaseUrlChanged = onBaseUrlChanged,
            onBackupBaseUrlChanged = onBackupBaseUrlChanged,
            onUsernameChanged = onUsernameChanged,
            onPasswordChanged = onPasswordChanged,
            onConnect = onConnect,
            onSync = onSync,
            onSecondarySync = onSecondarySync,
        )
        CoverPreloadSection(
            state = coverPreloadState,
            label = preloadCoversLabel,
            onPreloadCovers = onPreloadCovers,
            onRetryFailedCovers = onRetryFailedCovers,
            onCancelPreload = onCancelPreload,
            onClearCachedCovers = onClearCachedCovers,
        )
        ComicReadingDirectionSettingsSection(
            libraries = readingDirectionLibraries,
            onLibraryDirectionSelected = onLibraryDirectionSelected,
        )
        ComicReaderModeSettingsSection(
            libraries = readerModeLibraries,
            onLibraryModeSelected = onLibraryModeSelected,
        )
    }
}
