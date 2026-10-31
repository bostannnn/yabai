package com.example.unifiedreader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
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
import com.example.unifiedreader.feature.library.rememberLibrarySelectionTip
import com.example.unifiedreader.feature.library.browse.LibraryBrowseRequest
import com.example.unifiedreader.feature.library.browse.LibraryBrowseRequestSaver
import com.example.unifiedreader.feature.library.browse.LibraryBrowseTopBar
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserOptionsButton
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserTopBar
import com.example.unifiedreader.feature.library.shelves.browserState
import com.example.unifiedreader.feature.library.shelves.rememberLibraryShelfBrowserSearchQuery
import com.example.unifiedreader.feature.library.shelves.rememberLibraryShelfBrowserViewState
import com.example.unifiedreader.feature.lists.rememberListsRouteState
import com.example.unifiedreader.feature.navigation.SourceNavigationDestination
import com.example.unifiedreader.feature.navigation.SourceNavigationFrame
import com.example.unifiedreader.feature.navigation.canAddNavigationSource
import com.example.unifiedreader.feature.navigation.knownConnectedServers
import com.example.unifiedreader.feature.navigation.navigationSources
import com.example.unifiedreader.feature.navigation.sourceNavigationUsesRail
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import com.example.unifiedreader.feature.settings.SettingsPage
import com.example.unifiedreader.feature.settings.SettingsPageSaver
import com.example.unifiedreader.feature.settings.SettingsScreen
import com.example.unifiedreader.feature.settings.SourceStartMode
import com.example.unifiedreader.feature.settings.SyncSettingsItemRow
import com.example.unifiedreader.feature.settings.SyncSettingsSourceGroup
import com.example.unifiedreader.feature.settings.SyncSettingsUiState
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.direction.buildComicReadingDirectionLibraryRows
import com.example.unifiedreader.feature.settings.formatSettingsDay
import com.example.unifiedreader.feature.settings.formatSettingsMoment
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.feature.settings.mode.buildComicReaderModeLibraryRows
import com.example.unifiedreader.feature.settings.parent
import com.example.unifiedreader.feature.settings.settingsUsesSidePane
import com.example.unifiedreader.feature.updates.AppUpdateActions
import com.example.unifiedreader.feature.updates.AppUpdateState
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppToastHost
import com.example.unifiedreader.ui.components.AppTopBarDefaults
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.LocalAppGlassState
import com.example.unifiedreader.ui.components.appGlassSource
import com.example.unifiedreader.ui.components.appSystemBars
import com.example.unifiedreader.ui.components.currentAppWindowWidth
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
    // "Add a server" and Connect on an empty library open the start flow.
    onStartSource: (SourceStartRequest) -> Unit = {},
    // The user's lists; without it there is no Lists screen and no Add to list.
    listsViewModel: AppListsViewModel? = null,
) {
    val themeColors = UnifiedReaderThemeTokens.colors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    // With the rail beside the screens, the bars have no menu button.
    val openDrawer: (() -> Unit)? = if (sourceNavigationUsesRail()) {
        null
    } else {
        { drawerScope.launch { drawerState.open() } }
    }
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
    // Browse, with its picked chips; null while it is closed.
    var libraryBrowseRequest by rememberSaveable(uiState.libraryBrowseContextKey, stateSaver = LibraryBrowseRequestSaver) {
        mutableStateOf<LibraryBrowseRequest?>(null)
    }
    var showLibraryBrowseRows by rememberSaveable(uiState.libraryBrowseContextKey) { mutableStateOf(false) }
    var libraryBrowseScrolled by remember(uiState.libraryBrowseContextKey) { mutableStateOf(false) }
    // Which library screens (by browse context) have scrolled their grid under the bar.
    val libraryScrolled = remember { mutableStateMapOf<String, Boolean>() }
    var settingsPage by rememberSaveable(stateSaver = SettingsPageSaver) {
        mutableStateOf<SettingsPage?>(null)
    }
    val settingsUsesSidePane = settingsUsesSidePane(currentAppWindowWidth())
    // Whether anything has scrolled under the settings and journal bars (they then need their glass).
    var settingsScrolled by remember { mutableStateOf(false) }
    var journalScrolled by remember { mutableStateOf(false) }
    val listsState = listsViewModel?.routeState ?: rememberListsRouteState()
    var filtersToSave by remember { mutableStateOf<LibraryFilterState?>(null) }
    val openListOnLists: (String) -> Unit = { listId ->
        listsState.openList(listId)
        viewModel.onTopLevelSelected(TopLevelDestination.Lists)
    }
    // Add to list for a book's page (the page stays under the sheet) or for a selection.
    var addToListItems by remember { mutableStateOf<List<ReadableItem>?>(null) }
    var addToListFromBookPage by remember { mutableStateOf(false) }
    // The journal's "Open in library" and a finished download: the book's page, once its source
    // has loaded it.
    var pendingBookPageItemId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.topLevel) {
        if (uiState.topLevel != TopLevelDestination.Library) pendingBookPageItemId = null
    }
    var activeLibraryShelfSearchQuery by rememberLibraryShelfBrowserSearchQuery(
        browseContextKey = uiState.libraryBrowseContextKey,
        browserKind = activeLibraryShelfBrowserKind,
    )
    val libraryShelfBrowserViewState = rememberLibraryShelfBrowserViewState(uiState.libraryBrowseContextKey)
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
    // See all stays open when its shelf empties (it says so); it closes when the library does.
    LaunchedEffect(uiState.topLevel) {
        if (uiState.topLevel != TopLevelDestination.Library) {
            activeLibraryShelfBrowserKind = null
            libraryBrowseRequest = null
        }
    }
    BackHandler(enabled = activeLibraryShelfBrowser != null) {
        activeLibraryShelfBrowserKind = null
    }
    BackHandler(enabled = libraryBrowseRequest != null && activeLibraryShelfBrowser == null) {
        libraryBrowseRequest = null
    }
    // Selection starts with a long-press and has no button of its own, so the tip in the "•••"
    // menu explains it until the first time.
    val librarySelectionTip = rememberLibrarySelectionTip()

    fun updateLibraryDownloadSelectionMode(enabled: Boolean) {
        isLibraryDownloadSelectionMode = enabled
        if (enabled) {
            librarySelectionTip.markSelected()
        } else {
            selectedDownloadItemIds = emptyList()
        }
    }
    BackHandler(enabled = isLibraryDownloadSelectionMode) {
        updateLibraryDownloadSelectionMode(false)
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
    // The rail and the drawer list the connected servers; the set is kept for the next launch.
    val connectedServers = uiState.navigationConnectedServers
    val knownConnectedServers = knownConnectedServers(uiState.serverSessions)
    LaunchedEffect(knownConnectedServers) {
        knownConnectedServers?.let(viewModel::recordConnectedSources)
    }
    SourceNavigationFrame(
        drawerState = drawerState,
        sourceOrder = navigationSources(uiState.sourceOrder, connectedServers),
        showAddSource = canAddNavigationSource(connectedServers),
        settingsBadge = waitingUpdateVersion,
        selectedDestination = when (uiState.topLevel) {
            TopLevelDestination.Library -> SourceNavigationDestination.Source(uiState.selectedSource)
            TopLevelDestination.Lists -> SourceNavigationDestination.Lists
            TopLevelDestination.Journal -> SourceNavigationDestination.Journal
            TopLevelDestination.Settings -> SourceNavigationDestination.Settings
        },
        onDestinationSelected = { destination ->
            drawerScope.launch {
                drawerState.close()
            }
            when (destination) {
                is SourceNavigationDestination.Source -> viewModel.openLibrarySource(destination.sourceType)
                SourceNavigationDestination.AddSource -> onStartSource(SourceStartRequest(SourceStartMode.AddSource))
                SourceNavigationDestination.Lists -> viewModel.onTopLevelSelected(TopLevelDestination.Lists)
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
            contentWindowInsets = WindowInsets.appSystemBars,
        ) { innerPadding ->
            // Content fills the screen and scrolls under the floating glass top bar; each
            // screen keeps AppTopBarDefaults.contentInset of top padding so its first row
            // starts below the bar.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Toasts float over the content (beside the rail), blurring it like the bars.
                AppToastHost(modifier = Modifier.zIndex(1f), aboveNavigationBar = false)
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
                                onScrolledChanged = { contextKey, scrolled ->
                                    if (scrolled == null) libraryScrolled.remove(contextKey) else libraryScrolled[contextKey] = scrolled
                                },
                                onImportLocalFile = onImportLocalFileClick,
                                onConnectSource = { server -> onStartSource(SourceStartRequest(SourceStartMode.AddSource, server)) },
                                onRetrySourceConnection = viewModel::retrySelectedSourceConnection,
                                onSortModeChanged = viewModel::onLibrarySortModeChanged,
                                onViewModeChanged = onLibraryViewModeChanged,
                                onGridCellSizeDpChanged = viewModel::onLibraryGridCellSizeChanged,
                                onFadeReadItemsChanged = viewModel::onLibraryFadeReadItemsChanged,
                                showSortControl = !uiState.isSeriesBrowseActive && !uiState.isKavitaReadingListActive,
                                activeShelfBrowserKind = activeLibraryShelfBrowserKind,
                                onActiveShelfBrowserKindChanged = { activeLibraryShelfBrowserKind = it },
                                shelfSearchQuery = activeLibraryShelfSearchQuery,
                                onShelfSearchQueryChanged = { query -> activeLibraryShelfSearchQuery = query },
                                shelfBrowserViewState = libraryShelfBrowserViewState,
                                onAddToList = listsViewModel?.let {
                                    { item ->
                                        addToListFromBookPage = true
                                        addToListItems = listOf(item)
                                    }
                                },
                                listsContaining = listsViewModel?.let { rememberBookListMemberships(it, uiState) } ?: { emptyList() },
                                onOpenList = openListOnLists,
                                readingOrderCard = listsViewModel?.let {
                                    rememberHomeReadingOrderCard(it, viewModel, uiState, onOpenList = openListOnLists)
                                },
                                onSaveFiltersAsList = listsViewModel?.let { { filters -> filtersToSave = filters } },
                                browseRequest = libraryBrowseRequest,
                                onBrowseRequestChanged = { request -> libraryBrowseRequest = request },
                                showBrowseRows = showLibraryBrowseRows,
                                onShowBrowseRowsChanged = { show -> showLibraryBrowseRows = show },
                                onBrowseScrolledChanged = { scrolled -> libraryBrowseScrolled = scrolled },
                                bookPageRequest = pendingBookPageItemId,
                                onBookPageRequestHandled = { pendingBookPageItemId = null },
                                contentTopInset = AppTopBarDefaults.contentInset,
                                modifier = Modifier.fillMaxSize(),
                            )

                            TopLevelDestination.Lists -> if (listsViewModel != null) {
                                ListsRoute(
                                    state = listsState,
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    listsViewModel = listsViewModel,
                                    contentTopInset = AppTopBarDefaults.contentInset,
                                    modifier = Modifier.fillMaxSize(),
                                    onOpenSeries = { series ->
                                        viewModel.openLibrarySource(series.sourceType)
                                        viewModel.onItemSelected(series)
                                    },
                                )
                            }

                            TopLevelDestination.Journal -> {
                                // The heavy full journal snapshot (stats/timelines/catalog join) is
                                // built while the Journal tab is on screen and kept a little after;
                                // until the first one, the lightweight write-state stands in (the
                                // journal shows its books as loading).
                                val journalSnapshot by viewModel.journalFullSnapshotFlow.collectAsStateWithLifecycle()
                                JournalScreen(
                                    snapshot = journalSnapshot ?: uiState.journalSnapshot,
                                    coverImageUrlProvider = viewModel::coverImageUrlFor,
                                    onCoverVisible = viewModel::ensureCoverCached,
                                    onDeleteItemJournal = viewModel::deleteItemJournal,
                                    onSaveJournalEntry = viewModel::saveJournalEntry,
                                    onOpenInLibrary = { item ->
                                        pendingBookPageItemId = item.id
                                        viewModel.openLibrarySource(item.sourceType)
                                    },
                                    contentTopInset = AppTopBarDefaults.contentInset,
                                    onScrolledChanged = { scrolled -> journalScrolled = scrolled },
                                    modifier = Modifier.fillMaxSize(),
                                    finishedLists = listsViewModel?.let { rememberJournalFinishedLists(it, uiState) }.orEmpty(),
                                    listCover = rememberJournalListCover(viewModel),
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
                                onScrolledChanged = { scrolled -> settingsScrolled = scrolled },
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
                                    searchQuery = activeLibraryShelfSearchQuery,
                                    onSearchQueryChanged = { query -> activeLibraryShelfSearchQuery = query },
                                    onBack = { activeLibraryShelfBrowserKind = null },
                                    scrolled = libraryShelfBrowserViewState.scrolled,
                                    actions = if (currentAppWindowWidth() == AppWindowWidth.Compact) {
                                        {
                                            LibraryShelfBrowserOptionsButton(
                                                shelves = uiState.libraryShelvesState,
                                                kind = activeLibraryShelfBrowser.kind,
                                                viewState = libraryShelfBrowserViewState,
                                                gridCellSizeDp = uiState.libraryGridCellSizeDp,
                                                onGridCellSizeDpChanged = viewModel::onLibraryGridCellSizeChanged,
                                                iconOnly = true,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            } else if (libraryBrowseRequest != null) {
                                LibraryBrowseTopBar(
                                    onBack = { libraryBrowseRequest = null },
                                    onShowRows = { showLibraryBrowseRows = true },
                                    scrolled = libraryBrowseScrolled,
                                )
                            } else {
                                LibraryTopBar(
                                    selectedSource = uiState.selectedSource,
                                    sourceConnectivity = uiState.selectedSourceConnectivity,
                                    searchQuery = uiState.selectedLibrarySearchQuery,
                                    contextTitle = uiState.selectedLibraryContextTitle,
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
                                    onDrawerClick = openDrawer,
                                    onSearchQueryChanged = viewModel::onLibrarySearchQueryChanged,
                                    onRefreshClick = viewModel::fastSyncKavitaLibraryInPlace,
                                    downloadsButtonState = viewModel.downloadsSnapshot.buttonState,
                                    onDownloadsClick = viewModel::showDownloadsSheet,
                                    onDownloadSelectionModeChanged = ::updateLibraryDownloadSelectionMode,
                                    onSelectAllDownloadItems = ::selectAllLibraryDownloadItems,
                                    onDownloadSelectedItems = ::downloadSelectedLibraryItems,
                                    onAddSelectedToList = listsViewModel?.let {
                                        {
                                            val selectedItems = effectiveSelectableDownloadItems
                                                .filter { item -> item.id in selectedDownloadItemIdSet }
                                            if (selectedItems.isNotEmpty()) {
                                                addToListFromBookPage = false
                                                addToListItems = selectedItems
                                                updateLibraryDownloadSelectionMode(false)
                                            }
                                        }
                                    },
                                    onImportLocalFileClick = onImportLocalFileClick,
                                    onLeaveSeries = viewModel::onBackPressed,
                                    showSelectionTip = librarySelectionTip.shouldShow,
                                    // Until the screen reports, the saved scroll position says.
                                    scrolled = libraryScrolled[uiState.libraryBrowseContextKey]
                                        ?: viewModel.libraryScrollPositionFor(uiState.libraryBrowseContextKey).let { position ->
                                            position.index > 0 || position.offset > 0
                                        },
                                )
                            }

                            TopLevelDestination.Lists -> if (listsViewModel != null) {
                                ListsTopBar(
                                    state = listsState,
                                    listsViewModel = listsViewModel,
                                    onDrawerClick = openDrawer,
                                )
                            }

                            TopLevelDestination.Journal -> JournalTopBar(
                                onDrawerClick = openDrawer,
                                scrolled = journalScrolled,
                            )

                            TopLevelDestination.Settings -> SettingsTopBar(
                                title = if (settingsUsesSidePane) null else settingsPage?.title,
                                onDrawerClick = openDrawer,
                                onBack = if (settingsPage != null && !settingsUsesSidePane) {
                                    { settingsPage = settingsPage?.parent }
                                } else {
                                    null
                                },
                                scrolled = settingsScrolled,
                            )
                        }
                    }
                }
                if (listsViewModel != null) {
                    KeepListBooksDownloaded(listsViewModel, uiState, onQueue = viewModel::onKeepDownloaded)
                }
                val saving = filtersToSave
                if (saving != null && listsViewModel != null) {
                    SaveFiltersAsListDialog(
                        filters = saving,
                        uiState = uiState,
                        listsViewModel = listsViewModel,
                        onOpenList = openListOnLists,
                        onDismiss = { filtersToSave = null },
                    )
                }
                val addToList = addToListItems
                if (addToList != null && listsViewModel != null) {
                    AddToListHost(
                        items = addToList,
                        libraryItems = uiState.items,
                        grimmoryConnected = uiState.grimmoryConnection.hasStoredSession,
                        kavitaConnected = uiState.kavitaConnection.hasStoredSession,
                        listsViewModel = listsViewModel,
                        onDismiss = { addToListItems = null },
                        onBack = if (addToListFromBookPage) {
                            { addToListItems = null }
                        } else {
                            null
                        },
                    )
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
                        onReadEntry = viewModel::openDownloadedEntry,
                        onOpenBookPage = { itemId, source ->
                            pendingBookPageItemId = itemId
                            viewModel.hideDownloadsSheet()
                            viewModel.openLibrarySource(source)
                        },
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
    onScrolledChanged: (Boolean) -> Unit,
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val sources = rememberSourceSettingsEntries(
        uiState = uiState,
        viewModel = viewModel,
        kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
        stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
        kavitaReaderModeLibraries = kavitaReaderModeLibraries,
        stumpReaderModeLibraries = stumpReaderModeLibraries,
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
            onScrolledChanged = onScrolledChanged,
        )
    }
}

private fun AppPendingRemoteSyncSummary.toSyncSettingsUiState(): SyncSettingsUiState = SyncSettingsUiState(
    pendingCount = totalCount,
    queuedCount = queuedCount,
    failedCount = failedCount,
    waitingSinceLabel = groups.flatMap { it.items }.mapNotNull { it.updatedAtEpochMillis }.minOrNull()?.let { formatSettingsDay(it) },
    lastAttemptLabel = groups.mapNotNull { it.lastAttempt?.attemptedAtEpochMillis }.maxOrNull()?.let { formatSettingsMoment(it) },
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
                    updatedAtLabel = item.updatedAtEpochMillis?.let { updatedAt -> "since ${formatSettingsDay(updatedAt)}" },
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
