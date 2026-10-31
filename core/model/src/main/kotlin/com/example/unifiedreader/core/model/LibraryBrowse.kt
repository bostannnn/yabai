package com.example.unifiedreader.core.model

import java.util.Locale

/** A row of chips on Browse, named for the field of a book its chips come from. */
enum class LibraryBrowseRow(val label: String) {
    SET_IN("Set in"),
    WHEN("When"),
    ABOUT("About"),
    GENRE("Genre"),
    MOOD("Mood"),
    SHELF("Your shelves"),
    READING("Your reading"),
    AUTHOR("Author"),
    SERIES("Series"),
    PUBLISHER("Publisher"),
    LANGUAGE("Language"),
}

/**
 * The rows Browse shows until you change them, in order. A row whose field no book has stays hidden.
 * Your shelves is there to switch on: Lists already has them.
 */
val DefaultLibraryBrowseRows: List<LibraryBrowseRow> = listOf(
    LibraryBrowseRow.SET_IN,
    LibraryBrowseRow.WHEN,
    LibraryBrowseRow.ABOUT,
    LibraryBrowseRow.GENRE,
    LibraryBrowseRow.MOOD,
    LibraryBrowseRow.READING,
)

/** One picked value: "About · Espionage". */
data class LibraryBrowseChip(
    val row: LibraryBrowseRow,
    val value: String,
)

/** A chip Browse offers, with how many of the current books have it. */
data class LibraryBrowseFacet(
    val value: String,
    val count: Int,
)

/** What Browse shows for the picked chips: the books that have all of them and each row's chips among those books. */
data class LibraryBrowseResult(
    val items: List<ReadableItem>,
    /** Most books first, then by name; a row with no chips is left out. */
    val facets: Map<LibraryBrowseRow, List<LibraryBrowseFacet>>,
)

/** A pile on Browse's shelf: how many books are set in that stretch of time, and the first few of them. */
data class LibraryEraBar(
    val slot: LibraryEraSlot,
    val count: Int,
    val books: List<ReadableItem> = emptyList(),
)

/** The values an item has in [row], its tags placed by [sorting]. [readingStatus] answers Your reading. */
fun ReadableItem.libraryBrowseValues(
    row: LibraryBrowseRow,
    sorting: LibraryTagSorting,
    readingStatus: LibraryReadingStatus? = null,
): List<String> = facetMetadata().browseValues(row, readingStatus, sorting)

/**
 * Browse over [items]: the ones that have every [picked] chip (each chip narrows), and the chips of
 * each of [rows] counted among them, tags placed by [sorting]. A chip on When also matches the tags
 * its pile holds, so "Middle Ages" picks a book tagged "Medieval", and a country on Set in matches
 * its cities, so "England" picks a book tagged "London".
 */
fun libraryBrowse(
    items: List<ReadableItem>,
    picked: List<LibraryBrowseChip>,
    sorting: LibraryTagSorting,
    rows: List<LibraryBrowseRow> = DefaultLibraryBrowseRows,
    readingStatusProvider: (ReadableItem) -> LibraryReadingStatus? = { null },
): LibraryBrowseResult {
    val wantedRows = (rows + picked.map(LibraryBrowseChip::row)).distinct()
    val valuesByItem = items.map { item ->
        val facets = item.facetMetadata()
        val status = if (LibraryBrowseRow.READING in wantedRows) readingStatusProvider(item) else null
        item to wantedRows.associateWith { row -> facets.browseValues(row, status, sorting) }
    }
    val matching = valuesByItem.filter { (_, values) ->
        picked.all { chip -> values[chip.row].orEmpty().any { value -> chip.matches(value) } }
    }
    val facets = rows.associateWith { row -> countValues(matching.map { (_, values) -> values[row].orEmpty() }) }
        .filterValues { facets -> facets.isNotEmpty() }
    return LibraryBrowseResult(items = matching.map { (item, _) -> item }, facets = facets)
}

/**
 * Browse's shelf over [items], in time order; a book counts once in each pile it falls in, and each
 * pile keeps up to [bookCount] of its books.
 */
