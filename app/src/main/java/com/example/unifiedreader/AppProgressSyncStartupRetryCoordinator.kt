package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.RemoteArtifactFlushRequest
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppProgressSyncStartupRetryCoordinator @Inject constructor(
    private val readableItemRepository: ReadableItemRepository,
    private val readerSessionStore: ReaderSessionStore,
    private val remoteCatalogReadiness: RemoteCatalogReadiness,
    private val flushPendingRemoteArtifactsUseCase: FlushPendingRemoteArtifactsUseCase,
    private val remoteItemLocalAvailability: RemoteItemLocalAvailability,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    fun retryAfterHydration(
        scope: CoroutineScope,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
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

            val startupProgressItems = items.filterStartupProgressItems(
                remoteItemLocalAvailability = remoteItemLocalAvailability,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            )
            val startupJournalItems = items.filterStartupJournalItems(remoteSourceConnectivityPolicy)
            val allFailures = flushPendingRemoteArtifactsUseCase(
                RemoteArtifactFlushRequest(
                    items = (startupProgressItems + startupJournalItems).distinctBy(ReadableItem::id),
                ),
            )
            val attemptedSourceTypes = startupProgressItems
                .mapTo(mutableSetOf(), ReadableItem::sourceType)
                .also { sourceTypes ->
                    sourceTypes += startupJournalItems.map(ReadableItem::sourceType)
                }
            remoteSourceConnectivityPolicy.markFromSyncFailures(
                attemptedSourceTypes = attemptedSourceTypes,
                failures = allFailures,
            )
            val reportableFailures = allFailures.withoutDownloadedReachabilityFailures(
                attemptedItems = startupProgressItems + startupJournalItems,
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
