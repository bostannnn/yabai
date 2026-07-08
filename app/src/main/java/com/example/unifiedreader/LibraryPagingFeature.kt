package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType

/**
 * A/B flag for the DB-paged library grid (Stage B). Currently ON so on-device measurement needs no
 * rebuild-with-a-different-signature; flip to false to compare against the pure in-memory path.
 *
 * Paging is only engaged for the plain Kavita root view in a column sort; every other view (series
 * browse, Kavita scopes, active search/filters, and the default LAST_READ sort until Stage D) still
 * renders from the in-memory [AppUiState.visibleItems] path. NOTE: while ON, switching the Kavita
 * grid to Title / Date-added shows the minimal paged scaffold (no shelves / filter summary /
 * alphabet rail yet) — those reach parity across Stages C–E.
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

    private val PAGEABLE_SORTS = setOf(LibrarySortMode.TITLE, LibrarySortMode.DATE_ADDED)
}
