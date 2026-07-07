package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Restores and captures the last-rendered library landing grid order per source so a cold start
 * renders the same view, in the same order, before the full catalog hydrates. The restored order is
 * imposed via a cold-start [com.example.unifiedreader.feature.library.LibraryOrderedItemScope] and
 * applies only while the source is not yet hydrated (see [AppUiState.coldStartOrderedItemIdsBySource]),
 * so normal sorting resumes automatically once the real catalog lands.
 */
class AppColdStartLibraryViewCoordinator @Inject constructor(
    private val orderStore: ColdStartLibraryOrderStore,
    private val coldStartCatalogSeeder: ColdStartCatalogSeeder,
) {
    fun restore(
        scope: CoroutineScope,
        source: SourceType,
        updateState: AppUiStateUpdater,
    ) {
        if (source == SourceType.LOCAL) return
        scope.launch {
            val orderedIds = orderStore.restore(source)
            if (orderedIds.isEmpty()) return@launch
            // Impose the saved order first, so the items render in order the moment they load.
            updateState { state ->
                state.copy(
                    coldStartOrderedItemIdsBySource =
                        state.coldStartOrderedItemIdsBySource + (source to orderedIds),
                )
            }
            coldStartCatalogSeeder.seedItemsByIds(source, orderedIds)
        }
    }

    @OptIn(FlowPreview::class)
    fun startCapture(
        scope: CoroutineScope,
        uiStates: Flow<AppUiState>,
    ) {
        scope.launch {
            uiStates
                .filter { state -> state.isCapturableLibraryLanding }
                .map { state -> state.selectedSource to state.visibleItems.map(ReadableItem::id) }
                .distinctUntilChanged()
                .debounce(CAPTURE_DEBOUNCE_MILLIS)
                .collect { (source, orderedIds) -> orderStore.save(source, orderedIds) }
        }
    }
}

private val AppUiState.isCapturableLibraryLanding: Boolean
    get() = topLevel == TopLevelDestination.Library &&
        selectedSource != SourceType.LOCAL &&
        isSelectedSourceHydrated &&
        !isSeriesBrowseActive &&
        !isKavitaReadingListActive &&
        !isKavitaCollectionActive &&
        visibleItems.isNotEmpty()

private const val CAPTURE_DEBOUNCE_MILLIS = 750L
