package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import javax.inject.Inject

class LibraryShelvesStateComputer @Inject constructor(
    private val seriesContinuationResolver: LibrarySeriesContinuationResolver,
) {
    fun compute(
        input: LibraryShelvesInput,
    ): LibraryShelvesState {
        val sourceItems = input.items.filter { item ->
            item.sourceType == input.selectedSource
        }
        val continueReading = buildContinueReadingShelf(
            items = sourceItems,
            progressByItemId = input.progressByItemId,
        )
        val nextInSeries = buildNextInSeriesShelf(
            selectedSource = input.selectedSource,
            items = sourceItems,
            progressByItemId = input.progressByItemId,
        )
        val recentlyFinished = buildRecentlyFinishedShelf(
            items = sourceItems,
            progressByItemId = input.progressByItemId,
        )
        return LibraryShelvesState(
            continueReading = continueReading,
            nextInSeries = nextInSeries,
            recentlyFinished = recentlyFinished,
        )
    }

    private fun buildContinueReadingShelf(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): ContinueReadingShelfItem? = items.mapNotNull { item ->
        val progress = progressByItemId[item.id] ?: return@mapNotNull null
        if (progress.isComplete) return@mapNotNull null
        ContinueReadingShelfItem(
            item = item,
            progress = progress,
        )
    }.maxByOrNull { entry ->
        entry.progress.lastReadAtEpochMillis
    }

    private fun buildNextInSeriesShelf(
        selectedSource: SourceType,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): List<NextInSeriesShelfItem> {
        val nextUnstartedByCurrentItemId = seriesContinuationResolver.nextUnstartedAfterByCurrentItem(
            items = items,
            progressByItemId = progressByItemId,
        )
        return items
            .asSequence()
            .filter { item -> item.kind == ReadableItemKind.COMIC }
            .mapNotNull { currentItem ->
                val progress = progressByItemId[currentItem.id] ?: return@mapNotNull null
                if (!progress.hasActivity) return@mapNotNull null
                val nextItem = nextUnstartedByCurrentItemId[currentItem.id] ?: return@mapNotNull null
                NextInSeriesShelfItem(
                    item = nextItem,
                    currentItem = currentItem,
                    lastActivityAtEpochMillis = progress.lastReadAtEpochMillis,
                )
            }
            .groupBy { entry ->
                entry.seriesGroupKey(selectedSource)
            }
            .mapNotNull { (seriesKey, entries) ->
                seriesKey?.let {
                    entries.maxByOrNull(NextInSeriesShelfItem::lastActivityAtEpochMillis)
                }
            }
            .sortedByDescending(NextInSeriesShelfItem::lastActivityAtEpochMillis)
            .take(NextInSeriesShelfLimit)
    }

    private fun buildRecentlyFinishedShelf(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): List<RecentlyFinishedShelfItem> = items.mapNotNull { item ->
        val progress = progressByItemId[item.id] ?: return@mapNotNull null
        if (!progress.isComplete) return@mapNotNull null
        if (progress.lastReadAtEpochMillis <= 0L) return@mapNotNull null
        RecentlyFinishedShelfItem(
            item = item,
            progress = progress,
        )
    }
        .sortedByDescending { entry -> entry.progress.lastReadAtEpochMillis }
        .take(RecentlyFinishedShelfLimit)
}

private val ItemProgressSummary.isComplete: Boolean
    get() = isFinished || fraction >= 0.999f

private val ItemProgressSummary.hasActivity: Boolean
    get() = lastReadAtEpochMillis > 0L || fraction > 0f || isFinished

private fun NextInSeriesShelfItem.seriesGroupKey(
    selectedSource: SourceType,
): String? = when (selectedSource) {
    SourceType.KAVITA -> item.kavitaSeriesGroupKey()
    SourceType.STUMP -> item.stumpSeriesGroupKey()
    SourceType.GRIMMORY,
    SourceType.LOCAL -> item.localSeriesGroupKey()
}

private fun ReadableItem.localSeriesGroupKey(): String? {
    val series = comicMetadata?.series
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return "$libraryId::${series.lowercase()}"
}

private const val NextInSeriesShelfLimit = 4
private const val RecentlyFinishedShelfLimit = 4
