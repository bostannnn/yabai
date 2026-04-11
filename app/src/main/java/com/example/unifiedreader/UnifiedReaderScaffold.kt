package com.example.unifiedreader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.downloads.DownloadsSheet
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.settings.SettingsScreen

@Composable
fun UnifiedReaderScaffold(
    uiState: AppUiState,
    libraryViewMode: LibraryViewMode,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onImportLocalFileClick: () -> Unit,
    viewModel: AppViewModel,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState.topLevel) {
                TopLevelDestination.Library -> LibraryTopBar(
                    selectedSource = uiState.selectedSource,
                    searchQuery = uiState.selectedLibrarySearchQuery,
                    seriesTitle = uiState.selectedSeriesTitle,
                    onSourceSelected = { source ->
                        viewModel.onTopLevelSelected(TopLevelDestination.Library)
                        viewModel.onSourceSelected(source)
                    },
                    onSearchQueryChanged = viewModel::onLibrarySearchQueryChanged,
                    downloadBadgeCount = viewModel.downloadsSnapshot.badgeCount,
                    onDownloadsClick = viewModel::showDownloadsSheet,
                    onSettingsClick = {
                        viewModel.onTopLevelSelected(TopLevelDestination.Settings)
                    },
                    onImportLocalFileClick = onImportLocalFileClick,
                    onLeaveSeries = viewModel::onBackPressed,
                )

                TopLevelDestination.Settings -> SettingsTopBar(
                    selectedSource = uiState.selectedSource,
                    onSourceSelected = { source ->
                        viewModel.onTopLevelSelected(TopLevelDestination.Library)
                        viewModel.onSourceSelected(source)
                    },
                    onSettingsClick = {
                        viewModel.onTopLevelSelected(TopLevelDestination.Settings)
                    },
                    onImportLocalFileClick = onImportLocalFileClick,
                )
            }
            when (uiState.topLevel) {
                TopLevelDestination.Library -> LibraryRoute(
                    uiState = uiState,
                    viewMode = libraryViewMode,
                    browseContextKey = uiState.libraryBrowseContextKey,
                    initialScrollPosition = viewModel.libraryScrollPositionFor(uiState.libraryBrowseContextKey),
                    coverImageUrlProvider = viewModel::coverImageUrlFor,
                    onCoverVisible = viewModel::ensureCoverCached,
                    onScrollPositionChanged = { position ->
                        viewModel.onLibraryScrollPositionChanged(
                            contextKey = uiState.libraryBrowseContextKey,
                            position = position,
                        )
                    },
                    onItemSelected = viewModel::onItemSelected,
                    onDeleteImportedItem = viewModel::onDeleteImportedItem,
                    onDownloadRemoteItem = viewModel::onDownloadRemoteItem,
                    onDeleteRemoteLocalFile = viewModel::onDeleteRemoteLocalFile,
                    onFilterStateChanged = viewModel::onLibraryFiltersChanged,
                    onClearFilters = viewModel::clearLibraryFilters,
                    onSortModeChanged = viewModel::onLibrarySortModeChanged,
                    onViewModeChanged = onLibraryViewModeChanged,
                    showSortControl = !uiState.isSeriesBrowseActive,
                    modifier = Modifier.weight(1f),
                )

                TopLevelDestination.Settings -> SettingsRoute(
                    uiState = uiState,
                    onThemeModeSelected = viewModel::onThemeModeSelected,
                    onKavitaDisplayNameChanged = viewModel::onKavitaDisplayNameChanged,
                    onKavitaBaseUrlChanged = viewModel::onKavitaBaseUrlChanged,
                    onKavitaUsernameChanged = viewModel::onKavitaUsernameChanged,
                    onKavitaPasswordChanged = viewModel::onKavitaPasswordChanged,
                    onConnectKavita = viewModel::connectKavitaServer,
                    onStumpDisplayNameChanged = viewModel::onStumpDisplayNameChanged,
                    onStumpBaseUrlChanged = viewModel::onStumpBaseUrlChanged,
                    onStumpUsernameChanged = viewModel::onStumpUsernameChanged,
                    onStumpPasswordChanged = viewModel::onStumpPasswordChanged,
                    onConnectStump = viewModel::connectStumpServer,
                    onGrimmoryDisplayNameChanged = viewModel::onGrimmoryDisplayNameChanged,
                    onGrimmoryBaseUrlChanged = viewModel::onGrimmoryBaseUrlChanged,
                    onGrimmoryUsernameChanged = viewModel::onGrimmoryUsernameChanged,
                    onGrimmoryPasswordChanged = viewModel::onGrimmoryPasswordChanged,
                    onConnectGrimmory = viewModel::connectGrimmoryServer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (viewModel.isDownloadsSheetVisible) {
            DownloadsSheet(
                snapshot = viewModel.downloadsSnapshot,
                onDismiss = viewModel::hideDownloadsSheet,
                onCancel = viewModel::cancelDownload,
                onRetry = viewModel::retryDownload,
                onClearCompleted = viewModel::clearCompletedDownloads,
                onOpenEntry = viewModel::openDownloadedEntry,
            )
        }
    }
}

