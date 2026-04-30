package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryBrowseState

internal fun AppUiState.toLibraryBrowseState(): LibraryBrowseState = LibraryBrowseState(
    filtersBySource = libraryFiltersBySource,
    searchQueriesBySource = librarySearchQueriesBySource,
    sortsBySource = librarySortsBySource,
    filterPanelsBySource = libraryFilterPanelsBySource,
    gridCellSizeDp = libraryGridCellSizeDp,
    fadeReadItems = libraryFadeReadItems,
)

internal fun AppUiState.withLibraryBrowseState(
    browseState: LibraryBrowseState,
): AppUiState = copy(
    libraryFiltersBySource = browseState.filtersBySource,
    librarySearchQueriesBySource = browseState.searchQueriesBySource,
    librarySortsBySource = browseState.sortsBySource,
    libraryFilterPanelsBySource = browseState.filterPanelsBySource,
    libraryGridCellSizeDp = browseState.gridCellSizeDp,
    libraryFadeReadItems = browseState.fadeReadItems,
)
