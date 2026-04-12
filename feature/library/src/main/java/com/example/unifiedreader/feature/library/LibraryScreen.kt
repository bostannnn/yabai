package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.library.menu.buildReadStatusMenuActionState
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import kotlinx.coroutines.flow.distinctUntilChanged

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
    filterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    libraryTitlesById: Map<String, String> = emptyMap(),
    searchQuery: String = "",
    sortMode: LibrarySortMode = defaultLibrarySortMode,
    showSortControl: Boolean = true,
    coverImageUrlProvider: (ReadableItem) -> String? = { it.coverUrl },
    coverPrefetchKeyProvider: (ReadableItem) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem) -> Unit = {},
    onFilterStateChanged: (LibraryFilterState) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSortModeChanged: (LibrarySortMode) -> Unit = {},
    onViewModeChanged: (LibraryViewMode) -> Unit = {},
    initialScrollPosition: LibraryScrollPosition = LibraryScrollPosition(),
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit = {},
    onItemSelected: (ReadableItem) -> Unit,
    seriesReadingDirectionFor: (ReadableItem) -> ComicReadingDirection? = { null },
    onSeriesDirectionSelected: (ReadableItem, ComicReadingDirection) -> Unit = { _, _ -> },
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
    onDownloadItem: (ReadableItem) -> Unit = {},
    onDeleteDownloadedItem: (ReadableItem) -> Unit = {},
    onSetReadStatus: (ReadableItem, Boolean) -> Unit = { _, _ -> },
) {
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }
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
        )
    }

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = viewMode.gridCells(maxWidth = maxWidth, maxHeight = maxHeight),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = if (alphabetRailVisible) 56.dp else 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFilterSummary(
                    selectedSource = selectedSource,
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                    sortMode = sortMode,
                    viewMode = viewMode,
                    showSortControl = showSortControl,
                    onOpenFilterPanel = { openFilterPanel = it },
                    onClearFilters = onClearFilters,
                    onSortModeChanged = {
                        selectedSortLetter = null
                        onSortModeChanged(it)
                    },
                    onViewModeChanged = onViewModeChanged,
                )
            }

            if (visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (isLoadingSource) {
                        LoadingLibraryState(selectedSource = selectedSource)
                    } else {
                        EmptyLibraryState(
                            selectedSource = selectedSource,
                            filtersActive = !filterState.isEmpty,
                            searchActive = searchQuery.isNotBlank(),
                        )
                    }
                }
            } else {
                items(visibleItems, key = { it.id }) { item ->
                    when (viewMode) {
                        LibraryViewMode.Detailed -> DetailedItemCard(
                            item = item,
                            progress = progressByItemId[item.id],
                            isDownloaded = item.isDownloaded(locallyAvailableItemIds),
                            isDownloading = item.id in downloadingItemIds,
                            seriesReadingDirection = seriesReadingDirectionFor(item),
                            coverImageUrlProvider = coverImageUrlProvider,
                            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                            onCoverVisible = onCoverVisible,
                            onClick = { onItemSelected(item) },
                            onSeriesReadingDirectionSelected = { direction ->
                                onSeriesDirectionSelected(item, direction)
                            },
                            readStatusActionState = buildReadStatusMenuActionState(
                                item = item,
                                progress = progressByItemId[item.id],
                                onSetReadStatus = onSetReadStatus,
                            ),
                            onDeleteRequested = itemDeleteRequest(item) { deleteCandidate = it },
                            onDownloadRequested = itemDownloadRequest(
                                item = item,
                                downloadableItemIds = downloadableItemIds,
                                onDownloadItem = onDownloadItem,
                            ),
                            onDeleteDownloadedRequested = itemDownloadedDeleteRequest(
                                item = item,
                                deletableDownloadedItemIds = deletableDownloadedItemIds,
                                onDeleteDownloadedItem = onDeleteDownloadedItem,
                            ),
                        )
                        LibraryViewMode.Grid -> GridCoverCard(
                            item = item,
                            progress = progressByItemId[item.id],
                            isDownloaded = item.isDownloaded(locallyAvailableItemIds),
                            isDownloading = item.id in downloadingItemIds,
                            seriesReadingDirection = seriesReadingDirectionFor(item),
                            coverImageUrlProvider = coverImageUrlProvider,
                            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                            onCoverVisible = onCoverVisible,
                            onClick = { onItemSelected(item) },
                            onSeriesReadingDirectionSelected = { direction ->
                                onSeriesDirectionSelected(item, direction)
                            },
                            readStatusActionState = buildReadStatusMenuActionState(
                                item = item,
                                progress = progressByItemId[item.id],
                                onSetReadStatus = onSetReadStatus,
                            ),
                            onDeleteRequested = itemDeleteRequest(item) { deleteCandidate = it },
                            onDownloadRequested = itemDownloadRequest(
                                item = item,
                                downloadableItemIds = downloadableItemIds,
                                onDownloadItem = onDownloadItem,
                            ),
                            onDeleteDownloadedRequested = itemDownloadedDeleteRequest(
                                item = item,
                                deletableDownloadedItemIds = deletableDownloadedItemIds,
                                onDeleteDownloadedItem = onDeleteDownloadedItem,
                            ),
                        )
                    }
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
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(text = "Remove local file?") },
            text = {
                Text(
                    text = "This removes ${item.libraryDisplayLabel()} from your library and clears local reading state.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteImportedItem(item)
                        deleteCandidate = null
                    },
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(text = "Cancel")
                }
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
