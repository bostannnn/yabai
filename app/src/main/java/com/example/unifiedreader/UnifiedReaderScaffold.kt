package com.example.unifiedreader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.feature.downloads.DownloadsSheet
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.navigation.SourceNavigationDestination
import com.example.unifiedreader.feature.navigation.SourceNavigationDrawer
import com.example.unifiedreader.feature.navigation.SourceNavigationSettingsSection
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.SettingsScreen
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.direction.buildComicReadingDirectionLibraryRows
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.feature.settings.mode.buildComicReaderModeLibraryRows
import kotlinx.coroutines.launch

@Composable
fun UnifiedReaderScaffold(
    uiState: AppUiState,
    libraryViewMode: LibraryViewMode,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onImportLocalFileClick: () -> Unit,
    viewModel: AppViewModel,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val kavitaReadingDirectionLibraries = remember(
        uiState.kavitaConnection.availableLibraries,
        uiState.comicReadingDirectionPreferences,
    ) {
        buildComicReadingDirectionLibraryRows(
            availableLibraries = uiState.kavitaConnection.availableLibraries,
            preferences = uiState.comicReadingDirectionPreferences,
            sourceType = SourceType.KAVITA,
        )
    }
    val stumpReadingDirectionLibraries = remember(
        uiState.stumpConnection.availableLibraries,
        uiState.comicReadingDirectionPreferences,
    ) {
        buildComicReadingDirectionLibraryRows(
            availableLibraries = uiState.stumpConnection.availableLibraries,
            preferences = uiState.comicReadingDirectionPreferences,
            sourceType = SourceType.STUMP,
        )
    }
    val kavitaReaderModeLibraries = remember(
        uiState.kavitaConnection.availableLibraries,
        uiState.comicReaderModePreferences,
    ) {
        buildComicReaderModeLibraryRows(
            availableLibraries = uiState.kavitaConnection.availableLibraries,
            preferences = uiState.comicReaderModePreferences,
            sourceType = SourceType.KAVITA,
        )
    }
    val stumpReaderModeLibraries = remember(
        uiState.stumpConnection.availableLibraries,
        uiState.comicReaderModePreferences,
    ) {
        buildComicReaderModeLibraryRows(
            availableLibraries = uiState.stumpConnection.availableLibraries,
            preferences = uiState.comicReaderModePreferences,
            sourceType = SourceType.STUMP,
        )
    }

    SourceNavigationDrawer(
        drawerState = drawerState,
        sourceOrder = uiState.sourceOrder,
        selectedDestination = when (uiState.topLevel) {
            TopLevelDestination.Library -> SourceNavigationDestination.Source(uiState.selectedSource)
            TopLevelDestination.Settings -> SourceNavigationDestination.Settings
        },
        onDestinationSelected = { destination ->
            drawerScope.launch {
                drawerState.close()
            }
            when (destination) {
                is SourceNavigationDestination.Source -> viewModel.openLibrarySource(destination.sourceType)
                SourceNavigationDestination.Settings -> viewModel.onTopLevelSelected(TopLevelDestination.Settings)
            }
        },
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
                        onDrawerClick = {
                            drawerScope.launch {
                                drawerState.open()
                            }
                        },
                        onSearchQueryChanged = viewModel::onLibrarySearchQueryChanged,
                        downloadBadgeCount = viewModel.downloadsSnapshot.badgeCount,
                        onDownloadsClick = viewModel::showDownloadsSheet,
                        onImportLocalFileClick = onImportLocalFileClick,
                        onLeaveSeries = viewModel::onBackPressed,
                    )

                    TopLevelDestination.Settings -> SettingsTopBar(
                        onDrawerClick = {
                            drawerScope.launch {
                                drawerState.open()
                            }
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
                        coverPrefetchKeyProvider = viewModel::coverPrefetchKeyFor,
                        onCoverVisible = viewModel::ensureCoverCached,
                        onScrollPositionChanged = { position ->
                            viewModel.onLibraryScrollPositionChanged(
                                contextKey = uiState.libraryBrowseContextKey,
                                position = position,
                            )
                        },
                        onItemSelected = viewModel::onItemSelected,
                        seriesReadingDirectionFor = viewModel::seriesReadingDirectionFor,
                        onSeriesDirectionSelected = viewModel::onSeriesDirectionSelected,
                        seriesReaderModeFor = viewModel::seriesReaderModeFor,
                        onSeriesReaderModeSelected = viewModel::onSeriesReaderModeSelected,
                        onDeleteImportedItem = viewModel::onDeleteImportedItem,
                        onDownloadRemoteItem = viewModel::onDownloadRemoteItem,
                        onDeleteRemoteLocalFile = viewModel::onDeleteRemoteLocalFile,
                        onSetReadStatus = viewModel::onSetReadStatus,
                        onFilterStateChanged = viewModel::onLibraryFiltersChanged,
                        onClearFilters = viewModel::clearLibraryFilters,
                        onSortModeChanged = viewModel::onLibrarySortModeChanged,
                        onViewModeChanged = onLibraryViewModeChanged,
                        showSortControl = !uiState.isSeriesBrowseActive,
                        modifier = Modifier.weight(1f),
                    )

                    TopLevelDestination.Settings -> SettingsRoute(
                        uiState = uiState,
                        kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
                        stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
                        kavitaReaderModeLibraries = kavitaReaderModeLibraries,
                        stumpReaderModeLibraries = stumpReaderModeLibraries,
                        onThemeModeSelected = viewModel::onThemeModeSelected,
                        onMoveSourceUp = viewModel::moveSourceUp,
                        onMoveSourceDown = viewModel::moveSourceDown,
                        onLibraryFilterPanelsChanged = viewModel::onLibraryFilterPanelsChanged,
                        onLibraryDirectionSelected = viewModel::onLibraryDirectionSelected,
                        onLibraryReaderModeSelected = viewModel::onLibraryReaderModeSelected,
                        onKavitaDisplayNameChanged = viewModel::onKavitaDisplayNameChanged,
                        onKavitaBaseUrlChanged = viewModel::onKavitaBaseUrlChanged,
                        onKavitaBackupBaseUrlChanged = viewModel::onKavitaBackupBaseUrlChanged,
                        onKavitaUsernameChanged = viewModel::onKavitaUsernameChanged,
                        onKavitaPasswordChanged = viewModel::onKavitaPasswordChanged,
                        onConnectKavita = viewModel::connectKavitaServer,
                        onSyncKavita = viewModel::refreshKavitaLibrary,
                        onStumpDisplayNameChanged = viewModel::onStumpDisplayNameChanged,
                        onStumpBaseUrlChanged = viewModel::onStumpBaseUrlChanged,
                        onStumpBackupBaseUrlChanged = viewModel::onStumpBackupBaseUrlChanged,
                        onStumpUsernameChanged = viewModel::onStumpUsernameChanged,
                        onStumpPasswordChanged = viewModel::onStumpPasswordChanged,
                        onConnectStump = viewModel::connectStumpServer,
                        onSyncStump = viewModel::refreshStumpLibrary,
                        onGrimmoryDisplayNameChanged = viewModel::onGrimmoryDisplayNameChanged,
                        onGrimmoryBaseUrlChanged = viewModel::onGrimmoryBaseUrlChanged,
                        onGrimmoryBackupBaseUrlChanged = viewModel::onGrimmoryBackupBaseUrlChanged,
                        onGrimmoryUsernameChanged = viewModel::onGrimmoryUsernameChanged,
                        onGrimmoryPasswordChanged = viewModel::onGrimmoryPasswordChanged,
                        onConnectGrimmory = viewModel::connectGrimmoryServer,
                        onSyncGrimmory = viewModel::refreshGrimmoryLibrary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (viewModel.isDownloadsSheetVisible) {
                DownloadsSheet(
                    snapshot = viewModel.downloadsSnapshot,
                    onDismiss = viewModel::hideDownloadsSheet,
                    onCancel = viewModel::cancelDownload,
                    onRemove = viewModel::removeDownload,
                    onRetry = viewModel::retryDownload,
                    onClearCompleted = viewModel::clearCompletedDownloads,
                    onOpenEntry = viewModel::openDownloadedEntry,
                )
            }
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
    coverPrefetchKeyProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    seriesReadingDirectionFor: (ReadableItem) -> ComicReadingDirection?,
    onSeriesDirectionSelected: (ReadableItem, ComicReadingDirection) -> Unit,
    seriesReaderModeFor: (ReadableItem) -> ComicReaderMode?,
    onSeriesReaderModeSelected: (ReadableItem, ComicReaderMode) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
    onDownloadRemoteItem: (ReadableItem) -> Unit,
    onDeleteRemoteLocalFile: (ReadableItem) -> Unit,
    onSetReadStatus: (ReadableItem, Boolean) -> Unit,
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
            filterPanels = uiState.selectedLibraryFilterPanels,
            filterFacets = uiState.selectedLibraryFilterFacets,
            libraryTitlesById = uiState.selectedSourceLibraryTitlesById,
            searchQuery = uiState.selectedLibrarySearchQuery,
            sortMode = uiState.selectedLibrarySortMode,
            showSortControl = showSortControl,
            coverImageUrlProvider = coverImageUrlProvider,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            onCoverVisible = onCoverVisible,
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onSortModeChanged = onSortModeChanged,
            onViewModeChanged = onViewModeChanged,
            initialScrollPosition = initialScrollPosition,
            onScrollPositionChanged = onScrollPositionChanged,
            onItemSelected = onItemSelected,
            seriesReadingDirectionFor = seriesReadingDirectionFor,
            onSeriesDirectionSelected = onSeriesDirectionSelected,
            seriesReaderModeFor = seriesReaderModeFor,
            onSeriesReaderModeSelected = onSeriesReaderModeSelected,
            onDeleteImportedItem = onDeleteImportedItem,
            onDownloadItem = onDownloadRemoteItem,
            onDeleteDownloadedItem = onDeleteRemoteLocalFile,
            onSetReadStatus = onSetReadStatus,
        )
    }
}

