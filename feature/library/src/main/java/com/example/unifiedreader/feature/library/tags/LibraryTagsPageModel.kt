package com.example.unifiedreader.feature.library.tags

import com.example.unifiedreader.ui.components.appCount
import androidx.compose.runtime.Immutable
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagEntry
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryEraSlot
import com.example.unifiedreader.core.model.libraryTagEntries

/** What Settings › Tags shows for one source: the tags waiting under New, and the sorted ones. */
@Immutable
internal data class LibraryTagsPageModel(
    val entries: List<LibraryTagEntry>,
) {
    val new: List<LibraryTagEntry> = entries.filter(LibraryTagEntry::isNew)
    val sorted: List<LibraryTagEntry> = entries.filterNot(LibraryTagEntry::isNew)

    /** Too many to read as rows (the first time): grouped by the app's guess, with Accept all. */
    val reviewsGuesses: Boolean get() = new.size > NewRowLimit
}

internal fun libraryTagsPageModel(
    items: List<ReadableItem>,
    source: SourceType,
    sorting: LibraryTagSorting,
): LibraryTagsPageModel = LibraryTagsPageModel(libraryTagEntries(items.filter { it.sourceType == source }, sorting))

/** The places among [entries] by region, in the regions' order, each region's places most books first. */
internal fun libraryTagsByRegion(
    entries: List<LibraryTagEntry>,
    placement: (LibraryTagEntry) -> LibraryTagChoice,
): List<Pair<LibraryPlaceRegion, List<LibraryTagEntry>>> = entries
    .filter { entry -> placement(entry).kind == LibraryTagKind.PLACE }
    .groupBy { entry -> placement(entry).region ?: LibraryPlaceRegion.ELSEWHERE }
    .toList()
    .sortedBy { (region, _) -> region.ordinal }

/** The times among [entries]: the ones with a timeline bar in time order, then the periods, most books first. */
internal fun libraryTagsByTime(entries: List<LibraryTagEntry>): Pair<List<LibraryTagEntry>, List<LibraryTagEntry>> {
    val (onTimeline, periods) = entries.partition { entry -> libraryEraSlot(entry.name) != null }
    return onTimeline.sortedBy { entry -> libraryEraSlot(entry.name)?.order ?: 0 } to periods
}

/** A New row's line: how many books, and the app's guess or that it has none. */
internal fun libraryTagNewRowLine(entry: LibraryTagEntry): String {
    val books = bookCountLabel(entry.count)
    val region = entry.guess?.region
    return when (entry.field) {
        LibraryTagField.TAG -> when (entry.guess?.kind) {
            LibraryTagKind.PLACE -> "$books · Looks like a place · ${(region ?: LibraryPlaceRegion.ELSEWHERE).label}"
            LibraryTagKind.ERA -> "$books · Looks like a time"
            else -> "$books · No guess, in About for now"
        }
        LibraryTagField.LOCATION -> if (region != null) "Location · $books · Looks like ${region.label}" else "Location · $books · No region guess"
    }
}

/** The one action a New row offers: take the guess, keep a tag in About, or open the sheet for a Location's region. */
internal fun libraryTagNewRowAction(entry: LibraryTagEntry): String = when {
    entry.guess != null -> "Accept"
    entry.field == LibraryTagField.LOCATION -> "Pick a region"
    else -> "Keep in About"
}

/**
 * The Tags row's line in the settings hub: "264 tags to check" while nothing is sorted anywhere,
 * "3 new tags" while some wait, else what the page is for.
 */
fun libraryTagsSummary(items: List<ReadableItem>, sortingBySource: Map<SourceType, LibraryTagSorting>): String {
    val bySource = items.groupBy(ReadableItem::sourceType)
    val waiting = bySource.entries.sumOf { (source, sourceItems) ->
        libraryTagEntries(sourceItems, sortingBySource[source] ?: LibraryTagSorting()).count(LibraryTagEntry::isNew)
    }
    val anySorted = sortingBySource.values.any { sorting -> sorting.tags.isNotEmpty() || sorting.locationRegions.isNotEmpty() }
    return when {
        waiting == 0 -> "Where Filters shows each tag"
        !anySorted -> if (waiting == 1) "1 tag to check" else "$waiting tags to check"
        else -> if (waiting == 1) "1 new tag" else "$waiting new tags"
    }
}

/** More waiting than this, and New groups them by the app's guess instead of listing rows. */
internal const val NewRowLimit = 10

/** "1 book", "1,474 books". */
internal fun bookCountLabel(count: Int): String = if (count == 1) "1 book" else "${appCount(count)} books"
