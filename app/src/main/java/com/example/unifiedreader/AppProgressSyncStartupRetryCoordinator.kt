package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private typealias RemoteSyncAttemptRecorder =
    suspend (Set<SourceType>, List<SourceProgressSyncFailure>, List<ReadableItem>) -> Unit

class AppProgressSyncStartupRetryCoordinator @Inject constructor(
    private val readableItemRepository: ReadableItemRepository,
    private val readerSessionStore: ReaderSessionStore,
    private val journalRepository: JournalRepository,
    private val remoteCatalogReadiness: RemoteCatalogReadiness,
    private val flushPendingRemoteArtifactsUseCase: FlushPendingRemoteArtifactsUseCase,
    private val remoteItemLocalAvailability: RemoteItemLocalAvailability,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    private val autoFlushPolicy: AppRemoteArtifactAutoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(),
) {
    fun retryAfterHydration(
        scope: CoroutineScope,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
        onSyncAttempt: RemoteSyncAttemptRecorder = { _, _, _ -> },
    ) {
        scope.launch {
            val items = combine(
                readerSessionStore.isHydratedFlow,
                remoteCatalogReadiness.isHydratedFlow,
                readableItemRepository.observeItems(),
            ) { readerSessionsHydrated, remoteCatalogHydrated, items ->
                StartupRetrySnapshot(
                    ready = readerSessionsHydrated && remoteCatalogHydrated,
                    items = items,
                )
            }.first { snapshot -> snapshot.ready }.items

            val progressByItemId = readerSessionStore.progressByItemIdFlow.value
            val journalSnapshot = journalRepository.observeWriteState().first()
            val pendingFlushKeys = pendingRemoteArtifactAutoFlushKeys(
                progressByItemId = progressByItemId,
                journalSnapshot = journalSnapshot,
            )
            val pendingItemIds = pendingFlushKeys.mapTo(mutableSetOf(), AppRemoteArtifactAutoFlushKey::itemId)
            val pendingItems = items.filter { item -> item.id in pendingItemIds }
            val startupProgressItems = pendingItems.filterStartupProgressItems(
                remoteItemLocalAvailability = remoteItemLocalAvailability,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            )
            val startupJournalItems = pendingItems.filterStartupJournalItems(remoteSourceConnectivityPolicy)
            val startupItems = (startupProgressItems + startupJournalItems).distinctBy(ReadableItem::id)
            val flushItems = autoFlushPolicy.eligibleItemsForAutomaticFlush(pendingFlushKeys, startupItems)
            autoFlushPolicy.recordAutomaticFlushAttempt(pendingFlushKeys, flushItems, emptyList())
            val allFailures = if (flushItems.isEmpty()) {
                emptyList()
            } else {
                flushPendingRemoteArtifactsUseCase(
                    RemoteArtifactFlushRequest(
                        items = flushItems,
                    ),
                )
            }
            val attemptedSourceTypes = flushItems.mapTo(mutableSetOf(), ReadableItem::sourceType)
            remoteSourceConnectivityPolicy.markFromSyncFailures(
                attemptedSourceTypes = attemptedSourceTypes,
                failures = allFailures,
            )
            autoFlushPolicy.recordAutomaticFlushAttempt(pendingFlushKeys, flushItems, allFailures)
            onSyncAttempt(
                attemptedSourceTypes,
                allFailures,
                flushItems,
            )
            val reportableFailures = allFailures.withoutDownloadedReachabilityFailures(
                attemptedItems = flushItems,
                remoteItemLocalAvailability = remoteItemLocalAvailability,
            )
            reportableFailures.forEach { failure ->
                Log.w(
                    LOG_TAG,
                    "Unable to push pending startup remote artifact for ${failure.sourceType}.",
                    failure.throwable,
                )
            }
            onSyncFailures(reportableFailures)
        }
    }

}

private fun List<ReadableItem>.filterStartupProgressItems(
    remoteItemLocalAvailability: RemoteItemLocalAvailability,
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
): List<ReadableItem> = filter { item ->
    remoteSourceConnectivityPolicy.canFlushStartupProgress(
        sourceType = item.sourceType,
        hasStoredDocument = remoteItemLocalAvailability.hasStoredDocument(item),
    )
}

private fun List<ReadableItem>.filterStartupJournalItems(
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
): List<ReadableItem> = filter { item ->
    item.sourceType != SourceType.LOCAL &&
        remoteSourceConnectivityPolicy.canAttemptProgressSync(item.sourceType)
}

private data class StartupRetrySnapshot(
    val ready: Boolean,
    val items: List<ReadableItem>,
)

private const val LOG_TAG = "ProgressSyncStartup"
