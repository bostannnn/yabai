package com.example.unifiedreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchScope
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.theme.AppMotion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    fadeReadItems: Boolean = false,
    locallyAvailableItemIds: Set<String> = emptySet(),
    downloadableItemIds: Set<String> = emptySet(),
    deletableDownloadedItemIds: Set<String> = emptySet(),
    downloadingItemIds: Set<String> = emptySet(),
    isLoadingSource: Boolean = false,
    filterState: LibraryFilterState = LibraryFilterState(),
    filterPanels: List<LibraryFilterPanel> = emptyList(),
    filterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    libraryTitlesById: Map<String, String> = emptyMap(),
    searchQuery: String = "",
    sortMode: LibrarySortMode = defaultLibrarySortMode,
    gridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    showSortControl: Boolean = true,
    preferIssueLabels: Boolean = false,
    includeSeriesInIssueLabels: Boolean = false,
    lockedSortLabel: String? = null,
    readingListScopeState: LibraryReadingListScopeState = LibraryReadingListScopeState(),
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    // Drawn above the filter summary as a full-width grid row; receives the grid's column setup so it
    // can size its covers to match the grid's.
    // The large title over everything else; while it shows, the top bar draws no name of its own.
    header: LibraryHeader? = null,
    onHeaderOnScreenChanged: (Boolean) -> Unit = {},
    topContent: (@Composable (LibraryGridMetrics) -> Unit)? = null,
    // Extra top padding so the first row clears a floating top bar the content scrolls under.
    contentTopInset: Dp = 0.dp,
    onFilterStateChanged: (LibraryFilterState) -> Unit = {},
    onClearFilters: () -> Unit = {},
    // The empty library's actions; null leaves an action out.
    sourceUnreachable: Boolean = false,
    onImportLocalFile: (() -> Unit)? = null,
    onConnectServer: (() -> Unit)? = null,
    onRetrySource: (() -> Unit)? = null,
    onReadingListSelected: (String?) -> Unit = {},
    onSortModeChanged: (LibrarySortMode) -> Unit = {},
    onViewModeChanged: (LibraryViewMode) -> Unit = {},
    onGridCellSizeDpChanged: (Int) -> Unit = {},
    onFadeReadItemsChanged: (Boolean) -> Unit = {},
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
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
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
) {
    val pagedItems = pagedGridItems?.collectAsLazyPagingItems()
    val isPaged = pagedItems != null
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }
    var gridExpandedMenuItemId by remember { mutableStateOf<String?>(null) }
    var openFilterPanel by rememberSaveable { mutableStateOf<LibraryFilterPanel?>(null) }
    var isFilterSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isReadingListDialogOpen by rememberSaveable { mutableStateOf(false) }
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
        )
    } else {
        LibraryWindowBadges()
    }
    val effectiveProgressByItemId = windowBadges.displayProgressByItemId + progressByItemId
    val effectiveRatingByItemId = windowBadges.displayRatingByItemId + ratingByItemId
    val effectiveLocallyAvailableItemIds = locallyAvailableItemIds + windowBadges.locallyAvailableItemIds
    val effectiveDownloadableItemIds =
        downloadableItemIds + windowBadges.downloadActionAvailability.downloadableItemIds
    val effectiveDeletableDownloadedItemIds =
        deletableDownloadedItemIds + windowBadges.downloadActionAvailability.deletableDownloadedItemIds
    val effectiveDownloadingItemIds =
        downloadingItemIds + windowBadges.downloadActionAvailability.activeDownloadingItemIds
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
    )
    val availableSortModes = remember(renderedItems, sortMode) {
        librarySortModesAvailableFor(items = renderedItems, selectedSortMode = sortMode)
    }
    val itemAlphabetSections = remember(items, sortMode) {
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
    val inMemorySortLetters = remember(itemAlphabetSections) {
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
    val selectableDownloadItems = remember(renderedItems, downloadableItemIds, downloadingItemIds) {
        renderedItems.filter { item -> item.id in downloadableItemIds && item.id !in downloadingItemIds }
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
    // The paged grid reloads from scratch on every sort or filter change, so it has nothing to
    // move and keeps a grid per browse context.
    val gridStateKey = if (isPaged || gridIdentityKey == null) browseContextKey else gridIdentityKey
    val gridState = remember(gridStateKey) {
        LazyGridState(
            firstVisibleItemIndex = initialScrollPosition.index,
            firstVisibleItemScrollOffset = initialScrollPosition.offset,
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
            if (initialScrollPosition.index == 0) {
                gridState.scrollToItem(0, initialScrollPosition.offset)
            }
        }
    }
    // Held, and not reported, until the grid can reach it (see LibraryScrollRestoreStep); a drag or
    // fling hands the position back to the user.
    var pendingScrollRestore by remember(browseContextKey) {
        mutableStateOf(initialScrollPosition.takeIf { position -> position.index > 0 })
    }
    val dateAddedTopItemId = if (showSortControl && sortMode == LibrarySortMode.DATE_ADDED) {
        renderedItems.firstOrNull()?.id
    } else {
        null
    }
    val leadingContentItemCount = 1 +
        (if (header != null) 1 else 0) +
        (if (topContent != null) 1 else 0)
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridEndPadding = if (alphabetRailVisible) {
            libraryGridNavigationEndPaddingDp
        } else {
            libraryGridEndPaddingDp
        }
        val gridPresentation = remember(maxWidth, gridEndPadding, gridCellSizeDp) {
            libraryGridPresentation(
                maxWidth = maxWidth,
                endPadding = gridEndPadding,
                requestedGridCellSizeDp = gridCellSizeDp,
            )
        }
        val gridCoverImageVariant = libraryGridCoverImageVariant(
            gridPresentation.selectedGridSize.cellSizeDp,
        )
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
            val hasMenuContent = itemActionContext.hasMenuContent(item)
            GridCoverCard(
                item = item,
                progress = effectiveProgressByItemId[item.id],
                rating = effectiveRatingByItemId[item.id],
                isDownloaded = itemActionContext.isDownloaded(item),
                fadeRead = fadeReadItems,
                imageVariant = gridCoverImageVariant,
                coverImageUrlProvider = coverImageUrlProvider,
                seriesIssueCount = item.issueCount,
                title = when {
                    preferIssueLabels && includeSeriesInIssueLabels -> itemMixedIssueBrowsePrimaryText(item)
                    preferIssueLabels -> itemSeriesBrowsePrimaryText(item)
                    else -> itemPrimaryText(item)
                },
                isSelectionMode = isDownloadSelectionMode,
                isSelected = item.id in selectedDownloadItemIdSet,
                onClick = { onItemSelected(item) },
                onSelectionToggle = { toggleDownloadSelection(item) },
                onSelectionStart = if (item.id in selectableDownloadItemIds) {
                    { startDownloadSelection(item) }
                } else {
                    null
                },
                menuContent = if (hasMenuContent) {
                    {
                        LibraryAnchoredItemMenu(
                            item = item,
                            actionContext = itemActionContext,
                            expanded = gridExpandedMenuItemId == item.id,
                            onExpandedChange = { expanded ->
                                gridExpandedMenuItemId = if (expanded) item.id else null
                            },
                            onDeleteImportedRequested = { deleteCandidate = it },
                            buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = AppMotion.spatial(IntOffset.VisibilityThreshold),
                    fadeOutSpec = null,
                ),
            )
        }

        LazyVerticalGrid(
            columns = LibraryViewMode.Grid.gridCells(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp,
            ),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = libraryGridStartPaddingDp,
                // Under a header the bar is see-through, so the title can sit right under its icons.
                top = contentTopInset + if (header != null) 4.dp else 16.dp,
                end = gridEndPadding,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
            if (topContent != null) {
                item(
                    key = "top-content",
                    contentType = LibraryScreenContentTypes.TopContent,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    topContent(LibraryGridMetrics(cellSizeDp = gridPresentation.selectedGridSize.cellSizeDp))
                }
            }

            item(
                key = "filter-summary",
                contentType = LibraryScreenContentTypes.FilterSummary,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                LibraryFilterRow(
                    filterPanels = filterPanels,
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                    sortMode = sortMode,
                    gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp,
                    gridSizeOptions = gridPresentation.availableGridSizes,
                    fadeReadItems = fadeReadItems,
                    showSortControl = showSortControl,
                    availableSortModes = availableSortModes,
                    lockedSortLabel = lockedSortLabel,
                    readingListScopeState = readingListScopeState,
                    onOpenReadingListScope = { isReadingListDialogOpen = true },
                    onOpenFilterPanel = { panel ->
                        openFilterPanel = panel
                        isFilterSheetOpen = true
                    },
                    onFilterStateChanged = onFilterStateChanged,
                    onSortModeChanged = onSortModeChanged,
                    onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                    onFadeReadItemsChanged = onFadeReadItemsChanged,
                )
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
        // The header counts as gone once half of it has scrolled under the bar. Item offsets start
        // after the top padding, so at rest the header sits at 0.
        val headerOnScreen by remember(gridState, header != null) {
            derivedStateOf {
                header != null && gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> info.key == LibraryHeaderItemKey }
                    ?.let { info -> info.offset.y + info.size.height / 2 > 0 } == true
            }
        }
        LaunchedEffect(headerOnScreen) { onHeaderOnScreenChanged(headerOnScreen) }
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
                    .padding(end = 8.dp, top = contentTopInset + 8.dp, bottom = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = if (alphabetRailVisible) {
                        libraryGridNavigationEndPaddingDp + 12.dp
                    } else {
                        libraryGridEndPaddingDp
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

    deleteCandidate?.let { item ->
        LibraryDeleteImportedItemDialog(
            item = item,
            onDismiss = { deleteCandidate = null },
            onConfirmDelete = {
                onDeleteImportedItem(it)
                deleteCandidate = null
            },
        )
    }

    if (isFilterSheetOpen) {
        LibraryFilterSheet(
            filterPanels = filterPanels,
            filterState = filterState,
            filterFacets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            initialPanel = openFilterPanel,
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onDismiss = { isFilterSheetOpen = false },
        )
    }
    if (isReadingListDialogOpen) {
        LibraryReadingListScopeDialog(
            state = readingListScopeState,
            onReadingListSelected = onReadingListSelected,
            onDismiss = { isReadingListDialogOpen = false },
        )
    }
}

private fun librarySortModesAvailableFor(
    items: List<ReadableItem>,
    selectedSortMode: LibrarySortMode,
): List<LibrarySortMode> {
    val hasIssueCounts = items.any { item -> item.issueCount != null }
    return LibrarySortMode.entries.filter { mode ->
        mode != LibrarySortMode.ISSUE_COUNT || hasIssueCounts || selectedSortMode == LibrarySortMode.ISSUE_COUNT
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
