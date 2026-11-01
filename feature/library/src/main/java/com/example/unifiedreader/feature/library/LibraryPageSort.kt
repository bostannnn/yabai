package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPageCount

// The Pages sort on the grid: the length under each cover (the sort has no rail).

/** A grid item's length: the derived one (a series tile's is all its issues), else its own. */
internal fun Map<String, Int>.pageCountOf(item: ReadableItem): Int? = this[item.id] ?: item.libraryPageCount()

/**
 * The line under a cover while the grid is sorted by pages: "212 pages", a series "212 pages ·
 * 8 issues". Null when the length is unknown, which leaves the usual line.
 */
internal fun libraryLengthLabel(
    pageCount: Int?,
    seriesIssueCount: Int?,
): String? {
    pageCount ?: return null
    val pages = if (pageCount == 1) "1 page" else "${"%,d".format(pageCount)} pages"
    val issues = seriesIssueCount?.let { count -> if (count == 1) "1 issue" else "$count issues" }
    return listOfNotNull(pages, issues).joinToString(" · ")
}
