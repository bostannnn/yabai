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
 * Each grid item's length for the Pages filter, the Pages sort and the tile's caption: a series
 * tile's is all its issues together, anything else's its own. Items whose length is unknown are
 * left out.
 */
internal fun buildPageCountByItemId(
    input: LibraryDerivedStateInput,
    sourceIndex: LibrarySourceIndex,
    sourceItems: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): Map<String, Int> = buildMap {
    sourceItems.forEach { item ->
        val issues = if (input.isSeriesBrowseActive) {
            null
        } else {
            when {
                item.isKavitaSeriesContainer() -> item.remoteId?.let(sourceIndex.issueGroupsFor(SourceType.KAVITA)::get)
                item.isStumpSeriesContainer() -> item.remoteId?.let(sourceIndex.issueGroupsFor(SourceType.STUMP)::get)
                else -> null
            }
        }
        val pageCount = if (issues.isNullOrEmpty()) {
            libraryItemPageCount(item, progressByItemId[item.id])
        } else {
            issues.mapNotNull { issue -> libraryItemPageCount(issue, progressByItemId[issue.id]) }
                .takeIf(List<Int>::isNotEmpty)
                ?.sum()
        }
        pageCount?.let { put(item.id, it) }
    }
}
