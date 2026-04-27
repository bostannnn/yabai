package com.example.unifiedreader.feature.library

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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    selectedSource: SourceType,
    browseContextKey: String,
    items: List<ReadableItem>,
    viewMode: LibraryViewMode,
    progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
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
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    topContent: (@Composable () -> Unit)? = null,
    onFilterStateChanged: (LibraryFilterState) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSortModeChanged: (LibrarySortMode) -> Unit = {},
    onViewModeChanged: (LibraryViewMode) -> Unit = {},
    onGridCellSizeDpChanged: (Int) -> Unit = {},
    initialScrollPosition: LibraryScrollPosition = LibraryScrollPosition(),
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit = {},
    onItemSelected: (ReadableItem) -> Unit,
    seriesReadingDirectionFor: (ReadableItem) -> ComicReadingDirection? = { null },
    onSeriesDirectionSelected: (ReadableItem, ComicReadingDirection) -> Unit = { _, _ -> },
    seriesReaderModeFor: (ReadableItem) -> ComicReaderMode? = { null },
    onSeriesReaderModeSelected: (ReadableItem, ComicReaderMode) -> Unit = { _, _ -> },
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
    onDownloadItem: (ReadableItem) -> Unit = {},
    onDeleteDownloadedItem: (ReadableItem) -> Unit = {},
    onSetReadStatus: (ReadableItem, Boolean) -> Unit = { _, _ -> },
) {
    val itemActionContext = LibraryItemActionContext(
        progressByItemId = progressByItemId,
        locallyAvailableItemIds = locallyAvailableItemIds,
        downloadableItemIds = downloadableItemIds,
        deletableDownloadedItemIds = deletableDownloadedItemIds,
        downloadingItemIds = downloadingItemIds,
        seriesReadingDirectionFor = seriesReadingDirectionFor,
        onSeriesDirectionSelected = onSeriesDirectionSelected,
        seriesReaderModeFor = seriesReaderModeFor,
        onSeriesReaderModeSelected = onSeriesReaderModeSelected,
        onDownloadItem = onDownloadItem,
        onDeleteDownloadedItem = onDeleteDownloadedItem,
        onSetReadStatus = onSetReadStatus,
    )
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }
    var gridExpandedMenuItemId by remember { mutableStateOf<String?>(null) }
    var openFilterPanel by rememberSaveable { mutableStateOf<LibraryFilterPanel?>(null) }
    var selectedSortLetter by rememberSaveable(sortMode) { mutableStateOf<String?>(null) }
    val alphabetRailVisible = showSortControl && sortMode.usesAlphabetRail && items.isNotEmpty()
    val sortLetters = remember(items, sortMode) {
        if (!sortMode.usesAlphabetRail) {
            emptyList()
        } else {
            items.mapNotNull { item -> item.sortFilterLetter(sortMode) }
                .distinct()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
    }
    val effectiveSortLetter = selectedSortLetter?.takeIf { showSortControl && it in sortLetters }
    val visibleItems = remember(items, sortMode, effectiveSortLetter) {
        effectiveSortLetter?.let { letter ->
            items.filter { item -> item.sortFilterLetter(sortMode) == letter }
        } ?: items
    }
    val gridState = remember(browseContextKey) {
        LazyGridState(
            firstVisibleItemIndex = initialScrollPosition.index,
            firstVisibleItemScrollOffset = initialScrollPosition.offset,
            prefetchStrategy = LibraryGridScrollPrefetchStrategy,
        )
    }
    val leadingContentItemCount = 1 + if (topContent != null) 1 else 0

    LaunchedEffect(selectedSortLetter, effectiveSortLetter) {
        if (selectedSortLetter != effectiveSortLetter) {
            selectedSortLetter = effectiveSortLetter
        }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridEndPadding = if (alphabetRailVisible) {
            libraryGridAlphabetRailEndPaddingDp
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
            items = visibleItems,
            leadingContentItemCount = leadingContentItemCount,
            variant = gridCoverImageVariant,
            maxTargetsWhileScrolling = gridPresentation.scrollingPrefetchItemLimit,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            onCoverVisible = onCoverVisible,
        )

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
                    showSortControl = showSortControl,
                    onOpenFilterPanel = { openFilterPanel = it },
                    onClearFilters = onClearFilters,
                    onSortModeChanged = {
                        selectedSortLetter = null
                        onSortModeChanged(it)
                    },
                    onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                )
            }

            if (visibleItems.isEmpty()) {
                item(
                    key = "empty-state",
                    contentType = if (isLoadingSource) {
                        LibraryScreenContentTypes.LoadingState
                    } else {
                        LibraryScreenContentTypes.EmptyState
                    },
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    if (isLoadingSource) {
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
            } else {
                items(
                    items = visibleItems,
                    key = { it.id },
                    contentType = { LibraryScreenContentTypes.GridItem },
                ) { item ->
                    val hasMenuContent = itemActionContext.hasMenuContent(item)
                    GridCoverCard(
                        item = item,
                        progress = progressByItemId[item.id],
                        isDownloaded = itemActionContext.isDownloaded(item),
                        imageVariant = gridCoverImageVariant,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onClick = { onItemSelected(item) },
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
            }
        }
        if (alphabetRailVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                AlphabetRail(
                    letters = sortLetters,
                    selectedLetter = effectiveSortLetter,
                    onLetterSelected = { letter ->
                        selectedSortLetter = if (effectiveSortLetter == letter) null else letter
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
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
