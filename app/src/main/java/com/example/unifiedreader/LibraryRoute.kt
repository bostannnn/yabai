package com.example.unifiedreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesZone
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

@Composable
internal fun LibraryRoute(
    uiState: AppUiState,
    viewMode: LibraryViewMode,
    browseContextKey: String,
    initialScrollPosition: LibraryScrollPosition,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit,
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
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onViewModeChanged: (LibraryViewMode) -> Unit,
    showSortControl: Boolean,
    modifier: Modifier = Modifier,
) {
    val itemActionContext = LibraryItemActionContext(
        progressByItemId = uiState.displayProgressByItemId,
        locallyAvailableItemIds = uiState.locallyAvailableRemoteItemIds,
        downloadableItemIds = uiState.remoteDownloadActionAvailability.downloadableItemIds,
        deletableDownloadedItemIds = uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
        downloadingItemIds = uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
        seriesReadingDirectionFor = seriesReadingDirectionFor,
        onSeriesDirectionSelected = onSeriesDirectionSelected,
        seriesReaderModeFor = seriesReaderModeFor,
        onSeriesReaderModeSelected = onSeriesReaderModeSelected,
        onDownloadItem = onDownloadRemoteItem,
        onDeleteDownloadedItem = onDeleteRemoteLocalFile,
        onSetReadStatus = onSetReadStatus,
    )
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LibraryScreen(
            selectedSource = uiState.selectedSource,
            browseContextKey = browseContextKey,
            items = uiState.visibleItems,
            viewMode = viewMode,
            progressByItemId = uiState.displayProgressByItemId,
            locallyAvailableItemIds = uiState.locallyAvailableRemoteItemIds,
            downloadableItemIds = uiState.remoteDownloadActionAvailability.downloadableItemIds,
            deletableDownloadedItemIds = uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
            downloadingItemIds = uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
            isLoadingSource = !uiState.isSelectedSourceHydrated,
            filterState = uiState.selectedLibraryFilters,
            filterPanels = uiState.selectedLibraryFilterPanels,
            filterFacets = uiState.selectedLibraryFilterFacets,
            libraryTitlesById = uiState.selectedSourceLibraryTitlesById,
            searchQuery = uiState.selectedLibrarySearchQuery,
            sortMode = uiState.selectedLibrarySortMode,
            showSortControl = showSortControl,
            coverImageUrlProvider = coverImageUrlProvider,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            onCoverVisible = onCoverVisible,
            topContent = if (uiState.shouldReserveLibraryShelvesSlot) {
                @Composable {
                    if (uiState.isSelectedSourceHydrated && uiState.shouldShowLibraryShelves) {
                        LibraryShelvesZone(
                            state = uiState.libraryShelvesState,
                            itemActionContext = itemActionContext,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onItemSelected = onItemSelected,
                            onDeleteImportedItem = onDeleteImportedItem,
                        )
                    }
                }
            } else {
                null
            },
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onSortModeChanged = onSortModeChanged,
            onViewModeChanged = onViewModeChanged,
            initialScrollPosition = initialScrollPosition,
            onScrollPositionChanged = onScrollPositionChanged,
            onItemSelected = onItemSelected,
            seriesReadingDirectionFor = seriesReadingDirectionFor,
            onSeriesDirectionSelected = onSeriesDirectionSelected,
            seriesReaderModeFor = seriesReaderModeFor,
            onSeriesReaderModeSelected = onSeriesReaderModeSelected,
            onDeleteImportedItem = onDeleteImportedItem,
            onDownloadItem = onDownloadRemoteItem,
            onDeleteDownloadedItem = onDeleteRemoteLocalFile,
            onSetReadStatus = onSetReadStatus,
        )
    }
}
