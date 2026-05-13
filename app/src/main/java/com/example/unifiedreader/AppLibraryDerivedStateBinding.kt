package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryDerivedState
import com.example.unifiedreader.feature.library.LibraryDerivedStateInput
import com.example.unifiedreader.feature.library.LibraryOrderedItemScope

internal fun AppUiState.toLibraryDerivedStateInput(): LibraryDerivedStateInput = LibraryDerivedStateInput(
    selectedSource = selectedSource,
    libraries = libraries,
    items = items,
    selectedLibraryFilters = selectedLibraryFilters,
    selectedLibrarySearchQuery = selectedLibrarySearchQuery,
    selectedLibrarySortMode = selectedLibrarySortMode,
    progressByItemId = progressByItemId,
    ratingByItemId = journalSnapshot.entryByItemId.mapNotNull { (itemId, entry) ->
        entry.rating?.let { rating -> itemId to rating }
    }.toMap(),
    downloadedRemoteItemIds = downloadedRemoteItemIds,
    locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
    downloadingRemoteItemIds = downloadingRemoteItemIds,
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
    orderedItemScope = selectedKavitaReadingListItemIds
        .takeIf { isKavitaReadingListActive }
        ?.let { itemIds ->
            LibraryOrderedItemScope(
                key = kavitaReadingLists.selectedReadingListId.orEmpty(),
                itemIds = itemIds,
            )
        },
)

internal fun AppUiState.withLibraryDerivedState(
    derivedState: LibraryDerivedState,
): AppUiState = copy(
    sourceItems = derivedState.sourceItems,
    selectedLibraryFilterFacets = derivedState.selectedLibraryFilterFacets,
    selectedSourceLibraryTitlesById = derivedState.selectedSourceLibraryTitlesById,
    displayProgressByItemId = derivedState.displayProgressByItemId,
    displayRatingByItemId = derivedState.displayRatingByItemId,
    remoteDownloadActionAvailability = derivedState.remoteDownloadActionAvailability,
    visibleItems = derivedState.visibleItems,
    libraryBrowseContextKey = derivedState.libraryBrowseContextKey,
)
