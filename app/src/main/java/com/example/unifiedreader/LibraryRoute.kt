package com.example.unifiedreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.LibraryAlphabetSection
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.KavitaScopeLandingItem
import com.example.unifiedreader.feature.library.KavitaScopeLandingScreen
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryHeader
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryReadingListScopeState
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibrarySeriesReadingCard
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.LibraryWindowBadges
import com.example.unifiedreader.feature.library.librarySeriesNextIssue
import com.example.unifiedreader.feature.library.librarySeriesSummary
import com.example.unifiedreader.feature.library.librarySourceSummary
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserState
import com.example.unifiedreader.feature.library.shelves.LibraryShelfItemsBrowser
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesTopSlot
import com.example.unifiedreader.feature.library.shelves.browserState
import com.example.unifiedreader.feature.library.withWindowBadges
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
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
    onHeaderOnScreenChanged: (browseContextKey: String, onScreen: Boolean?) -> Unit,
    onImportLocalFile: () -> Unit,
    onOpenSettings: () -> Unit,
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
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.selectedSource) {
        onPrepareCoverSource(uiState.selectedSource)
    }
    val activeShelfBrowser = activeShelfBrowserKind?.let { kind ->
        uiState.libraryShelvesState.browserState(kind)
    }
    LaunchedEffect(activeShelfBrowserKind, activeShelfBrowser) {
        if (activeShelfBrowserKind != null && activeShelfBrowser == null) {
            onActiveShelfBrowserKindChanged(null)
        }
    }
    val screen = libraryScreenKey(uiState, activeShelfBrowser?.kind)
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
        activeShelfBrowser = activeShelfBrowser,
        shelfSearchQuery = shelfSearchQuery,
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
            val itemActionContext = LibraryItemActionContext(
                progressByItemId = frame.uiState.displayProgressByItemId,
                locallyAvailableItemIds = frame.uiState.locallyAvailableRemoteItemIds,
                downloadableItemIds = frame.uiState.remoteDownloadActionAvailability.downloadableItemIds,
                deletableDownloadedItemIds = frame.uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
                downloadingItemIds = frame.uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
                seriesReadingDirectionFor = seriesReadingDirectionFor,
                onSeriesDirectionSelected = onSeriesDirectionSelected,
                seriesReaderModeFor = seriesReaderModeFor,
                onSeriesReaderModeSelected = onSeriesReaderModeSelected,
                onDownloadItem = onDownloadRemoteItem,
                onDeleteDownloadedItem = onDeleteRemoteLocalFile,
                onSetReadStatus = onSetReadStatus,
            ).withWindowBadges(shelfWindowBadges)
            if (frame.activeShelfBrowser != null) {
                LibraryShelfItemsBrowser(
                    browser = frame.activeShelfBrowser,
                    browseContextKey = "${frame.browseContextKey}:shelf:${frame.activeShelfBrowser.kind.name}",
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                    gridCellSizeDp = frame.uiState.libraryGridCellSizeDp,
                    onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                    searchQuery = frame.shelfSearchQuery,
                    onSearchQueryChanged = onShelfSearchQueryChanged,
                    showEmbeddedTopBar = false,
                    contentTopInset = contentTopInset,
                    onCoverVisible = onCoverVisible,
                    onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                    onBack = { onActiveShelfBrowserKindChanged(null) },
                    onItemSelected = { item ->
                        onActiveShelfBrowserKindChanged(null)
                        onItemSelected(item)
                    },
                    onDeleteImportedItem = onDeleteImportedItem,
                )
            } else if (frame.uiState.isKavitaVisualSectionActive) {
                KavitaScopeLandingScreen(
                    contentTopInset = contentTopInset,
                    title = frame.uiState.kavitaLibrarySection.displayName(),
                    items = frame.uiState.kavitaScopeLandingItems(),
                    isLoading = !frame.uiState.isSelectedSourceHydrated || frame.uiState.kavitaReadingLists.isLoadingSummaries,
                    errorMessage = frame.uiState.kavitaReadingLists.errorMessage,
                    searchQuery = frame.uiState.selectedLibrarySearchQuery,
                    gridCellSizeDp = frame.uiState.libraryGridCellSizeDp,
                    browseContextKey = frame.browseContextKey,
                    emptyMessage = when (frame.uiState.kavitaLibrarySection) {
                        KavitaLibrarySection.Collections -> "No Kavita collections yet"
                        KavitaLibrarySection.ReadingLists -> "No Kavita reading lists yet"
                        KavitaLibrarySection.Home -> "No Kavita lists yet"
                    },
                    coverImageUrlProvider = coverImageUrlProvider,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                    onCoverVisible = onCoverVisible,
                    onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                    onItemSelected = onReadingListSelected,
                )
            } else {
                val header = remember(
                    frame.uiState.selectedSource,
                    frame.uiState.selectedLibrarySearchQuery.isBlank(),
                    frame.uiState.isSeriesBrowseActive,
                    frame.uiState.isKavitaScopeActive,
                    frame.uiState.isKavitaVisualSectionActive,
                    frame.uiState.selectedLibraryContextTitle,
                    frame.uiState.sourceItems,
                    frame.uiState.visibleItems,
                    frame.uiState.displayProgressByItemId,
                    frame.uiState.libraryShelvesState.continueReading.size,
                ) { frame.uiState.libraryHeader() }
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
                DisposableEffect(frame.browseContextKey) {
                    onDispose { onHeaderOnScreenChanged(frame.browseContextKey, null) }
                }
                LibraryScreen(
                    contentTopInset = contentTopInset,
                    header = header,
                    onHeaderOnScreenChanged = { onScreen -> onHeaderOnScreenChanged(frame.browseContextKey, onScreen) },
                    selectedSource = frame.uiState.selectedSource,
                    browseContextKey = frame.browseContextKey,
                    gridIdentityKey = shownScreen,
                    items = frame.uiState.visibleItems,
                    viewMode = frame.viewMode,
                    progressByItemId = frame.uiState.displayProgressByItemId,
                    ratingByItemId = frame.uiState.displayRatingByItemId,
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
                                onDeleteImportedItem = onDeleteImportedItem,
                            )
                        }
                    } else if (seriesNext != null) {
                        @Composable { gridMetrics: LibraryGridMetrics ->
                            LibrarySeriesReadingCard(
                                next = seriesNext,
                                progress = frame.uiState.displayProgressByItemId[seriesNext.item.id],
                                gridMetrics = gridMetrics,
                                coverImageUrlProvider = coverImageUrlProvider,
                                onCoverVisible = onCoverVisible,
                                onItemSelected = onItemSelected,
                                actionContext = itemActionContext,
                                onDeleteImportedRequested = onDeleteImportedItem,
                            )
                        }
                    } else {
                        null
                    },
                    onFilterStateChanged = onFilterStateChanged,
                    onClearFilters = onClearFilters,
                    sourceUnreachable = frame.uiState.selectedSource != SourceType.LOCAL &&
                        frame.uiState.selectedSourceConnectivity == SourceConnectivityState.Offline,
                    onImportLocalFile = onImportLocalFile,
                    onConnectServer = onOpenSettings,
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
                    onDeleteImportedItem = onDeleteImportedItem,
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
}