@Composable
private fun LibraryRoute(
    uiState: AppUiState,
    viewMode: LibraryViewMode,
    browseContextKey: String,
    initialScrollPosition: LibraryScrollPosition,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
    onDownloadRemoteItem: (ReadableItem) -> Unit,
    onDeleteRemoteLocalFile: (ReadableItem) -> Unit,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onViewModeChanged: (LibraryViewMode) -> Unit,
    showSortControl: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LibraryScreen(
            selectedSource = uiState.selectedSource,
            browseContextKey = browseContextKey,
            items = uiState.visibleItems,
            viewMode = viewMode,
            progressByItemId = uiState.displayProgressByItemId,
            locallyAvailableItemIds = uiState.locallyAvailableRemoteItemIds,
            downloadableItemIds = uiState.remoteDownloadActionAvailability.downloadableItemIds,
            deletableDownloadedItemIds = uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
            downloadingItemIds = uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
            isLoadingSource = !uiState.isSelectedSourceHydrated,
            filterState = uiState.selectedLibraryFilters,
            filterFacets = uiState.selectedLibraryFilterFacets,
            libraryTitlesById = uiState.selectedSourceLibraryTitlesById,
            searchQuery = uiState.selectedLibrarySearchQuery,
            sortMode = uiState.selectedLibrarySortMode,
            showSortControl = showSortControl,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onSortModeChanged = onSortModeChanged,
            onViewModeChanged = onViewModeChanged,
            initialScrollPosition = initialScrollPosition,
            onScrollPositionChanged = onScrollPositionChanged,
            onItemSelected = onItemSelected,
            onDeleteImportedItem = onDeleteImportedItem,
            onDownloadItem = onDownloadRemoteItem,
            onDeleteDownloadedItem = onDeleteRemoteLocalFile,
        )
    }
}

@Composable
private fun SettingsRoute(
    uiState: AppUiState,
    onThemeModeSelected: (com.example.unifiedreader.feature.settings.ThemeMode) -> Unit,
    onKavitaDisplayNameChanged: (String) -> Unit,
    onKavitaBaseUrlChanged: (String) -> Unit,
    onKavitaUsernameChanged: (String) -> Unit,
    onKavitaPasswordChanged: (String) -> Unit,
    onConnectKavita: () -> Unit,
    onStumpDisplayNameChanged: (String) -> Unit,
    onStumpBaseUrlChanged: (String) -> Unit,
    onStumpUsernameChanged: (String) -> Unit,
    onStumpPasswordChanged: (String) -> Unit,
    onConnectStump: () -> Unit,
    onGrimmoryDisplayNameChanged: (String) -> Unit,
    onGrimmoryBaseUrlChanged: (String) -> Unit,
    onGrimmoryUsernameChanged: (String) -> Unit,
    onGrimmoryPasswordChanged: (String) -> Unit,
    onConnectGrimmory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        SettingsScreen(
            themeMode = uiState.themeMode,
            kavitaConnection = uiState.kavitaConnection,
            stumpConnection = uiState.stumpConnection,
            grimmoryConnection = uiState.grimmoryConnection,
            onThemeModeSelected = onThemeModeSelected,
            onKavitaDisplayNameChanged = onKavitaDisplayNameChanged,
            onKavitaBaseUrlChanged = onKavitaBaseUrlChanged,
            onKavitaUsernameChanged = onKavitaUsernameChanged,
            onKavitaPasswordChanged = onKavitaPasswordChanged,
            onConnectKavita = onConnectKavita,
            onStumpDisplayNameChanged = onStumpDisplayNameChanged,
            onStumpBaseUrlChanged = onStumpBaseUrlChanged,
            onStumpUsernameChanged = onStumpUsernameChanged,
            onStumpPasswordChanged = onStumpPasswordChanged,
            onConnectStump = onConnectStump,
            onGrimmoryDisplayNameChanged = onGrimmoryDisplayNameChanged,
            onGrimmoryBaseUrlChanged = onGrimmoryBaseUrlChanged,
            onGrimmoryUsernameChanged = onGrimmoryUsernameChanged,
            onGrimmoryPasswordChanged = onGrimmoryPasswordChanged,
            onConnectGrimmory = onConnectGrimmory,
        )
    }
}
