package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.downloads.DownloadEntriesSnapshot
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AppRemoteDownloadController @Inject constructor(
    private val libraryActionsCoordinator: AppLibraryActionsCoordinator,
    private val remoteDownloadQueueController: RemoteDownloadQueueController,
    private val readableItemLookup: ReadableItemLookup,
) {
    fun observeEntries(): Flow<DownloadEntriesSnapshot> = remoteDownloadQueueController.observeEntries()

    fun onDownloadRemoteItem(
        stateProvider: () -> AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        item: ReadableItem,
        canScheduleSource: (SourceType) -> Boolean,
    ) {
        scope.launch {
            // Only the selected item's series issues are needed to resolve download action items,
            // not the whole catalog (a standalone resolves to itself with an empty set).
            remoteDownloadQueueController.enqueue(
                item = item,
                items = readableItemLookup.getSeriesMemberItems(item),
                canScheduleSource = canScheduleSource,
            )
        }
    }

    fun onDownloadRemoteItems(
        stateProvider: () -> AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        items: List<ReadableItem>,
        canScheduleSource: (SourceType) -> Boolean,
    ) {
        scope.launch {
            // The pool only needs each selected item's series issues so per-item resolution can
            // find them; resolution filters by each item's own series key.
            val allItems = items
                .flatMap { selectedItem -> readableItemLookup.getSeriesMemberItems(selectedItem) }
                .distinctBy(ReadableItem::id)
            remoteDownloadQueueController.enqueueBatch(
                selectedItems = items,
                allItems = allItems,
                canScheduleSource = canScheduleSource,
            )
        }
    }

    fun scheduleQueuedDownloads(
        scope: CoroutineScope,
        canScheduleSource: (SourceType) -> Boolean,
    ) {
        scope.launch {
            remoteDownloadQueueController.ensureScheduled(canScheduleSource)
        }
    }

    fun cancelDownload(
        scope: CoroutineScope,
        entryId: String,
    ) {
        scope.launch {
            remoteDownloadQueueController.cancel(entryId)
        }
    }

    fun retryDownload(
        scope: CoroutineScope,
        entryId: String,
        canScheduleSource: (SourceType) -> Boolean,
    ) {
        scope.launch {
            remoteDownloadQueueController.retry(entryId, canScheduleSource)
        }
    }

    fun removeDownload(
        scope: CoroutineScope,
        entryId: String,
    ) {
        scope.launch {
            remoteDownloadQueueController.remove(entryId)
        }
    }

    fun clearCompletedDownloads(
        scope: CoroutineScope,
    ) {
        scope.launch {
            remoteDownloadQueueController.clearCompleted()
        }
    }

    suspend fun resolveOpenItem(
        entryId: String,
    ): ReadableItem? = remoteDownloadQueueController.resolveOpenItem(entryId)

    fun onDeleteRemoteLocalFile(
        stateProvider: () -> AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        item: ReadableItem,
    ) {
        scope.launch {
            // Kept on the full list: delete recomputes the complete local-availability sets (over all
            // items) that replace state, so a bounded set would drop availability for other items.
            // Migrating this needs a scope-aware availability recompute (a later Stage E slice).
            val result = libraryActionsCoordinator.deleteRemoteLocalFile(item, stateProvider().items)
            updateState { state ->
                state.withRemoteItemLocalState(
                    downloadedRemoteItemIds = result.downloadedRemoteItemIds,
                    locallyAvailableRemoteItemIds = result.locallyAvailableRemoteItemIds,
                    isKnown = true,
                )
            }
        }
    }
}
