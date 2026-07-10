package com.example.unifiedreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchScope
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    selectedSource: SourceType,
    browseContextKey: String,
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
    topContent: (@Composable () -> Unit)? = null,
    onFilterStateChanged: (LibraryFilterState) -> Unit = {},
    onClearFilters: () -> Unit = {},
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
) {
    val pagedItems = pagedGridItems?.collectAsLazyPagingItems()
    val isPaged = pagedItems != null
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }
    var gridExpandedMenuItemId by remember { mutableStateOf<String?>(null) }
    var openFilterPanel by rememberSaveable { mutableStateOf<LibraryFilterPanel?>(null) }
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
    // badges only fill the keys the recompute doesn't have yet (i.e. everything at cold start). Re-runs
    // as the window grows / reorders (keyed on size + endpoints, which change on scroll and last-read
    // re-sort). Empty on every non-paged path, so `effective*` below equals the raw inputs there.
    val latestBadgeResolver by rememberUpdatedState(pagedWindowBadgeResolver)
    val windowSignature = renderedItems.size to
        (renderedItems.firstOrNull()?.id to renderedItems.lastOrNull()?.id)
    val windowBadges = if (isPaged && pagedWindowBadgeResolver != null) {
        produceState(initialValue = LibraryWindowBadges(), windowSignature) {
            value = latestBadgeResolver
                ?.takeIf { renderedItems.isNotEmpty() }
                ?.invoke(renderedItems)
                ?: LibraryWindowBadges()
        }.value
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
    val sortLetters = remember(itemAlphabetSections) {
        alphabetNavigationSections(itemAlphabetSections.filterNotNull())
    }
    val alphabetSectionStarts = remember(itemAlphabetSections) {
        alphabetSectionStarts(itemAlphabetSections)
    }
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
    val selectedDownloadItemIdSet = selectedDownloadItemIds
        .filter { itemId -> itemId in selectableDownloadItemIds }
        .toSet()
    val gridState = remember(browseContextKey) {
        LazyGridState(
            firstVisibleItemIndex = initialScrollPosition.index,
            firstVisibleItemScrollOffset = initialScrollPosition.offset,
            prefetchStrategy = LibraryGridScrollPrefetchStrategy,
        )
    }
    val dateAddedTopItemId = if (showSortControl && sortMode == LibrarySortMode.DATE_ADDED) {
        renderedItems.firstOrNull()?.id
    } else {
        null
    }
    val leadingContentItemCount = 1 +
        (if (topContent != null) 1 else 0)
    val activeSortLetter by remember(
        gridState,
        itemAlphabetSections,
        leadingContentItemCount,
    ) {
        derivedStateOf {
            activeAlphabetSection(
                sectionKeys = itemAlphabetSections,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                leadingContentItemCount = leadingContentItemCount,
            )
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
            )
        }
            .distinctUntilChanged()
            .collect(onScrollPositionChanged)
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
        val sanitizedSelection = selectedDownloadItemIds.filter { itemId -> itemId in selectableDownloadItemIds }
        if (sanitizedSelection != selectedDownloadItemIds) {
            onSelectedDownloadItemIdsChanged(sanitizedSelection)
        }
        if (selectableDownloadItemIds.isEmpty() && isDownloadSelectionMode) {
            onDownloadSelectionModeChanged(false)
        }
    }

    fun toggleDownloadSelection(item: ReadableItem) {
        if (item.id !in selectableDownloadItemIds) return
        onSelectedDownloadItemIdsChanged(
            selectedDownloadItemIdSet
                .toggle(item.id)
                .let { selectedIds -> renderedItems.map(ReadableItem::id).filter { it in selectedIds } },
        )
    }

    fun startDownloadSelection(item: ReadableItem) {
        if (item.id !in selectableDownloadItemIds) return
        onDownloadSelectionModeChanged(true)
        if (item.id !in selectedDownloadItemIdSet) {
            onSelectedDownloadItemIdsChanged(
                renderedItems
                    .map(ReadableItem::id)
                    .filter { itemId -> itemId == item.id || itemId in selectedDownloadItemIdSet },
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
            items = renderedItems,
            leadingContentItemCount = leadingContentItemCount,
            variant = gridCoverImageVariant,
            maxTargetsWhileScrolling = gridPresentation.scrollingPrefetchItemLimit,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            onCoverVisible = onCoverVisible,
        )

        val gridCard: @Composable (ReadableItem) -> Unit = { item ->
            val hasMenuContent = itemActionContext.hasMenuContent(item)
            GridCoverCard(
                item = item,
                progress = effectiveProgressByItemId[item.id],
                rating = effectiveRatingByItemId[item.id],
                isDownloaded = itemActionContext.isDownloaded(item),
                fadeRead = fadeReadItems,
                imageVariant = gridCoverImageVariant,
                coverImageUrlProvider = coverImageUrlProvider,
                issueCountBadge = item.issueCount.takeIf { count ->
                    showSortControl && sortMode == LibrarySortMode.ISSUE_COUNT && count != null && count > 0
                },
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
                top = 16.dp,
                end = gridEndPadding,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (topContent != null) {
                item(
                    key = "top-content",
                    contentType = LibraryScreenContentTypes.TopContent,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    topContent()
                }
            }

            item(
                key = "filter-summary",
                contentType = LibraryScreenContentTypes.FilterSummary,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                LibraryFilterSummary(
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
                    onOpenFilterPanel = { openFilterPanel = it },
                    onClearFilters = onClearFilters,
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
                        EmptyLibraryState(
                            selectedSource = selectedSource,
                            filtersActive = !filterState.isEmpty,
                            searchActive = searchQuery.isNotBlank(),
                        )
                    }
                }
            } else if (isPaged) {
                items(
                    count = pagedItems!!.itemCount,
                    key = pagedItems.itemKey { it.id },
                    contentType = pagedItems.itemContentType { LibraryScreenContentTypes.GridItem },
                ) { index ->
                    pagedItems[index]?.let { item -> gridCard(item) }
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
                    .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
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

    openFilterPanel?.let { panel ->
        LibraryFilterPanelDialog(
            panel = panel,
            filterState = filterState,
            filterFacets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            onFilterStateChanged = onFilterStateChanged,
            onDismiss = { openFilterPanel = null },
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
