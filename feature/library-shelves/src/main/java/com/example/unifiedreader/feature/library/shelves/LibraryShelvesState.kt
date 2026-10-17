package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary

data class LibraryShelvesInput(
    val selectedSource: SourceType,
    val items: List<ReadableItem>,
    val progressByItemId: Map<String, ItemProgressSummary>,
)

data class ContinueReadingShelfItem(
    val item: ReadableItem,
    val progress: ItemProgressSummary,
)

data class NextInSeriesShelfItem(
    val item: ReadableItem,
    val currentItem: ReadableItem,
    val lastActivityAtEpochMillis: Long,
)

data class RecentlyFinishedShelfItem(
    val item: ReadableItem,
    val progress: ItemProgressSummary,
)

data class LibraryShelvesState(
    val continueReading: List<ContinueReadingShelfItem> = emptyList(),
    val nextInSeries: List<NextInSeriesShelfItem> = emptyList(),
    val recentlyFinished: List<RecentlyFinishedShelfItem> = emptyList(),
    val isLoading: Boolean = false,
) {
    val hasContent: Boolean
        get() = continueReading.isNotEmpty() || nextInSeries.isNotEmpty() || recentlyFinished.isNotEmpty()

    /**
     * Every distinct [ReadableItem] shown across the shelves (each shelf item, plus the current issue
     * of a Next-In-Series entry). Bounded, so the paged-window badge resolver can rebuild the shelves'
     * read/download badges from early sources instead of the late in-memory recompute maps.
     */
    fun allItems(): List<ReadableItem> = buildList {
        continueReading.forEach { entry -> add(entry.item) }
        nextInSeries.forEach { entry ->
            add(entry.item)
            add(entry.currentItem)
        }
        recentlyFinished.forEach { entry -> add(entry.item) }
    }.distinctBy(ReadableItem::id)

    fun asLoading(): LibraryShelvesState = if (isLoading) this else copy(isLoading = true)

    fun ready(): LibraryShelvesState = if (isLoading) copy(isLoading = false) else this

    companion object {
        fun placeholderLoading(): LibraryShelvesState = LibraryShelvesState(isLoading = true)
    }
}
