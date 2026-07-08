package com.example.unifiedreader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.downloads.DownloadsSheet
import com.example.unifiedreader.feature.journal.JournalScreen
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserTopBar
import com.example.unifiedreader.feature.library.shelves.browserState
import com.example.unifiedreader.feature.library.shelves.rememberLibraryShelfBrowserSearchQuery
import com.example.unifiedreader.feature.navigation.SourceNavigationDestination
import com.example.unifiedreader.feature.navigation.SourceNavigationDrawer
import com.example.unifiedreader.feature.navigation.SourceNavigationSettingsSection
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.SettingsScreen
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import com.example.unifiedreader.feature.settings.SyncSettingsItemRow
import com.example.unifiedreader.feature.settings.SyncSettingsSourceGroup
import com.example.unifiedreader.feature.settings.SyncSettingsUiState
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.direction.buildComicReadingDirectionLibraryRows
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.feature.settings.mode.buildComicReaderModeLibraryRows
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun UnifiedReaderScaffold(
    uiState: AppUiState,
    libraryViewMode: LibraryViewMode,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onImportLocalFileClick: () -> Unit,
    viewModel: AppViewModel,
) {
    val themeColors = UnifiedReaderThemeTokens.colors
    val appBackgroundRecipe = UnifiedReaderThemeTokens.components.appBackground
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var isLibraryDownloadSelectionMode by rememberSaveable(uiState.libraryBrowseContextKey) {
        mutableStateOf(false)
    }
    var selectedDownloadItemIds by rememberSaveable(uiState.libraryBrowseContextKey) {
        mutableStateOf<List<String>>(emptyList())
    }
    var selectableDownloadItemIds by remember(uiState.libraryBrowseContextKey) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var hasScreenSelectableDownloadIds by remember(uiState.libraryBrowseContextKey) {
        mutableStateOf(false)
    }
    var activeLibraryShelfBrowserKind by rememberSaveable(uiState.libraryBrowseContextKey) {
        mutableStateOf<LibraryShelfBrowserKind?>(null)
    }
    var activeLibraryShelfSearchQuery by rememberLibraryShelfBrowserSearchQuery(
        browseContextKey = uiState.libraryBrowseContextKey,
        browserKind = activeLibraryShelfBrowserKind,
    )
    val activeLibraryShelfBrowser = if (uiState.topLevel == TopLevelDestination.Library) {
        activeLibraryShelfBrowserKind?.let(uiState.libraryShelvesState::browserState)
    } else {
        null
    }
    val visibleDownloadableItemIds = remember(
        uiState.visibleItems,
        uiState.remoteDownloadActionAvailability.downloadableItemIds,
        uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
    ) {
        uiState.visibleItems
            .asSequence()
            .map { item -> item.id }
            .filter { itemId ->
                itemId in uiState.remoteDownloadActionAvailability.downloadableItemIds &&
                    itemId !in uiState.remoteDownloadActionAvailability.activeDownloadingItemIds
            }
            .toSet()
    }
    val effectiveSelectableDownloadItemIds = if (hasScreenSelectableDownloadIds) {
        selectableDownloadItemIds
    } else {
        visibleDownloadableItemIds
    }
    val selectedDownloadItemIdSet = selectedDownloadItemIds
        .filter { itemId -> itemId in effectiveSelectableDownloadItemIds }
        .toSet()

    LaunchedEffect(
        effectiveSelectableDownloadItemIds,
        selectedDownloadItemIds,
        isLibraryDownloadSelectionMode,
    ) {
        val sanitizedSelection = selectedDownloadItemIds
            .filter { itemId -> itemId in effectiveSelectableDownloadItemIds }
        if (sanitizedSelection != selectedDownloadItemIds) {
            selectedDownloadItemIds = sanitizedSelection
        }
        if (effectiveSelectableDownloadItemIds.isEmpty() && isLibraryDownloadSelectionMode) {
            isLibraryDownloadSelectionMode = false
            selectedDownloadItemIds = emptyList()
        }
    }
    LaunchedEffect(uiState.topLevel, activeLibraryShelfBrowserKind, activeLibraryShelfBrowser) {
        if (uiState.topLevel != TopLevelDestination.Library ||
            activeLibraryShelfBrowserKind != null && activeLibraryShelfBrowser == null
        ) {
            activeLibraryShelfBrowserKind = null
        }
    }
    BackHandler(enabled = activeLibraryShelfBrowser != null) {
        activeLibraryShelfBrowserKind = null
    }

    fun updateLibraryDownloadSelectionMode(enabled: Boolean) {
        isLibraryDownloadSelectionMode = enabled
        if (!enabled) {
            selectedDownloadItemIds = emptyList()
        }
    }

    fun selectAllLibraryDownloadItems() {
        selectedDownloadItemIds = uiState.visibleItems
            .map { item -> item.id }
            .filter { itemId -> itemId in effectiveSelectableDownloadItemIds }
    }

    fun downloadSelectedLibraryItems() {
        val selectedItems = uiState.visibleItems
            .filter { item -> item.id in selectedDownloadItemIdSet }
        if (selectedItems.isEmpty()) return
        viewModel.onDownloadRemoteItems(selectedItems)
        updateLibraryDownloadSelectionMode(false)
    }

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
            TopLevelDestination.Library -> if (uiState.selectedSource == SourceType.KAVITA) {
                SourceNavigationDestination.KavitaSection(uiState.kavitaLibrarySection)
            } else {
                SourceNavigationDestination.Source(uiState.selectedSource)
            }
            TopLevelDestination.Journal -> SourceNavigationDestination.Journal
            TopLevelDestination.Settings -> SourceNavigationDestination.Settings
        },
        onDestinationSelected = { destination ->
            drawerScope.launch {
                drawerState.close()
            }
            when (destination) {
                is SourceNavigationDestination.Source -> viewModel.openLibrarySource(destination.sourceType)
                is SourceNavigationDestination.KavitaSection -> viewModel.openKavitaLibrarySection(destination.section)
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
                    .unifiedReaderSurfacePattern(
                        treatment = appBackgroundRecipe.surfaceTreatment,
                        accentColor = themeColors.readerAccent,
                        overlayColor = MaterialTheme.colorScheme.onBackground,
                        intensity = appBackgroundRecipe.patternIntensity,
                    )
                    .padding(innerPadding),
            ) {
                when (uiState.topLevel) {
                    TopLevelDestination.Library -> if (activeLibraryShelfBrowser != null) {
                        LibraryShelfBrowserTopBar(
                            title = activeLibraryShelfBrowser.title,
                            count = activeLibraryShelfBrowser.count,
                            searchQuery = activeLibraryShelfSearchQuery,
                            onSearchQueryChanged = { query -> activeLibraryShelfSearchQuery = query },
                            onBack = { activeLibraryShelfBrowserKind = null },
                        )
                    } else {
                        LibraryTopBar(
                            selectedSource = uiState.selectedSource,
                            sourceConnectivity = uiState.selectedSourceConnectivity,
                            searchQuery = uiState.selectedLibrarySearchQuery,
                            seriesTitle = uiState.selectedLibraryContextTitle,
                            downloadSelectionState = LibraryDownloadSelectionTopBarState(
                                isSelectionMode = isLibraryDownloadSelectionMode,
                                selectedCount = selectedDownloadItemIdSet.size,
                                selectableCount = effectiveSelectableDownloadItemIds.size,
                            ),
                            refreshState = LibraryRefreshTopBarState(
                                isVisible = uiState.selectedSource == SourceType.KAVITA &&
                                    uiState.kavitaConnection.hasStoredSession,
                                isEnabled = uiState.selectedSource == SourceType.KAVITA &&
                                    uiState.kavitaConnection.hasStoredSession &&
                                    !uiState.kavitaConnection.isConnecting,
                                isRefreshing = uiState.selectedSource == SourceType.KAVITA &&
                                    uiState.kavitaConnection.hasStoredSession &&
                                    uiState.kavitaConnection.isConnecting &&
                                    uiState.kavitaConnection.activeAction == ServerConnectionAction.FastSync,
                            ),
                            onSourceConnectivityClick = viewModel::retrySelectedSourceConnection,
                            onDrawerClick = {
                                drawerScope.launch {
                                    drawerState.open()
                                }
                            },
                            onSearchQueryChanged = viewModel::onLibrarySearchQueryChanged,
                            onRefreshClick = viewModel::fastSyncKavitaLibraryInPlace,
                            downloadBadgeCount = viewModel.downloadsSnapshot.badgeCount,
                            onDownloadsClick = viewModel::showDownloadsSheet,
                            onDownloadSelectionModeChanged = ::updateLibraryDownloadSelectionMode,
                            onSelectAllDownloadItems = ::selectAllLibraryDownloadItems,
                            onDownloadSelectedItems = ::downloadSelectedLibraryItems,
                            onImportLocalFileClick = onImportLocalFileClick,
                            onLeaveSeries = viewModel::onBackPressed,
                        )
                    }

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
                        isDownloadSelectionMode = isLibraryDownloadSelectionMode,
                        selectedDownloadItemIds = selectedDownloadItemIds,
                        onDownloadSelectionModeChanged = ::updateLibraryDownloadSelectionMode,
                        onSelectedDownloadItemIdsChanged = { selectedIds ->
                            selectedDownloadItemIds = selectedIds
                        },
                        onSelectableDownloadItemIdsChanged = { selectableIds ->
                            hasScreenSelectableDownloadIds = true
                            selectableDownloadItemIds = selectableIds
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
                        activeShelfBrowserKind = activeLibraryShelfBrowserKind,
                        onActiveShelfBrowserKindChanged = { activeLibraryShelfBrowserKind = it },
                        shelfSearchQuery = activeLibraryShelfSearchQuery,
                        onShelfSearchQueryChanged = { query -> activeLibraryShelfSearchQuery = query },
                        modifier = Modifier.weight(1f),
                    )

                    TopLevelDestination.Journal -> {
                        // Only compute the heavy full journal snapshot (stats/timelines/catalog
                        // join) while the Journal tab is actually on screen; elsewhere uiState
                        // carries just the lightweight write-state.
                        val journalSnapshot by viewModel.journalFullSnapshotFlow
                            .collectAsStateWithLifecycle(initialValue = uiState.journalSnapshot)
                        JournalScreen(
                            snapshot = journalSnapshot,
                            coverImageUrlProvider = viewModel::coverImageUrlFor,
                            onCoverVisible = viewModel::ensureCoverCached,
                            onDeleteItemJournal = viewModel::deleteItemJournal,
                            onSaveJournalEntry = viewModel::saveJournalEntry,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    TopLevelDestination.Settings -> SettingsRoute(
                        uiState = uiState,
                        kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
                        stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
                        kavitaReaderModeLibraries = kavitaReaderModeLibraries,
                        stumpReaderModeLibraries = stumpReaderModeLibraries,
                        onThemeModeSelected = viewModel::onThemeModeSelected,
                        onRetrySync = viewModel::retryPendingRemoteSync,
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
                        onFastSyncKavita = viewModel::fastSyncKavitaLibrary,
                        onPreloadKavitaCovers = { viewModel.preloadCovers(SourceType.KAVITA) },
                        onRetryFailedKavitaCovers = { viewModel.retryFailedCovers(SourceType.KAVITA) },
                        onCancelKavitaCoverPreload = { viewModel.cancelCoverPreload(SourceType.KAVITA) },
                        onClearKavitaCoverCache = { viewModel.clearCachedCovers(SourceType.KAVITA) },
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
                        onClearStumpCoverCache = { viewModel.clearCachedCovers(SourceType.STUMP) },
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
                        onClearGrimmoryCoverCache = { viewModel.clearCachedCovers(SourceType.GRIMMORY) },
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
    onRetrySync: () -> Unit,
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
    onFastSyncKavita: () -> Unit,
    onPreloadKavitaCovers: () -> Unit,
    onRetryFailedKavitaCovers: () -> Unit,
    onCancelKavitaCoverPreload: () -> Unit,
    onClearKavitaCoverCache: () -> Unit,
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
    onClearStumpCoverCache: () -> Unit,
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
    onClearGrimmoryCoverCache: () -> Unit,
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
            syncSettings = uiState.pendingRemoteSyncSummary.toSyncSettingsUiState(),
            onThemeModeSelected = onThemeModeSelected,
            onRetrySync = onRetrySync,
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
            onFastSyncKavita = onFastSyncKavita,
            onPreloadKavitaCovers = onPreloadKavitaCovers,
            onRetryFailedKavitaCovers = onRetryFailedKavitaCovers,
            onCancelKavitaCoverPreload = onCancelKavitaCoverPreload,
            onClearKavitaCoverCache = onClearKavitaCoverCache,
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
            onClearStumpCoverCache = onClearStumpCoverCache,
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
            onClearGrimmoryCoverCache = onClearGrimmoryCoverCache,
        )
    }
}

private fun AppPendingRemoteSyncSummary.toSyncSettingsUiState(): SyncSettingsUiState = SyncSettingsUiState(
    pendingCount = totalCount,
    queuedCount = queuedCount,
    failedCount = failedCount,
    sourceGroups = groups.map { group ->
        SyncSettingsSourceGroup(
            sourceLabel = group.sourceType.displayName(),
            stateLabel = group.syncStatusLabel(),
            queuedCount = group.queuedCount,
            failedCount = group.failedCount,
            lastAttemptLabel = group.lastAttempt?.attemptedAtEpochMillis?.let { attemptedAt ->
                "Last attempt: ${attemptedAt.toSettingsDateTimeLabel()}"
            },
            lastError = group.lastAttempt?.errorMessage,
            items = group.items.map { item ->
                SyncSettingsItemRow(
                    title = item.itemTitle,
                    subtitle = item.itemSubtitle,
                    artifactLabel = item.artifact.settingsLabel(),
                    stateLabel = item.state.settingsLabel(),
                    updatedAtLabel = item.updatedAtEpochMillis?.let { updatedAt ->
                        "Updated ${updatedAt.toSettingsDateTimeLabel()}"
                    },
                )
            },
        )
    },
)

private fun AppPendingRemoteSyncSourceGroup.syncStatusLabel(): String = when {
    failedCount > 0 -> "Needs retry"
    connectivityState == SourceConnectivityState.Offline -> "Offline"
    connectivityState == SourceConnectivityState.Checking -> "Checking"
    connectivityState == SourceConnectivityState.Online -> "Online"
    else -> "Queued"
}

private fun RemoteSyncArtifact.settingsLabel(): String = when (this) {
    RemoteSyncArtifact.PROGRESS -> "Progress"
    RemoteSyncArtifact.JOURNAL_ENTRY -> "Journal entry"
    RemoteSyncArtifact.READING_SESSION -> "Reading session"
}

private fun AppPendingRemoteSyncItemState.settingsLabel(): String = when (this) {
    AppPendingRemoteSyncItemState.QUEUED -> "Queued"
    AppPendingRemoteSyncItemState.FAILED -> "Failed"
}

private fun Long.toSettingsDateTimeLabel(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
