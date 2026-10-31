package com.example.unifiedreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.example.unifiedreader.core.model.LibraryAlphabetSection
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryCatalogKind
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryHeader
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryItemListMembership
import com.example.unifiedreader.feature.library.LibraryReadingOrderCard
import com.example.unifiedreader.feature.library.LibraryListScopeTopContent
import com.example.unifiedreader.feature.library.LibraryReadingListScopeState
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibrarySeriesReadingCard
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.LibraryWindowBadges
import com.example.unifiedreader.feature.library.libraryCatalogKind
import com.example.unifiedreader.feature.library.librarySeriesNextIssue
import com.example.unifiedreader.feature.library.browse.LibraryBrowsePage
import com.example.unifiedreader.feature.library.browse.LibraryBrowseRequest
import com.example.unifiedreader.feature.library.librarySeriesSummary
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserViewState
import com.example.unifiedreader.feature.library.shelves.LibraryShelfItemsBrowser
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesTopSlot
import com.example.unifiedreader.feature.library.withWindowBadges
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.journal.readingTimeLeftMillis
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppScreenTransition
import kotlinx.coroutines.flow.Flow

@Composable
internal fun LibraryRoute(
    uiState: AppUiState,
    viewMode: LibraryViewMode,
    pagedGridFlow: Flow<PagingData<ReadableItem>>? = null,
    pagedWindowBadgeResolver: (suspend (List<ReadableItem>) -> LibraryWindowBadges)? = null,
    pagedAlphabetSectionsProvider: ((LibrarySortMode) -> Flow<List<LibraryAlphabetSection>>)? = null,
    browseContextKey: String,
    initialScrollPosition: LibraryScrollPosition,
    scrollToTopRequest: Long,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onPrepareCoverSource: (SourceType) -> Unit,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onCoverViewportScrollingChanged: (Boolean) -> Unit,
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit,
    isDownloadSelectionMode: Boolean,
    selectedDownloadItemIds: List<String>,
    onDownloadSelectionModeChanged: (Boolean) -> Unit,
    onSelectedDownloadItemIdsChanged: (List<String>) -> Unit,
    onSelectableDownloadItemsChanged: (List<ReadableItem>) -> Unit,
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
    // Null when that screen leaves, so the bar falls back to the default for the next one.
    onScrolledChanged: (browseContextKey: String, scrolled: Boolean?) -> Unit,
    onImportLocalFile: () -> Unit,
    // Connect on an empty library: that server's connect page, or every choice from Local (null).
    onConnectSource: (SourceType?) -> Unit,
    onRetrySourceConnection: () -> Unit,
    onReadingListSelected: (String?) -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onViewModeChanged: (LibraryViewMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
    showSortControl: Boolean,
    activeShelfBrowserKind: LibraryShelfBrowserKind? = null,
    onActiveShelfBrowserKindChanged: (LibraryShelfBrowserKind?) -> Unit = {},
    shelfSearchQuery: String = "",
    onShelfSearchQueryChanged: (String) -> Unit = {},
    shelfBrowserViewState: LibraryShelfBrowserViewState? = null,
    // Browse, open with its picked chips; null while it is closed.
    browseRequest: LibraryBrowseRequest? = null,
    onBrowseRequestChanged: (LibraryBrowseRequest?) -> Unit = {},
    showBrowseRows: Boolean = false,
    onShowBrowseRowsChanged: (Boolean) -> Unit = {},
    onBrowseScrolledChanged: (Boolean) -> Unit = {},
    // A book page asked for from elsewhere (the journal), opened once the book is in this library.
    // The book page's Add to list; null where there are no lists.
    onAddToList: ((ReadableItem) -> Unit)? = null,
    // The book page's In your lists, and opening one of them.
    listsContaining: (ReadableItem) -> List<LibraryItemListMembership> = { emptyList() },
    onOpenList: (String) -> Unit = {},
    // The home's card when it follows a reading order.
    readingOrderCard: LibraryReadingOrderCard? = null,
    // Save as list in Filters.
    onSaveFiltersAsList: ((LibraryFilterState) -> Unit)? = null,
    bookPageRequest: String? = null,
    onBookPageRequestHandled: () -> Unit = {},
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.selectedSource) {
        onPrepareCoverSource(uiState.selectedSource)
    }
    // The book page opened by a cover's "•••" or the reading card's Details, with the pages opened
    // from its rows of covers on top: Back returns to the book before, × closes them all.
    var bookPageStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val bookPageItemId = bookPageStack.lastOrNull()
    LaunchedEffect(bookPageRequest, uiState.sourceItems, uiState.items) {
        val requested = bookPageRequest ?: return@LaunchedEffect
        if (uiState.sourceItems.any { it.id == requested } || uiState.items.any { it.id == requested }) {
            bookPageStack = listOf(requested)
            onBookPageRequestHandled()
        }
    }
    fun itemActionContextFor(state: AppUiState) = LibraryItemActionContext(
        progressByItemId = state.displayProgressByItemId,
        locallyAvailableItemIds = state.locallyAvailableRemoteItemIds,
        downloadableItemIds = state.remoteDownloadActionAvailability.downloadableItemIds,
        deletableDownloadedItemIds = state.remoteDownloadActionAvailability.deletableDownloadedItemIds,
        downloadingItemIds = state.remoteDownloadActionAvailability.activeDownloadingItemIds,
        seriesReadingDirectionFor = seriesReadingDirectionFor,
        onSeriesDirectionSelected = onSeriesDirectionSelected,
        seriesReaderModeFor = seriesReaderModeFor,
        onSeriesReaderModeSelected = onSeriesReaderModeSelected,
        onDownloadItem = onDownloadRemoteItem,
        onDeleteDownloadedItem = onDeleteRemoteLocalFile,
        onSetReadStatus = onSetReadStatus,
        onOpenItemPage = { item -> bookPageStack = listOf(item.id) },
        onAddToList = onAddToList,
        listsContaining = listsContaining,
        onOpenList = onOpenList,
        readingOrderCard = readingOrderCard,
    )
    // One screen for See all whichever shelf it shows: its tabs switch in place.
    val screen = libraryScreenKey(uiState, shelvesOpen = activeShelfBrowserKind != null, browseOpen = browseRequest != null)
    val currentFrame = LibraryRouteFrame(
        uiState = uiState,
        viewMode = viewMode,
        pagedGridFlow = pagedGridFlow,
        browseContextKey = browseContextKey,
        initialScrollPosition = initialScrollPosition,
        scrollToTopRequest = scrollToTopRequest,
        isDownloadSelectionMode = isDownloadSelectionMode,
        selectedDownloadItemIds = selectedDownloadItemIds,
        showSortControl = showSortControl,
        activeShelfBrowserKind = activeShelfBrowserKind,
        shelfSearchQuery = shelfSearchQuery,
        browseRequest = browseRequest,
    )
    AppBackground(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // A series, a shelf or a list slides in over the library and back out; another source
        // fades through. The screen that is leaving keeps drawing its own frame.
        AppScreenHost(
            screen = screen,
            frame = currentFrame,
            transitionFor = ::libraryScreenTransition,
            modifier = Modifier.fillMaxSize(),
        ) { shownScreen, frame ->
            // The shelves (and the "See all" browser) render from this context. Its download/read maps come
            // from the late in-memory recompute, so at cold start the shelves' badges lag ~1 s behind the grid.
            // Resolve the bounded shelf item set's badges from the same early window resolver as the grid and
            // overlay them, so the shelf badges commit early too.
            val shelfItems = remember(frame.uiState.libraryShelvesState) { frame.uiState.libraryShelvesState.allItems() }
            val latestShelfBadgeResolver by rememberUpdatedState(pagedWindowBadgeResolver)
            val shelfWindowBadges by produceState(LibraryWindowBadges(), shelfItems) {
                val resolver = latestShelfBadgeResolver
                value = if (resolver != null && shelfItems.isNotEmpty()) resolver(shelfItems) else LibraryWindowBadges()
            }
            val itemActionContext = itemActionContextFor(frame.uiState).withWindowBadges(shelfWindowBadges)
            if (frame.activeShelfBrowserKind != null && shelfBrowserViewState != null) {
                LibraryShelfItemsBrowser(
                    shelves = frame.uiState.libraryShelvesState,
                    kind = frame.activeShelfBrowserKind,
                    onKindSelected = onActiveShelfBrowserKindChanged,
                    viewState = shelfBrowserViewState,
                    browseContextKey = "${frame.browseContextKey}:shelves",
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                    gridCellSizeDp = frame.uiState.libraryGridCellSizeDp,
                    onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                    searchQuery = frame.shelfSearchQuery,
                    onSearchQueryChanged = onShelfSearchQueryChanged,
                    contentTopInset = contentTopInset,
                    onCoverVisible = onCoverVisible,
                    onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                    onBack = { onActiveShelfBrowserKindChanged(null) },
                    onItemSelected = { item ->
                        onActiveShelfBrowserKindChanged(null)
                        onItemSelected(item)
                    },
                )
            } else if (frame.browseRequest != null) {
                DisposableEffect(Unit) { onDispose { onBrowseScrolledChanged(false) } }
                LibraryBrowsePage(
                    source = frame.uiState.selectedSource,
                    items = frame.uiState.sourceItems,
                    request = frame.browseRequest,
                    onRequestChanged = onBrowseRequestChanged,
                    showRows = showBrowseRows,
                    onShowRowsChanged = onShowBrowseRowsChanged,
                    actionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    gridCellSizeDp = frame.uiState.libraryGridCellSizeDp,
                    contentTopInset = contentTopInset,
                    onClose = { onBrowseRequestChanged(null) },
                    onItemSelected = onItemSelected,
                    onScrolledChanged = onBrowseScrolledChanged,
                )
            } else {
                val header = remember(
                    frame.uiState.selectedLibrarySearchQuery.isBlank(),
                    frame.uiState.isSeriesBrowseActive,
                    frame.uiState.isKavitaScopeActive,
                    frame.uiState.selectedLibraryContextTitle,
                    frame.uiState.visibleItems,
                    frame.uiState.displayProgressByItemId,
                ) { frame.uiState.libraryHeader() }
                // The reading card's "About 25 min left", from the journal's sessions of that book.
                val journalSnapshot = frame.uiState.journalSnapshot
                val readingTimeLeftMillis = remember(journalSnapshot) {
                    { item: ReadableItem, progress: ItemProgressSummary ->
                        journalSnapshot.readingTimeLeftMillis(item.id, progress.fraction)
                    }
                }
                val catalogKind = remember(
                    frame.uiState.isSeriesBrowseActive,
                    frame.uiState.isKavitaScopeActive,
                    frame.uiState.sourceItems,
                ) { frame.uiState.libraryCatalogKind() }
                val seriesNext = remember(
                    frame.uiState.isSeriesBrowseActive,
                    frame.uiState.visibleItems,
                    frame.uiState.displayProgressByItemId,
                ) {
                    if (frame.uiState.isSeriesBrowseActive) {
                        librarySeriesNextIssue(frame.uiState.visibleItems, frame.uiState.displayProgressByItemId)
                    } else {
                        null
                    }
                }
                val listTop = remember(
                    frame.uiState.isSeriesBrowseActive,
                    frame.uiState.kavitaReadingLists,
                    frame.uiState.visibleItems,
                    frame.uiState.displayProgressByItemId,
                ) { frame.uiState.kavitaListScopeTop() }
                DisposableEffect(frame.browseContextKey) {
                    onDispose { onScrolledChanged(frame.browseContextKey, null) }
                }
                LibraryScreen(
                    contentTopInset = contentTopInset,
                    header = header,
                    onScrolledChanged = { scrolled -> onScrolledChanged(frame.browseContextKey, scrolled) },
                    selectedSource = frame.uiState.selectedSource,
                    browseContextKey = frame.browseContextKey,
                    gridIdentityKey = shownScreen,
                    items = frame.uiState.visibleItems,
                    viewMode = frame.viewMode,
                    progressByItemId = frame.uiState.displayProgressByItemId,
                    ratingByItemId = frame.uiState.displayRatingByItemId,
                    pageCountByItemId = frame.uiState.displayPageCountByItemId,
                    fadeReadItems = frame.uiState.libraryFadeReadItems,
                    locallyAvailableItemIds = frame.uiState.locallyAvailableRemoteItemIds,
                    downloadableItemIds = frame.uiState.remoteDownloadActionAvailability.downloadableItemIds,
                    deletableDownloadedItemIds = frame.uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
                    downloadingItemIds = frame.uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
                    isLoadingSource = !frame.uiState.isSelectedSourceHydrated ||
                        (frame.uiState.selectedSource == SourceType.KAVITA && frame.uiState.kavitaReadingLists.isLoadingSelectedItems) ||
                        frame.uiState.isSelectedLibraryLocalAvailabilityPending,
                    filterState = frame.uiState.selectedLibraryFilters,
                    filterPanels = frame.uiState.selectedLibraryFilterPanels,
                    filterFacets = frame.uiState.selectedLibraryFilterFacets,
                    libraryTitlesById = frame.uiState.selectedSourceLibraryTitlesById,
                    totalItemCount = frame.uiState.sourceItems.size,
                    catalogKind = catalogKind,
                    // At a cold start the items are the grid's first rows until the catalog has loaded.
                    catalogCountKnown = frame.uiState.isSelectedSourceHydrated,
                    searchQuery = frame.uiState.selectedLibrarySearchQuery,
                    sortMode = frame.uiState.selectedLibrarySortMode,
                    gridCellSizeDp = frame.uiState.libraryGridCellSizeDp,
                    showSortControl = frame.showSortControl,
                    preferIssueLabels = frame.uiState.isSeriesBrowseActive || frame.uiState.isKavitaReadingListActive,
                    includeSeriesInIssueLabels = frame.uiState.isKavitaReadingListActive,
                    lockedSortLabel = "List order".takeIf { frame.uiState.isKavitaReadingListActive },
                    readingListScopeState = if (frame.uiState.selectedSource == SourceType.KAVITA) {
                        frame.uiState.kavitaReadingListScopeState()
                    } else {
                        LibraryReadingListScopeState()
                    },
                    coverImageUrlProvider = coverImageUrlProvider,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                    onCoverVisible = onCoverVisible,
                    onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                    topContent = if (frame.uiState.shouldShowLibraryShelves) {
                        @Composable { gridMetrics: LibraryGridMetrics ->
                            LibraryShelvesTopSlot(
                                browseContextKey = frame.browseContextKey,
                                shouldReserveSlot = frame.uiState.shouldReserveLibraryShelvesSlot,
                                state = frame.uiState.libraryShelvesState,
                                gridMetrics = gridMetrics,
                                itemActionContext = itemActionContext,
                                coverImageUrlProvider = coverImageUrlProvider,
                                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                                onCoverVisible = onCoverVisible,
                                onItemSelected = onItemSelected,
                                onBrowseShelf = onActiveShelfBrowserKindChanged,
                                readingTimeLeftMillis = readingTimeLeftMillis,
                            )
                        }
                    } else if (seriesNext != null) {
                        @Composable { gridMetrics: LibraryGridMetrics ->
                            val seriesNextProgress = frame.uiState.displayProgressByItemId[seriesNext.item.id]
                            val seriesNextTimeLeftMillis = remember(seriesNext.item, seriesNextProgress, readingTimeLeftMillis) {
                                seriesNextProgress?.let { progress -> readingTimeLeftMillis(seriesNext.item, progress) }
                            }
                            LibrarySeriesReadingCard(
                                next = seriesNext,
                                progress = seriesNextProgress,
                                gridMetrics = gridMetrics,
                                coverImageUrlProvider = coverImageUrlProvider,
                                onCoverVisible = onCoverVisible,
                                onItemSelected = onItemSelected,
                                actionContext = itemActionContext,
                                timeLeftMillis = seriesNextTimeLeftMillis,
                            )
                        }
                    } else if (listTop != null) {
                        @Composable { gridMetrics: LibraryGridMetrics ->
                            LibraryListScopeTopContent(
                                top = listTop,
                                progressByItemId = frame.uiState.displayProgressByItemId,
                                gridMetrics = gridMetrics,
                                coverImageUrlProvider = coverImageUrlProvider,
                                onCoverVisible = onCoverVisible,
                                onItemSelected = onItemSelected,
                                actionContext = itemActionContext,
                            )
                        }
                    } else {
                        null
                    },
                    onFilterStateChanged = onFilterStateChanged,
                    onClearFilters = onClearFilters,
                    onSaveFiltersAsList = onSaveFiltersAsList,
                    sourceUnreachable = frame.uiState.selectedSource != SourceType.LOCAL &&
                        frame.uiState.selectedSourceConnectivity == SourceConnectivityState.Offline,
                    sourceNotConnected = !frame.uiState.hasStoredSessionFor(frame.uiState.selectedSource),
                    onImportLocalFile = onImportLocalFile,
                    onConnectServer = { onConnectSource(frame.uiState.selectedSource.takeIf { it != SourceType.LOCAL }) },
                    onRetrySource = onRetrySourceConnection,
                    onReadingListSelected = onReadingListSelected,
                    onSortModeChanged = onSortModeChanged,
                    onViewModeChanged = onViewModeChanged,
                    onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                    onFadeReadItemsChanged = onFadeReadItemsChanged,
                    initialScrollPosition = frame.initialScrollPosition,
                    scrollToTopRequest = frame.scrollToTopRequest,
                    onScrollPositionChanged = onScrollPositionChanged,
                    isDownloadSelectionMode = frame.isDownloadSelectionMode,
                    selectedDownloadItemIds = frame.selectedDownloadItemIds,
                    onDownloadSelectionModeChanged = onDownloadSelectionModeChanged,
                    onSelectedDownloadItemIdsChanged = onSelectedDownloadItemIdsChanged,
                    onSelectableDownloadItemsChanged = onSelectableDownloadItemsChanged,
                    onItemSelected = onItemSelected,
                    seriesReadingDirectionFor = seriesReadingDirectionFor,
                    onSeriesDirectionSelected = onSeriesDirectionSelected,
                    seriesReaderModeFor = seriesReaderModeFor,
                    onSeriesReaderModeSelected = onSeriesReaderModeSelected,
                    onOpenItemPage = { item -> bookPageStack = listOf(item.id) },
                    onOpenBrowse = { onBrowseRequestChanged(LibraryBrowseRequest()) }
                        .takeUnless { frame.uiState.isSeriesBrowseActive || frame.uiState.isKavitaScopeActive },
                    searchPool = frame.uiState.sourceItems,
                    onBrowseTag = { chip: LibraryBrowseChip -> onBrowseRequestChanged(LibraryBrowseRequest(picked = listOf(chip))) }
                        .takeUnless { frame.uiState.isSeriesBrowseActive || frame.uiState.isKavitaScopeActive },
                    onDownloadItem = onDownloadRemoteItem,
                    onDeleteDownloadedItem = onDeleteRemoteLocalFile,
                    onSetReadStatus = onSetReadStatus,
                    pagedGridItems = frame.pagedGridFlow,
                    pagedWindowBadgeResolver = pagedWindowBadgeResolver,
                    pagedAlphabetSectionsProvider = pagedAlphabetSectionsProvider,
                    // Series browse is non-paged but bounded (one series' issues); resolve its badges early
                    // too so download status commits with the grid instead of waiting for the async recompute.
                    resolveWindowBadgesForVisibleItems = frame.uiState.isSeriesBrowseActive,
                )
            }
        }
    }
    bookPageItemId?.let { itemId ->
        LibraryBookPageHost(
            uiState = uiState,
            itemId = itemId,
            actionContext = itemActionContextFor(uiState),
            coverImageUrlProvider = coverImageUrlProvider,
            onItemSelected = { item ->
                onActiveShelfBrowserKindChanged(null)
                onItemSelected(item)
            },
            onShowItem = { item -> bookPageStack = bookPageStack + item.id },
            onBack = { bookPageStack = bookPageStack.dropLast(1) }.takeIf { bookPageStack.size > 1 },
            onDeleteImportedItem = onDeleteImportedItem,
            onFilterStateChanged = onFilterStateChanged,
            // A tag opens Browse with it picked, over the library or in place of the chips there.
            onBrowse = { chip ->
                onActiveShelfBrowserKindChanged(null)
                onBrowseRequestChanged(LibraryBrowseRequest(picked = listOf(chip)))
            },
            onDismiss = { bookPageStack = emptyList() },
        )
    }
}

