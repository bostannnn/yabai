package com.example.unifiedreader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.downloads.DownloadsSheet
import com.example.unifiedreader.feature.journal.JournalScreen
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.libraryDownloadSelectableItems
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserTopBar
import com.example.unifiedreader.feature.library.shelves.browserState
import com.example.unifiedreader.feature.library.shelves.rememberLibraryShelfBrowserSearchQuery
import com.example.unifiedreader.feature.navigation.SourceNavigationDestination
import com.example.unifiedreader.feature.navigation.SourceNavigationDrawer
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import com.example.unifiedreader.feature.settings.RemoteSourceSettingsActions
import com.example.unifiedreader.feature.settings.SettingsPage
import com.example.unifiedreader.feature.settings.SettingsPageSaver
import com.example.unifiedreader.feature.settings.SettingsScreen
import com.example.unifiedreader.feature.settings.SourceSettingsEntry
import com.example.unifiedreader.feature.settings.settingsUsesSidePane
import com.example.unifiedreader.feature.settings.SyncSettingsItemRow
import com.example.unifiedreader.feature.settings.SyncSettingsSourceGroup
import com.example.unifiedreader.feature.settings.SyncSettingsUiState
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.updates.AppUpdateActions
import com.example.unifiedreader.feature.updates.AppUpdateState
import com.example.unifiedreader.feature.settings.direction.buildComicReadingDirectionLibraryRows
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.feature.settings.mode.buildComicReaderModeLibraryRows
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppTopBarDefaults
import com.example.unifiedreader.ui.components.LocalAppGlassState
import com.example.unifiedreader.ui.components.appGlassSource
import com.example.unifiedreader.ui.components.rememberAppGlassState
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
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
    updateState: AppUpdateState = AppUpdateState(),
    updateActions: AppUpdateActions = AppUpdateActions(),
) {
    val themeColors = UnifiedReaderThemeTokens.colors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var isLibraryDownloadSelectionMode by rememberSaveable(uiState.libraryBrowseContextKey) {
        mutableStateOf(false)
    }
    var selectedDownloadItemIds by rememberSaveable(uiState.libraryBrowseContextKey) {
        mutableStateOf<List<String>>(emptyList())
    }
    // The rendered downloadable items reported up by the active library screen (paged snapshot or
    // in-memory). For the paged path that's only the loaded window, so select-all/download add the
    // recompute's [AppUiState.visibleItems] (see libraryDownloadSelectableItems).
    var selectableDownloadItems by remember(uiState.libraryBrowseContextKey) {
        mutableStateOf<List<ReadableItem>>(emptyList())
    }
    var hasScreenSelectableDownloadItems by remember(uiState.libraryBrowseContextKey) {
        mutableStateOf(false)
    }
    var activeLibraryShelfBrowserKind by rememberSaveable(uiState.libraryBrowseContextKey) {
        mutableStateOf<LibraryShelfBrowserKind?>(null)
    }
    var settingsPage by rememberSaveable(stateSaver = SettingsPageSaver) {
        mutableStateOf<SettingsPage?>(null)
    }
    val settingsUsesSidePane = settingsUsesSidePane(LocalConfiguration.current.screenWidthDp)
    var activeLibraryShelfSearchQuery by rememberLibraryShelfBrowserSearchQuery(
        browseContextKey = uiState.libraryBrowseContextKey,
        browserKind = activeLibraryShelfBrowserKind,
    )
    val activeLibraryShelfBrowser = if (uiState.topLevel == TopLevelDestination.Library) {
        activeLibraryShelfBrowserKind?.let(uiState.libraryShelvesState::browserState)
    } else {
        null
    }
    // The whole view's downloadable roots, once the recompute has landed: the fallback before the
    // screen reports, and the rest of a paged view beyond its loaded window.
    val visibleDownloadableItems = remember(
        uiState.visibleItems,
        uiState.remoteDownloadActionAvailability.downloadableItemIds,
        uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
    ) {
        uiState.visibleItems.filter { item ->
            item.id in uiState.remoteDownloadActionAvailability.downloadableItemIds &&
                item.id !in uiState.remoteDownloadActionAvailability.activeDownloadingItemIds
        }
    }
    val isPagedLibrary = LibraryPagingFeature.isPageable(uiState)
    val effectiveSelectableDownloadItems = remember(
        hasScreenSelectableDownloadItems,
        selectableDownloadItems,
        visibleDownloadableItems,
        isPagedLibrary,
    ) {
        libraryDownloadSelectableItems(
            screenSelectableItems = selectableDownloadItems.takeIf { hasScreenSelectableDownloadItems },
            viewDownloadableItems = visibleDownloadableItems,
            isPaged = isPagedLibrary,
        )
    }
    val effectiveSelectableDownloadItemIds = remember(effectiveSelectableDownloadItems) {
        effectiveSelectableDownloadItems.mapTo(mutableSetOf(), ReadableItem::id)
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
        selectedDownloadItemIds = effectiveSelectableDownloadItems.map { item -> item.id }
    }

    fun downloadSelectedLibraryItems() {
        val selectedItems = effectiveSelectableDownloadItems
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

    // One glass state for the whole shell: the drawer and the top bar both blur the content.
    val glassState = rememberAppGlassState()
    CompositionLocalProvider(LocalAppGlassState provides glassState) {
    val waitingUpdateVersion = updateState.newerRelease?.version?.toString()
    SourceNavigationDrawer(
        drawerState = drawerState,
        sourceOrder = uiState.sourceOrder,
        settingsBadge = waitingUpdateVersion,
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
                SourceNavigationDestination.Settings -> {
                    // Picking Settings in the drawer always lands on the hub, even if a page was open.
                    settingsPage = null
                    viewModel.onTopLevelSelected(TopLevelDestination.Settings)
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            // Content fills the screen and scrolls under the floating glass top bar; each
            // screen keeps AppTopBarDefaults.contentInset of top padding so its first row
            // starts below the bar.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .appGlassSource(),
                ) {
                    AppScreenHost(
                        screen = uiState.topLevel,
                        modifier = Modifier.fillMaxSize(),
                    ) { topLevel ->
                        when (topLevel) {
                            TopLevelDestination.Library -> LibraryRoute(
                                uiState = uiState,
                                viewMode = libraryViewMode,
                                pagedGridFlow = if (LibraryPagingFeature.isPageable(uiState)) {
                                    remember(
                                        uiState.selectedSource,
                                        uiState.selectedLibrarySortMode,
                                        uiState.selectedLibraryFilters,
                                    ) {
                                        viewModel.libraryGridPagingData(
                                            source = uiState.selectedSource,
                                            sortMode = uiState.selectedLibrarySortMode,
                                            filters = uiState.selectedLibraryFilters,
                                        )
                                    }
                                } else {
                                    null
                                },
                                pagedWindowBadgeResolver = viewModel::resolvePagedWindowBadges,
                                pagedAlphabetSectionsProvider = viewModel::pagedAlphabetSections,
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
                                onSelectableDownloadItemsChanged = { selectableItems ->
                                    hasScreenSelectableDownloadItems = true
                                    selectableDownloadItems = selectableItems
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
                                onImportLocalFile = onImportLocalFileClick,
                                onOpenSettings = { viewModel.onTopLevelSelected(TopLevelDestination.Settings) },
                                onRetrySourceConnection = viewModel::retrySelectedSourceConnection,
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
                                contentTopInset = AppTopBarDefaults.contentInset,
                                modifier = Modifier.fillMaxSize(),
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
                                    contentTopInset = AppTopBarDefaults.contentInset,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            TopLevelDestination.Settings -> SettingsRoute(
                                uiState = uiState,
                                viewModel = viewModel,
                                page = settingsPage,
                                onPageChanged = { page -> settingsPage = page },
                                kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
                                stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
                                kavitaReaderModeLibraries = kavitaReaderModeLibraries,
                                stumpReaderModeLibraries = stumpReaderModeLibraries,
                                updateState = updateState,
                                updateActions = updateActions,
                                contentTopInset = AppTopBarDefaults.contentInset,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    CompositionLocalProvider(LocalDrawerButtonDot provides (waitingUpdateVersion != null)) {
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
                                    downloadsButtonState = viewModel.downloadsSnapshot.buttonState,
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
                                title = if (settingsUsesSidePane) "Settings" else settingsPage?.title ?: "Settings",
                                onDrawerClick = {
                                    drawerScope.launch {
                                        drawerState.open()
                                    }
                                },
                                onBack = if (settingsPage != null && !settingsUsesSidePane) {
                                    { settingsPage = null }
                                } else {
                                    null
                                },
                            )
                        }
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
    }
}

@Composable
private fun SettingsRoute(
    uiState: AppUiState,
    viewModel: AppViewModel,
    page: SettingsPage?,
    onPageChanged: (SettingsPage?) -> Unit,
    kavitaReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    stumpReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    kavitaReaderModeLibraries: List<ReaderModeLibraryUiState>,
    stumpReaderModeLibraries: List<ReaderModeLibraryUiState>,
    updateState: AppUpdateState,
    updateActions: AppUpdateActions,
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val sources = listOf(
        SourceSettingsEntry(
            sourceType = SourceType.KAVITA,
            connection = uiState.kavitaConnection,
            connectivity = uiState.sourceConnectivityBySource[SourceType.KAVITA] ?: SourceConnectivityState.Unknown,
            readingDirectionLibraries = kavitaReadingDirectionLibraries,
            readerModeLibraries = kavitaReaderModeLibraries,
            actions = RemoteSourceSettingsActions(
                onDisplayNameChanged = viewModel::onKavitaDisplayNameChanged,
                onBaseUrlChanged = viewModel::onKavitaBaseUrlChanged,
                onBackupBaseUrlChanged = viewModel::onKavitaBackupBaseUrlChanged,
                onUsernameChanged = viewModel::onKavitaUsernameChanged,
                onPasswordChanged = viewModel::onKavitaPasswordChanged,
                onConnect = viewModel::connectKavitaServer,
                onSync = viewModel::refreshKavitaLibrary,
                onFastSync = viewModel::fastSyncKavitaLibrary,
                onPreloadCovers = { viewModel.preloadCovers(SourceType.KAVITA) },
                onRetryFailedCovers = { viewModel.retryFailedCovers(SourceType.KAVITA) },
                onCancelCoverPreload = { viewModel.cancelCoverPreload(SourceType.KAVITA) },
                onClearCoverCache = { viewModel.clearCachedCovers(SourceType.KAVITA) },
            ),
        ),
        SourceSettingsEntry(
            sourceType = SourceType.GRIMMORY,
            connection = uiState.grimmoryConnection,
            connectivity = uiState.sourceConnectivityBySource[SourceType.GRIMMORY] ?: SourceConnectivityState.Unknown,
            actions = RemoteSourceSettingsActions(
                onDisplayNameChanged = viewModel::onGrimmoryDisplayNameChanged,
                onBaseUrlChanged = viewModel::onGrimmoryBaseUrlChanged,
                onBackupBaseUrlChanged = viewModel::onGrimmoryBackupBaseUrlChanged,
                onUsernameChanged = viewModel::onGrimmoryUsernameChanged,
                onPasswordChanged = viewModel::onGrimmoryPasswordChanged,
                onConnect = viewModel::connectGrimmoryServer,
                onSync = viewModel::refreshGrimmoryLibrary,
                onPreloadCovers = { viewModel.preloadCovers(SourceType.GRIMMORY) },
                onRetryFailedCovers = { viewModel.retryFailedCovers(SourceType.GRIMMORY) },
                onCancelCoverPreload = { viewModel.cancelCoverPreload(SourceType.GRIMMORY) },
                onClearCoverCache = { viewModel.clearCachedCovers(SourceType.GRIMMORY) },
            ),
        ),
        SourceSettingsEntry(
            sourceType = SourceType.STUMP,
            connection = uiState.stumpConnection,
            connectivity = uiState.sourceConnectivityBySource[SourceType.STUMP] ?: SourceConnectivityState.Unknown,
            readingDirectionLibraries = stumpReadingDirectionLibraries,
            readerModeLibraries = stumpReaderModeLibraries,
            actions = RemoteSourceSettingsActions(
                onDisplayNameChanged = viewModel::onStumpDisplayNameChanged,
                onBaseUrlChanged = viewModel::onStumpBaseUrlChanged,
                onBackupBaseUrlChanged = viewModel::onStumpBackupBaseUrlChanged,
                onUsernameChanged = viewModel::onStumpUsernameChanged,
                onPasswordChanged = viewModel::onStumpPasswordChanged,
                onConnect = viewModel::connectStumpServer,
                onSync = viewModel::refreshStumpLibrary,
                onPreloadCovers = { viewModel.preloadCovers(SourceType.STUMP) },
                onRetryFailedCovers = { viewModel.retryFailedCovers(SourceType.STUMP) },
                onCancelCoverPreload = { viewModel.cancelCoverPreload(SourceType.STUMP) },
                onClearCoverCache = { viewModel.clearCachedCovers(SourceType.STUMP) },
            ),
        ),
    )
    AppBackground(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        SettingsScreen(
            page = page,
            onPageChanged = onPageChanged,
            themeMode = uiState.themeMode,
            onThemeModeSelected = viewModel::onThemeModeSelected,
            sources = sources,
            sourceOrder = uiState.sourceOrder,
            onSourceOrderChanged = viewModel::reorderSources,
            libraryFilterPanelsBySource = SourceType.entries.associateWith { sourceType ->
                resolveLibraryFilterPanels(
                    sourceType = sourceType,
                    preferredPanels = uiState.libraryFilterPanelsBySource[sourceType],
                )
            },
            onLibraryFilterPanelsChanged = viewModel::onLibraryFilterPanelsChanged,
            syncSettings = uiState.pendingRemoteSyncSummary.toSyncSettingsUiState(),
            onRetrySync = viewModel::retryPendingRemoteSync,
            onLibraryDirectionSelected = viewModel::onLibraryDirectionSelected,
            onLibraryReaderModeSelected = viewModel::onLibraryReaderModeSelected,
            versionName = versionName,
            contentTopInset = contentTopInset,
            updateState = updateState,
            updateActions = updateActions,
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
