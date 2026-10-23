package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.feature.library.LibraryBookPage
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext

/**
 * The book page over the library for [itemId], opened by the "•••" under a cover or the reading
 * card's Details. It follows the item as the library updates and closes if the item goes away.
 */
@Composable
internal fun LibraryBookPageHost(
    uiState: AppUiState,
    itemId: String,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: (ReadableItem) -> Unit,
    onShowItem: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    val item = remember(itemId, uiState.items, uiState.sourceItems, uiState.libraryShelvesState) {
        uiState.sourceItems.firstOrNull { it.id == itemId }
            ?: uiState.items.firstOrNull { it.id == itemId }
            ?: uiState.libraryShelvesState.allItems().firstOrNull { it.id == itemId }
    }
    if (item == null) {
        LaunchedEffect(itemId) { onDismiss() }
        return
    }
    // The books of its source: the issues and books, and on Kavita and Stump the series too.
    val pool = remember(item.sourceType, uiState.items, uiState.sourceItems) {
        (uiState.sourceItems + uiState.items)
            .filter { other -> other.sourceType == item.sourceType }
            .distinctBy(ReadableItem::id)
    }
    val filters = uiState.selectedLibraryFilters
    val panels = uiState.selectedLibraryFilterPanels
    val seriesContainer = remember(item, uiState.sourceItems) {
        val containerId = item.kavitaSeriesGroupKey()?.let(::kavitaSeriesContainerId)
            ?: item.stumpSeriesGroupKey()?.let(::stumpSeriesContainerId)
        containerId?.let { id -> uiState.sourceItems.firstOrNull { it.id == id } }
    }
    LibraryBookPage(
        item = item,
        relatedPool = pool,
        actionContext = actionContext,
        coverImageUrlProvider = coverImageUrlProvider,
        onOpen = onItemSelected,
        onShowBook = onShowItem,
        onDeleteImported = onDeleteImportedItem,
        onDismiss = onDismiss,
        onFilterByGenre = { genre: String -> onFilterStateChanged(filters.copy(genres = setOf(genre))) }
            .takeIf { LibraryFilterPanel.Genre in panels },
        onFilterByTag = { tag: String -> onFilterStateChanged(filters.copy(tags = setOf(tag))) }
            .takeIf { LibraryFilterPanel.Tag in panels },
        // On Kavita and Stump the series is a screen of its own; elsewhere it is a filter.
        onSeeAllInSeries = when {
            seriesContainer != null -> { _: ReadableItem -> onItemSelected(seriesContainer) }
            LibraryFilterPanel.Series in panels && !uiState.isSeriesBrowseActive -> { book: ReadableItem ->
                book.facetMetadata().series.firstOrNull()?.let { series ->
                    onFilterStateChanged(filters.copy(series = setOf(series)))
                }
            }
            else -> null
        },
    )
}
