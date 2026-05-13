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
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.feature.downloads.DownloadsSheet
import com.example.unifiedreader.feature.journal.JournalScreen
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
            TopLevelDestination.Journal -> SourceNavigationDestination.Journal
            TopLevelDestination.Settings -> SourceNavigationDestination.Settings
        },
        onDestinationSelected = { destination ->
            drawerScope.launch {
                drawerState.close()
            }
            when (destination) {
                is SourceNavigationDestination.Source -> viewModel.openLibrarySource(destination.sourceType)
                SourceNavigationDestination.Journal -> viewModel.onTopLevelSelected(TopLevelDestination.Journal)
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
                        sourceConnectivity = uiState.selectedSourceConnectivity,
                        searchQuery = uiState.selectedLibrarySearchQuery,
                        seriesTitle = uiState.selectedSeriesTitle,
                        onSourceConnectivityClick = viewModel::retrySelectedSourceConnection,
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

                    TopLevelDestination.Journal -> JournalTopBar(
                        onDrawerClick = {
                            drawerScope.launch {
                                drawerState.open()
                            }
                        },
                        onImportLocalFileClick = onImportLocalFileClick,
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
                        scrollToTopRequest = uiState.libraryScrollToTopRequest,
                        coverImageUrlProvider = viewModel::coverImageUrlFor,
                        coverPrefetchKeyProvider = viewModel::coverPrefetchKeyFor,
                        onPrepareCoverSource = viewModel::prepareCoverSource,
                        onCoverVisible = viewModel::ensureCoverCached,
                        onCoverViewportScrollingChanged = viewModel::onLibraryViewportScrollingChanged,
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
                        onReadingListSelected = viewModel::onKavitaReadingListSelected,
                        onSortModeChanged = viewModel::onLibrarySortModeChanged,
                        onViewModeChanged = onLibraryViewModeChanged,
                        onGridCellSizeDpChanged = viewModel::onLibraryGridCellSizeChanged,
                        onFadeReadItemsChanged = viewModel::onLibraryFadeReadItemsChanged,
                        showSortControl = !uiState.isSeriesBrowseActive && !uiState.isKavitaReadingListActive,
                        modifier = Modifier.weight(1f),
                    )

                    TopLevelDestination.Journal -> JournalScreen(
                        snapshot = uiState.journalSnapshot,
                        coverImageUrlProvider = viewModel::coverImageUrlFor,
                        onCoverVisible = viewModel::ensureCoverCached,
                        onDeleteItemJournal = viewModel::deleteItemJournal,
                        onSaveJournalEntry = viewModel::saveJournalEntry,
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
                        onPreloadKavitaCovers = { viewModel.preloadCovers(SourceType.KAVITA) },
                        onRetryFailedKavitaCovers = { viewModel.retryFailedCovers(SourceType.KAVITA) },
                        onCancelKavitaCoverPreload = { viewModel.cancelCoverPreload(SourceType.KAVITA) },
                        onStumpDisplayNameChanged = viewModel::onStumpDisplayNameChanged,
                        onStumpBaseUrlChanged = viewModel::onStumpBaseUrlChanged,
                        onStumpBackupBaseUrlChanged = viewModel::onStumpBackupBaseUrlChanged,
                        onStumpUsernameChanged = viewModel::onStumpUsernameChanged,
                        onStumpPasswordChanged = viewModel::onStumpPasswordChanged,
                        onConnectStump = viewModel::connectStumpServer,
                        onSyncStump = viewModel::refreshStumpLibrary,
                        onPreloadStumpCovers = { viewModel.preloadCovers(SourceType.STUMP) },
                        onRetryFailedStumpCovers = { viewModel.retryFailedCovers(SourceType.STUMP) },
                        onCancelStumpCoverPreload = { viewModel.cancelCoverPreload(SourceType.STUMP) },
                        onGrimmoryDisplayNameChanged = viewModel::onGrimmoryDisplayNameChanged,
                        onGrimmoryBaseUrlChanged = viewModel::onGrimmoryBaseUrlChanged,
                        onGrimmoryBackupBaseUrlChanged = viewModel::onGrimmoryBackupBaseUrlChanged,
                        onGrimmoryUsernameChanged = viewModel::onGrimmoryUsernameChanged,
                        onGrimmoryPasswordChanged = viewModel::onGrimmoryPasswordChanged,
                        onConnectGrimmory = viewModel::connectGrimmoryServer,
                        onSyncGrimmory = viewModel::refreshGrimmoryLibrary,
                        onPreloadGrimmoryCovers = { viewModel.preloadCovers(SourceType.GRIMMORY) },
                        onRetryFailedGrimmoryCovers = { viewModel.retryFailedCovers(SourceType.GRIMMORY) },
                        onCancelGrimmoryCoverPreload = { viewModel.cancelCoverPreload(SourceType.GRIMMORY) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (viewModel.isDownloadsSheetVisible) {
                DownloadsSheet(
                    snapshot = viewModel.downloadsSnapshot,
                    itemResolver = { itemId ->
                        uiState.items.firstOrNull { item -> item.id == itemId }
                    },
                    coverImageUrlProvider = viewModel::coverImageUrlFor,
                    onCoverVisible = viewModel::ensureCoverCached,
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
    onPreloadKavitaCovers: () -> Unit,
    onRetryFailedKavitaCovers: () -> Unit,
    onCancelKavitaCoverPreload: () -> Unit,
    onStumpDisplayNameChanged: (String) -> Unit,
    onStumpBaseUrlChanged: (String) -> Unit,
    onStumpBackupBaseUrlChanged: (String) -> Unit,
    onStumpUsernameChanged: (String) -> Unit,
    onStumpPasswordChanged: (String) -> Unit,
    onConnectStump: () -> Unit,
    onSyncStump: () -> Unit,
    onPreloadStumpCovers: () -> Unit,
    onRetryFailedStumpCovers: () -> Unit,
    onCancelStumpCoverPreload: () -> Unit,
    onGrimmoryDisplayNameChanged: (String) -> Unit,
    onGrimmoryBaseUrlChanged: (String) -> Unit,
    onGrimmoryBackupBaseUrlChanged: (String) -> Unit,
    onGrimmoryUsernameChanged: (String) -> Unit,
    onGrimmoryPasswordChanged: (String) -> Unit,
    onConnectGrimmory: () -> Unit,
    onSyncGrimmory: () -> Unit,
    onPreloadGrimmoryCovers: () -> Unit,
    onRetryFailedGrimmoryCovers: () -> Unit,
    onCancelGrimmoryCoverPreload: () -> Unit,
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
            onPreloadKavitaCovers = onPreloadKavitaCovers,
            onRetryFailedKavitaCovers = onRetryFailedKavitaCovers,
            onCancelKavitaCoverPreload = onCancelKavitaCoverPreload,
            onStumpDisplayNameChanged = onStumpDisplayNameChanged,
            onStumpBaseUrlChanged = onStumpBaseUrlChanged,
            onStumpBackupBaseUrlChanged = onStumpBackupBaseUrlChanged,
            onStumpUsernameChanged = onStumpUsernameChanged,
            onStumpPasswordChanged = onStumpPasswordChanged,
            onConnectStump = onConnectStump,
            onSyncStump = onSyncStump,
            onPreloadStumpCovers = onPreloadStumpCovers,
            onRetryFailedStumpCovers = onRetryFailedStumpCovers,
            onCancelStumpCoverPreload = onCancelStumpCoverPreload,
            onGrimmoryDisplayNameChanged = onGrimmoryDisplayNameChanged,
            onGrimmoryBaseUrlChanged = onGrimmoryBaseUrlChanged,
            onGrimmoryBackupBaseUrlChanged = onGrimmoryBackupBaseUrlChanged,
            onGrimmoryUsernameChanged = onGrimmoryUsernameChanged,
            onGrimmoryPasswordChanged = onGrimmoryPasswordChanged,
            onConnectGrimmory = onConnectGrimmory,
            onSyncGrimmory = onSyncGrimmory,
            onPreloadGrimmoryCovers = onPreloadGrimmoryCovers,
            onRetryFailedGrimmoryCovers = onRetryFailedGrimmoryCovers,
            onCancelGrimmoryCoverPreload = onCancelGrimmoryCoverPreload,
        )
    }
}
