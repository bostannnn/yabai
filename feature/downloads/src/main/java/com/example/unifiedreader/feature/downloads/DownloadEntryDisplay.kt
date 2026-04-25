package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ReadableItem

internal fun DownloadEntry.displayItemForDownloads(
    itemResolver: (String) -> ReadableItem?,
): ReadableItem? {
    val preferredItemId = when {
        status == DownloadEntryStatus.Completed && totalItemCount > 1 -> selectedItemId
        currentItemId != null -> currentItemId
        completedItemIds.size == 1 -> completedItemIds.first()
        actionItems.size == 1 -> actionItems.first().id
        else -> selectedItemId
    }
    return itemResolver(preferredItemId)
        ?: itemResolver(selectedItemId)
        ?: actionItems.firstOrNull { actionItem -> actionItem.id == preferredItemId }?.toReadableItem()
        ?: actionItems.firstOrNull()?.toReadableItem()
}
