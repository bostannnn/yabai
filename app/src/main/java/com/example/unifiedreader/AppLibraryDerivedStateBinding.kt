package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryDerivedState
import com.example.unifiedreader.feature.library.LibraryDerivedStateInput

internal fun AppUiState.toLibraryDerivedStateInput(): LibraryDerivedStateInput = LibraryDerivedStateInput(
    selectedSource = selectedSource,
    libraries = libraries,
    items = items,
    selectedLibraryFilters = selectedLibraryFilters,
    selectedLibrarySearchQuery = selectedLibrarySearchQuery,
    selectedLibrarySortMode = selectedLibrarySortMode,
    progressByItemId = progressByItemId,
    downloadedRemoteItemIds = downloadedRemoteItemIds,
    locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
    downloadingRemoteItemIds = downloadingRemoteItemIds,
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
)

internal fun AppUiState.withLibraryDerivedState(
    derivedState: LibraryDerivedState,
): AppUiState = copy(
    sourceItems = derivedState.sourceItems,
    selectedLibraryFilterFacets = derivedState.selectedLibraryFilterFacets,
    selectedSourceLibraryTitlesById = derivedState.selectedSourceLibraryTitlesById,
    displayProgressByItemId = derivedState.displayProgressByItemId,
    remoteDownloadActionAvailability = derivedState.remoteDownloadActionAvailability,
    visibleItems = derivedState.visibleItems,
    libraryBrowseContextKey = derivedState.libraryBrowseContextKey,
)
