package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.trackedRemoteDownloadItemIds

data class RemoteDownloadState(
    val downloadingItemIds: Set<String> = emptySet(),
)

data class RemoteDownloadStart(
    val state: RemoteDownloadState,
    val trackedItemIds: Set<String>,
    val libraryMessage: String,
)

class RemoteDownloadStateCoordinator {
    fun startDownload(
        currentState: RemoteDownloadState,
        item: ReadableItem,
        items: List<ReadableItem>,
    ): RemoteDownloadStart? {
        val trackedItemIds = trackedRemoteDownloadItemIds(item, items)
        if (trackedItemIds.any(currentState.downloadingItemIds::contains)) {
            return null
        }
        return RemoteDownloadStart(
            state = currentState.copy(
                downloadingItemIds = currentState.downloadingItemIds + trackedItemIds,
            ),
            trackedItemIds = trackedItemIds,
            libraryMessage = "Downloading ${item.libraryDisplayLabel()}.",
        )
    }

    fun finishDownload(
        currentState: RemoteDownloadState,
        trackedItemIds: Set<String>,
    ): RemoteDownloadState = currentState.copy(
        downloadingItemIds = currentState.downloadingItemIds - trackedItemIds,
    )
}
