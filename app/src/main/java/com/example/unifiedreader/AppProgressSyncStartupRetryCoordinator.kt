package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
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
    private val flushPendingProgressOnStartupUseCase: FlushPendingProgressOnStartupUseCase,
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

            val failures = flushPendingProgressOnStartupUseCase(items)
            failures.forEach { failure ->
                Log.w(
                    LOG_TAG,
                    "Unable to push pending startup progress for ${failure.sourceType}.",
                    failure.throwable,
                )
            }
            onSyncFailures(failures)
        }
    }
}

private data class StartupRetrySnapshot(
    val ready: Boolean,
    val items: List<ReadableItem>,
)

private const val LOG_TAG = "ProgressSyncStartup"
