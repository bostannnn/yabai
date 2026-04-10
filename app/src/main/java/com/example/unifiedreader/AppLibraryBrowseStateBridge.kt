package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryBrowseState

internal fun AppUiState.toLibraryBrowseState(): LibraryBrowseState = LibraryBrowseState(
    filtersBySource = libraryFiltersBySource,
    searchQueriesBySource = librarySearchQueriesBySource,
    sortsBySource = librarySortsBySource,
)

internal fun AppUiState.withLibraryBrowseState(
    browseState: LibraryBrowseState,
): AppUiState = copy(
    libraryFiltersBySource = browseState.filtersBySource,
    librarySearchQueriesBySource = browseState.searchQueriesBySource,
    librarySortsBySource = browseState.sortsBySource,
)
