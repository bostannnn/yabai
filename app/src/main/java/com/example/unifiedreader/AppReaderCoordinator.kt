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
        updateState { state -> state.copy(topLevel = destination) }
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
    ) {
        if (item.isKavitaSeriesContainer()) {
            updateState { state ->
                state.copy(
                    selectedKavitaSeriesKey = item.remoteId,
                    selectedStumpSeriesKey = null,
                    selectedItemId = null,
                    activeReader = null,
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
                )
            }
            refreshProgress()
            return
        }
        val progressSync = sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
        val requestId = ++readerSelectionRequestId
        updateState { state ->
            state.copy(
                selectedItemId = item.id,
                activeReader = item.format,
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
                flushPendingRemoteProgress(catalogItemsProvider())
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
    ) {
        scope.launch {
            readerSessionStore.progressByItemIdFlow
                .map { progressByItemId ->
                    progressByItemId.values
                        .filter { progress ->
                            progress.syncState == com.example.unifiedreader.core.model.SyncState.DIRTY
                        }
                        .sortedBy(com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot::itemId)
                        .map { progress ->
                            "${progress.itemId}:${progress.syncState.name}:${progress.updatedAtEpochMillis}"
                        }
                }
                .distinctUntilChanged()
                .debounce(1500L)
                .collect { dirtyProgressKeys ->
                    if (dirtyProgressKeys.isNotEmpty()) {
                        flushPendingRemoteProgress(catalogItemsProvider())
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
}

private const val LIBRARY_SCROLL_POSITION_CACHE_LIMIT = 64
private const val LOG_TAG = "UnifiedReader"
