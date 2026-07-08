package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType

/**
 * A/B flag for the DB-paged library grid (Stage B). While OFF (the default), the app behaves exactly
 * as before — the paged path is never engaged. Flip [ENABLED] and rebuild to measure the paged grid
 * against the in-memory path on device.
 *
 * Paging is only engaged for the plain Kavita root view in a column sort; every other view (series
 * browse, Kavita scopes, active search/filters, and LAST_READ until Stage D) still renders from the
 * in-memory [AppUiState.visibleItems] path.
 */
internal object LibraryPagingFeature {
    const val ENABLED = false

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
