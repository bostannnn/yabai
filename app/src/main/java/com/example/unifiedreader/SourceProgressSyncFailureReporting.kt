package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

internal fun List<SourceProgressSyncFailure>.withoutDownloadedReachabilityFailures(
    attemptedItems: List<ReadableItem>,
    remoteItemLocalAvailability: RemoteItemLocalAvailability,
): List<SourceProgressSyncFailure> {
    if (isEmpty() || attemptedItems.isEmpty()) return this
    val attemptedItemsBySource = attemptedItems.groupBy(ReadableItem::sourceType)
    return filterNot { failure ->
        failure.throwable.isRemoteServerAddressRetryableFailure() &&
            attemptedItemsBySource.allItemsForSourceAreDownloaded(
                sourceType = failure.sourceType,
                remoteItemLocalAvailability = remoteItemLocalAvailability,
            )
    }
}

private fun Map<SourceType, List<ReadableItem>>.allItemsForSourceAreDownloaded(
    sourceType: SourceType,
    remoteItemLocalAvailability: RemoteItemLocalAvailability,
): Boolean {
    val sourceItems = this[sourceType].orEmpty()
    return sourceItems.isNotEmpty() && sourceItems.all(remoteItemLocalAvailability::hasStoredDocument)
}
