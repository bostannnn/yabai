package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.resolveRemoteDownloadActionItems
import com.example.unifiedreader.core.model.trackedRemoteDownloadItemIds

data class RemoteDownloadActionAvailability(
    val downloadableItemIds: Set<String> = emptySet(),
    val deletableDownloadedItemIds: Set<String> = emptySet(),
    val activeDownloadingItemIds: Set<String> = emptySet(),
)

fun buildRemoteDownloadActionAvailability(
    sourceItems: List<ReadableItem>,
    allItems: List<ReadableItem>,
    downloadedItemIds: Set<String>,
    downloadingItemIds: Set<String>,
): RemoteDownloadActionAvailability {
    val activeDownloadingItemIds = buildActiveDownloadingItemIds(
        sourceItems = sourceItems,
        allItems = allItems,
        downloadingItemIds = downloadingItemIds,
    )
    return buildRemoteDownloadActionAvailability(
        sourceItems = sourceItems,
        downloadedItemIds = downloadedItemIds,
        activeDownloadingItemIds = activeDownloadingItemIds,
        actionItemsProvider = { item ->
            resolveRemoteDownloadActionItems(
                selectedItem = item,
                items = allItems,
            )
        },
    )
}

internal fun buildRemoteDownloadActionAvailability(
    sourceItems: List<ReadableItem>,
    downloadedItemIds: Set<String>,
    activeDownloadingItemIds: Set<String>,
    actionItemsProvider: (ReadableItem) -> List<ReadableItem>,
): RemoteDownloadActionAvailability {
    val downloadableItemIds = mutableSetOf<String>()
    val deletableDownloadedItemIds = mutableSetOf<String>()
    sourceItems.forEach { item ->
        if (!item.supportsOfflineDownload()) return@forEach
        val actionItems = actionItemsProvider(item)
        if (actionItems.isEmpty()) return@forEach
        if (item.id !in activeDownloadingItemIds && actionItems.any { actionItem -> actionItem.id !in downloadedItemIds }) {
            downloadableItemIds += item.id
        }
        if (actionItems.any { actionItem -> actionItem.id in downloadedItemIds }) {
            deletableDownloadedItemIds += item.id
        }
    }
    return RemoteDownloadActionAvailability(
        downloadableItemIds = downloadableItemIds,
        deletableDownloadedItemIds = deletableDownloadedItemIds,
        activeDownloadingItemIds = activeDownloadingItemIds,
    )
}

private fun buildActiveDownloadingItemIds(
    sourceItems: List<ReadableItem>,
    allItems: List<ReadableItem>,
    downloadingItemIds: Set<String>,
): Set<String> {
    val knownItemsById = (sourceItems + allItems)
        .associateBy(ReadableItem::id)
    return buildSet {
        addAll(downloadingItemIds)
        downloadingItemIds.forEach { downloadingItemId ->
            val downloadingItem = knownItemsById[downloadingItemId] ?: return@forEach
            addAll(trackedRemoteDownloadItemIds(downloadingItem, allItems))
        }
    }
}

fun ReadableItem.supportsOfflineDownload(): Boolean = when (sourceType) {
    SourceType.GRIMMORY -> format == FormatType.EPUB || format == FormatType.PDF
    SourceType.KAVITA -> format == FormatType.CBZ || isKavitaSeriesContainer()
    SourceType.STUMP -> format == FormatType.CBZ || format == FormatType.CBR || isStumpSeriesContainer()
    SourceType.LOCAL -> false
}
