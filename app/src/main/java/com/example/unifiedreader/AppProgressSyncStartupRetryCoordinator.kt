package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.RemoteJournalPusher
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
    private val flushPendingProgressOnStartupUseCase: FlushPendingProgressOnStartupUseCase,
    private val remoteItemLocalAvailability: RemoteItemLocalAvailability,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    private val remoteJournalPusher: RemoteJournalPusher,
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
            val failures = flushPendingProgressOnStartupUseCase(startupProgressItems)
            items
                .filterStartupJournalItems(remoteSourceConnectivityPolicy)
                .groupBy(ReadableItem::sourceType)
                .forEach { (sourceType, sourceItems) ->
                    runCatching {
                        remoteJournalPusher.pushPendingJournalEntries(
                            sourceType = sourceType,
                            items = sourceItems,
                        )
                    }.onFailure { error ->
                        Log.w(
                            LOG_TAG,
                            "Unable to push pending startup journal entries for $sourceType.",
                            error,
                        )
                    }
                }
            remoteSourceConnectivityPolicy.markFromSyncFailures(
                attemptedSourceTypes = startupProgressItems.mapTo(mutableSetOf(), ReadableItem::sourceType),
                failures = failures,
            )
            val reportableFailures = failures.withoutDownloadedReachabilityFailures(
                attemptedItems = startupProgressItems,
                remoteItemLocalAvailability = remoteItemLocalAvailability,
            )
            reportableFailures.forEach { failure ->
                Log.w(
                    LOG_TAG,
                    "Unable to push pending startup progress for ${failure.sourceType}.",
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
