package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppReaderCoordinator @Inject constructor(
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val readerSessionStore: com.example.unifiedreader.feature.reader.core.ReaderSessionStore,
    private val sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
) {
    private var readerSelectionRequestId = 0
    private val retainedProgressItemsById = linkedMapOf<String, ReadableItem>()
    private val libraryScrollPositionByContext =
        object : LinkedHashMap<String, LibraryScrollPosition>(LIBRARY_SCROLL_POSITION_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, LibraryScrollPosition>,
            ): Boolean = size > LIBRARY_SCROLL_POSITION_CACHE_LIMIT
        }

    fun onTopLevelSelected(
        destination: TopLevelDestination,
        updateState: AppUiStateUpdater,
        refreshProgress: () -> Unit,
    ) {
        readerSelectionRequestId++
        updateState { state ->
            state.copy(
                topLevel = destination,
                selectedReaderItem = state.selectedReaderItem.takeIf { state.selectedItemId != null },
            )
        }
        refreshProgress()
    }

    fun onSourceSelected(
        sourceType: SourceType,
        updateState: AppUiStateUpdater,
        refreshProgress: () -> Unit,
    ) {
        readerSelectionRequestId++
        updateState { state ->
            state.copy(
                selectedSource = sourceType,
                selectedItemId = null,
                activeReader = null,
                selectedReaderItem = null,
                selectedKavitaSeriesKey = null,
                selectedStumpSeriesKey = null,
            )
        }
        refreshProgress()
    }

    fun onItemSelected(
        item: ReadableItem,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        refreshProgress: () -> Unit,
        selectedReaderItem: ReadableItem? = null,
    ) {
        if (item.isKavitaSeriesContainer()) {
            updateState { state ->
                state.copy(
                    selectedKavitaSeriesKey = item.remoteId,
                    selectedStumpSeriesKey = null,
                    selectedItemId = null,
                    activeReader = null,
                    selectedReaderItem = null,
                )
            }
            refreshProgress()
            return
        }
        if (item.isStumpSeriesContainer()) {
            updateState { state ->
                state.copy(
                    selectedStumpSeriesKey = item.remoteId,
                    selectedKavitaSeriesKey = null,
                    selectedItemId = null,
                    activeReader = null,
                    selectedReaderItem = null,
                )
            }
            refreshProgress()
            return
        }
        val progressSync = sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
        val requestId = ++readerSelectionRequestId
        selectedReaderItem?.let { retainedItem ->
            retainedProgressItemsById[retainedItem.id] = retainedItem
        }
        updateState { state ->
            state.copy(
                selectedItemId = item.id,
                activeReader = item.format,
                selectedReaderItem = selectedReaderItem,
            )
        }
        refreshProgress()
        if (progressSync != null) {
            scope.launch {
                runCatching {
                    progressSync.syncItem(item)
                }.onFailure { throwable ->
                    Log.w(LOG_TAG, "Unable to sync progress before opening ${item.id}.", throwable)
                }
                if (readerSelectionRequestId != requestId) return@launch
                refreshProgress()
            }
        }
    }

    fun libraryScrollPositionFor(
        contextKey: String,
    ): LibraryScrollPosition = libraryScrollPositionByContext[contextKey] ?: LibraryScrollPosition()

    fun onLibraryScrollPositionChanged(
        contextKey: String,
        position: LibraryScrollPosition,
    ) {
        if (libraryScrollPositionByContext[contextKey] == position) return
        libraryScrollPositionByContext[contextKey] = position
    }

    fun onBackPressed(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        catalogItemsProvider: () -> List<ReadableItem>,
        refreshProgress: () -> Unit,
    ) {
        readerSelectionRequestId++
        val state = stateProvider()
        val wasReaderOpen = state.activeReader != null && state.selectedItem != null
        updateState {
            when {
                it.activeReader != null -> it.copy(
                    activeReader = null,
                    selectedItemId = null,
                    selectedReaderItem = null,
                )
                it.selectedKavitaSeriesKey != null -> it.copy(
                    selectedKavitaSeriesKey = null,
                )
                it.selectedStumpSeriesKey != null -> it.copy(
                    selectedStumpSeriesKey = null,
                )
                it.selectedItemId != null -> it.copy(selectedItemId = null)
                else -> it
            }
        }
        if (wasReaderOpen) {
            scope.launch {
                flushPendingRemoteProgress(
                    catalogItemsProvider().withPendingSyncItems(state.selectedItem),
                )
            }
        }
        refreshProgress()
    }

    fun refreshProgress(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        refreshDownloadedRemoteItemIds: (List<ReadableItem>) -> Unit,
    ) {
        val items = stateProvider().items
        updateState { state ->
            state.copy(
                progressByItemId = libraryStateAssembler.buildProgressSummaries(
                    items = items,
                    progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                    sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                ),
            )
        }
        refreshDownloadedRemoteItemIds(items)
    }

    @OptIn(FlowPreview::class)
    fun observeDirtyProgress(
        scope: CoroutineScope,
        catalogItemsProvider: () -> List<ReadableItem>,
        stateProvider: AppUiStateProvider,
    ) {
        scope.launch {
            readerSessionStore.progressByItemIdFlow
                .map { progressByItemId ->
                    progressByItemId.values
                        .filter { progress ->
                            progress.syncState == com.example.unifiedreader.core.model.SyncState.DIRTY ||
                                progress.syncState == com.example.unifiedreader.core.model.SyncState.FAILED
                        }
                        .sortedBy(com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot::itemId)
                        .map { progress ->
                            PendingProgressKey(
                                itemId = progress.itemId,
                                syncState = progress.syncState.name,
                                updatedAtEpochMillis = progress.updatedAtEpochMillis,
                            )
                        }
                }
                .distinctUntilChanged()
                .debounce(1500L)
                .collect { pendingProgressKeys ->
                    retainedProgressItemsById.keys.retainAll(
                        pendingProgressKeys.mapTo(mutableSetOf(), PendingProgressKey::itemId),
                    )
                    if (pendingProgressKeys.isNotEmpty()) {
                        flushPendingRemoteProgress(
                            catalogItemsProvider().withPendingSyncItems(stateProvider().selectedItem),
                        )
                    }
                }
        }
    }

    private suspend fun flushPendingRemoteProgress(
        remoteItems: List<ReadableItem>,
    ) {
        sourceProgressSyncs.forEach { sync ->
            runCatching {
                sync.pushPendingProgress(remoteItems)
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Unable to push pending remote progress.", throwable)
            }
        }
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

private data class PendingProgressKey(
    val itemId: String,
    val syncState: String,
    val updatedAtEpochMillis: Long?,
)

private const val LIBRARY_SCROLL_POSITION_CACHE_LIMIT = 64
private const val LOG_TAG = "UnifiedReader"
