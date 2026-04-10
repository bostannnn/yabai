package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType

data class LibraryBrowseState(
    val filtersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val searchQueriesBySource: Map<SourceType, String> = emptyMap(),
    val sortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
)

class LibraryBrowseStateCoordinator(
    private val preferencesStore: LibraryBrowsePreferencesStore,
) {
    fun restoreState(
        currentSearchQueriesBySource: Map<SourceType, String> = emptyMap(),
    ): LibraryBrowseState {
        val preferences = preferencesStore.preferencesFlow.value
        return LibraryBrowseState(
            filtersBySource = preferences.filtersBySource,
            searchQueriesBySource = currentSearchQueriesBySource,
            sortsBySource = preferences.sortsBySource,
        )
    }

    fun updateFilters(
        currentState: LibraryBrowseState,
        selectedSource: SourceType,
        filters: LibraryFilterState,
    ): LibraryBrowseState = currentState.copy(
        filtersBySource = currentState.filtersBySource.updatedFilters(
            sourceType = selectedSource,
            filters = filters,
        ),
    )

    fun clearFilters(
        currentState: LibraryBrowseState,
        selectedSource: SourceType,
    ): LibraryBrowseState = updateFilters(
        currentState = currentState,
        selectedSource = selectedSource,
        filters = LibraryFilterState(),
    )

    fun updateSearchQuery(
        currentState: LibraryBrowseState,
        selectedSource: SourceType,
        query: String,
    ): LibraryBrowseState = currentState.copy(
        searchQueriesBySource = currentState.searchQueriesBySource.updatedSearchQuery(
            sourceType = selectedSource,
            query = query,
        ),
    )

    fun updateSortMode(
        currentState: LibraryBrowseState,
        selectedSource: SourceType,
        sortMode: LibrarySortMode,
    ): LibraryBrowseState = currentState.copy(
        sortsBySource = currentState.sortsBySource.updatedSortMode(
            sourceType = selectedSource,
            sortMode = sortMode,
        ),
    )

    suspend fun persistFilters(
        sourceType: SourceType,
        filters: LibraryFilterState,
    ) {
        preferencesStore.setFilters(
            sourceType = sourceType,
            filters = filters,
        )
    }

    suspend fun persistSortMode(
        sourceType: SourceType,
        sortMode: LibrarySortMode,
    ) {
        preferencesStore.setSortMode(
            sourceType = sourceType,
            sortMode = sortMode,
        )
    }
}

internal fun Map<SourceType, LibraryFilterState>.updatedFilters(
    sourceType: SourceType,
    filters: LibraryFilterState,
): Map<SourceType, LibraryFilterState> = if (filters.isEmpty) {
    this - sourceType
} else {
    this + (sourceType to filters)
}

internal fun Map<SourceType, LibrarySortMode>.updatedSortMode(
    sourceType: SourceType,
    sortMode: LibrarySortMode,
): Map<SourceType, LibrarySortMode> = if (sortMode == defaultLibrarySortMode) {
    this - sourceType
} else {
    this + (sourceType to sortMode)
}

internal fun Map<SourceType, String>.updatedSearchQuery(
    sourceType: SourceType,
    query: String,
): Map<SourceType, String> = this + (sourceType to query)
