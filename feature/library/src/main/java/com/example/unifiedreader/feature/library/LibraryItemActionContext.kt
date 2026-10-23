package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

data class LibraryItemActionContext(
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val locallyAvailableItemIds: Set<String> = emptySet(),
    val downloadableItemIds: Set<String> = emptySet(),
    val deletableDownloadedItemIds: Set<String> = emptySet(),
    val downloadingItemIds: Set<String> = emptySet(),
    val seriesReadingDirectionFor: (ReadableItem) -> ComicReadingDirection? = { null },
    val onSeriesDirectionSelected: (ReadableItem, ComicReadingDirection) -> Unit = { _, _ -> },
    val seriesReaderModeFor: (ReadableItem) -> ComicReaderMode? = { null },
    val onSeriesReaderModeSelected: (ReadableItem, ComicReaderMode) -> Unit = { _, _ -> },
    val onDownloadItem: (ReadableItem) -> Unit = {},
    val onDeleteDownloadedItem: (ReadableItem) -> Unit = {},
    val onSetReadStatus: (ReadableItem, Boolean) -> Unit = { _, _ -> },
    // The "•••" under a cover and the reading card's Details open the item's book page.
    val onOpenItemPage: (ReadableItem) -> Unit = {},
)

fun LibraryItemActionContext.isDownloaded(
    item: ReadableItem,
): Boolean = item.id in locallyAvailableItemIds
