package com.example.unifiedreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchScope
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.unifiedreader.core.model.LibraryAlphabetSection
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySearchTagSuggestion
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.librarySearchTagSuggestions
import com.example.unifiedreader.feature.library.tags.LocalLibraryTagSorting
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.theme.AppMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// How many leading roots the paged-window badge resolver processes in its fast first phase — enough to
// cover the visible cards plus a small scroll buffer, so their badges commit near-instantly before the
// full loaded window is resolved.

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    selectedSource: SourceType,
    browseContextKey: String,
    // What the grid shows apart from its sort, filters and search (a source, a series). While it
    // stays the same, the in-memory grid keeps one state, so a sort or filter change moves the
    // covers instead of rebuilding the grid. Null keeps a grid per browse context.
    gridIdentityKey: Any? = null,
    items: List<ReadableItem>,
    viewMode: LibraryViewMode,
    progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    ratingByItemId: Map<String, LibraryRatingBadgeState> = emptyMap(),
    // Each item's length in pages (a series tile's is all its issues), for the Pages sort.
    pageCountByItemId: Map<String, Int> = emptyMap(),
    fadeReadItems: Boolean = false,
    locallyAvailableItemIds: Set<String> = emptySet(),
    downloadableItemIds: Set<String> = emptySet(),
    deletableDownloadedItemIds: Set<String> = emptySet(),
    downloadingItemIds: Set<String> = emptySet(),
    isLoadingSource: Boolean = false,
    filterState: LibraryFilterState = LibraryFilterState(),
    filterPanels: List<LibraryFilterPanel> = emptyList(),
    filterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    // A source's books, to count Set in, When, About and Mood with your tag sorting; empty in a
    // series or a Kavita list, where the filters keep [filterFacets] as they are.
    filterPool: List<ReadableItem> = emptyList(),
    libraryTitlesById: Map<String, String> = emptyMap(),
    // How many items this view has before filters and search, for the filter sheet's "14 of 21".
    totalItemCount: Int? = null,
    // A source's home: what its catalog holds, for the heading by the filters ("All books 1,474").
    // Null in a series or a Kavita list. The counts stay out until [catalogCountKnown].
    catalogKind: LibraryCatalogKind? = null,
    catalogCountKnown: Boolean = true,
    searchQuery: String = "",
    sortMode: LibrarySortMode = defaultLibrarySortMode,
    gridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    showSortControl: Boolean = true,
    preferIssueLabels: Boolean = false,
    includeSeriesInIssueLabels: Boolean = false,
    lockedSortLabel: String? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    // Drawn above the filter summary as a full-width grid row; receives the grid's column setup so it
    // can size its covers to match the grid's.
    // The large title over everything else: a series' name, or a Kavita list's.
    header: LibraryHeader? = null,
    // Whether the grid has scrolled under the top bar, which shows its glass only then.
    onScrolledChanged: (Boolean) -> Unit = {},
    topContent: (@Composable (LibraryGridMetrics) -> Unit)? = null,
    // Extra top padding so the first row clears a floating top bar the content scrolls under.
    contentTopInset: Dp = 0.dp,
    onFilterStateChanged: (LibraryFilterState) -> Unit = {},
    onClearFilters: () -> Unit = {},
    // Save as list in Filters: keeps the filters as a smart list. Null where lists aren't offered.
    onSaveFiltersAsList: ((LibraryFilterState) -> Unit)? = null,
    // Filters opened on a list's behalf (a new smart list, a smart list's filters), with its own buttons.
    filterListMode: LibraryFilterListMode? = null,
    // Opens Filters (docked or as a sheet) each time it goes up.
    filterPanelRequest: Int = 0,
    // Picking books for a list: every book can be selected, not only those still to download.
    selectAnyItem: Boolean = false,
    // The empty library's actions; null leaves an action out.
    sourceUnreachable: Boolean = false,
    // A server source with no stored session: the empty state offers Connect instead of a sync.
    sourceNotConnected: Boolean = false,
    onImportLocalFile: (() -> Unit)? = null,
    onConnectServer: (() -> Unit)? = null,
    onRetrySource: (() -> Unit)? = null,
    onSortModeChanged: (LibrarySortMode) -> Unit = {},
    onViewModeChanged: (LibraryViewMode) -> Unit = {},
    onGridCellSizeDpChanged: (Int) -> Unit = {},
    onFadeReadItemsChanged: (Boolean) -> Unit = {},
    onCoverAsPrintedChanged: ((Boolean) -> Unit)? = null,
    initialScrollPosition: LibraryScrollPosition = LibraryScrollPosition(),
    scrollToTopRequest: Long = 0L,
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit = {},
    isDownloadSelectionMode: Boolean = false,
    selectedDownloadItemIds: List<String> = emptyList(),
    onDownloadSelectionModeChanged: (Boolean) -> Unit = {},
    onSelectedDownloadItemIdsChanged: (List<String>) -> Unit = {},
    onSelectableDownloadItemsChanged: (List<ReadableItem>) -> Unit = {},
    onItemSelected: (ReadableItem) -> Unit,
    seriesReadingDirectionFor: (ReadableItem) -> ComicReadingDirection? = { null },
    onSeriesDirectionSelected: (ReadableItem, ComicReadingDirection) -> Unit = { _, _ -> },
    seriesReaderModeFor: (ReadableItem) -> ComicReaderMode? = { null },
    onSeriesReaderModeSelected: (ReadableItem, ComicReaderMode) -> Unit = { _, _ -> },
    // The "•••" under a cover opens the item's book page, which the host draws.
    onOpenItemPage: (ReadableItem) -> Unit = {},
    // While searching: the source's books, for the tags the search names; picking one filters by it.
    searchPool: List<ReadableItem> = emptyList(),
    onFilterByTag: ((LibraryBrowseChip) -> Unit)? = null,
    onDownloadItem: (ReadableItem) -> Unit = {},
    onDeleteDownloadedItem: (ReadableItem) -> Unit = {},
    onSetReadStatus: (ReadableItem, Boolean) -> Unit = { _, _ -> },
    // When non-null, the grid body renders from this Room-backed paging flow instead of [items]; the
    // rest of the screen (shelves, filter summary, sort, dialogs) is unchanged. Existing callers pass
    // null and behave exactly as before.
    pagedGridItems: Flow<PagingData<ReadableItem>>? = null,
    // Resolves the paged window's per-card badges (read/progress, rating, download status) from bounded
    // sources so they commit with the grid instead of waiting ~1s for the in-memory recompute. Only
    // used on the paged path; null (all other callers) keeps the recompute maps as the sole source.
    pagedWindowBadgeResolver: (suspend (List<ReadableItem>) -> LibraryWindowBadges)? = null,
    // Supplies the SQL-computed alphabet rail (present letters + their grid offsets) for a paged rail
    // sort, so the paged grid can render + jump the A–Z rail without the in-memory ordered list. It
    // emits again when the grid roots change. Null (non-paged callers) keeps the rail driven by [items].
    pagedAlphabetSectionsProvider: ((LibrarySortMode) -> Flow<List<LibraryAlphabetSection>>)? = null,
    // Also run the badge overlay for a non-paged but *bounded* view (series browse): there the grid +
    // progress/rating come from the synchronous computeQuick, but download availability lands only with
    // the async recompute, so resolving the visible issues' download status early commits it with them.
    // Must stay false for the large non-paged views (other sources, filtered-runtime) — resolving the
    // whole in-memory list would be wasteful.
    resolveWindowBadgesForVisibleItems: Boolean = false,
    // False once the recompute's badge maps are complete (download availability included): the window
    // lookup then has nothing to add.
    windowBadgesNeeded: Boolean = true,
) {
    val pagedItems = pagedGridItems?.collectAsLazyPagingItems()
    val isPaged = pagedItems != null
    var openFilterPanel by rememberSaveable { mutableStateOf<LibraryFilterPanel?>(null) }
    var isFilterSheetOpen by rememberSaveable { mutableStateOf(false) }
    // On an Expanded window the filters dock beside the library instead of opening as a sheet.
    val dockFilters = currentAppWindowWidth() == AppWindowWidth.Expanded
    val filterDock = rememberLibraryFilterDockState()
    LaunchedEffect(filterPanelRequest) {
        if (filterPanelRequest == 0) return@LaunchedEffect
        if (dockFilters) filterDock.show() else isFilterSheetOpen = true
    }
    // Docked open, the books the filters find start at content top: the reading card and shelves step
    // aside, as for a search, and come back when the panel closes (a filter left on never hides them).
    val filtersDocked = dockFilters && filterDock.open
    val shownTopContent = topContent.takeUnless { filtersDocked }
    val shownFilterFacets = rememberLibraryFilterFacetsWithTags(
        facets = filterFacets,
        pool = filterPool,
        filters = filterState,
        sorting = LocalLibraryTagSorting.current.sortingFor(selectedSource),
        progressByItemId = progressByItemId,
        pageCountByItemId = pageCountByItemId,
        locallyAvailableItemIds = locallyAvailableItemIds,
    )
    val filterSeriesCovers = remember(filterPool, progressByItemId, coverImageUrlProvider) {
        libraryFilterSeriesCovers(filterPool, progressByItemId, coverImageUrlProvider)
    }
    var handledScrollToTopRequest by remember { mutableStateOf(scrollToTopRequest) }
    val coroutineScope = rememberCoroutineScope()
    val visibleItems = items
    // What the grid actually renders — the paging snapshot when paged, else the in-memory list. The
    // paged path reads this (not [items]/visibleItems) everywhere it needs the on-screen set — sort
    // menu, date-added scroll anchor, selection, cover prefetch — so the in-memory sorted list can be
    // skipped for the paged view (M2).
    val renderedItems = if (isPaged) pagedItems!!.itemSnapshotList.items else visibleItems
    // Paged-window badge overlay: resolve the on-screen roots' read/progress, rating, and download
    // status from bounded sources so they paint with the grid instead of ~1s later. The recompute maps
    // stay the base (recompute wins once it lands — its values match, so nothing flips); the window
    // badges only fill the keys the recompute doesn't have yet (i.e. everything at cold start). Empty on
    // every non-paged path, so `effective*` below equals the raw inputs there.
    val windowBadges = if ((isPaged || resolveWindowBadgesForVisibleItems) && pagedWindowBadgeResolver != null) {
        rememberLibraryWindowBadges(
            renderedItems = renderedItems,
            resolver = pagedWindowBadgeResolver,
            recomputedBadgeInputs = listOf(
                progressByItemId,
                ratingByItemId,
                locallyAvailableItemIds,
                downloadableItemIds,
                deletableDownloadedItemIds,
                downloadingItemIds,
            ),
            isNeeded = windowBadgesNeeded,
        )
    } else {
        LibraryWindowBadges()
    }
    // Kept across screen updates: the recompute's maps hold a whole server's items, so copying them on
    // every recomposition costs more than the comparison of the keys.
    val effectiveProgressByItemId = remember(windowBadges.displayProgressByItemId, progressByItemId) {
        windowBadges.displayProgressByItemId.overlaidBy(progressByItemId)
    }
    val effectiveRatingByItemId = remember(windowBadges.displayRatingByItemId, ratingByItemId) {
        windowBadges.displayRatingByItemId.overlaidBy(ratingByItemId)
    }
    val effectiveLocallyAvailableItemIds = remember(locallyAvailableItemIds, windowBadges.locallyAvailableItemIds) {
        locallyAvailableItemIds.unionWith(windowBadges.locallyAvailableItemIds)
    }
    val windowDownloads = windowBadges.downloadActionAvailability
    val effectiveDownloadableItemIds = remember(downloadableItemIds, windowDownloads.downloadableItemIds) {
        downloadableItemIds.unionWith(windowDownloads.downloadableItemIds)
    }
    val effectiveDeletableDownloadedItemIds =
        remember(deletableDownloadedItemIds, windowDownloads.deletableDownloadedItemIds) {
            deletableDownloadedItemIds.unionWith(windowDownloads.deletableDownloadedItemIds)
        }
    val effectiveDownloadingItemIds = remember(downloadingItemIds, windowDownloads.activeDownloadingItemIds) {
        downloadingItemIds.unionWith(windowDownloads.activeDownloadingItemIds)
    }
    val itemActionContext = LibraryItemActionContext(
        progressByItemId = effectiveProgressByItemId,
        locallyAvailableItemIds = effectiveLocallyAvailableItemIds,
        downloadableItemIds = effectiveDownloadableItemIds,
        deletableDownloadedItemIds = effectiveDeletableDownloadedItemIds,
        downloadingItemIds = effectiveDownloadingItemIds,
        seriesReadingDirectionFor = seriesReadingDirectionFor,
        onSeriesDirectionSelected = onSeriesDirectionSelected,
        seriesReaderModeFor = seriesReaderModeFor,
        onSeriesReaderModeSelected = onSeriesReaderModeSelected,
        onDownloadItem = onDownloadItem,
        onDeleteDownloadedItem = onDeleteDownloadedItem,
        onSetReadStatus = onSetReadStatus,
        onOpenItemPage = onOpenItemPage,
    )
    val availableSortModes = remember(renderedItems, sortMode, pageCountByItemId) {
        librarySortModesAvailableFor(items = renderedItems, selectedSortMode = sortMode, pageCountByItemId = pageCountByItemId)
    }
    val itemAlphabetSections = remember(items, sortMode, pageCountByItemId) {
        if (!sortMode.usesAlphabetRail) {
            emptyList()
        } else {
            items.map { item -> item.sortFilterLetter(sortMode) }
        }
    }
    // On the paged path the ordered set isn't in memory, so the rail comes from SQL (present letters +
    // the grid offset of each section's first root). Resolved off-thread and followed as the grid roots
    // change (a sync, the rebuild after a migration); empty for rail-less sorts.
    val latestAlphabetProvider by rememberUpdatedState(pagedAlphabetSectionsProvider)
    val pagedAlphabetSections by produceState(emptyList<LibraryAlphabetSection>(), isPaged, sortMode) {
        val provider = latestAlphabetProvider
        if (isPaged && provider != null && sortMode.usesAlphabetRail) {
            provider(sortMode).collect { sections -> value = sections }
        } else {
            value = emptyList()
        }
    }
    // Rail buttons + per-section scroll offsets. Both variants are computed unconditionally (remember
    // must not be called inside a branch — `isPaged` can flip when a runtime filter is applied) and the
    // paged one is selected when the grid is served by Room.
    val inMemorySortLetters = remember(itemAlphabetSections, sortMode) {
        alphabetNavigationSections(itemAlphabetSections.filterNotNull())
    }
    val inMemorySectionStarts = remember(itemAlphabetSections) { alphabetSectionStarts(itemAlphabetSections) }
    val pagedSortLetters = remember(pagedAlphabetSections) { pagedAlphabetSections.map(LibraryAlphabetSection::letter) }
    val pagedSectionStarts = remember(pagedAlphabetSections) {
        pagedAlphabetSections.associate { section -> section.letter to section.itemOffset }
    }
    val sortLetters = if (isPaged) pagedSortLetters else inMemorySortLetters
    val alphabetSectionStarts = if (isPaged) pagedSectionStarts else inMemorySectionStarts
    val alphabetRailVisible = showSortControl && searchQuery.isBlank() && sortLetters.isNotEmpty()
    // When paged, the grid body's emptiness/loading come from the paging flow, not [items].
    val gridIsEmpty = if (isPaged) pagedItems!!.itemCount == 0 else visibleItems.isEmpty()
    val showGridLoading = isLoadingSource || (isPaged && pagedItems!!.loadState.refresh is LoadState.Loading)
    val selectableDownloadItems = remember(renderedItems, downloadableItemIds, downloadingItemIds, selectAnyItem) {
        renderedItems.filter { item ->
            if (selectAnyItem) {
                // A whole series goes in as its issues (the list's mode opens them out on Done).
                true
            } else {
                item.id in downloadableItemIds && item.id !in downloadingItemIds
            }
        }
    }
    val selectableDownloadItemIds = remember(selectableDownloadItems) {
        selectableDownloadItems.mapTo(mutableSetOf(), ReadableItem::id)
    }
    val renderedItemIds = remember(renderedItems) { renderedItems.mapTo(mutableSetOf(), ReadableItem::id) }
    val selectedDownloadItemIdSet = sanitizeLibraryDownloadSelection(
        selectedIds = selectedDownloadItemIds,
        renderedIds = renderedItemIds,
        selectableIds = selectableDownloadItemIds,
        isPaged = isPaged,
    ).toSet()
    val leadingContentItemCount = 1 +
        (if (header != null) 1 else 0) +
        (if (shownTopContent != null) 1 else 0)
    // A change of picks opens the library at All books' row, the books they found, instead of at the
    // reading card above it; a combination left further down still opens there.
    val contextPicks = remember { LibraryContextPicks() }
    val startPosition = remember(browseContextKey, initialScrollPosition, leadingContentItemCount) {
        libraryStartScrollPosition(
            saved = initialScrollPosition,
            picksChanged = contextPicks.changedFor(browseContextKey, filterState),
            resultsRowIndex = leadingContentItemCount - 1,
        )
    }
    SideEffect { contextPicks.record(browseContextKey, filterState) }
    // The paged grid reloads from scratch on every sort or filter change, so it has nothing to
    // move and keeps a grid per browse context.
    val gridStateKey = if (isPaged || gridIdentityKey == null) browseContextKey else gridIdentityKey
    val gridState = remember(gridStateKey) {
        LazyGridState(
            firstVisibleItemIndex = startPosition.index,
            firstVisibleItemScrollOffset = startPosition.offset,
            prefetchStrategy = LibraryGridScrollPrefetchStrategy,
        )
    }
    // A kept grid still opens each sort and filter combination where it was left: one saved
    // further down is restored below (pendingScrollRestore); one near the top is set here. A
    // scroll jump plays no item animations, so covers only move when the grid stays put.
    var gridStateContextKey by remember(gridStateKey) { mutableStateOf(browseContextKey) }
    if (gridStateContextKey != browseContextKey) {
        LaunchedEffect(browseContextKey) {
            gridStateContextKey = browseContextKey
            if (startPosition.index == 0) {
                gridState.scrollToItem(0, startPosition.offset)
            }
        }
    }
    // Held, and not reported, until the grid can reach it (see LibraryScrollRestoreStep); a drag or
    // fling hands the position back to the user.
    var pendingScrollRestore by remember(browseContextKey) {
        mutableStateOf(startPosition.takeIf { position -> position.index > 0 })
    }
    val dateAddedTopItemId = if (showSortControl && sortMode == LibrarySortMode.DATE_ADDED) {
        renderedItems.firstOrNull()?.id
    } else {
        null
    }
    // The tags a search names, counted over the whole source and in the rows you sorted them into, off the main thread.
    val tagSorting = LocalLibraryTagSorting.current.sortingFor(selectedSource) ?: LibraryTagSorting()
    val searchTagSuggestions by produceState(
        emptyList<LibrarySearchTagSuggestion>(),
        searchQuery,
        searchPool,
        onFilterByTag != null,
        tagSorting,
    ) {
        value = if (onFilterByTag == null || searchQuery.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { librarySearchTagSuggestions(searchPool, searchQuery, tagSorting) }
        }
    }
    // A row that arrives above a grid still at its very top (the shelves, when the library was
    // drawn under a back gesture before they were computed) is scrolled into view: the lazy grid
    // would otherwise keep its old first row in place and leave the new one above the screen.
    var shownLeadingContentItemCount by remember(browseContextKey) { mutableIntStateOf(leadingContentItemCount) }
    LaunchedEffect(leadingContentItemCount) {
        val added = leadingContentItemCount - shownLeadingContentItemCount
        shownLeadingContentItemCount = leadingContentItemCount
        if (added > 0 &&
            pendingScrollRestore == null &&
            gridState.firstVisibleItemIndex <= added &&
            gridState.firstVisibleItemScrollOffset == 0
        ) {
            gridState.scrollToItem(0)
        }
    }
    pendingScrollRestore?.let { savedPosition ->
        val gridItemCount = leadingContentItemCount + if (isPaged) pagedItems!!.itemCount else visibleItems.size
        val restoreStep = libraryScrollRestoreStep(
            saved = savedPosition,
            gridItemCount = gridItemCount,
            isLoading = showGridLoading || (isPaged && pagedItems!!.loadState.append is LoadState.Loading),
            canLoadMore = isPaged && !pagedItems!!.loadState.append.endOfPaginationReached,
        )
        LaunchedEffect(browseContextKey, restoreStep, gridItemCount) {
            when (restoreStep) {
                LibraryScrollRestoreStep.ScrollToSaved -> {
                    gridState.scrollToItem(savedPosition.index, savedPosition.offset)
                    pendingScrollRestore = null
                }
                // Reading a slot near the end makes paging append the next page.
                LibraryScrollRestoreStep.LoadMore -> pagedItems?.takeIf { it.itemCount > 0 }?.let { it[it.itemCount - 1] }
                LibraryScrollRestoreStep.GiveUp -> pendingScrollRestore = null
                LibraryScrollRestoreStep.Wait -> Unit
            }
        }
    }
    val activeSortLetter by remember(
        gridState,
        isPaged,
        itemAlphabetSections,
        pagedAlphabetSections,
        leadingContentItemCount,
    ) {
        derivedStateOf {
            val rootIndex = gridState.firstVisibleItemIndex - leadingContentItemCount
            if (isPaged) {
                // Paged: find the last rail section whose offset is at or before the current root.
                pagedAlphabetSections.lastOrNull { section -> section.itemOffset <= rootIndex }?.letter
            } else {
                activeAlphabetSection(
                    sectionKeys = itemAlphabetSections,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    leadingContentItemCount = leadingContentItemCount,
                )
            }
        }
    }
    var previousDateAddedTopItemId by remember(browseContextKey) {
        mutableStateOf(dateAddedTopItemId)
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > handledScrollToTopRequest) {
            handledScrollToTopRequest = scrollToTopRequest
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(dateAddedTopItemId) {
        val previousTopItemId = previousDateAddedTopItemId
        if (dateAddedTopItemId != null &&
            previousTopItemId != null &&
            dateAddedTopItemId != previousTopItemId
        ) {
            gridState.scrollToItem(0)
        }
        previousDateAddedTopItemId = dateAddedTopItemId
    }
    LaunchedEffect(browseContextKey, gridState) {
        snapshotFlow {
            LibraryScrollPosition(
                index = gridState.firstVisibleItemIndex,
                offset = gridState.firstVisibleItemScrollOffset,
            ).takeIf { pendingScrollRestore == null }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(onScrollPositionChanged)
    }
    LaunchedEffect(browseContextKey, gridState) {
        snapshotFlow { gridState.isScrollInProgress }.first { scrolling -> scrolling }
        pendingScrollRestore = null
    }
    LaunchedEffect(browseContextKey, gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(onCoverViewportScrollingChanged)
    }
    DisposableEffect(browseContextKey) {
        onDispose {
            onCoverViewportScrollingChanged(false)
        }
    }
    LaunchedEffect(browseContextKey, selectableDownloadItems) {
        onSelectableDownloadItemsChanged(selectableDownloadItems)
    }
    LaunchedEffect(browseContextKey, selectableDownloadItemIds, selectedDownloadItemIds, isDownloadSelectionMode) {
        val sanitizedSelection = sanitizeLibraryDownloadSelection(
            selectedIds = selectedDownloadItemIds,
            renderedIds = renderedItemIds,
            selectableIds = selectableDownloadItemIds,
            isPaged = isPaged,
        )
        if (sanitizedSelection != selectedDownloadItemIds) {
            onSelectedDownloadItemIdsChanged(sanitizedSelection)
        }
        // A paged window can lack selectable roots while picks remain elsewhere; the host ends the
        // mode once the whole view has none.
        if (selectableDownloadItemIds.isEmpty() && isDownloadSelectionMode && !isPaged) {
            onDownloadSelectionModeChanged(false)
        }
    }

    fun toggleDownloadSelection(item: ReadableItem) {
        if (item.id !in selectableDownloadItemIds) return
        onSelectedDownloadItemIdsChanged(
            orderLibraryDownloadSelection(
                selectedIds = selectedDownloadItemIdSet.toggle(item.id),
                renderedIds = renderedItems.map(ReadableItem::id),
                isPaged = isPaged,
            ),
        )
    }

    fun startDownloadSelection(item: ReadableItem) {
        if (item.id !in selectableDownloadItemIds) return
        onDownloadSelectionModeChanged(true)
        if (item.id !in selectedDownloadItemIdSet) {
            onSelectedDownloadItemIdsChanged(
                orderLibraryDownloadSelection(
                    selectedIds = selectedDownloadItemIdSet + item.id,
                    renderedIds = renderedItems.map(ReadableItem::id),
                    isPaged = isPaged,
                ),
            )
        }
    }

    // The paged grid's own item count is only the pages loaded so far.
    val shownItemCount = {
        libraryShownItemCount(
            isPaged = isPaged,
            visibleCount = visibleItems.size,
            loadedCount = pagedItems?.itemCount ?: 0,
            filterState = filterState,
            facetCounts = filterFacets.counts,
            totalCount = totalItemCount,
        )
    }

    val gutter = appGutter()
    Row(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val gridEndPadding = if (alphabetRailVisible) {
                libraryGridNavigationEndPaddingDp
            } else {
                gutter
            }
            // The docked panel takes its width and the gutter after it; the covers keep the size they had without it.
            val undockedMaxWidth = if (filtersDocked) maxWidth + AppLayout.sheetWidth + gutter else maxWidth
            val gridPresentation = remember(maxWidth, undockedMaxWidth, gutter, gridEndPadding, gridCellSizeDp) {
                libraryGridPresentation(
                    maxWidth = maxWidth,
                    startPadding = gutter,
                    endPadding = gridEndPadding,
                    requestedGridCellSizeDp = gridCellSizeDp,
                    undockedMaxWidth = undockedMaxWidth,
                )
            }
            val gridCoverImageVariant = libraryGridCoverImageVariant(
                gridPresentation.selectedGridSize.cellSizeDp,
            )
            val showGridTitles = LibraryCoverSize.of(gridPresentation.selectedGridSize.cellSizeDp).showsTitle
            VisibleRemoteCoverPrefetcher(
                browseContextKey = browseContextKey,
                gridState = gridState,
                // Indexed by grid slot: the paged snapshot keeps placeholders where `renderedItems` drops them.
                items = if (isPaged) pagedItems!!.itemSnapshotList else renderedItems,
                leadingContentItemCount = leadingContentItemCount,
                variant = gridCoverImageVariant,
                maxTargetsWhileScrolling = gridPresentation.scrollingPrefetchItemLimit,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                onCoverVisible = onCoverVisible,
            )

            // Sort and filter changes move covers to their new places instead of cutting.
            val gridCard: @Composable LazyGridItemScope.(ReadableItem) -> Unit = { item ->
                GridCoverCard(
                    item = item,
                    progress = effectiveProgressByItemId[item.id],
                    rating = effectiveRatingByItemId[item.id],
                    isDownloaded = itemActionContext.isDownloaded(item),
                    fadeRead = fadeReadItems,
                    imageVariant = gridCoverImageVariant,
                    coverImageUrlProvider = coverImageUrlProvider,
                    seriesIssueCount = item.issueCount,
                    // A series or a Kavita list keeps its own order, so its captions stay as they are.
                    lengthLabel = if (sortMode == LibrarySortMode.PAGES && showSortControl) {
                        libraryLengthLabel(pageCountByItemId.pageCountOf(item), item.issueCount)
                    } else {
                        null
                    },
                    title = when {
                        preferIssueLabels && includeSeriesInIssueLabels -> itemMixedIssueBrowsePrimaryText(item)
                        preferIssueLabels -> itemSeriesBrowsePrimaryText(item)
                        else -> itemPrimaryText(item)
                    },
                    showTitle = showGridTitles,
                    isSelectionMode = isDownloadSelectionMode,
                    isSelected = item.id in selectedDownloadItemIdSet,
                    onClick = { onItemSelected(item) },
                    onSelectionToggle = { toggleDownloadSelection(item) },
                    onSelectionStart = if (item.id in selectableDownloadItemIds) {
                        { startDownloadSelection(item) }
                    } else {
                        null
                    },
                    menuContent = {
                        LibraryItemPageButton(
                            item = item,
                            actionContext = itemActionContext,
                            buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                        )
                    },
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = AppMotion.spatial(IntOffset.VisibilityThreshold),
                        fadeOutSpec = null,
                    ),
                )
            }

            LazyVerticalGrid(
                columns = if (gridPresentation.fixedColumns) {
                    GridCells.Fixed(gridPresentation.selectedGridSize.columnCount)
                } else {
                    LibraryViewMode.Grid.gridCells(
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp,
                    )
                },
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = gutter,
                    top = contentTopInset + AppLayout.contentTop,
                    end = gridEndPadding,
                    bottom = AppLayout.contentBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
                horizontalArrangement = Arrangement.spacedBy(libraryGridHorizontalSpacingDp),
            ) {
                if (header != null) {
                    item(
                        key = LibraryHeaderItemKey,
                        contentType = LibraryScreenContentTypes.Header,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        LibraryHeaderBlock(header = header, compact = isLibraryCompactWindow())
                    }
                }
                if (shownTopContent != null) {
                    item(
                        key = "top-content",
                        contentType = LibraryScreenContentTypes.TopContent,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        // The default size whatever the cover size: the slider sizes All books only, so its row, which the
                        // sort menu hangs from, stays put (Huge stacked the reading card and pushed the row off screen).
                        shownTopContent(LibraryGridMetrics())
                    }
                }

                item(
                    key = "filter-summary",
                    contentType = LibraryScreenContentTypes.FilterSummary,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Column(
                        // A series' stack rises into the gap under the chips; this keeps the row's gap clear of it.
                        modifier = Modifier.padding(bottom = LibrarySeriesStackRaise),
                        verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
                    ) {
                        LibraryFilterRow(
                            heading = catalogKind?.let { kind ->
                                libraryGridHeading(
                                    kind = kind,
                                    searchActive = searchQuery.isNotBlank(),
                                    filtersActive = !filterState.isEmpty,
                                    shownCount = shownItemCount().takeIf { catalogCountKnown },
                                    totalCount = totalItemCount.takeIf { catalogCountKnown },
                                )
                            },
                            filterPanels = filterPanels,
                            filterState = filterState,
                            filterFacets = shownFilterFacets,
                            libraryTitlesById = libraryTitlesById,
                            sortMode = sortMode,
                            // What was picked, not what the width made of it: on a phone Small and Medium
                            // can lay out alike, and the menu still shows the pick.
                            gridCellSizeDp = gridCellSizeDp,
                            fadeReadItems = fadeReadItems,
                            showSortControl = showSortControl,
                            availableSortModes = availableSortModes,
                            lockedSortLabel = lockedSortLabel,
                            onOpenFilterPanel = { panel ->
                                openFilterPanel = panel
                                when {
                                    !dockFilters -> isFilterSheetOpen = true
                                    // The Filters chip shows and hides the docked panel; a value's chip opens its section.
                                    panel == null && filterDock.open -> filterDock.hide()
                                    else -> filterDock.show()
                                }
                            },
                            filtersOpen = filtersDocked,
                            onFilterStateChanged = onFilterStateChanged,
                            onSortModeChanged = onSortModeChanged,
                            onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                            onFadeReadItemsChanged = onFadeReadItemsChanged,
                            onCoverAsPrintedChanged = onCoverAsPrintedChanged,
                        )
                        if (searchTagSuggestions.isNotEmpty() && onFilterByTag != null) {
                            LibrarySearchTagStrip(suggestions = searchTagSuggestions, onFilterByTag = onFilterByTag)
                        }
                    }
                }

                if (gridIsEmpty) {
                    item(
                        key = "empty-state",
                        contentType = if (showGridLoading) {
                            LibraryScreenContentTypes.LoadingState
                        } else {
                            LibraryScreenContentTypes.EmptyState
                        },
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        if (showGridLoading) {
                            LoadingLibraryState(
                                gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp,
                            )
                        } else {
                            LibraryEmptyState(
                                reason = libraryEmptyReason(
                                    searchActive = searchQuery.isNotBlank(),
                                    filtersActive = !filterState.isEmpty,
                                    sourceUnreachable = sourceUnreachable,
                                    sourceNotConnected = sourceNotConnected,
                                ),
                                selectedSource = selectedSource,
                                onClearFilters = onClearFilters,
                                onImportFile = onImportLocalFile,
                                onConnectServer = onConnectServer,
                                onRetry = onRetrySource,
                            )
                        }
                    }
                } else if (isPaged) {
                    items(
                        count = pagedItems!!.itemCount,
                        key = pagedItems.itemKey { it.id },
                        contentType = pagedItems.itemContentType { LibraryScreenContentTypes.GridItem },
                    ) { index ->
                        // Placeholders are on for rail sorts (for the jump rail); a not-yet-loaded slot
                        // renders a sized stand-in so the row keeps its height instead of collapsing.
                        pagedItems[index]?.let { item -> gridCard(item) } ?: LibraryGridPlaceholderCard()
                    }
                } else if (searchQuery.isNotBlank()) {
                    // Searching: each result is a row that says why it matched.
                    items(
                        items = visibleItems,
                        key = { it.id },
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = { LibraryScreenContentTypes.SearchResult },
                    ) { item ->
                        LibrarySearchResultRow(
                            item = item,
                            title = itemPrimaryText(item),
                            query = searchQuery,
                            progress = effectiveProgressByItemId[item.id],
                            actionContext = itemActionContext,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onClick = { onItemSelected(item) },
                        )
                    }
                } else {
                    items(
                        items = visibleItems,
                        key = { it.id },
                        contentType = { LibraryScreenContentTypes.GridItem },
                    ) { item ->
                        gridCard(item)
                    }
                }
            }
            val scrolled by remember(gridState) {
                derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
            }
            LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }
            val showBackToTop by remember(
                gridState,
                leadingContentItemCount,
                gridPresentation.selectedGridSize.columnCount,
            ) {
                derivedStateOf {
                    shouldShowLibraryBackToTop(
                        firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                        leadingContentItemCount = leadingContentItemCount,
                        columnCount = gridPresentation.selectedGridSize.columnCount,
                    )
                }
            }
            if (alphabetRailVisible) {
                AlphabetNavigationRail(
                    sections = sortLetters,
                    activeSection = activeSortLetter,
                    onSectionSelected = { section ->
                        alphabetSectionStarts[section]?.let { itemIndex ->
                            coroutineScope.launch {
                                gridState.scrollToItem(
                                    leadingContentItemCount + itemIndex,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp, top = contentTopInset + AppLayout.contentTop, bottom = 8.dp),
                )
            }
            // Plain, not the Row's: the button sits in the grid's box beside the docked filters.
            androidx.compose.animation.AnimatedVisibility(
                visible = showBackToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = if (alphabetRailVisible) {
                            libraryGridNavigationEndPaddingDp + 12.dp
                        } else {
                            gutter
                        },
                        bottom = 24.dp,
                    ),
            ) {
                LibraryBackToTopButton(
                    onClick = {
                        coroutineScope.launch {
                            if (gridState.firstVisibleItemIndex <= 80) {
                                gridState.animateScrollToItem(0)
                            } else {
                                gridState.scrollToItem(0)
                            }
                        }
                    },
                )
            }
        }

        if (filtersDocked) {
            LibraryFilterDockedPanel(
                filterPanels = filterPanels,
                filterState = filterState,
                filterFacets = shownFilterFacets,
                libraryTitlesById = libraryTitlesById,
                shownCount = shownItemCount(),
                totalCount = totalItemCount,
                initialPanel = openFilterPanel,
                openRequest = filterDock.openRequest,
                onFilterStateChanged = onFilterStateChanged,
                onClearFilters = onClearFilters,
                onHide = filterDock::hide,
                onSaveAsList = onSaveFiltersAsList?.takeIf { !filterState.isEmpty }?.let { save -> { save(filterState) } },
                listMode = filterListMode,
                seriesCovers = filterSeriesCovers,
                modifier = Modifier
                    .padding(top = contentTopInset + AppLayout.contentTop, end = gutter, bottom = AppLayout.contentBottom)
                    .width(AppLayout.sheetWidth)
                    .fillMaxHeight(),
            )
        }
    }

    if (isFilterSheetOpen && !dockFilters) {
        LibraryFilterSheet(
            filterPanels = filterPanels,
            filterState = filterState,
            filterFacets = shownFilterFacets,
            libraryTitlesById = libraryTitlesById,
            shownCount = shownItemCount(),
            totalCount = totalItemCount,
            initialPanel = openFilterPanel,
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onDismiss = { isFilterSheetOpen = false },
            onSaveAsList = onSaveFiltersAsList?.takeIf { !filterState.isEmpty }?.let { save -> { save(filterState) } },
            listMode = filterListMode,
            seriesCovers = filterSeriesCovers,
        )
    }
}

private fun librarySortModesAvailableFor(
    items: List<ReadableItem>,
    selectedSortMode: LibrarySortMode,
    pageCountByItemId: Map<String, Int>,
): List<LibrarySortMode> {
    val hasIssueCounts = items.any { item -> item.issueCount != null }
    val hasPageCounts = items.any { item -> pageCountByItemId.pageCountOf(item) != null }
    return LibrarySortMode.entries.filter { mode ->
        when (mode) {
            LibrarySortMode.ISSUE_COUNT -> hasIssueCounts || selectedSortMode == mode
            LibrarySortMode.PAGES -> hasPageCounts || selectedSortMode == mode
            else -> true
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object LibraryGridScrollPrefetchStrategy : LazyGridPrefetchStrategy {
    override fun LazyGridPrefetchScope.onScroll(
        delta: Float,
        layoutInfo: LazyGridLayoutInfo,
    ) = Unit

    override fun LazyGridPrefetchScope.onVisibleItemsUpdated(
        layoutInfo: LazyGridLayoutInfo,
    ) = Unit

    override fun NestedPrefetchScope.onNestedPrefetch(
        firstVisibleItemIndex: Int,
    ) = Unit
}