@Composable
private fun SettingsRoute(
    uiState: AppUiState,
    kavitaReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    stumpReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    kavitaReaderModeLibraries: List<ReaderModeLibraryUiState>,
    stumpReaderModeLibraries: List<ReaderModeLibraryUiState>,
    onThemeModeSelected: (com.example.unifiedreader.feature.settings.ThemeMode) -> Unit,
    onMoveSourceUp: (SourceType) -> Unit,
    onMoveSourceDown: (SourceType) -> Unit,
    onLibraryFilterPanelsChanged: (SourceType, List<LibraryFilterPanel>) -> Unit,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryReaderModeSelected: (String, ComicReaderMode) -> Unit,
    onKavitaDisplayNameChanged: (String) -> Unit,
    onKavitaBaseUrlChanged: (String) -> Unit,
    onKavitaBackupBaseUrlChanged: (String) -> Unit,
    onKavitaUsernameChanged: (String) -> Unit,
    onKavitaPasswordChanged: (String) -> Unit,
    onConnectKavita: () -> Unit,
    onSyncKavita: () -> Unit,
    onStumpDisplayNameChanged: (String) -> Unit,
    onStumpBaseUrlChanged: (String) -> Unit,
    onStumpBackupBaseUrlChanged: (String) -> Unit,
    onStumpUsernameChanged: (String) -> Unit,
    onStumpPasswordChanged: (String) -> Unit,
    onConnectStump: () -> Unit,
    onSyncStump: () -> Unit,
    onGrimmoryDisplayNameChanged: (String) -> Unit,
    onGrimmoryBaseUrlChanged: (String) -> Unit,
    onGrimmoryBackupBaseUrlChanged: (String) -> Unit,
    onGrimmoryUsernameChanged: (String) -> Unit,
    onGrimmoryPasswordChanged: (String) -> Unit,
    onConnectGrimmory: () -> Unit,
    onSyncGrimmory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        SettingsScreen(
            themeMode = uiState.themeMode,
            kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
            stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
            kavitaReaderModeLibraries = kavitaReaderModeLibraries,
            stumpReaderModeLibraries = stumpReaderModeLibraries,
            kavitaConnection = uiState.kavitaConnection,
            stumpConnection = uiState.stumpConnection,
            grimmoryConnection = uiState.grimmoryConnection,
            onThemeModeSelected = onThemeModeSelected,
            libraryFilterPanelsBySource = SourceType.entries.associateWith { sourceType ->
                resolveLibraryFilterPanels(
                    sourceType = sourceType,
                    preferredPanels = uiState.libraryFilterPanelsBySource[sourceType],
                )
            },
            onLibraryFilterPanelsChanged = onLibraryFilterPanelsChanged,
            supplementarySection = com.example.unifiedreader.feature.settings.SettingsSupplementarySection(
                id = "navigation",
                title = "Navigation",
                content = {
                    SourceNavigationSettingsSection(
                        sourceOrder = uiState.sourceOrder,
                        onMoveSourceUp = onMoveSourceUp,
                        onMoveSourceDown = onMoveSourceDown,
                    )
                },
            ),
            onLibraryDirectionSelected = onLibraryDirectionSelected,
            onLibraryReaderModeSelected = onLibraryReaderModeSelected,
            onKavitaDisplayNameChanged = onKavitaDisplayNameChanged,
            onKavitaBaseUrlChanged = onKavitaBaseUrlChanged,
            onKavitaBackupBaseUrlChanged = onKavitaBackupBaseUrlChanged,
            onKavitaUsernameChanged = onKavitaUsernameChanged,
            onKavitaPasswordChanged = onKavitaPasswordChanged,
            onConnectKavita = onConnectKavita,
            onSyncKavita = onSyncKavita,
            onStumpDisplayNameChanged = onStumpDisplayNameChanged,
            onStumpBaseUrlChanged = onStumpBaseUrlChanged,
            onStumpBackupBaseUrlChanged = onStumpBackupBaseUrlChanged,
            onStumpUsernameChanged = onStumpUsernameChanged,
            onStumpPasswordChanged = onStumpPasswordChanged,
            onConnectStump = onConnectStump,
            onSyncStump = onSyncStump,
            onGrimmoryDisplayNameChanged = onGrimmoryDisplayNameChanged,
            onGrimmoryBaseUrlChanged = onGrimmoryBaseUrlChanged,
            onGrimmoryBackupBaseUrlChanged = onGrimmoryBackupBaseUrlChanged,
            onGrimmoryUsernameChanged = onGrimmoryUsernameChanged,
            onGrimmoryPasswordChanged = onGrimmoryPasswordChanged,
            onConnectGrimmory = onConnectGrimmory,
            onSyncGrimmory = onSyncGrimmory,
        )
    }
}