private fun AppUiState.kavitaScopeLandingItems(): List<KavitaScopeLandingItem> = when (kavitaLibrarySection) {
    KavitaLibrarySection.Collections -> kavitaReadingLists.collections.map { collection ->
        KavitaScopeLandingItem(
            id = collection.id,
            title = collection.title,
            itemCount = collection.itemCount,
            summary = collection.summary,
            coverUrl = collection.coverUrl,
            sourceType = collection.sourceType,
            remoteId = collection.remoteId,
            coverCacheScope = "collection",
        )
    }

    KavitaLibrarySection.ReadingLists -> kavitaReadingLists.summaries.map { summary ->
        KavitaScopeLandingItem(
            id = summary.id,
            title = summary.title,
            itemCount = summary.itemCount,
            summary = summary.summary,
            coverUrl = summary.coverUrl,
            sourceType = summary.sourceType,
            remoteId = summary.remoteId,
            coverCacheScope = "reading-list",
        )
    }

    KavitaLibrarySection.Home -> emptyList()
}

/** Which library screen is up. Each level of depth is one thing opened from the level above. */
internal data class LibraryScreenKey(
    val source: SourceType,
    /** The Kavita Collections or Reading Lists landing, and everything opened from it. */
    val kavitaSection: KavitaLibrarySection?,
    /** An open Kavita reading list or collection. */
    val scopeId: String?,
    val seriesKey: String?,
    val shelf: LibraryShelfBrowserKind?,
) {
    val depth: Int
        get() = listOfNotNull(kavitaSection, scopeId, seriesKey, shelf).size
}

internal fun libraryScreenKey(
    uiState: AppUiState,
    shelf: LibraryShelfBrowserKind?,
): LibraryScreenKey {
    val source = uiState.selectedSource
    val kavita = source == SourceType.KAVITA
    return LibraryScreenKey(
        source = source,
        kavitaSection = uiState.kavitaLibrarySection.takeIf { kavita && it != KavitaLibrarySection.Home },
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
        shelf = shelf,
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
    val activeShelfBrowser: LibraryShelfBrowserState?,
    val shelfSearchQuery: String,
)

/**
 * The large title over the library home (the source's name and what it holds) and over a series
 * (its name and how far through it you are). None while searching or inside a Kavita list or
 * section, where the bar keeps the name.
 */
private fun AppUiState.libraryHeader(): LibraryHeader? = when {
    !showsLibraryHeader -> null
    isSeriesBrowseActive -> LibraryHeader(
        title = selectedLibraryContextTitle ?: selectedSource.displayName(),
        summary = librarySeriesSummary(visibleItems, displayProgressByItemId),
    )
    else -> LibraryHeader(
        title = selectedSource.displayName(),
        summary = librarySourceSummary(sourceItems, inProgressCount = libraryShelvesState.continueReading.size),
    )
}

/** Whether the library screen opens with a large title (see [libraryHeader]). */
internal val AppUiState.showsLibraryHeader: Boolean
    get() = selectedLibrarySearchQuery.isBlank() && !isKavitaScopeActive && !isKavitaVisualSectionActive