/** Whether [source] has a server session saved; Local always has its books. */
private fun AppUiState.hasStoredSessionFor(source: SourceType): Boolean = when (source) {
    SourceType.LOCAL -> true
    SourceType.KAVITA -> kavitaConnection.hasStoredSession
    SourceType.STUMP -> stumpConnection.hasStoredSession
    SourceType.GRIMMORY -> grimmoryConnection.hasStoredSession
}

/** Which library screen is up. Each level of depth is one thing opened from the level above. */
internal data class LibraryScreenKey(
    val source: SourceType,
    /** An open Kavita reading list or collection. */
    val scopeId: String?,
    val seriesKey: String?,
    /** See all, whichever shelf its tabs show. */
    val shelves: Boolean,
    /** Browse, whatever its chips. */
    val browse: Boolean = false,
) {
    val depth: Int
        get() = listOfNotNull(scopeId, seriesKey).size + (if (shelves) 1 else 0) + (if (browse) 1 else 0)
}

internal fun libraryScreenKey(
    uiState: AppUiState,
    shelvesOpen: Boolean,
    browseOpen: Boolean = false,
): LibraryScreenKey {
    val source = uiState.selectedSource
    val kavita = source == SourceType.KAVITA
    return LibraryScreenKey(
        source = source,
        scopeId = if (kavita) {
            uiState.kavitaReadingLists.selectedReadingListId ?: uiState.kavitaReadingLists.selectedCollectionId
        } else {
            null
        },
        seriesKey = when (source) {
            SourceType.KAVITA -> uiState.selectedKavitaSeriesKey
            SourceType.STUMP -> uiState.selectedStumpSeriesKey
            SourceType.GRIMMORY,
            SourceType.LOCAL -> null
        },
        shelves = shelvesOpen,
        browse = browseOpen,
    )
}

