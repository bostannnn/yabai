package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppReaderCoordinator @Inject constructor(
    private val libraryProgressRefreshCoordinator: AppLibraryProgressRefreshCoordinator,
    private val syncItemProgressUseCase: SyncItemProgressUseCase,
    private val pendingProgressFlushCoordinator: AppPendingProgressFlushCoordinator,
    private val seriesContinuationResolver: LibrarySeriesContinuationResolver,
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
    ) {
        readerSelectionRequestId++
        updateState { state ->
            state.copy(
                topLevel = destination,
                selectedReaderItem = state.selectedReaderItem.takeIf { state.selectedItemId != null },
            )
        }
    }

    fun onLibrarySourceSelected(
        sourceType: SourceType,
        updateState: AppUiStateUpdater,
    ) {
        readerSelectionRequestId++
        updateState { state ->
            state.copy(
                topLevel = TopLevelDestination.Library,
                selectedSource = sourceType,
                selectedItemId = null,
                activeReader = null,
                selectedReaderItem = null,
                selectedKavitaSeriesKey = null,
                selectedStumpSeriesKey = null,
            )
        }
    }

    fun onItemSelected(
        item: ReadableItem,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        refreshProgress: () -> Unit,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
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
        val hasProgressSync = syncItemProgressUseCase.hasSyncFor(item)
        val requestId = ++readerSelectionRequestId
        selectedReaderItem?.let { retainedItem ->
            pendingProgressFlushCoordinator.retainProgressItem(retainedItem)
        }
        refreshProgress()
        if (hasProgressSync) {
            scope.launch {
                val failures = syncItemProgressUseCase(item).fold(
                    onSuccess = { emptyList() },
                    onFailure = { throwable ->
                        listOf(
                            SourceProgressSyncFailure(
                                sourceType = item.sourceType,
                                throwable = throwable,
                            ),
                        )
                    },
                )
                failures.forEach { failure ->
                    Log.w(LOG_TAG, "Unable to sync progress before opening ${item.id}.", failure.throwable)
                }
                if (readerSelectionRequestId != requestId) return@launch
                onSyncFailures(failures)
                refreshProgress()
                openReader(
                    item = item,
                    selectedReaderItem = selectedReaderItem,
                    updateState = updateState,
                )
            }
        } else {
            openReader(
                item = item,
                selectedReaderItem = selectedReaderItem,
                updateState = updateState,
            )
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
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
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
            pendingProgressFlushCoordinator.flushPendingProgress(
                catalogItems = catalogItemsProvider(),
                selectedItem = state.selectedItem,
                scope = scope,
                onSyncFailures = onSyncFailures,
            )
        }
        refreshProgress()
    }

    fun onReaderNextRequested(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        refreshProgress: () -> Unit,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) {
        refreshProgress()
        val state = stateProvider()
        val currentItem = state.selectedItem ?: return
        val nextItem = seriesContinuationResolver.nextUnreadAfter(
            currentItem = currentItem,
            items = state.items,
            progressByItemId = state.progressByItemId,
        ) ?: return
        onItemSelected(
            item = nextItem,
            updateState = updateState,
            scope = scope,
            refreshProgress = refreshProgress,
            onSyncFailures = onSyncFailures,
        )
    }

    fun refreshProgress(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        refreshDownloadedRemoteItemIds: (List<ReadableItem>) -> Unit,
    ) = libraryProgressRefreshCoordinator.refresh(
        scope = scope,
        stateProvider = stateProvider,
        updateState = updateState,
        refreshDownloadedRemoteItemIds = refreshDownloadedRemoteItemIds,
    )

    fun observeDirtyProgress(
        scope: CoroutineScope,
        catalogItemsProvider: () -> List<ReadableItem>,
        stateProvider: AppUiStateProvider,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) = pendingProgressFlushCoordinator.observeDirtyProgress(
        scope = scope,
        catalogItemsProvider = catalogItemsProvider,
        stateProvider = stateProvider,
        onSyncFailures = onSyncFailures,
    )

    fun flushPendingProgress(
        catalogItems: List<ReadableItem>,
        selectedItem: ReadableItem?,
        scope: CoroutineScope,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) = pendingProgressFlushCoordinator.flushPendingProgress(
        catalogItems = catalogItems,
        selectedItem = selectedItem,
        scope = scope,
        onSyncFailures = onSyncFailures,
    )

    private fun openReader(
        item: ReadableItem,
        selectedReaderItem: ReadableItem?,
        updateState: AppUiStateUpdater,
    ) {
        updateState { state ->
            state.copy(
                selectedItemId = item.id,
                activeReader = item.format,
                selectedReaderItem = selectedReaderItem,
            )
        }
    }
}

private const val LIBRARY_SCROLL_POSITION_CACHE_LIMIT = 64
private const val LOG_TAG = "UnifiedReader"
