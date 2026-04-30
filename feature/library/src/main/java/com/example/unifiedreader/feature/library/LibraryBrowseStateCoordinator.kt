package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.core.model.visibleInPanels

data class LibraryBrowseState(
    val filtersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val searchQueriesBySource: Map<SourceType, String> = emptyMap(),
    val sortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val filterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>> = emptyMap(),
    val gridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    val fadeReadItems: Boolean = false,
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
            filterPanelsBySource = preferences.filterPanelsBySource,
            gridCellSizeDp = preferences.gridCellSizeDp,
            fadeReadItems = preferences.fadeReadItems,
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

    fun updateFilterPanels(
        currentState: LibraryBrowseState,
        selectedSource: SourceType,
        panels: List<LibraryFilterPanel>,
    ): LibraryBrowseState {
        val resolvedPanels = resolveLibraryFilterPanels(
            sourceType = selectedSource,
            preferredPanels = panels,
        )
        val visibleFilters = currentState.filtersBySource[selectedSource]
            ?.visibleInPanels(resolvedPanels)
            ?: LibraryFilterState()
        return currentState.copy(
            filtersBySource = currentState.filtersBySource.updatedFilters(
                sourceType = selectedSource,
                filters = visibleFilters,
            ),
            filterPanelsBySource = currentState.filterPanelsBySource.updatedFilterPanels(
                sourceType = selectedSource,
                panels = resolvedPanels,
            ),
        )
    }

    fun updateGridCellSizeDp(
        currentState: LibraryBrowseState,
        gridCellSizeDp: Int,
    ): LibraryBrowseState = currentState.copy(
        gridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp),
    )

    fun updateFadeReadItems(
        currentState: LibraryBrowseState,
        fadeReadItems: Boolean,
    ): LibraryBrowseState = currentState.copy(
        fadeReadItems = fadeReadItems,
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

    suspend fun persistFilterPanels(
        sourceType: SourceType,
        panels: List<LibraryFilterPanel>,
    ) {
        preferencesStore.setFilterPanels(
            sourceType = sourceType,
            panels = panels,
        )
    }

    suspend fun persistGridCellSizeDp(
        gridCellSizeDp: Int,
    ) {
        preferencesStore.setGridCellSizeDp(
            gridCellSizeDp = gridCellSizeDp,
        )
    }

    suspend fun persistFadeReadItems(
        fadeReadItems: Boolean,
    ) {
        preferencesStore.setFadeReadItems(
            fadeReadItems = fadeReadItems,
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

internal fun Map<SourceType, List<LibraryFilterPanel>>.updatedFilterPanels(
    sourceType: SourceType,
    panels: List<LibraryFilterPanel>,
): Map<SourceType, List<LibraryFilterPanel>> {
    val resolvedPanels = resolveLibraryFilterPanels(
        sourceType = sourceType,
        preferredPanels = panels,
    )
    return if (resolvedPanels == defaultVisibleLibraryFilterPanels(sourceType)) {
        this - sourceType
    } else {
        this + (sourceType to resolvedPanels)
    }
}
