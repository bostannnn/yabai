package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType

/**
 * Flag for the DB-paged library grid. ON so the default Kavita root view pages its grid body from
 * SQL while reusing the in-memory-computed shelves on top; flip to false to force the pure in-memory
 * path.
 *
 * Engaged only for the plain Kavita root view in a rail-less sort (LAST_READ, DATE_ADDED). The
 * rail-bearing sorts (Title/Author/Series), active filters/search, series browse, and Kavita scopes
 * still render from the in-memory [AppUiState.visibleItems] path, so nothing loses the alphabet rail
 * or filter behaviour yet. Filters/rail/search in the paged view arrive in later milestones.
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
            state.selectedLibraryFilters.isEmpty &&
            state.selectedLibrarySortMode in PAGEABLE_SORTS

    // Rail-less sorts only, so the alphabet-rail sorts keep the in-memory path (which renders it).
    private val PAGEABLE_SORTS = setOf(LibrarySortMode.LAST_READ, LibrarySortMode.DATE_ADDED)
}
