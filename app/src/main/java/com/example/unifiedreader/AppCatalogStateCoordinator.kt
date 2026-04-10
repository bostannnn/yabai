package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppCatalogStateCoordinator @Inject constructor(
    libraryRepository: LibraryRepository,
    readableItemRepository: ReadableItemRepository,
    private val localDocumentRepository: LocalDocumentDataSource,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val observeAppCatalogSnapshotsUseCase: ObserveAppCatalogSnapshotsUseCase,
    private val readerSessionStore: com.example.unifiedreader.feature.reader.core.ReaderSessionStore,
    private val stumpCoverCacheController: StumpCoverCacheController,
    private val grimmoryCoverCacheController: GrimmoryCoverCacheController,
) {
    private var catalogLibraries = libraryRepository.getLibraries()
    private var catalogItems = readableItemRepository.getItems()

    fun catalogItems(): List<ReadableItem> = catalogItems

    fun updateLibraryState(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        topLevel: TopLevelDestination = currentState.topLevel,
        selectedSource: SourceType = currentState.selectedSource,
        selectedItemId: String? = currentState.selectedItemId,
        activeReader: FormatType? = currentState.activeReader,
        libraryMessage: String? = currentState.libraryMessage,
        libraryMessageSeverity: LibraryMessageSeverity = currentState.libraryMessageSeverity,
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
                selectedKavitaSeriesKey = selectedKavitaSeriesKey?.takeIf { seriesKey ->
                    selectedSource == SourceType.KAVITA && items.any { item -> item.belongsToKavitaSeries(seriesKey) }
                },
                selectedStumpSeriesKey = selectedStumpSeriesKey?.takeIf { seriesKey ->
                    selectedSource == SourceType.STUMP && items.any { item -> item.belongsToStumpSeries(seriesKey) }
                },
                progressByItemId = buildProgressSummaries(items),
                libraryMessage = libraryMessage,
                libraryMessageSeverity = libraryMessageSeverity,
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
                val activeItemIds = snapshot.items.mapTo(mutableSetOf(), ReadableItem::id)
                stumpCoverCacheController.removeMissingItems(activeItemIds)
                grimmoryCoverCacheController.removeMissingItems(activeItemIds)
                updateState { state ->
                    state.copy(
                        libraries = snapshot.libraries,
                        items = snapshot.items,
                        progressByItemId = snapshot.progressByItemId,
                        downloadedRemoteItemIds = snapshot.downloadedRemoteItemIds,
                        locallyAvailableRemoteItemIds = snapshot.locallyAvailableRemoteItemIds,
                        hydratedSources = snapshot.hydratedSources,
                        themeMode = snapshot.themeMode,
                        selectedKavitaSeriesKey = state.selectedKavitaSeriesKey?.takeIf { seriesKey ->
                            state.selectedSource == SourceType.KAVITA &&
                                snapshot.items.any { item -> item.belongsToKavitaSeries(seriesKey) }
                        },
                        selectedStumpSeriesKey = state.selectedStumpSeriesKey?.takeIf { seriesKey ->
                            state.selectedSource == SourceType.STUMP &&
                                snapshot.items.any { item -> item.belongsToStumpSeries(seriesKey) }
                        },
                    )
                }
            }
        }
    }

    fun refreshDownloadedRemoteItemIdsAsync(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        items: List<ReadableItem>,
    ) {
        val requestedItemIds = items.mapTo(mutableSetOf(), ReadableItem::id)
        scope.launch {
            val remoteItemLocalState = libraryStateAssembler.loadRemoteItemLocalState(items)
            val currentItemIds = stateProvider().items.mapTo(mutableSetOf(), ReadableItem::id)
            if (currentItemIds == requestedItemIds) {
                updateState { state ->
                    state.copy(
                        downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
                        locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
                    )
                }
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
