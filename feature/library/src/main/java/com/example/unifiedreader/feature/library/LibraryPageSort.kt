package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryPageStops
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPageCount

// The Pages sort on the grid: the rail's page-range sections and the length under each cover.

/** A grid item's length: the derived one (a series tile's is all its issues), else its own. */
internal fun Map<String, Int>.pageCountOf(item: ReadableItem): Int? = this[item.id] ?: item.libraryPageCount()

/**
 * The rail section a length falls in: the stop its range starts from ("150" holds 150 to 199),
 * "1k" from 1,000 up, "?" when the length is unknown.
 */
internal fun libraryPageSection(pageCount: Int?): String {
    if (pageCount == null) return UnknownPageSection
    val stop = LibraryPageStops.last { stop -> pageCount >= stop }
    return if (stop >= 1_000) "${stop / 1_000}k" else stop.toString()
}

/** An item's rail section: its letter under a letter sort, its page range under Pages. */
internal fun ReadableItem.librarySortSection(
    sortMode: LibrarySortMode,
    pageCount: Int?,
): String? = if (sortMode == LibrarySortMode.PAGES) libraryPageSection(pageCount) else sortFilterLetter(sortMode)

/**
 * The rail's sections in order: letters alphabetically ("#" first), page ranges as the sorted grid
 * meets them, shortest first and "?" last.
 */
internal fun librarySortRailSections(
    sectionKeys: List<String>,
    sortMode: LibrarySortMode,
): List<String> = if (sortMode == LibrarySortMode.PAGES) sectionKeys.distinct() else alphabetNavigationSections(sectionKeys)

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

internal const val UnknownPageSection = "?"
