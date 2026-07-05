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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppPendingProgressFlushCoordinator @Inject constructor(
    private val readerSessionStore: ReaderSessionStore,
    private val readableItemRepository: ReadableItemRepository,
    private val journalRepository: JournalRepository,
    private val flushPendingRemoteArtifactsUseCase: FlushPendingRemoteArtifactsUseCase,
    private val remoteItemLocalAvailability: RemoteItemLocalAvailability,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    private val autoFlushPolicy: AppRemoteArtifactAutoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(),
) {
    private val retainedProgressItemsById = linkedMapOf<String, ReadableItem>()
    private val syncAttemptBySource = linkedMapOf<SourceType, AppRemoteSyncAttempt>()
    private var lastPendingRemoteSyncSummary = AppPendingRemoteSyncSummary()
    private var pendingRemoteSyncSummaryReporter: ((AppPendingRemoteSyncSummary) -> Unit)? = null

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
        onPendingRemoteSyncSummaryChanged: (AppPendingRemoteSyncSummary) -> Unit = {},
    ) {
        pendingRemoteSyncSummaryReporter = onPendingRemoteSyncSummaryChanged
        scope.launch {
            combine(
                readerSessionStore.progressByItemIdFlow,
                journalRepository.observeSnapshot(),
                readableItemRepository.observeItems(),
                remoteSourceConnectivityPolicy.reachabilityFlow,
            ) { progressByItemId, journalSnapshot, catalogItems, reachabilityBySource ->
                val pendingSyncKeys = pendingSyncKeys(
                    progressByItemId = progressByItemId,
                    journalSnapshot = journalSnapshot,
                )
                PendingProgressObservation(
                    pendingSyncKeys = pendingSyncKeys,
                    progressByItemId = progressByItemId,
                    journalSnapshot = journalSnapshot,
                    catalogItems = catalogItems,
                    reachabilityBySource = reachabilityBySource,
                    resolvablePendingItemIds = pendingSyncKeys.resolvableIn(catalogItems),
                )
            }
                .distinctUntilChanged { previous, next ->
                    previous.pendingSyncKeys == next.pendingSyncKeys &&
                        previous.resolvablePendingItemIds == next.resolvablePendingItemIds &&
                        previous.reachabilityBySource == next.reachabilityBySource &&
                        previous.progressByItemId == next.progressByItemId &&
                        previous.journalSnapshot == next.journalSnapshot
                }
                .debounce(PENDING_PROGRESS_DEBOUNCE_MILLIS)
                .collect { observation ->
                    val pendingSyncKeys = observation.pendingSyncKeys
                    retainedProgressItemsById.keys.retainAll(
                        pendingSyncKeys.mapTo(mutableSetOf(), PendingRemoteSyncKey::itemId),
                    )
                    val remoteItems = observation.catalogItems
                        .withPendingSyncItems(stateProvider().selectedItem)
                        .withPendingSyncKeys(pendingSyncKeys)
                        .withoutUnavailableProgressSources(
                            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                        )
                    publishPendingRemoteSyncSummary(
                        summary = buildPendingRemoteSyncSummary(
                            progressByItemId = observation.progressByItemId,
                            journalSnapshot = observation.journalSnapshot,
                            catalogItems = observation.catalogItems.withPendingSyncItems(stateProvider().selectedItem),
                            reachabilityBySource = observation.reachabilityBySource,
                            attemptsBySource = syncAttemptBySource,
                        ),
                        reporter = onPendingRemoteSyncSummaryChanged,
                    )
                    val pendingFlushKeys = pendingSyncKeys.toAutoFlushKeys()
                    val flushItems = autoFlushPolicy.eligibleItemsForAutomaticFlush(pendingFlushKeys, remoteItems)
                    if (flushItems.isNotEmpty()) {
                        autoFlushPolicy.recordAutomaticFlushAttempt(pendingFlushKeys, flushItems, emptyList())
                        val failures = flushPendingRemoteProgress(flushItems, onSyncFailures)
                        autoFlushPolicy.recordAutomaticFlushAttempt(pendingFlushKeys, flushItems, failures)
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
            val pendingSyncKeys = pendingSyncKeys(
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                journalSnapshot = journalRepository.observeSnapshot().first(),
            )
            val remoteItems = catalogItems
                .withPendingSyncItems(selectedItem)
                .withPendingSyncKeys(pendingSyncKeys)
                .withoutUnavailableProgressSources(
                    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                )
            val pendingFlushKeys = pendingSyncKeys.toAutoFlushKeys()
            autoFlushPolicy.recordExternalFlushAttempt(pendingFlushKeys, remoteItems, emptyList())
            val failures = flushPendingRemoteProgress(remoteItems, onSyncFailures)
            autoFlushPolicy.recordExternalFlushAttempt(pendingFlushKeys, remoteItems, failures)
        }
    }

    suspend fun recordRemoteSyncAttempt(
        attemptedSourceTypes: Set<SourceType>,
        failures: List<SourceProgressSyncFailure>,
        attemptedItems: List<ReadableItem> = emptyList(),
    ) {
        if (attemptedSourceTypes.isEmpty()) return
        rememberAttemptedPendingArtifacts(attemptedItems, failures)
        recordSyncAttempts(
            attemptedAtEpochMillis = System.currentTimeMillis(),
            attemptedSourceTypes = attemptedSourceTypes,
            failures = failures,
        )
    }

    private suspend fun rememberAttemptedPendingArtifacts(
        attemptedItems: List<ReadableItem>,
        failures: List<SourceProgressSyncFailure>,
    ) {
        if (attemptedItems.isEmpty()) return
        val pendingSyncKeys = pendingSyncKeys(
            progressByItemId = readerSessionStore.progressByItemIdFlow.value,
            journalSnapshot = journalRepository.observeSnapshot().first(),
        )
        val attemptedPendingItems = attemptedItems.withPendingSyncKeys(pendingSyncKeys)
        if (attemptedPendingItems.isEmpty()) return
        autoFlushPolicy.recordExternalFlushAttempt(pendingSyncKeys.toAutoFlushKeys(), attemptedPendingItems, failures)
    }

    private suspend fun flushPendingRemoteProgress(
        remoteItems: List<ReadableItem>,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ): List<SourceProgressSyncFailure> {
        if (remoteItems.isEmpty()) return emptyList()
        val attemptedSourceTypes = remoteItems.mapTo(mutableSetOf(), ReadableItem::sourceType)
        val attemptedAt = System.currentTimeMillis()
        val allFailures = flushPendingRemoteArtifactsUseCase(
            RemoteArtifactFlushRequest(items = remoteItems),
        )
        remoteSourceConnectivityPolicy.markFromSyncFailures(
            attemptedSourceTypes = attemptedSourceTypes,
            failures = allFailures,
        )
        recordSyncAttempts(
            attemptedAtEpochMillis = attemptedAt,
            attemptedSourceTypes = attemptedSourceTypes,
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

    private fun recordSyncAttempts(
        attemptedAtEpochMillis: Long,
        attemptedSourceTypes: Set<SourceType>,
        failures: List<SourceProgressSyncFailure>,
    ) {
        val failuresBySource = failures.groupBy(SourceProgressSyncFailure::sourceType)
        attemptedSourceTypes.forEach { sourceType ->
            val failure = failuresBySource[sourceType]?.firstOrNull()
            syncAttemptBySource[sourceType] = AppRemoteSyncAttempt(
                attemptedAtEpochMillis = attemptedAtEpochMillis,
                errorMessage = failure?.throwable?.userFacingMessage(),
                artifact = failure?.artifact,
            )
        }
        publishPendingRemoteSyncSummary(lastPendingRemoteSyncSummary.withAttempts(syncAttemptBySource))
    }

    private fun publishPendingRemoteSyncSummary(
        summary: AppPendingRemoteSyncSummary,
        reporter: (AppPendingRemoteSyncSummary) -> Unit = pendingRemoteSyncSummaryReporter ?: {},
    ) {
        lastPendingRemoteSyncSummary = summary
        reporter(summary)
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

private fun List<ReadableItem>.withPendingSyncKeys(
    pendingSyncKeys: List<PendingRemoteSyncKey>,
): List<ReadableItem> {
    if (isEmpty() || pendingSyncKeys.isEmpty()) return emptyList()
    val pendingItemIds = pendingSyncKeys.mapTo(mutableSetOf(), PendingRemoteSyncKey::itemId)
    return filter { item -> item.id in pendingItemIds }
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
    .filter(ReaderProgressSnapshot::isPendingRemoteSync)
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

private fun pendingSyncKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
    journalSnapshot: JournalSnapshot,
): List<PendingRemoteSyncKey> = (
    pendingProgressKeys(progressByItemId) + pendingJournalKeys(journalSnapshot)
    ).sortedBy(PendingRemoteSyncKey::sortKey)

private fun SyncState.isPendingSync(): Boolean =
    toPendingRemoteSyncItemState() != null

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
    val progressByItemId: Map<String, ReaderProgressSnapshot>,
    val journalSnapshot: JournalSnapshot,
    val catalogItems: List<ReadableItem>,
    val reachabilityBySource: Map<SourceType, SourceConnectivityState>,
    val resolvablePendingItemIds: Set<String>,
)

private fun List<PendingRemoteSyncKey>.toAutoFlushKeys(): List<AppRemoteArtifactAutoFlushKey> =
    map { key ->
        AppRemoteArtifactAutoFlushKey(
            artifact = key.artifact,
            itemId = key.itemId,
            updatedAtEpochMillis = key.updatedAtEpochMillis,
        )
    }.distinct()

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
