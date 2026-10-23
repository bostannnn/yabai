package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

internal fun ReadableItem.isDownloaded(
    downloadedItemIds: Set<String>,
): Boolean = id in downloadedItemIds

internal fun shouldPrefetchRemoteCover(
    item: ReadableItem,
): Boolean = item.sourceType != SourceType.LOCAL
