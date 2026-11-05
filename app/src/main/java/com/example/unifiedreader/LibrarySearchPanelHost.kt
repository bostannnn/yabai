package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.filterPanel
import com.example.unifiedreader.core.model.filteringBy
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibrarySearchPanel
import com.example.unifiedreader.feature.library.LibrarySearchPanelOutcome

/**
 * The library's search panel, open or not: the bar's field opens it, out of where the field
 * last was, and it remembers the search it opened on so Cancel can put that back.
 */
@Stable
internal class LibrarySearchPanelController {
    var isOpen by mutableStateOf(false)
        private set
    var anchor by mutableStateOf<Rect?>(null)
    var queryAtOpen = ""
        private set

    fun open(currentQuery: String) {
        queryAtOpen = currentQuery
        isOpen = true
    }

    fun close() {
        isOpen = false
    }
}

/**
 * Draws the open panel over the library and acts on how it was left: Cancel puts the search back,
 * a picked book opens (and the search goes back too, as Spotlight leaves nothing behind), Show all
 * keeps the search on All books, and a tag becomes a filter in its place.
 */
@Composable
internal fun LibrarySearchPanelHost(
    controller: LibrarySearchPanelController,
    uiState: AppUiState,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onSearchQueryChanged: (String) -> Unit,
    onFiltersChanged: (LibraryFilterState) -> Unit,
    onOpenItem: (ReadableItem) -> Unit,
) {
    if (!controller.isOpen) return
    // In a series the tags don't apply (its filters keep their own facets).
    val tagPool = if (uiState.isSeriesBrowseActive) emptyList() else uiState.sourceItems
    LibrarySearchPanel(
        query = uiState.selectedLibrarySearchQuery,
        onQueryChange = onSearchQueryChanged,
        placeholder = "Search ${uiState.selectedLibraryContextTitle ?: uiState.selectedSource.displayName()}",
        results = if (uiState.selectedLibrarySearchQuery.isBlank()) emptyList() else uiState.visibleItems,
        selectedSource = uiState.selectedSource,
        tagPool = tagPool,
        progressByItemId = uiState.displayProgressByItemId,
        locallyAvailableItemIds = uiState.locallyAvailableRemoteItemIds,
        coverImageUrlProvider = coverImageUrlProvider,
        anchor = controller.anchor,
        onClosed = { outcome ->
            controller.close()
            when (outcome) {
                LibrarySearchPanelOutcome.Cancel -> onSearchQueryChanged(controller.queryAtOpen)
                LibrarySearchPanelOutcome.ShowAll -> Unit
                is LibrarySearchPanelOutcome.Open -> {
                    onSearchQueryChanged(controller.queryAtOpen)
                    onOpenItem(outcome.item)
                }
                is LibrarySearchPanelOutcome.FilterByTag -> {
                    if (outcome.chip.filterPanel in uiState.selectedLibraryFilterPanels) {
                        onFiltersChanged(uiState.selectedLibraryFilters.filteringBy(outcome.chip))
                    }
                    onSearchQueryChanged("")
                }
            }
        },
    )
}

@Composable
internal fun rememberLibrarySearchPanelController(): LibrarySearchPanelController = remember { LibrarySearchPanelController() }
