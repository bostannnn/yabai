package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Restores and captures the last-rendered library landing grid order per source so a cold start
 * renders the same view, in the same order, before the full catalog hydrates. The restored order is
 * imposed via a cold-start [com.example.unifiedreader.feature.library.LibraryOrderedItemScope] and
 * applies only while the source is not yet hydrated (see [AppUiState.coldStartOrderedItemIdsBySource]),
 * so normal sorting resumes automatically once the real catalog lands.
 *
 * The default paged Kavita view no longer materializes [AppUiState.visibleItems], so its order is
 * captured from the grid-roots table via [LibraryGridPager.orderedGridRootIds]; every other landing
 * still snapshots the in-memory [AppUiState.visibleItems] order.
 */
class AppColdStartLibraryViewCoordinator @Inject constructor(
    private val orderStore: ColdStartLibraryOrderStore,
    private val coldStartCatalogSeeder: ColdStartCatalogSeeder,
    private val libraryGridPager: LibraryGridPager,
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
                .mapNotNull { state -> state.captureRequest() }
                .distinctUntilChanged()
                .debounce(CAPTURE_DEBOUNCE_MILLIS)
                .collectLatest { request ->
                    val orderedIds = when (request) {
                        is CaptureRequest.FromVisibleItems -> request.orderedIds
                        is CaptureRequest.FromPagedRoots ->
                            libraryGridPager.orderedGridRootIds(
                                source = request.source,
                                sortMode = request.sortMode,
                                limit = COLD_START_LIBRARY_ORDER_LIMIT,
                            )
                    }
                    if (orderedIds.isNotEmpty()) {
                        orderStore.save(request.source, orderedIds)
                    }
                }
        }
    }
}

/**
 * What the capture stream records for a settled library landing: the in-memory grid order snapshot
 * for the classic path, or a request to read the paged view's order from SQL. Kept `distinctUntilChanged`
 * so the capture only re-fires (and re-queries) when the recorded order can actually have changed.
 */
private sealed interface CaptureRequest {
    val source: SourceType

    data class FromVisibleItems(
        override val source: SourceType,
        val orderedIds: List<String>,
    ) : CaptureRequest

    data class FromPagedRoots(
        override val source: SourceType,
        val sortMode: LibrarySortMode,
        // Re-capture when the last-read order can shift (reading progress changed); DATE_ADDED order is
        // stable within a catalog, so it carries no progress signal and captures once per landing.
        val orderSignal: Map<String, ItemProgressSummary>?,
    ) : CaptureRequest
}

private fun AppUiState.captureRequest(): CaptureRequest? {
    if (topLevel != TopLevelDestination.Library) return null
    if (selectedSource == SourceType.LOCAL) return null
    // TODO(paging): the paged branch reads its order from SQL, so it no longer needs the in-memory
    // catalog; drop this hydration gate for it once hydration is retired for paged sources (Phase 3).
    if (!isSelectedSourceHydrated) return null
    if (isSeriesBrowseActive || isKavitaScopeActive) return null
    return if (LibraryPagingFeature.isPageable(this)) {
        CaptureRequest.FromPagedRoots(
            source = selectedSource,
            sortMode = selectedLibrarySortMode,
            orderSignal = progressByItemId.takeIf {
                selectedLibrarySortMode == LibrarySortMode.LAST_READ
            },
        )
    } else {
        visibleItems
            .takeIf { items -> items.isNotEmpty() }
            ?.let { items ->
                CaptureRequest.FromVisibleItems(
                    source = selectedSource,
                    orderedIds = items.map(ReadableItem::id),
                )
            }
    }
}

private const val CAPTURE_DEBOUNCE_MILLIS = 750L