fun libraryEraTimeline(items: List<ReadableItem>, sorting: LibraryTagSorting, bookCount: Int = 0): List<LibraryEraBar> {
    val counts = mutableMapOf<LibraryEraSlot, Int>()
    val books = mutableMapOf<LibraryEraSlot, MutableList<ReadableItem>>()
    items.forEach { item ->
        item.facetMetadata().browseValues(LibraryBrowseRow.WHEN, null, sorting)
            .mapNotNullTo(mutableSetOf(), ::libraryEraSlot)
            .forEach { slot ->
                counts[slot] = (counts[slot] ?: 0) + 1
                val kept = books.getOrPut(slot) { mutableListOf() }
                if (kept.size < bookCount) kept += item
            }
    }
    return counts.map { (slot, count) -> LibraryEraBar(slot, count, books[slot].orEmpty()) }.sortedBy { bar -> bar.slot.order }
}

/**
 * Up to [perValue] of [items] for each value of [row], in the order given, keyed by [libraryTagKey]:
 * the books a value's covers show.
 */
fun libraryBrowseBooks(
    items: List<ReadableItem>,
    row: LibraryBrowseRow,
    sorting: LibraryTagSorting,
    perValue: Int,
): Map<String, List<ReadableItem>> {
    val books = linkedMapOf<String, MutableList<ReadableItem>>()
    items.forEach { item ->
        item.facetMetadata().browseValues(row, null, sorting).forEach { value ->
            val kept = books.getOrPut(libraryTagKey(value)) { mutableListOf() }
            if (kept.size < perValue && kept.lastOrNull() !== item) kept += item
        }
    }
    return books
}

/** The When chips that aren't a timeline bar ("World War II", "20th century"), most books first. */
fun libraryEraPeriods(facets: List<LibraryBrowseFacet>): List<LibraryBrowseFacet> =
    facets.filter { facet -> libraryEraSlot(facet.value) == null }

// A pile's label picks the eras it holds, and a country picks its cities.
private fun LibraryBrowseChip.matches(value: String): Boolean =
    value.equals(this.value, ignoreCase = true) ||
        (row == LibraryBrowseRow.WHEN && libraryEraSlot(value)?.label.equals(this.value, ignoreCase = true)) ||
        (row == LibraryBrowseRow.SET_IN && libraryPlaceCountry(value).equals(this.value, ignoreCase = true))

private fun ReadableItemFacetMetadata.browseValues(
    row: LibraryBrowseRow,
    readingStatus: LibraryReadingStatus?,
    sorting: LibraryTagSorting,
): List<String> = when (row) {
    // A Location is a place already; a tag is where you put it, About until then.
    LibraryBrowseRow.SET_IN -> (tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.PLACE } + locations)
        .distinctBy(::libraryTagKey)
    LibraryBrowseRow.WHEN -> tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.ERA }
    LibraryBrowseRow.ABOUT -> tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.THEME }
    LibraryBrowseRow.GENRE -> genres
    LibraryBrowseRow.MOOD -> moods
    LibraryBrowseRow.SHELF -> shelves
    LibraryBrowseRow.READING -> listOfNotNull(readingStatus?.libraryBrowseLabel)
    LibraryBrowseRow.AUTHOR -> (authors + writers).distinctBy { name -> name.lowercase(Locale.ROOT) }
    LibraryBrowseRow.SERIES -> series
    LibraryBrowseRow.PUBLISHER -> publishers
    LibraryBrowseRow.LANGUAGE -> languages
}

/** How Your reading names a status: the same words as the Reading status filter. */
val LibraryReadingStatus.libraryBrowseLabel: String
    get() = when (this) {
        LibraryReadingStatus.NOT_STARTED -> "Not started"
        LibraryReadingStatus.IN_PROGRESS -> "In progress"
        LibraryReadingStatus.FINISHED -> "Finished"
    }

internal fun countValues(valueLists: List<List<String>>): List<LibraryBrowseFacet> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    valueLists.forEach { values ->
        values.distinctBy { value -> value.lowercase(Locale.ROOT) }.forEach { value ->
            val key = value.lowercase(Locale.ROOT)
            val (name, count) = counts[key] ?: (value to 0)
            counts[key] = name to count + 1
        }
    }
    return counts.values
        .map { (name, count) -> LibraryBrowseFacet(value = name, count = count) }
        .sortedWith(compareByDescending<LibraryBrowseFacet> { facet -> facet.count }.thenBy { facet -> facet.value.lowercase(Locale.ROOT) })
}
