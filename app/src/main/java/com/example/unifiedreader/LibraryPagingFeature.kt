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

    fun isPageable(state: AppUiState): Boolean {
        if (!ENABLED) return false
        if (state.selectedSource != SourceType.KAVITA) return false
        if (state.isSeriesBrowseActive || state.isKavitaScopeActive || state.isKavitaVisualSectionActive) return false
        if (state.selectedLibrarySearchQuery.isNotBlank()) return false
        val filters = state.selectedLibraryFilters
        return when (state.selectedLibrarySortMode) {
            // Rail-less sorts page unfiltered or with content filters (buildFilteredGridRootsQuery); the
            // runtime filter dimensions (reading status, file location) aren't in the tables → in-memory.
            LibrarySortMode.LAST_READ,
            LibrarySortMode.DATE_ADDED -> filters.isPageableFilter()

            // The alphabet-rail sorts page over the precomputed sort keys with an SQL-computed rail, but
            // only unfiltered: the rail offsets are counted over the full source set, and filtered rail
            // paging isn't built, so a filtered rail view stays on the in-memory path.
            LibrarySortMode.TITLE,
            LibrarySortMode.AUTHOR,
            LibrarySortMode.SERIES -> filters.isEmpty

            LibrarySortMode.ISSUE_COUNT -> false
        }
    }

    // Content filters live in library_root_facets and page via buildFilteredGridRootsQuery; the
    // runtime dimensions (reading status, file location) aren't in the tables, so those stay in-memory.
    private fun LibraryFilterState.isPageableFilter(): Boolean =
        readingStatuses.isEmpty() && fileLocations.isEmpty()
}
