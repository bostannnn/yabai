package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import javax.inject.Inject

class LibrarySeriesContinuationResolver @Inject constructor() {
    fun nextUnreadAfter(
        currentItem: ReadableItem,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): ReadableItem? = nextAfter(
        currentItem = currentItem,
        items = items,
        progressByItemId = progressByItemId,
        includeInProgress = true,
    )

    fun nextUnstartedAfter(
        currentItem: ReadableItem,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): ReadableItem? = nextAfter(
        currentItem = currentItem,
        items = items,
        progressByItemId = progressByItemId,
        includeInProgress = false,
    )

    fun nextUnstartedAfterByCurrentItem(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): Map<String, ReadableItem> = nextAfterByCurrentItem(
        items = items,
        progressByItemId = progressByItemId,
        includeInProgress = false,
    )

    /**
     * Like [nextUnstartedAfterByCurrentItem] but only resolves continuations for [seedItemIds] (and
     * returns entries only for them). It groups/sorts just the series those seeds belong to instead of
     * the whole catalog, so a "Next in series" shelf over a large library doesn't pay to order every
     * series. Equivalent to the full call filtered to the seeds, since each seed's complete series is
     * still included.
     */
    fun nextUnstartedAfterForItems(
        seedItemIds: Set<String>,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): Map<String, ReadableItem> {
        if (seedItemIds.isEmpty()) return emptyMap()
        val seedSeriesKeys = HashSet<SeriesIssueGroupKey>()
        items.forEach { item ->
            if (item.id in seedItemIds) {
                item.seriesIssueGroupKey()?.let(seedSeriesKeys::add)
            }
        }
        if (seedSeriesKeys.isEmpty()) return emptyMap()
        val relevantItems = items.filter { item -> item.seriesIssueGroupKey() in seedSeriesKeys }
        return nextAfterByCurrentItem(
            items = relevantItems,
            progressByItemId = progressByItemId,
            includeInProgress = false,
        ).filterKeys { itemId -> itemId in seedItemIds }
    }

    private fun nextAfter(
        currentItem: ReadableItem,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
        includeInProgress: Boolean,
    ): ReadableItem? = nextAfterByCurrentItem(
        items = currentItem.seriesIssues(items),
        progressByItemId = progressByItemId,
        includeInProgress = includeInProgress,
    )[currentItem.id]

    private fun nextAfterByCurrentItem(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
        includeInProgress: Boolean,
    ): Map<String, ReadableItem> {
        val nextByCurrentItemId = mutableMapOf<String, ReadableItem>()
        items.seriesIssueGroups().forEach { orderedIssues ->
            var nextEligibleIssue: ReadableItem? = null
            orderedIssues.asReversed().forEach { item ->
                nextEligibleIssue?.let { nextIssue ->
                    nextByCurrentItemId[item.id] = nextIssue
                }
                if (item.isContinuationCandidate(progressByItemId, includeInProgress)) {
                    nextEligibleIssue = item
                }
            }
        }
        return nextByCurrentItemId
    }
}

private fun ReadableItem.seriesIssues(
    items: List<ReadableItem>,
): List<ReadableItem> {
    val seriesKey = seriesIssueGroupKey() ?: return emptyList()
    return items
        .filter { item -> item.seriesIssueGroupKey() == seriesKey }
        .sortedInCanonicalSeriesIssueOrder()
}

private fun List<ReadableItem>.seriesIssueGroups(): Collection<List<ReadableItem>> = asSequence()
    .mapNotNull { item ->
        val seriesKey = item.seriesIssueGroupKey() ?: return@mapNotNull null
        seriesKey to item
    }
    .groupBy(
        keySelector = { entry -> entry.first },
        valueTransform = { entry -> entry.second },
    )
    .values
    .map { issues -> issues.sortedInCanonicalSeriesIssueOrder() }

private data class SeriesIssueGroupKey(
    val sourceType: SourceType,
    val key: String,
)

private fun ReadableItem.seriesIssueGroupKey(): SeriesIssueGroupKey? = when (sourceType) {
    SourceType.KAVITA -> kavitaSeriesGroupKey()?.let { seriesKey ->
        SeriesIssueGroupKey(sourceType, seriesKey)
    }

    SourceType.STUMP -> stumpSeriesGroupKey()?.let { seriesKey ->
        SeriesIssueGroupKey(sourceType, seriesKey)
    }

    SourceType.GRIMMORY,
    SourceType.LOCAL -> if (kind == ReadableItemKind.COMIC) {
        localMetadataSeriesKey()?.let { seriesKey ->
            SeriesIssueGroupKey(sourceType, "$libraryId::$seriesKey")
        }
    } else {
        null
    }
}

private fun ReadableItem.isContinuationCandidate(
    progressByItemId: Map<String, ItemProgressSummary>,
    includeInProgress: Boolean,
): Boolean {
    val progress = progressByItemId[id]
    return when {
        progress == null -> true
        progress.isComplete -> false
        includeInProgress -> true
        else -> false
    }
}

private fun ReadableItem.localMetadataSeriesKey(): String? =
    comicMetadata?.series
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.lowercase()
