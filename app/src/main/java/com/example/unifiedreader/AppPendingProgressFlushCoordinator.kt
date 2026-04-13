package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppPendingProgressFlushCoordinator @Inject constructor(
    private val readerSessionStore: ReaderSessionStore,
    private val flushPendingSourceProgressUseCase: FlushPendingSourceProgressUseCase,
) {
    private val retainedProgressItemsById = linkedMapOf<String, ReadableItem>()

    fun retainProgressItem(
        item: ReadableItem,
    ) {
        retainedProgressItemsById[item.id] = item
    }

    @OptIn(FlowPreview::class)
    fun observeDirtyProgress(
        scope: CoroutineScope,
        catalogItemsProvider: () -> List<ReadableItem>,
        stateProvider: AppUiStateProvider,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) {
        scope.launch {
            readerSessionStore.progressByItemIdFlow
                .map(::pendingProgressKeys)
                .distinctUntilChanged()
                .debounce(PENDING_PROGRESS_DEBOUNCE_MILLIS)
                .collect { pendingProgressKeys ->
                    retainedProgressItemsById.keys.retainAll(
                        pendingProgressKeys.mapTo(mutableSetOf(), PendingProgressKey::itemId),
                    )
                    if (pendingProgressKeys.isNotEmpty()) {
                        flushPendingRemoteProgress(
                            remoteItems = catalogItemsProvider().withPendingSyncItems(stateProvider().selectedItem),
                            onSyncFailures = onSyncFailures,
                        )
                    }
                }
        }
    }

    fun flushPendingProgress(
        catalogItems: List<ReadableItem>,
        selectedItem: ReadableItem?,
        scope: CoroutineScope,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) {
        scope.launch {
            flushPendingRemoteProgress(
                remoteItems = catalogItems.withPendingSyncItems(selectedItem),
                onSyncFailures = onSyncFailures,
            )
        }
    }

    private suspend fun flushPendingRemoteProgress(
        remoteItems: List<ReadableItem>,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) {
        val failures = flushPendingSourceProgressUseCase(remoteItems)
        failures.forEach { failure ->
            Log.w(
                LOG_TAG,
                "Unable to push pending remote progress for ${failure.sourceType}.",
                failure.throwable,
            )
        }
        onSyncFailures(failures)
    }

    private fun List<ReadableItem>.withPendingSyncItems(
        item: ReadableItem?,
    ): List<ReadableItem> {
        val itemsById = LinkedHashMap<String, ReadableItem>(size + retainedProgressItemsById.size + 1)
        forEach { existing -> itemsById.putIfAbsent(existing.id, existing) }
        item?.let { selectedItem -> itemsById.putIfAbsent(selectedItem.id, selectedItem) }
        retainedProgressItemsById.values.forEach { retainedItem ->
            itemsById.putIfAbsent(retainedItem.id, retainedItem)
        }
        return itemsById.values.toList()
    }
}

private fun pendingProgressKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
): List<PendingProgressKey> = progressByItemId.values
    .filter(ReaderProgressSnapshot::isPendingSync)
    .sortedBy(ReaderProgressSnapshot::itemId)
    .map { progress ->
        PendingProgressKey(
            itemId = progress.itemId,
            syncState = progress.syncState.name,
            updatedAtEpochMillis = progress.updatedAtEpochMillis,
        )
    }

private fun ReaderProgressSnapshot.isPendingSync(): Boolean =
    syncState == SyncState.DIRTY || syncState == SyncState.FAILED

private data class PendingProgressKey(
    val itemId: String,
    val syncState: String,
    val updatedAtEpochMillis: Long?,
)

private const val PENDING_PROGRESS_DEBOUNCE_MILLIS = 1500L
private const val LOG_TAG = "ProgressFlush"
