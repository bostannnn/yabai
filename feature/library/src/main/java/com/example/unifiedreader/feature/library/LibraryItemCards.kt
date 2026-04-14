package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

internal fun itemDeleteRequest(
    item: ReadableItem,
    onDeleteCandidateSelected: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.isLocalImport) {
    { onDeleteCandidateSelected(item) }
} else {
    null
}

internal fun ReadableItem.isDownloaded(
    downloadedItemIds: Set<String>,
): Boolean = id in downloadedItemIds

internal fun itemDownloadRequest(
    item: ReadableItem,
    downloadableItemIds: Set<String>,
    onDownloadItem: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.id in downloadableItemIds) {
    { onDownloadItem(item) }
} else {
    null
}

internal fun itemDownloadedDeleteRequest(
    item: ReadableItem,
    deletableDownloadedItemIds: Set<String>,
    onDeleteDownloadedItem: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.id in deletableDownloadedItemIds) {
    { onDeleteDownloadedItem(item) }
} else {
    null
}

internal fun shouldPrefetchRemoteCover(
    item: ReadableItem,
): Boolean = item.sourceType != SourceType.LOCAL
