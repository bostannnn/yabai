package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import javax.inject.Inject

class LibrarySeriesContinuationResolver @Inject constructor() {
    fun nextUnreadAfter(
        currentItem: ReadableItem,
        items: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): ReadableItem? {
        val orderedIssues = currentItem.seriesIssues(items)
        val currentIndex = orderedIssues.indexOfFirst { item -> item.id == currentItem.id }
        if (currentIndex < 0) return null
        return orderedIssues
            .drop(currentIndex + 1)
            .firstOrNull { item -> progressByItemId[item.id]?.isComplete != true }
    }
}

private fun ReadableItem.seriesIssues(
    items: List<ReadableItem>,
): List<ReadableItem> = when (sourceType) {
    SourceType.KAVITA -> kavitaSeriesGroupKey()?.let { seriesKey ->
        items.filter { item ->
            !item.isKavitaSeriesContainer() && item.belongsToKavitaSeries(seriesKey)
        }
    }

    SourceType.STUMP -> stumpSeriesGroupKey()?.let { seriesKey ->
        items.filter { item ->
            !item.isStumpSeriesContainer() && item.belongsToStumpSeries(seriesKey)
        }
    }

    SourceType.GRIMMORY,
    SourceType.LOCAL -> localMetadataSeriesKey()?.let { seriesKey ->
        items.filter { item ->
            item.kind == ReadableItemKind.COMIC &&
                item.sourceType == sourceType &&
                item.libraryId == libraryId &&
                item.localMetadataSeriesKey() == seriesKey
        }
    }
}.orEmpty().sortedInCanonicalSeriesIssueOrder()

private fun ReadableItem.localMetadataSeriesKey(): String? =
    comicMetadata?.series
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.lowercase()
