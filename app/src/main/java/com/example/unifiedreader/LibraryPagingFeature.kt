package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType

/**
 * Flag for the DB-paged library grid. ON so the default Kavita root view pages its grid body from
 * SQL while reusing the in-memory-computed shelves on top; flip to false to force the pure in-memory
 * path.
 *
 * Engaged for the Kavita root view in a rail-less sort (LAST_READ, DATE_ADDED), including
 * **content-filtered** views (format/library/author/series/publisher/genre/tag/language) which page
 * via `library_root_facets`. The rail-bearing sorts (Title/Author/Series), search, series browse,
 * Kavita scopes, and reading-status / file-location filters (runtime state not in the tables) still
 * render from the in-memory [AppUiState.visibleItems] path.
 */
internal object LibraryPagingFeature {
    const val ENABLED = true

    fun isPageable(state: AppUiState): Boolean =
        ENABLED &&
            state.selectedSource == SourceType.KAVITA &&
            !state.isSeriesBrowseActive &&
            !state.isKavitaScopeActive &&
            !state.isKavitaVisualSectionActive &&
            state.selectedLibrarySearchQuery.isBlank() &&
            state.selectedLibraryFilters.isPageableFilter() &&
            state.selectedLibrarySortMode in PAGEABLE_SORTS

    // Content filters live in library_root_facets and page via buildFilteredGridRootsQuery; the
    // runtime dimensions (reading status, file location) aren't in the tables, so those stay in-memory.
    private fun LibraryFilterState.isPageableFilter(): Boolean =
        readingStatuses.isEmpty() && fileLocations.isEmpty()

    // Rail-less sorts only, so the alphabet-rail sorts keep the in-memory path (which renders it).
    private val PAGEABLE_SORTS = setOf(LibrarySortMode.LAST_READ, LibrarySortMode.DATE_ADDED)
}