internal fun libraryScreenTransition(
    from: LibraryScreenKey,
    to: LibraryScreenKey,
): AppScreenTransition = when {
    from.source != to.source -> AppScreenTransition.FadeThrough
    to.depth > from.depth -> AppScreenTransition.Forward
    to.depth < from.depth -> AppScreenTransition.Backward
    else -> AppScreenTransition.FadeThrough
}

/** Everything one library screen draws from, kept per screen while it animates out. */
private class LibraryRouteFrame(
    val uiState: AppUiState,
    val viewMode: LibraryViewMode,
    val pagedGridFlow: Flow<PagingData<ReadableItem>>?,
    val browseContextKey: String,
    val initialScrollPosition: LibraryScrollPosition,
    val scrollToTopRequest: Long,
    val isDownloadSelectionMode: Boolean,
    val selectedDownloadItemIds: List<String>,
    val showSortControl: Boolean,
    val activeShelfBrowserKind: LibraryShelfBrowserKind?,
    val shelfSearchQuery: String,
    val browseRequest: LibraryBrowseRequest?,
)

/**
 * The large title over a series (its name and how far through it you are) and over a Kavita list
 * or collection (its name; its own top counts it). None on a source's home, where the search field
 * names the source, and none while searching.
 */
internal fun AppUiState.libraryHeader(): LibraryHeader? {
    if (selectedLibrarySearchQuery.isNotBlank()) return null
    val title = selectedLibraryContextTitle ?: return null
    return when {
        isSeriesBrowseActive -> LibraryHeader(
            title = title,
            summary = librarySeriesSummary(visibleItems, displayProgressByItemId),
        )
        isKavitaScopeActive -> LibraryHeader(title = title, hasSummaryLine = false)
        else -> null
    }
}

/**
 * What the source's catalog holds, for the heading over its grid ("All books 1,474"); null in a
 * series or a Kavita list, whose header names them.
 */
internal fun AppUiState.libraryCatalogKind(): LibraryCatalogKind? =
    if (isSeriesBrowseActive || isKavitaScopeActive) null else libraryCatalogKind(sourceItems)
