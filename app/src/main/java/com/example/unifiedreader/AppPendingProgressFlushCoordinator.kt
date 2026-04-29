package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppPendingProgressFlushCoordinator @Inject constructor(
    private val readerSessionStore: ReaderSessionStore,
    private val readableItemRepository: ReadableItemRepository,
    private val journalRepository: JournalRepository,
    private val flushPendingRemoteArtifactsUseCase: FlushPendingRemoteArtifactsUseCase,
    private val remoteItemLocalAvailability: RemoteItemLocalAvailability,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    private val retainedProgressItemsById = linkedMapOf<String, ReadableItem>()
    private var lastSuccessfulAutoFlush: PendingProgressFlushFingerprint? = null

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
            combine(
                readerSessionStore.progressByItemIdFlow.map(::pendingProgressKeys),
                journalRepository.observeSnapshot().map(::pendingJournalKeys),
                readableItemRepository.observeItems(),
                remoteSourceConnectivityPolicy.reachabilityFlow,
            ) { pendingProgressKeys, pendingJournalKeys, catalogItems, reachabilityBySource ->
                val pendingSyncKeys = (pendingProgressKeys + pendingJournalKeys).sortedBy(PendingRemoteSyncKey::sortKey)
                PendingProgressObservation(
                    pendingSyncKeys = pendingSyncKeys,
                    catalogItems = catalogItems,
                    reachabilityBySource = reachabilityBySource,
                    resolvablePendingItemIds = pendingSyncKeys.resolvableIn(catalogItems),
                )
            }
                .distinctUntilChanged { previous, next ->
                    previous.pendingSyncKeys == next.pendingSyncKeys &&
                        previous.resolvablePendingItemIds == next.resolvablePendingItemIds &&
                        previous.reachabilityBySource == next.reachabilityBySource
                }
                .debounce(PENDING_PROGRESS_DEBOUNCE_MILLIS)
                .collect { observation ->
                    val pendingSyncKeys = observation.pendingSyncKeys
                    retainedProgressItemsById.keys.retainAll(
                        pendingSyncKeys.mapTo(mutableSetOf(), PendingRemoteSyncKey::itemId),
                    )
                    val remoteItems = observation.catalogItems
                        .withPendingSyncItems(stateProvider().selectedItem)
                        .withoutUnavailableProgressSources(
                            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                        )
                    val flushFingerprint = PendingProgressFlushFingerprint(
                        pendingSyncKeys = pendingSyncKeys,
                        itemIds = remoteItems.map(ReadableItem::id),
                    )
                    if (remoteItems.isNotEmpty() && flushFingerprint != lastSuccessfulAutoFlush) {
                        val failures = flushPendingRemoteProgress(
                            remoteItems = remoteItems,
                            onSyncFailures = onSyncFailures,
                        )
                        if (failures.isEmpty()) {
                            lastSuccessfulAutoFlush = flushFingerprint
                        }
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
            val remoteItems = catalogItems
                .withPendingSyncItems(selectedItem)
                .withoutUnavailableProgressSources(
                    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                )
            flushPendingRemoteProgress(
                remoteItems = remoteItems,
                onSyncFailures = onSyncFailures,
            )
        }
    }

    private suspend fun flushPendingRemoteProgress(
        remoteItems: List<ReadableItem>,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ): List<SourceProgressSyncFailure> {
        if (remoteItems.isEmpty()) return emptyList()
        val allFailures = flushPendingRemoteArtifactsUseCase(
            RemoteArtifactFlushRequest(items = remoteItems),
        )
        remoteSourceConnectivityPolicy.markFromSyncFailures(
            attemptedSourceTypes = remoteItems.mapTo(mutableSetOf(), ReadableItem::sourceType),
            failures = allFailures,
        )
        val reportableFailures = allFailures.withoutDownloadedReachabilityFailures(
            attemptedItems = remoteItems,
            remoteItemLocalAvailability = remoteItemLocalAvailability,
        )
        reportableFailures.forEach { failure ->
            Log.w(
                LOG_TAG,
                "Unable to push pending remote artifact for ${failure.sourceType}.",
                failure.throwable,
            )
        }
        onSyncFailures(reportableFailures)
        return allFailures
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

private fun List<ReadableItem>.withoutUnavailableProgressSources(
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
): List<ReadableItem> {
    if (isEmpty()) return this
    return filterNot { item ->
        !remoteSourceConnectivityPolicy.canAttemptProgressSync(item.sourceType)
    }
}

private fun pendingProgressKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
): List<PendingRemoteSyncKey> = progressByItemId.values
    .filter(ReaderProgressSnapshot::isPendingSync)
    .sortedBy(ReaderProgressSnapshot::itemId)
    .map { progress ->
        PendingRemoteSyncKey(
            artifact = RemoteSyncArtifact.PROGRESS,
            itemId = progress.itemId,
            syncState = progress.syncState.name,
            updatedAtEpochMillis = progress.updatedAtEpochMillis,
        )
    }

private fun pendingJournalKeys(
    snapshot: JournalSnapshot,
): List<PendingRemoteSyncKey> =
    snapshot.entries.mapNotNull(ItemJournalEntry::pendingRemoteSyncKey) +
        snapshot.sessions.mapNotNull(JournalReadingSession::pendingRemoteSyncKey)

private fun ItemJournalEntry.pendingRemoteSyncKey(): PendingRemoteSyncKey? =
    syncState.takeIf(SyncState::isPendingSync)?.let { state ->
        PendingRemoteSyncKey(
            artifact = RemoteSyncArtifact.JOURNAL_ENTRY,
            itemId = itemId,
            syncState = state.name,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

private fun JournalReadingSession.pendingRemoteSyncKey(): PendingRemoteSyncKey? =
    syncState.takeIf(SyncState::isPendingSync)?.let { state ->
        PendingRemoteSyncKey(
            artifact = RemoteSyncArtifact.READING_SESSION,
            itemId = itemId,
            syncState = state.name,
            updatedAtEpochMillis = endedAtEpochMillis,
        )
    }

private fun ReaderProgressSnapshot.isPendingSync(): Boolean = syncState.isPendingSync()

private fun SyncState.isPendingSync(): Boolean =
    this == SyncState.DIRTY || this == SyncState.FAILED

private data class PendingRemoteSyncKey(
    val artifact: RemoteSyncArtifact,
    val itemId: String,
    val syncState: String,
    val updatedAtEpochMillis: Long?,
) {
    val sortKey: String = "${artifact.name}:$itemId:${updatedAtEpochMillis ?: 0L}:$syncState"
}

private data class PendingProgressObservation(
    val pendingSyncKeys: List<PendingRemoteSyncKey>,
    val catalogItems: List<ReadableItem>,
    val reachabilityBySource: Map<SourceType, SourceConnectivityState>,
    val resolvablePendingItemIds: Set<String>,
)

private data class PendingProgressFlushFingerprint(
    val pendingSyncKeys: List<PendingRemoteSyncKey>,
    val itemIds: List<String>,
)

private fun List<PendingRemoteSyncKey>.resolvableIn(
    catalogItems: List<ReadableItem>,
): Set<String> {
    if (isEmpty() || catalogItems.isEmpty()) return emptySet()
    val pendingItemIds = mapTo(mutableSetOf(), PendingRemoteSyncKey::itemId)
    return catalogItems.asSequence()
        .map(ReadableItem::id)
        .filter(pendingItemIds::contains)
        .toSet()
}

private const val PENDING_PROGRESS_DEBOUNCE_MILLIS = 1500L
private const val LOG_TAG = "ProgressFlush"
