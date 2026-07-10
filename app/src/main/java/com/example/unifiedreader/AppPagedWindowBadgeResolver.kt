package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.deriveKavitaSeriesGrouping
import com.example.unifiedreader.core.model.deriveStumpSeriesGrouping
import com.example.unifiedreader.feature.library.LibraryWindowBadges
import com.example.unifiedreader.feature.library.computeLibraryWindowBadges
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

/**
 * Resolves the paged grid's per-card badges — read/progress, rating, and download status — for the
 * bounded set of grid roots currently on screen, from sources that are available before the full
 * catalog hydrates. Lets the paged path commit those badges *with* the grid instead of waiting ~1 s
 * for the in-memory [com.example.unifiedreader.feature.library.LibraryDerivedStateComputer] recompute.
 *
 * The badges are computed by [computeLibraryWindowBadges], which reuses the exact root-view rollups the
 * recompute uses, so the values match what the recompute will later publish — overlaying them onto the
 * (initially empty) recompute maps never flips a badge.
 */
class AppPagedWindowBadgeResolver @Inject constructor(
    private val readableItemLookup: ReadableItemLookup,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val readerSessionStore: ReaderSessionStore,
) {
    suspend fun resolve(
        source: SourceType,
        rootItems: List<ReadableItem>,
        ratingByItemId: Map<String, Int>,
        downloadingItemIds: Set<String>,
    ): LibraryWindowBadges {
        if (rootItems.isEmpty()) return LibraryWindowBadges()
        // Each container root's issues + each standalone root itself (bounded by window x series size).
        val memberItems = readableItemLookup.getMemberItemsForRoots(source, rootItems.map(ReadableItem::id))
        // Group members by series key using the same core derivation the in-memory index uses, so the
        // container ids the rollups key by match the grid roots exactly.
        val issuesBySeriesKey = when (source) {
            SourceType.KAVITA -> deriveKavitaSeriesGrouping(memberItems).issuesBySeriesKey
            SourceType.STUMP -> deriveStumpSeriesGrouping(memberItems).issuesBySeriesKey
            SourceType.GRIMMORY,
            SourceType.LOCAL -> emptyMap()
        }
        val windowItems = (rootItems + memberItems).distinctBy(ReadableItem::id)
        // Progress summaries from the reader-session store's raw progress — the same pre-hydration
        // source the shelf cold-start seed uses (see AppLibraryShelvesInputResolver).
        val progressByItemId = libraryStateAssembler.buildProgressSummaries(
            items = windowItems,
            progressByItemId = readerSessionStore.progressByItemIdFlow.value,
            sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
        )
        // Bounded downloads scan for exactly the window; the uiState downloaded set is refreshed over
        // the whole catalog, so it lags at cold start.
        val localState = libraryStateAssembler.loadRemoteItemLocalState(windowItems)
        return computeLibraryWindowBadges(
            source = source,
            rootItems = rootItems,
            issuesBySeriesKey = issuesBySeriesKey,
            memberItems = memberItems,
            progressByItemId = progressByItemId,
            ratingByItemId = ratingByItemId,
            downloadedItemIds = localState.downloadedItemIds,
            locallyAvailableItemIds = localState.locallyAvailableItemIds,
            downloadingItemIds = downloadingItemIds,
        )
    }
}
