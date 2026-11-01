package com.example.unifiedreader

import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryRatingBadgeState
import com.example.unifiedreader.feature.library.RemoteDownloadActionAvailability
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesState
import java.lang.ref.WeakReference

/**
 * The last finished presentation of each library view (a server's library or one of its series, in its
 * sort, filters and search), so opening a view again (another server picked, back from a series, the
 * reader closed, back from the Journal) shows it at once while the recompute runs in the background,
 * instead of holding the whole screen until the recompute lands. It also keeps each server's last root
 * shelves, which a loading root presentation falls back to.
 */
internal class AppLibraryRetainedPresentations {
    private val presentationsByView =
        object : LinkedHashMap<LibraryPresentationViewKey, RetainedLibraryPresentation>(RETAINED_VIEWS_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<LibraryPresentationViewKey, RetainedLibraryPresentation>,
            ): Boolean = size > RETAINED_VIEWS_LIMIT
        }
    private val rootShelvesBySource =
        object : LinkedHashMap<SourceType, LibraryShelvesState>(RETAINED_VIEWS_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<SourceType, LibraryShelvesState>,
            ): Boolean = size > RETAINED_VIEWS_LIMIT
        }

    /**
     * Keeps [state]'s root shelves for its server, and its presentation for its view when the library is on
     * screen with its shelves settled.
     */
    fun retain(state: AppUiState) {
        if (state.shouldReserveLibraryShelvesSlot) {
            state.libraryShelvesState
                .takeIf(LibraryShelvesState::hasContent)
                ?.ready()
                ?.let { shelves -> rootShelvesBySource[state.selectedSource] = shelves }
        }
        if (!state.isLibraryPresentationActive || state.libraryShelvesState.isLoading) return
        presentationsByView[state.libraryPresentationViewKey()] = RetainedLibraryPresentation(
            items = WeakReference(state.items),
            presentation = state.libraryPresentation(),
        )
    }

    /**
     * The presentation last shown for [state]'s view, or null when there is none or its shelves hold a
     * book that is no longer in the catalog.
     */
    fun presentationFor(state: AppUiState): LibraryPresentation? {
        val retained = presentationsByView[state.libraryPresentationViewKey()] ?: return null
        if (retained.items.get() !== state.items &&
            !retained.presentation.libraryShelvesState.hasOnlyCurrentItems(state)
        ) {
            return null
        }
        return retained.presentation
    }

    /** [state]'s server's last root shelves, marked loading, when they hold only books still in the catalog. */
    fun loadingRootShelvesFor(state: AppUiState): LibraryShelvesState? =
        rootShelvesBySource[state.selectedSource]
            ?.takeIf { shelvesState -> shelvesState.hasOnlyCurrentItems(state) }
            ?.asLoading()
}

/** Everything the library screen draws from that the recompute derives. */
internal data class LibraryPresentation(
    val sourceItems: List<ReadableItem>,
    val selectedLibraryFilterFacets: LibraryFilterFacets,
    val selectedSourceLibraryTitlesById: Map<String, String>,
    val displayProgressByItemId: Map<String, ItemProgressSummary>,
    val displayRatingByItemId: Map<String, LibraryRatingBadgeState>,
    val displayPageCountByItemId: Map<String, Int>,
    val remoteDownloadActionAvailability: RemoteDownloadActionAvailability,
    val libraryHasAllBadges: Boolean,
    val visibleItems: List<ReadableItem>,
    val libraryBrowseContextKey: String,
    val libraryShelvesState: LibraryShelvesState,
)

internal fun AppUiState.libraryPresentation(): LibraryPresentation = LibraryPresentation(
    sourceItems = sourceItems,
    selectedLibraryFilterFacets = selectedLibraryFilterFacets,
    selectedSourceLibraryTitlesById = selectedSourceLibraryTitlesById,
    displayProgressByItemId = displayProgressByItemId,
    displayRatingByItemId = displayRatingByItemId,
    displayPageCountByItemId = displayPageCountByItemId,
    remoteDownloadActionAvailability = remoteDownloadActionAvailability,
    libraryHasAllBadges = libraryHasAllBadges,
    visibleItems = visibleItems,
    libraryBrowseContextKey = libraryBrowseContextKey,
    libraryShelvesState = libraryShelvesState,
)

internal fun AppUiState.withLibraryPresentation(presentation: LibraryPresentation): AppUiState = copy(
    sourceItems = presentation.sourceItems,
    selectedLibraryFilterFacets = presentation.selectedLibraryFilterFacets,
    selectedSourceLibraryTitlesById = presentation.selectedSourceLibraryTitlesById,
    displayProgressByItemId = presentation.displayProgressByItemId,
    displayRatingByItemId = presentation.displayRatingByItemId,
    displayPageCountByItemId = presentation.displayPageCountByItemId,
    remoteDownloadActionAvailability = presentation.remoteDownloadActionAvailability,
    libraryHasAllBadges = presentation.libraryHasAllBadges,
    visibleItems = presentation.visibleItems,
    libraryBrowseContextKey = presentation.libraryBrowseContextKey,
    libraryShelvesState = presentation.libraryShelvesState,
)

/** The library is on screen: its tab is shown and no reader covers it. */
internal val AppUiState.isLibraryPresentationActive: Boolean
    get() = topLevel == TopLevelDestination.Library && activeReader == null

/**
 * [this] opens a library view that [previous] didn't show: the library came back on screen, or another
 * server or series is picked.
 */
internal fun AppUiState.opensLibraryViewFrom(previous: AppUiState): Boolean =
    isLibraryPresentationActive && (
        !previous.isLibraryPresentationActive ||
            selectedSource != previous.selectedSource ||
            selectedKavitaSeriesKey != previous.selectedKavitaSeriesKey ||
            selectedStumpSeriesKey != previous.selectedStumpSeriesKey
        )

private data class LibraryPresentationViewKey(
    val selectedSource: SourceType,
    val selectedKavitaSeriesKey: String?,
    val selectedStumpSeriesKey: String?,
    val filters: LibraryFilterState,
    val searchQuery: String,
    val sortMode: LibrarySortMode,
)

private fun AppUiState.libraryPresentationViewKey(): LibraryPresentationViewKey = LibraryPresentationViewKey(
    selectedSource = selectedSource,
    selectedKavitaSeriesKey = selectedKavitaSeriesKey,
    selectedStumpSeriesKey = selectedStumpSeriesKey,
    filters = selectedLibraryFilters,
    searchQuery = selectedLibrarySearchQuery,
    sortMode = selectedLibrarySortMode,
)

/** A presentation and the catalog it was drawn from, held weakly so an old catalog isn't kept alive. */
private class RetainedLibraryPresentation(
    val items: WeakReference<List<ReadableItem>>,
    val presentation: LibraryPresentation,
)

private fun LibraryShelvesState.hasOnlyCurrentItems(
    state: AppUiState,
): Boolean {
    val itemIds = allItemIds()
    if (itemIds.isEmpty()) return true
    val currentItemIds = state.items.mapTo(hashSetOf(), ReadableItem::id)
    return itemIds.all(currentItemIds::contains)
}

private fun LibraryShelvesState.allItemIds(): List<String> = buildList {
    continueReading.forEach { item -> add(item.item.id) }
    nextInSeries.forEach { item ->
        add(item.item.id)
        add(item.currentItem.id)
    }
    recentlyFinished.forEach { item -> add(item.item.id) }
}

private const val RETAINED_VIEWS_LIMIT = 4
