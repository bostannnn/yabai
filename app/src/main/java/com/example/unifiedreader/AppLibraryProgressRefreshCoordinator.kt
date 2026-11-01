package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.ReadableItem
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ViewModelScoped
class AppLibraryProgressRefreshCoordinator @Inject constructor(
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val readerSessionStore: com.example.unifiedreader.feature.reader.core.ReaderSessionStore,
    private val appDispatchers: AppDispatchers,
) {
    private var refreshRequestId: Long = 0L
    private var refreshJob: Job? = null

    fun refresh(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        refreshDownloadedRemoteItemIds: (List<ReadableItem>) -> Unit,
    ) {
        val items = stateProvider().items
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val progressByItemId = withContext(appDispatchers.default) {
                libraryStateAssembler.buildProgressSummaries(
                    items = items,
                    progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                    sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                )
            }
            if (requestId != refreshRequestId) return@launch
            // The same catalog, or one with the same books: compared off the main thread, since a set of
            // every id costs a few milliseconds there on each tap that opens a series or a book.
            val currentItems = stateProvider().items
            val hasSameItems = currentItems === items ||
                withContext(appDispatchers.default) { currentItems.hasSameIdsAs(items) }
            if (!hasSameItems || requestId != refreshRequestId || stateProvider().items !== currentItems) return@launch
            updateState { state ->
                state.copy(progressByItemId = progressByItemId)
            }
            refreshDownloadedRemoteItemIds(currentItems)
        }.also { job ->
            job.invokeOnCompletion {
                if (refreshJob === job) {
                    refreshJob = null
                }
            }
        }
    }
}

private fun List<ReadableItem>.hasSameIdsAs(other: List<ReadableItem>): Boolean =
    mapTo(hashSetOf(), ReadableItem::id) == other.mapTo(hashSetOf(), ReadableItem::id)
