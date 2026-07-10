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
    ratingByItemId = journalRatingByItemId(),
    downloadedRemoteItemIds = downloadedRemoteItemIds,
    locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
    downloadingRemoteItemIds = downloadingRemoteItemIds,
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
    selectedKavitaCollectionId = kavitaReadingLists.selectedCollectionId,
    selectedKavitaCollectionSeriesKeys = selectedKavitaCollectionSeriesKeys,
    // When the grid pages from Room AND there's no filter, skip the redundant in-memory sort for
    // `visibleItems` (M2). A filtered paged view still pages its grid from Room, but keeps `visibleItems`
    // as the filtered in-memory set (computed async) so its consumers stay correct.
    pagedGridActive = LibraryPagingFeature.isPageable(this) && selectedLibraryFilters.isEmpty,
    orderedItemScope = selectedKavitaReadingListItemIds
        .takeIf { isKavitaReadingListActive }
        ?.let { itemIds ->
            LibraryOrderedItemScope(
                key = kavitaReadingLists.selectedReadingListId.orEmpty(),
                itemIds = itemIds,
            )
        }
        ?: coldStartOrderedItemIdsBySource[selectedSource]
            ?.takeIf { orderedItemIds -> !isSelectedSourceHydrated && orderedItemIds.isNotEmpty() }
            ?.let { orderedItemIds ->
                LibraryOrderedItemScope(
                    key = "coldstart:${selectedSource.name}",
                    itemIds = orderedItemIds,
                )
            },
)

/** Per-item ratings drawn from the journal — the rating badge source for both the in-memory recompute
 *  and the paged-window badge resolver. */
internal fun AppUiState.journalRatingByItemId(): Map<String, Int> =
    journalSnapshot.entryByItemId.mapNotNull { (itemId, entry) ->
        entry.rating?.let { rating -> itemId to rating }
    }.toMap()

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
