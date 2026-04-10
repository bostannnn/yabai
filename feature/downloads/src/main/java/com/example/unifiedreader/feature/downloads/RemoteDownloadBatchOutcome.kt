package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ReadableItem

data class RemoteDownloadBatchOutcome(
    val requestedItemCount: Int,
    val newlyDownloadedCount: Int,
    val alreadyAvailableCount: Int,
    val failedCount: Int,
) {
    val hasFailures: Boolean
        get() = failedCount > 0

    fun libraryMessage(
        selectedItemLabel: String,
    ): String = when {
        requestedItemCount == 1 && newlyDownloadedCount == 1 ->
            "Downloaded $selectedItemLabel for local reading."

        requestedItemCount == 1 && alreadyAvailableCount == 1 && failedCount == 0 ->
            "Local file already available for $selectedItemLabel."

        requestedItemCount == 1 ->
            "Unable to download $selectedItemLabel."

        newlyDownloadedCount == requestedItemCount ->
            "Downloaded ${countLabel(newlyDownloadedCount)} for local reading."

        newlyDownloadedCount == 0 && alreadyAvailableCount == requestedItemCount ->
            "All selected files are already available locally."

        else -> listOfNotNull(
            newlyDownloadedCount.takeIf { it > 0 }?.let { count ->
                "Downloaded ${countLabel(count)} for local reading."
            },
            alreadyAvailableCount.takeIf { it > 0 }?.let { count ->
                "${countLabel(count)} already available locally."
            },
            failedCount.takeIf { it > 0 }?.let { count ->
                "${countLabel(count)} failed to download."
            },
        ).joinToString(separator = " ")
    }
}

fun evaluateRemoteDownloadBatchOutcome(
    requestedItems: List<ReadableItem>,
    initiallyDownloadedItemIds: Set<String>,
    downloadedItemIdsAfterRequest: Set<String>,
): RemoteDownloadBatchOutcome {
    val requestedItemIds = requestedItems.mapTo(mutableSetOf(), ReadableItem::id)
    val initiallyDownloadedRequestedItemIds = initiallyDownloadedItemIds.intersect(requestedItemIds)
    val downloadedRequestedItemIds = downloadedItemIdsAfterRequest.intersect(requestedItemIds)
    val newlyDownloadedCount = downloadedRequestedItemIds
        .subtract(initiallyDownloadedRequestedItemIds)
        .size
    val alreadyAvailableCount = initiallyDownloadedRequestedItemIds.size
    return RemoteDownloadBatchOutcome(
        requestedItemCount = requestedItems.size,
        newlyDownloadedCount = newlyDownloadedCount,
        alreadyAvailableCount = alreadyAvailableCount,
        failedCount = (requestedItems.size - newlyDownloadedCount - alreadyAvailableCount).coerceAtLeast(0),
    )
}

private fun countLabel(
    count: Int,
): String = if (count == 1) {
    "1 file"
} else {
    "$count files"
}
