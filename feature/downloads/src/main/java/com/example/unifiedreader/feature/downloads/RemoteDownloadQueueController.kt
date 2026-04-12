package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.resolveRemoteDownloadActionItems
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadQueueActionResult(
    val message: String,
    val isError: Boolean = false,
)

interface DownloadWorkScheduler {
    fun scheduleEntry(
        entryId: String,
    )

    fun cancelEntry(
        entryId: String,
    )
}

class RemoteDownloadQueueController(
    private val store: DownloadQueueStore,
    private val scheduler: DownloadWorkScheduler,
    private val remoteDownloadDataSources: List<RemoteItemDownloadDataSource>,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    fun observeEntries() = store.observeEntries()

    suspend fun enqueue(
        item: ReadableItem,
        items: List<ReadableItem>,
    ): DownloadQueueActionResult {
        val resolvedActionItems = resolveRemoteDownloadActionItems(
            selectedItem = item,
            items = items,
        )
        val actionItems = withContext(Dispatchers.IO) {
            resolvedActionItems.filter { actionItem ->
                val dataSource = remoteDownloadDataSources.downloadDataSourceFor(actionItem)
                dataSource != null && dataSource.storedDocumentUri(actionItem) == null
            }
        }
        if (actionItems.isEmpty()) {
            return DownloadQueueActionResult(
                message = "Local file already available for ${item.libraryDisplayLabel()}.",
            )
        }
        val trackedItemIds = buildSet {
            add(item.id)
            addAll(actionItems.map(ReadableItem::id))
        }
        val now = timeProvider()
        val entry = DownloadEntry(
            id = UUID.randomUUID().toString(),
            selectedItemId = item.id,
            selectedItemTitle = item.libraryDisplayLabel(),
            sourceType = item.sourceType,
            actionItems = actionItems.map(ReadableItem::toQueuedDownloadItem),
            trackedItemIds = trackedItemIds,
            completedItemIds = emptySet(),
            status = DownloadEntryStatus.Queued,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val queued = store.putEntryIfNoActiveTrackedItems(entry)
        if (!queued) {
            return DownloadQueueActionResult(
                message = "Download already queued for ${item.libraryDisplayLabel()}.",
                isError = true,
            )
        }
        scheduler.scheduleEntry(entry.id)
        return DownloadQueueActionResult(
            message = if (actionItems.size == 1) {
                "Queued ${item.libraryDisplayLabel()} for download."
            } else {
                "Queued ${actionItems.size} files for download."
            },
        )
    }

    suspend fun cancel(
        entryId: String,
    ) {
        val canceledQueued = store.updateStatusIfCurrentStatusIn(
            entryId = entryId,
            expectedStatuses = setOf(DownloadEntryStatus.Queued),
            status = DownloadEntryStatus.Canceled,
            completedAtEpochMillis = timeProvider(),
        )
        if (!canceledQueued) {
            store.updateStatusIfCurrentStatusIn(
                entryId = entryId,
                expectedStatuses = setOf(DownloadEntryStatus.Running),
                status = DownloadEntryStatus.Canceling,
            )
        }
        scheduler.cancelEntry(entryId)
    }

    suspend fun retry(
        entryId: String,
    ) {
        val retried = store.retryEntryIfNoOtherActiveTrackedItems(entryId)
        if (retried) {
            scheduler.scheduleEntry(entryId)
        }
    }

    suspend fun remove(
        entryId: String,
    ): Boolean {
        val entry = store.getEntry(entryId) ?: return false
        if (entry.status != DownloadEntryStatus.Failed && entry.status != DownloadEntryStatus.Canceled) {
            return false
        }
        val completedItems = entry.actionItems
            .asSequence()
            .filter { actionItem -> actionItem.id in entry.completedItemIds }
            .map(QueuedDownloadItem::toReadableItem)
            .toList()
        val remainingCompletedItemIds = entry.completedItemIds.toMutableSet()
        for (item in completedItems) {
            val dataSource = remoteDownloadDataSources.downloadDataSourceFor(item)
            if (dataSource == null) {
                store.updateProgress(
                    entryId = entryId,
                    completedItemIds = remainingCompletedItemIds,
                    currentItemId = entry.currentItemId,
                    currentItemTitle = entry.currentItemTitle,
                )
                return false
            }
            val storedDocumentUri = dataSource.storedDocumentUri(item)
            if (storedDocumentUri == null) {
                remainingCompletedItemIds -= item.id
                continue
            }
            if (!dataSource.deleteStoredDocument(item)) {
                store.updateProgress(
                    entryId = entryId,
                    completedItemIds = remainingCompletedItemIds,
                    currentItemId = entry.currentItemId,
                    currentItemTitle = entry.currentItemTitle,
                )
                return false
            }
            remainingCompletedItemIds -= item.id
        }
        return store.deleteEntry(entryId) > 0
    }

    suspend fun clearCompleted(): Int = store.clearCompletedEntries()

    suspend fun resolveOpenItem(
        entryId: String,
    ): ReadableItem? {
        val entry = store.getEntry(entryId) ?: return null
        if (entry.status != DownloadEntryStatus.Completed) return null
        return withContext(Dispatchers.IO) {
            entry.actionItems
                .asSequence()
                .filter { actionItem -> actionItem.id in entry.completedItemIds }
                .map(QueuedDownloadItem::toReadableItem)
                .firstOrNull { item ->
                    remoteDownloadDataSources.downloadDataSourceFor(item)?.storedDocumentUri(item) != null
                }
        }
    }

    suspend fun ensureScheduled(): Int {
        val entries = store.getEntries()
        entries.filter { entry ->
            entry.status == DownloadEntryStatus.Canceling
        }.forEach { entry ->
            store.updateStatusIfCurrentStatusIn(
                entryId = entry.id,
                expectedStatuses = setOf(DownloadEntryStatus.Canceling),
                status = DownloadEntryStatus.Canceled,
                completedAtEpochMillis = timeProvider(),
            )
        }
        val pendingEntries = entries.filter { entry ->
            entry.status == DownloadEntryStatus.Queued || entry.status == DownloadEntryStatus.Running
        }
        pendingEntries.forEach { entry ->
            scheduler.scheduleEntry(entry.id)
        }
        return pendingEntries.size
    }
}

private fun ReadableItem.toQueuedDownloadItem(): QueuedDownloadItem = QueuedDownloadItem(
    id = id,
    sourceType = sourceType,
    libraryId = libraryId,
    remoteId = remoteId,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = libraryDisplayLabel(),
    formatName = format.name,
    pageCount = pageCount,
)
