package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppReaderCoordinator @Inject constructor(
    private val libraryProgressRefreshCoordinator: AppLibraryProgressRefreshCoordinator,
    private val syncItemProgressUseCase: SyncItemProgressUseCase,
    private val pendingProgressFlushCoordinator: AppPendingProgressFlushCoordinator,
    private val nextItemResolver: AppReaderNextItemResolver,
    private val readableAccessResolver: ReadableAccessResolver,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
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
                selectedReaderOpenMode = ReaderOpenMode.Default,
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
                selectedReaderOpenMode = ReaderOpenMode.Default,
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
        openMode: ReaderOpenMode = ReaderOpenMode.Default,
        skipProgressSyncBeforeOpen: Boolean = false,
    ) {
        if (item.isKavitaSeriesContainer()) {
            updateState { state ->
                state.copy(
                    selectedKavitaSeriesKey = item.remoteId,
                    selectedStumpSeriesKey = null,
                    selectedItemId = null,
                    activeReader = null,
                    selectedReaderOpenMode = ReaderOpenMode.Default,
                    selectedReaderItem = null,
                    readerCompletionState = null,
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
                    selectedReaderOpenMode = ReaderOpenMode.Default,
                    selectedReaderItem = null,
                    readerCompletionState = null,
                )
            }
            refreshProgress()
            return
        }
        val accessDecision = readableAccessResolver.resolve(item)
        val hasProgressSync = !skipProgressSyncBeforeOpen && syncItemProgressUseCase.hasSyncFor(item)
        val requestId = ++readerSelectionRequestId
        selectedReaderItem?.let { retainedItem ->
            pendingProgressFlushCoordinator.retainProgressItem(retainedItem)
        }
        refreshProgress()
        when (accessDecision) {
            ReadableAccessDecision.LocalFile -> {
                openReader(
                    item = item,
                    selectedReaderItem = selectedReaderItem,
                    openMode = openMode,
                    updateState = updateState,
                )
                return
            }

            ReadableAccessDecision.DownloadedRemoteFile -> {
                openReader(
                    item = item,
                    selectedReaderItem = selectedReaderItem,
                    openMode = openMode,
                    updateState = updateState,
                )
                if (hasProgressSync && remoteSourceConnectivityPolicy.canAttemptDownloadedProgressSync(item.sourceType)) {
                    scope.launch {
                        syncProgressForDownloadedItem(
                            item = item,
                            refreshProgress = refreshProgress,
                            onSyncFailures = onSyncFailures,
                        )
                    }
                }
                return
            }

            is ReadableAccessDecision.BlockedRemoteUnavailable -> {
                onSyncFailures(
                    listOf(
                        SourceProgressSyncFailure(
                            sourceType = accessDecision.sourceType,
                            throwable = RemoteSourceUnavailableException(accessDecision.sourceType),
                        ),
                    ),
                )
                return
            }

            ReadableAccessDecision.RemoteStream -> Unit
        }
        if (hasProgressSync) {
            scope.launch {
                val failures = syncItemProgressUseCase(item).fold(
                    onSuccess = {
                        remoteSourceConnectivityPolicy.markReachable(item.sourceType)
                        emptyList()
                    },
                    onFailure = { throwable ->
                        if (throwable.isRemoteServerAddressRetryableFailure()) {
                            remoteSourceConnectivityPolicy.markUnavailable(item.sourceType)
                            Log.w(LOG_TAG, "Unable to reach ${item.sourceType} before opening ${item.id}.", throwable)
                            listOf(
                                SourceProgressSyncFailure(
                                    sourceType = item.sourceType,
                                    throwable = RemoteSourceUnavailableException(item.sourceType),
                                ),
                            )
                        } else {
                            remoteSourceConnectivityPolicy.markReachable(item.sourceType)
                            listOf(
                                SourceProgressSyncFailure(
                                    sourceType = item.sourceType,
                                    throwable = throwable,
                                ),
                            )
                        }
                    },
                )
                failures.forEach { failure ->
                    Log.w(LOG_TAG, "Unable to sync progress before opening ${item.id}.", failure.throwable)
                }
                if (readerSelectionRequestId != requestId) return@launch
                onSyncFailures(failures)
                if (failures.any { failure -> failure.throwable is RemoteSourceUnavailableException }) return@launch
                refreshProgress()
                openReader(
                    item = item,
                    selectedReaderItem = selectedReaderItem,
                    openMode = openMode,
                    updateState = updateState,
                )
            }
        } else {
            openReader(
                item = item,
                selectedReaderItem = selectedReaderItem,
                openMode = openMode,
                updateState = updateState,
            )
        }
    }

    private suspend fun syncProgressForDownloadedItem(
        item: ReadableItem,
        refreshProgress: () -> Unit,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) {
        var isReachabilityFailure = false
        val failure = syncItemProgressUseCase(item).fold(
            onSuccess = {
                remoteSourceConnectivityPolicy.markReachable(item.sourceType)
                null
            },
            onFailure = { throwable ->
                isReachabilityFailure = throwable.isRemoteServerAddressRetryableFailure()
                remoteSourceConnectivityPolicy.markFromFailure(
                    sourceType = item.sourceType,
                    throwable = throwable,
                )
                SourceProgressSyncFailure(
                    sourceType = item.sourceType,
                    throwable = throwable,
                )
            },
        )
        if (failure == null) {
            refreshProgress()
        } else if (!isReachabilityFailure) {
            onSyncFailures(listOf(failure))
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
                    selectedReaderOpenMode = ReaderOpenMode.Default,
                    selectedReaderItem = null,
                    readerCompletionState = null,
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
        val nextItem = nextItemResolver.nextUnreadAfter(
            currentItem = currentItem,
            items = state.items,
        ) ?: return
        onItemSelected(
            item = nextItem,
            updateState = updateState,
            scope = scope,
            refreshProgress = refreshProgress,
            onSyncFailures = onSyncFailures,
            openMode = ReaderOpenMode.StartFromBeginning,
        )
    }

    fun onReaderCompletionRequested(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        refreshProgress: () -> Unit,
    ) {
        refreshProgress()
        val state = stateProvider()
        if (state.readerCompletionState != null) return
        val currentItem = state.selectedItem ?: return
        val nextItem = nextItemResolver.nextUnreadAfter(
            currentItem = currentItem,
            items = state.items,
        )
        updateState {
            it.copy(
                readerCompletionState = AppReaderCompletionState(
                    itemId = currentItem.id,
                    nextItemId = nextItem?.id,
                ),
            )
        }
    }

    fun dismissReaderCompletion(
        updateState: AppUiStateUpdater,
    ) {
        updateState { it.copy(readerCompletionState = null) }
    }

    fun startReaderCompletionNextItem(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        refreshProgress: () -> Unit,
        onSyncFailures: (List<SourceProgressSyncFailure>) -> Unit,
    ) {
        val state = stateProvider()
        val nextItemId = state.readerCompletionState?.nextItemId ?: return
        val nextItem = state.items.firstOrNull { item -> item.id == nextItemId } ?: return
        updateState { it.copy(readerCompletionState = null) }
        onItemSelected(
            item = nextItem,
            updateState = updateState,
            scope = scope,
            refreshProgress = refreshProgress,
            onSyncFailures = onSyncFailures,
            openMode = ReaderOpenMode.StartFromBeginning,
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
        openMode: ReaderOpenMode,
        updateState: AppUiStateUpdater,
    ) {
        updateState { state ->
            state.copy(
                selectedItemId = item.id,
                activeReader = item.format,
                selectedReaderOpenMode = openMode,
                selectedReaderItem = selectedReaderItem,
                readerCompletionState = null,
            )
        }
    }
}

private const val LIBRARY_SCROLL_POSITION_CACHE_LIMIT = 64
private const val LOG_TAG = "UnifiedReader"
