package com.example.unifiedreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Minimal, flag-gated grid that renders directly from a Room-backed [PagingData] over the derived
 * `library_grid_roots`, so only the visible window is resident instead of the whole catalog. Used
 * only for the plain Kavita root view in a pageable sort; the full in-memory [LibraryScreen] still
 * owns filters, search, shelves, series browse, and the alphabet rail. Reuses the shared grid card,
 * presentation sizing, and cover prefetcher rather than reinterpreting them.
 */
@Composable
fun LibraryPagedGrid(
    selectedSource: SourceType,
    browseContextKey: String,
    pagingData: Flow<PagingData<ReadableItem>>,
    gridCellSizeDp: Int,
    progressByItemId: Map<String, ItemProgressSummary>,
    ratingByItemId: Map<String, LibraryRatingBadgeState>,
    locallyAvailableItemIds: Set<String>,
    fadeReadItems: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onCoverViewportScrollingChanged: (Boolean) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
) {
    val pagedItems = pagingData.collectAsLazyPagingItems()
    val coroutineScope = rememberCoroutineScope()
    val gridState = remember(browseContextKey) { LazyGridState() }
    val loadedItems = pagedItems.itemSnapshotList.items
    val isInitialLoading = pagedItems.loadState.refresh is LoadState.Loading && pagedItems.itemCount == 0

    LaunchedEffect(browseContextKey, gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(onCoverViewportScrollingChanged)
    }
    DisposableEffect(browseContextKey) {
        onDispose { onCoverViewportScrollingChanged(false) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridPresentation = remember(maxWidth, gridCellSizeDp) {
            libraryGridPresentation(
                maxWidth = maxWidth,
                endPadding = libraryGridEndPaddingDp,
                requestedGridCellSizeDp = gridCellSizeDp,
            )
        }
        val gridCoverImageVariant = libraryGridCoverImageVariant(
            gridPresentation.selectedGridSize.cellSizeDp,
        )
        // Prefetch only the currently-loaded window's covers; paging owns which items are resident.
        VisibleRemoteCoverPrefetcher(
            browseContextKey = browseContextKey,
            gridState = gridState,
            items = loadedItems,
            leadingContentItemCount = 0,
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
                end = libraryGridEndPaddingDp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (pagedItems.itemCount == 0) {
                item(
                    key = "paged-empty-or-loading",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    if (isInitialLoading) {
                        LoadingLibraryState(gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp)
                    } else {
                        EmptyLibraryState(
                            selectedSource = selectedSource,
                            filtersActive = false,
                            searchActive = false,
                        )
                    }
                }
            } else {
                items(
                    count = pagedItems.itemCount,
                    key = pagedItems.itemKey(ReadableItem::id),
                    contentType = pagedItems.itemContentType { LibraryScreenContentTypes.GridItem },
                ) { index ->
                    val item = pagedItems[index] ?: return@items
                    GridCoverCard(
                        item = item,
                        progress = progressByItemId[item.id],
                        rating = ratingByItemId[item.id],
                        isDownloaded = item.id in locallyAvailableItemIds,
                        fadeRead = fadeReadItems,
                        imageVariant = gridCoverImageVariant,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onClick = { onItemSelected(item) },
                    )
                }
            }
        }

        val showBackToTop by remember(gridState, gridPresentation.selectedGridSize.columnCount) {
            derivedStateOf {
                shouldShowLibraryBackToTop(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    leadingContentItemCount = 0,
                    columnCount = gridPresentation.selectedGridSize.columnCount,
                )
            }
        }
        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = libraryGridEndPaddingDp, bottom = 24.dp),
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
}
