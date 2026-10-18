package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppCatalogStateCoordinator @Inject constructor(
    libraryRepository: LibraryRepository,
    readableItemRepository: ReadableItemRepository,
    private val localDocumentRepository: LocalDocumentDataSource,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val observeAppCatalogSnapshotsUseCase: ObserveAppCatalogSnapshotsUseCase,
    private val readerSessionStore: com.example.unifiedreader.feature.reader.core.ReaderSessionStore,
    private val remoteCoverController: RemoteCoverController,
) {
    private var catalogLibraries = libraryRepository.getLibraries()
    private var catalogItems = readableItemRepository.getItems()
    private var remoteItemLocalStateRefreshRequestId = 0L

    fun catalogItems(): List<ReadableItem> = catalogItems

    fun updateLibraryState(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        topLevel: TopLevelDestination = currentState.topLevel,
        selectedSource: SourceType = currentState.selectedSource,
        selectedItemId: String? = currentState.selectedItemId,
        activeReader: FormatType? = currentState.activeReader,
        themeMode: com.example.unifiedreader.feature.settings.ThemeMode = currentState.themeMode,
        selectedKavitaSeriesKey: String? = currentState.selectedKavitaSeriesKey,
        selectedStumpSeriesKey: String? = currentState.selectedStumpSeriesKey,
    ) {
        val items = catalogItems + localDocumentRepository.getImportedItems()
        updateState { state ->
            state.copy(
                topLevel = topLevel,
                selectedSource = selectedSource,
                libraries = catalogLibraries,
                items = items,
                selectedItemId = selectedItemId,
                activeReader = activeReader,
                selectedKavitaSeriesKey = retainedSeriesKey(
                    seriesKey = selectedKavitaSeriesKey,
                    seriesSource = SourceType.KAVITA,
                    selectedSource = selectedSource,
                    items = items,
                    hydratedSources = state.hydratedSources,
                    belongsToSeries = ReadableItem::belongsToKavitaSeries,
                ),
                selectedStumpSeriesKey = retainedSeriesKey(
                    seriesKey = selectedStumpSeriesKey,
                    seriesSource = SourceType.STUMP,
                    selectedSource = selectedSource,
                    items = items,
                    hydratedSources = state.hydratedSources,
                    belongsToSeries = ReadableItem::belongsToStumpSeries,
                ),
                progressByItemId = buildProgressSummaries(items),
                themeMode = themeMode,
            )
        }
    }

    fun observeCatalog(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            observeAppCatalogSnapshotsUseCase.observe().collect { snapshot ->
                catalogLibraries = snapshot.libraries
                catalogItems = snapshot.catalogItems
                val remoteItemLocalStateItemIds = snapshot.items.remoteItemLocalStateScopeItemIds()
                val remoteItemLocalAvailabilityScopeIds = snapshot.items.remoteItemLocalAvailabilityScopeIds()
                val remoteItemLocalStateSourceLibraryScopeIds =
                    snapshot.items.remoteItemLocalStateSourceLibraryScopeIds()
                val snapshotKnownSourceLibraryScopeIds = snapshot.remoteItemLocalStateKnownSourceLibraryScopeIds
                val snapshotKnownScopes = snapshot.items.remoteItemLocalStateCacheScopes()
                    .filter { scope -> scope.sourceLibraryScopeId in snapshotKnownSourceLibraryScopeIds }
                val snapshotKnownItemIds = snapshotKnownScopes
                    .flatMapTo(linkedSetOf()) { scope -> scope.itemIds }
                val snapshotKnownAvailabilityScopeIds = snapshotKnownScopes
                    .flatMapTo(linkedSetOf()) { scope -> scope.availabilityScopeIds }
                val activeItemIds = snapshot.items.mapTo(mutableSetOf(), ReadableItem::id)
                remoteCoverController.removeMissingItems(activeItemIds)
                updateState { state ->
                    val hasCompatibleRemoteItemLocalStateScope =
                        state.remoteItemLocalStateItemIds == remoteItemLocalStateItemIds &&
                            state.remoteItemLocalAvailabilityScopeIds == remoteItemLocalAvailabilityScopeIds
                    val retainedDownloadedRemoteItemIds = if (hasCompatibleRemoteItemLocalStateScope) {
                        state.downloadedRemoteItemIds.filterTo(mutableSetOf()) { itemId ->
                            itemId in remoteItemLocalStateItemIds &&
                                (!snapshot.includesRemoteItemLocalState || itemId !in snapshotKnownItemIds)
                        }
                    } else {
                        mutableSetOf()
                    }
                    val downloadedRemoteItemIds = if (snapshot.includesRemoteItemLocalState) {
                        retainedDownloadedRemoteItemIds + snapshot.downloadedRemoteItemIds
                    } else {
                        retainedDownloadedRemoteItemIds
                    }
                    val retainedLocallyAvailableRemoteItemIds =
                        if (hasCompatibleRemoteItemLocalStateScope) {
                            state.locallyAvailableRemoteItemIds.filterTo(mutableSetOf()) { itemId ->
                                itemId in remoteItemLocalAvailabilityScopeIds &&
                                    (!snapshot.includesRemoteItemLocalState ||
                                        itemId !in snapshotKnownAvailabilityScopeIds)
                            }
                        } else {
                            mutableSetOf()
                        }
                    val locallyAvailableRemoteItemIds = if (snapshot.includesRemoteItemLocalState) {
                        retainedLocallyAvailableRemoteItemIds + snapshot.locallyAvailableRemoteItemIds
                    } else {
                        retainedLocallyAvailableRemoteItemIds
                    }
                    val retainedKnownSourceLibraryScopeIds = if (hasCompatibleRemoteItemLocalStateScope) {
                        state.remoteItemLocalStateKnownSourceLibraryScopeIds.ifEmpty {
                            if (state.isRemoteItemLocalStateKnown) {
                                remoteItemLocalStateSourceLibraryScopeIds
                            } else {
                                emptySet()
                            }
                        }
                    } else {
                        emptySet()
                    }
                    val remoteItemLocalStateKnownSourceLibraryScopeIds =
                        retainedKnownSourceLibraryScopeIds + snapshotKnownSourceLibraryScopeIds
                    val isRemoteItemLocalStateKnown =
                        remoteItemLocalStateKnownSourceLibraryScopeIds.containsAll(
                            remoteItemLocalStateSourceLibraryScopeIds,
                        )
                    val isRemoteItemLocalStateRefreshing = remoteItemLocalStateSourceLibraryScopeIds.isNotEmpty()
                    state.copy(
                        libraries = snapshot.libraries,
                        items = snapshot.items,
                        progressByItemId = snapshot.progressByItemId,
                        downloadedRemoteItemIds = downloadedRemoteItemIds,
                        locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
                        remoteItemLocalStateItemIds = remoteItemLocalStateItemIds,
                        remoteItemLocalAvailabilityScopeIds = remoteItemLocalAvailabilityScopeIds,
                        remoteItemLocalStateKnownSourceLibraryScopeIds = remoteItemLocalStateKnownSourceLibraryScopeIds,
                        isRemoteItemLocalStateKnown = isRemoteItemLocalStateKnown,
                        isRemoteItemLocalStateRefreshing = isRemoteItemLocalStateRefreshing,
                        hydratedSources = snapshot.hydratedSources,
                        themeMode = snapshot.themeMode,
                        selectedKavitaSeriesKey = retainedSeriesKey(
                            seriesKey = state.selectedKavitaSeriesKey,
                            seriesSource = SourceType.KAVITA,
                            selectedSource = state.selectedSource,
                            items = snapshot.items,
                            hydratedSources = snapshot.hydratedSources,
                            belongsToSeries = ReadableItem::belongsToKavitaSeries,
                        ),
                        selectedStumpSeriesKey = retainedSeriesKey(
                            seriesKey = state.selectedStumpSeriesKey,
                            seriesSource = SourceType.STUMP,
                            selectedSource = state.selectedSource,
                            items = snapshot.items,
                            hydratedSources = snapshot.hydratedSources,
                            belongsToSeries = ReadableItem::belongsToStumpSeries,
                        ),
                    )
                }
                refreshDownloadedRemoteItemIdsAsync(
                    scope = scope,
                    stateProvider = stateProvider,
                    updateState = updateState,
                    items = snapshot.items,
                )
            }
        }
    }

    fun refreshDownloadedRemoteItemIdsAsync(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        items: List<ReadableItem>,
    ) {
        val requestedItemIds = items.remoteItemLocalStateScopeItemIds()
        val requestedAvailabilityScopeIds = items.remoteItemLocalAvailabilityScopeIds()
        val requestId = ++remoteItemLocalStateRefreshRequestId
        updateState { state ->
            state.withRemoteItemLocalStateRefreshPending(
                itemIds = requestedItemIds,
                availabilityScopeIds = requestedAvailabilityScopeIds,
            )
        }
        val refreshAnchor = stateProvider().remoteItemLocalStateRefreshAnchor()
        scope.launch {
            val remoteItemLocalState = try {
                libraryStateAssembler.loadRemoteItemLocalState(items)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                settleFailedRemoteItemLocalStateRefresh(
                    requestId = requestId,
                    refreshAnchor = refreshAnchor,
                    requestedItemIds = requestedItemIds,
                    requestedAvailabilityScopeIds = requestedAvailabilityScopeIds,
                    stateProvider = stateProvider,
                    updateState = updateState,
                )
                return@launch
            }
            val currentState = stateProvider()
            if (
                requestId != remoteItemLocalStateRefreshRequestId ||
                currentState.remoteItemLocalStateRefreshAnchor() != refreshAnchor
            ) {
                return@launch
            }
            val currentItemIds = currentState.items.remoteItemLocalStateScopeItemIds()
            val currentAvailabilityScopeIds = currentState.items.remoteItemLocalAvailabilityScopeIds()
            if (
                currentItemIds == requestedItemIds &&
                currentAvailabilityScopeIds == requestedAvailabilityScopeIds
            ) {
                updateState { state ->
                    if (state.remoteItemLocalStateRefreshAnchor() != refreshAnchor) {
                        state
                    } else {
                        state.withRemoteItemLocalState(
                            downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
                            locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
                            itemIds = requestedItemIds,
                            availabilityScopeIds = requestedAvailabilityScopeIds,
                            isKnown = true,
                            knownSourceLibraryScopeIds = remoteItemLocalState.knownSourceLibraryScopeIds,
                            isRefreshing = false,
                        )
                    }
                }
            }
        }
    }

    private fun settleFailedRemoteItemLocalStateRefresh(
        requestId: Long,
        refreshAnchor: RemoteItemLocalStateRefreshAnchor,
        requestedItemIds: Set<String>,
        requestedAvailabilityScopeIds: Set<String>,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        val currentState = stateProvider()
        if (
            requestId != remoteItemLocalStateRefreshRequestId ||
            currentState.remoteItemLocalStateRefreshAnchor() != refreshAnchor
        ) {
            return
        }
        updateState { state ->
            if (state.remoteItemLocalStateRefreshAnchor() != refreshAnchor) {
                state
            } else {
                state.withRemoteItemLocalState(
                    downloadedRemoteItemIds = state.downloadedRemoteItemIds,
                    locallyAvailableRemoteItemIds = state.locallyAvailableRemoteItemIds,
                    itemIds = requestedItemIds,
                    availabilityScopeIds = requestedAvailabilityScopeIds,
                    isKnown = state.isRemoteItemLocalStateKnown,
                    knownSourceLibraryScopeIds = state.remoteItemLocalStateKnownSourceLibraryScopeIds,
                    isRefreshing = false,
                )
            }
        }
    }

    private fun buildProgressSummaries(
        items: List<ReadableItem>,
    ) = libraryStateAssembler.buildProgressSummaries(
        items = items,
        progressByItemId = readerSessionStore.progressByItemIdFlow.value,
        sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
    )
}

private data class RemoteItemLocalStateRefreshAnchor(
    val downloadedRemoteItemIds: Set<String>,
    val locallyAvailableRemoteItemIds: Set<String>,
    val itemIds: Set<String>,
    val availabilityScopeIds: Set<String>,
    val knownSourceLibraryScopeIds: Set<String>,
    val isKnown: Boolean,
    val isRefreshing: Boolean,
)

private fun AppUiState.remoteItemLocalStateRefreshAnchor(): RemoteItemLocalStateRefreshAnchor =
    RemoteItemLocalStateRefreshAnchor(
        downloadedRemoteItemIds = downloadedRemoteItemIds,
        locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
        itemIds = remoteItemLocalStateItemIds,
        availabilityScopeIds = remoteItemLocalAvailabilityScopeIds,
        knownSourceLibraryScopeIds = remoteItemLocalStateKnownSourceLibraryScopeIds,
        isKnown = isRemoteItemLocalStateKnown,
        isRefreshing = isRemoteItemLocalStateRefreshing,
    )
