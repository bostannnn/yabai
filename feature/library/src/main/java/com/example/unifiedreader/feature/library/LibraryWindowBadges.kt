package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesContainerId

/**
 * The per-card badge state for a bounded set of grid roots — the visible paged window. Computed from
 * bounded sources (reader-session progress, journal ratings, a window-scoped downloads scan, and
 * `library_root_members`) so the badges can commit *with* the paged grid at cold start instead of
 * waiting for the full in-memory [LibraryDerivedStateComputer] recompute (~1 s later).
 *
 * The maps reuse the exact rollup helpers the computer uses, so for the roots in the window they hold
 * the same values the recompute will later produce. Overlaying them onto the (initially empty)
 * recompute maps therefore never flips a badge — it only makes it appear earlier.
 */
data class LibraryWindowBadges(
    val displayProgressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val displayRatingByItemId: Map<String, LibraryRatingBadgeState> = emptyMap(),
    val downloadActionAvailability: RemoteDownloadActionAvailability = RemoteDownloadActionAvailability(),
    val locallyAvailableItemIds: Set<String> = emptySet(),
)

/**
 * Overlays these early window badges onto [base] (whose maps/sets come from the late in-memory
 * recompute), producing the [LibraryItemActionContext] a renderer should use: the recompute wins for
 * the progress map once it lands (its values match, so nothing flips), and the download sets union so
 * the badge shows as soon as either source has it. Empty window badges leave [base] unchanged. Used by
 * both the paged grid and the shelves so their read/download badges commit early and identically.
 */
fun LibraryItemActionContext.withWindowBadges(
    windowBadges: LibraryWindowBadges,
): LibraryItemActionContext = copy(
    progressByItemId = windowBadges.displayProgressByItemId + progressByItemId,
    locallyAvailableItemIds = locallyAvailableItemIds + windowBadges.locallyAvailableItemIds,
    downloadableItemIds = downloadableItemIds + windowBadges.downloadActionAvailability.downloadableItemIds,
    deletableDownloadedItemIds =
        deletableDownloadedItemIds + windowBadges.downloadActionAvailability.deletableDownloadedItemIds,
    downloadingItemIds = downloadingItemIds + windowBadges.downloadActionAvailability.activeDownloadingItemIds,
)

/**
 * Builds [LibraryWindowBadges] for [rootItems] (standalone items + synthetic series containers) using
 * the same root-view rollups as [LibraryDerivedStateComputer] (this covers the paged root view, which
 * is never a series browse):
 *  - progress: per-item summaries plus a "Finished" summary for containers whose members are all read;
 *  - rating: a per-item badge for completed items plus a series-average badge per container;
 *  - download availability: [buildRemoteDownloadActionAvailability] over the roots, with the members
 *    supplied as `allItems` so container action-item resolution matches the source-index path.
 *
 * @param issuesBySeriesKey members of each container root grouped by series key (`root.remoteId`).
 * @param memberItems the flattened member issues of the container roots (for download resolution).
 * @param progressByItemId progress summaries for the window items *and* all member issues.
 * @param ratingByItemId journal ratings by item id (window + members).
 */
fun computeLibraryWindowBadges(
    source: SourceType,
    rootItems: List<ReadableItem>,
    issuesBySeriesKey: Map<String, List<ReadableItem>>,
    memberItems: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    ratingByItemId: Map<String, Int>,
    downloadedItemIds: Set<String>,
    locallyAvailableItemIds: Set<String>,
    downloadingItemIds: Set<String>,
): LibraryWindowBadges {
    val seriesContainerId = source.seriesContainerIdOrNull()
    val displayProgressByItemId = if (seriesContainerId == null) {
        progressByItemId
    } else {
        val rootSeriesProgress = buildCompletedRootSeriesProgressByItemId(
            issuesBySeriesKey = issuesBySeriesKey,
            progressByItemId = progressByItemId,
            seriesContainerId = seriesContainerId,
        )
        if (rootSeriesProgress.isEmpty()) progressByItemId else progressByItemId + rootSeriesProgress
    }
    val displayRatingByItemId = buildMap {
        ratingByItemId.forEach { (itemId, rating) ->
            if (progressByItemId[itemId]?.isComplete == true) {
                put(itemId, itemRatingBadgeState(rating))
            }
        }
        if (seriesContainerId != null) {
            putSeriesAverageRatings(
                issuesBySeriesKey = issuesBySeriesKey,
                ratingByItemId = ratingByItemId,
                seriesContainerId = seriesContainerId,
            )
        }
    }
    val downloadActionAvailability = buildRemoteDownloadActionAvailability(
        sourceItems = rootItems,
        allItems = rootItems + memberItems,
        downloadedItemIds = downloadedItemIds,
        downloadingItemIds = downloadingItemIds,
    )
    return LibraryWindowBadges(
        displayProgressByItemId = displayProgressByItemId,
        displayRatingByItemId = displayRatingByItemId,
        downloadActionAvailability = downloadActionAvailability,
        locallyAvailableItemIds = locallyAvailableItemIds,
    )
}

private fun SourceType.seriesContainerIdOrNull(): ((String) -> String)? = when (this) {
    SourceType.KAVITA -> ::kavitaSeriesContainerId
    SourceType.STUMP -> ::stumpSeriesContainerId
    SourceType.GRIMMORY,
    SourceType.LOCAL -> null
}
