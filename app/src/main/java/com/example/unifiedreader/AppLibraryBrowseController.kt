package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryBrowseState
import com.example.unifiedreader.feature.library.LibraryBrowseStateCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppLibraryBrowseController @Inject constructor(
    private val libraryBrowseStateCoordinator: LibraryBrowseStateCoordinator,
) {
    fun restoreState(
        updateState: AppUiStateUpdater,
    ) {
        val initialState = libraryBrowseStateCoordinator.restoreState()
        updateState { state -> state.withLibraryBrowseState(initialState) }
    }

    fun onLibraryFiltersChanged(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        filters: LibraryFilterState,
    ) {
        val sourceType = currentState.selectedSource
        updateLibraryBrowseState(
            updateState = updateState,
        ) { state ->
            libraryBrowseStateCoordinator.updateFilters(
                currentState = state,
                selectedSource = sourceType,
                filters = filters,
            )
        }
        scope.launch {
            libraryBrowseStateCoordinator.persistFilters(
                sourceType = sourceType,
                filters = filters,
            )
        }
    }

    fun onLibrarySearchQueryChanged(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        query: String,
    ) {
        val sourceType = currentState.selectedSource
        updateLibraryBrowseState(
            updateState = updateState,
        ) { state ->
            libraryBrowseStateCoordinator.updateSearchQuery(
                currentState = state,
                selectedSource = sourceType,
                query = query,
            )
        }
    }

    fun clearLibraryFilters(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        val sourceType = currentState.selectedSource
        updateLibraryBrowseState(
            updateState = updateState,
        ) { state ->
            libraryBrowseStateCoordinator.clearFilters(
                currentState = state,
                selectedSource = sourceType,
            )
        }
        scope.launch {
            libraryBrowseStateCoordinator.persistFilters(
                sourceType = sourceType,
                filters = LibraryFilterState(),
            )
        }
    }

    fun onLibrarySortModeChanged(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        sortMode: LibrarySortMode,
    ) {
        val sourceType = currentState.selectedSource
        updateLibraryBrowseState(
            updateState = updateState,
        ) { state ->
            libraryBrowseStateCoordinator.updateSortMode(
                currentState = state,
                selectedSource = sourceType,
                sortMode = sortMode,
            )
        }
        scope.launch {
            libraryBrowseStateCoordinator.persistSortMode(
                sourceType = sourceType,
                sortMode = sortMode,
            )
        }
    }

    fun onLibraryFilterPanelsChanged(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        sourceType: SourceType,
        panels: List<LibraryFilterPanel>,
    ) {
        val nextBrowseState = libraryBrowseStateCoordinator.updateFilterPanels(
            currentState = currentState.toLibraryBrowseState(),
            selectedSource = sourceType,
            panels = panels,
        )
        updateLibraryBrowseState(
            updateState = updateState,
        ) {
            nextBrowseState
        }
        scope.launch {
            libraryBrowseStateCoordinator.persistFilters(
                sourceType = sourceType,
                filters = nextBrowseState.filtersBySource[sourceType] ?: LibraryFilterState(),
            )
            libraryBrowseStateCoordinator.persistFilterPanels(
                sourceType = sourceType,
                panels = panels,
            )
        }
    }

    private inline fun updateLibraryBrowseState(
        updateState: AppUiStateUpdater,
        crossinline transform: (LibraryBrowseState) -> LibraryBrowseState,
    ) {
        updateState {
            it.withLibraryBrowseState(
                transform(it.toLibraryBrowseState()),
            )
        }
    }
}
