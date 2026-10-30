package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.libraryPageCount

/**
 * An item's length in pages, as the book page shows it and the Pages filter reads it: what the
 * library knows, else what its reader counted once opened (not for an EPUB, whose reader counts
 * chapters and screens, not printed pages).
 */
internal fun libraryItemPageCount(
    item: ReadableItem,
    progress: ItemProgressSummary?,
): Int? = item.libraryPageCount()
    ?: progress?.pageCount?.takeIf { count -> count > 0 && item.format != FormatType.EPUB }

/**
 * Each grid item's page counts for the Pages filter: a series tile's are its issues' (so it fits
 * when one issue does), anything else's its own. Empty where no length is known.
 */
internal fun buildPageCountsByItemId(
    input: LibraryDerivedStateInput,
    sourceIndex: LibrarySourceIndex,
    sourceItems: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): Map<String, List<Int>> = sourceItems.associate { item ->
    val issues = if (input.isSeriesBrowseActive) {
        null
    } else {
        when {
            item.isKavitaSeriesContainer() -> item.remoteId?.let(sourceIndex.issueGroupsFor(SourceType.KAVITA)::get)
            item.isStumpSeriesContainer() -> item.remoteId?.let(sourceIndex.issueGroupsFor(SourceType.STUMP)::get)
            else -> null
        }
    }
    val pageCounts = issues
        ?.takeIf(List<ReadableItem>::isNotEmpty)
        ?.mapNotNull { issue -> libraryItemPageCount(issue, progressByItemId[issue.id]) }
        ?.distinct()
        ?: listOfNotNull(libraryItemPageCount(item, progressByItemId[item.id]))
    item.id to pageCounts
}
