package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
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
    private val remoteCatalogReadiness: RemoteCatalogReadiness,
    private val autoFlushPolicy: AppRemoteArtifactAutoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(),
    private val readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
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
                journalRepository.observeWriteState(),
                readableItemRepository.observeItems(),
                remoteSourceConnectivityPolicy.reachabilityFlow,
                remoteCatalogReadiness.isHydratedFlow,
            ) { progressByItemId, journalSnapshot, catalogItems, reachabilityBySource, isCatalogComplete ->
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
                    isCatalogComplete = isCatalogComplete,
                )
            }
                .combine(readerWorkCoordinator.activeReaderItemIds) { observation, openItemIds ->
                    observation.copy(heldItemIds = observation.progressByItemId.heldAtBookEnd(openItemIds))
                }
                .distinctUntilChanged { previous, next ->
                    previous.pendingSyncKeys == next.pendingSyncKeys &&
                        previous.resolvablePendingItemIds == next.resolvablePendingItemIds &&
                        previous.reachabilityBySource == next.reachabilityBySource &&
                        previous.progressByItemId == next.progressByItemId &&
                        previous.journalSnapshot == next.journalSnapshot &&
                        previous.isCatalogComplete == next.isCatalogComplete &&
                        previous.heldItemIds == next.heldItemIds
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
                            isCatalogComplete = observation.isCatalogComplete,
                        ),
                        reporter = onPendingRemoteSyncSummaryChanged,
                    )
                    val pendingFlushKeys = pendingSyncKeys.toAutoFlushKeys()
                    val flushItems = autoFlushPolicy.eligibleItemsForAutomaticFlush(pendingFlushKeys, remoteItems.withoutHeld(observation.heldItemIds))
                    if (flushItems.isNotEmpty()) {
                        autoFlushPolicy.recordAutomaticFlushAttempt(pendingFlushKeys, flushItems, emptyList())
                        val failures = flushPendingRemoteProgress(flushItems, onSyncFailures)
                        autoFlushPolicy.recordAutomaticFlushAttempt(pendingFlushKeys, flushItems, failures)
                    }
                }
        }
    }

    /** Uploads what is waiting; a book still open at its end waits for the reader to leave it, unless [isLeavingReader]. */
    fun flushPendingProgress(
        catalogItems: List<ReadableItem>,
        selectedItem: ReadableItem?,
        scope: CoroutineScope,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
        isLeavingReader: Boolean = false,
    ) {
        scope.launch {
            val progressByItemId = readerSessionStore.progressByItemIdFlow.value
            val pendingSyncKeys = pendingSyncKeys(
                progressByItemId = progressByItemId,
                journalSnapshot = journalRepository.observeWriteState().first(),
            )
            val heldItemIds = if (isLeavingReader) emptySet() else progressByItemId.heldAtBookEnd(readerWorkCoordinator.activeReaderItemIds.value)
            val remoteItems = catalogItems
                .withPendingSyncItems(selectedItem)
                .withPendingSyncKeys(pendingSyncKeys)
                .withoutUnavailableProgressSources(
                    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                )
                .withoutHeld(heldItemIds)
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
            journalSnapshot = journalRepository.observeWriteState().first(),
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

private data class PendingProgressObservation(
    val pendingSyncKeys: List<PendingRemoteSyncKey>,
    val progressByItemId: Map<String, ReaderProgressSnapshot>,
    val journalSnapshot: JournalSnapshot,
    val catalogItems: List<ReadableItem>,
    val reachabilityBySource: Map<SourceType, SourceConnectivityState>,
    val resolvablePendingItemIds: Set<String>,
    val isCatalogComplete: Boolean,
    val heldItemIds: Set<String> = emptySet(),
)

private const val PENDING_PROGRESS_DEBOUNCE_MILLIS = 1500L
private const val LOG_TAG = "ProgressFlush"
