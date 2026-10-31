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

/** The rows Browse shows until you change them, in order. A row whose field no book has stays hidden. */
val DefaultLibraryBrowseRows: List<LibraryBrowseRow> = listOf(
    LibraryBrowseRow.SET_IN,
    LibraryBrowseRow.WHEN,
    LibraryBrowseRow.ABOUT,
    LibraryBrowseRow.GENRE,
    LibraryBrowseRow.MOOD,
    LibraryBrowseRow.SHELF,
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

/** A bar on Browse's timeline: how many books are set in that stretch of time. */
data class LibraryEraBar(
    val slot: LibraryEraSlot,
    val count: Int,
)

/** A region on Browse's landing page: how many books are set there and its places, most books first. */
data class LibraryPlaceGroup(
    val region: LibraryPlaceRegion,
    val count: Int,
    val places: List<LibraryBrowseFacet>,
)

/** The values an item has in [row]. [readingStatus] answers Your reading. */
fun ReadableItem.libraryBrowseValues(
    row: LibraryBrowseRow,
    readingStatus: LibraryReadingStatus? = null,
): List<String> = facetMetadata().browseValues(row, readingStatus)

/**
 * Browse over [items]: the ones that have every [picked] chip (each chip narrows), and the chips of
 * each of [rows] counted among them. A chip on When also matches the tags its timeline bar holds,
 * so "Middle Ages" picks a book tagged "Medieval".
 */
fun libraryBrowse(
    items: List<ReadableItem>,
    picked: List<LibraryBrowseChip>,
    rows: List<LibraryBrowseRow> = DefaultLibraryBrowseRows,
    readingStatusProvider: (ReadableItem) -> LibraryReadingStatus? = { null },
): LibraryBrowseResult {
    val wantedRows = (rows + picked.map(LibraryBrowseChip::row)).distinct()
    val valuesByItem = items.map { item ->
        val facets = item.facetMetadata()
        val status = if (LibraryBrowseRow.READING in wantedRows) readingStatusProvider(item) else null
        item to wantedRows.associateWith { row -> facets.browseValues(row, status) }
    }
    val matching = valuesByItem.filter { (_, values) ->
        picked.all { chip -> values[chip.row].orEmpty().any { value -> chip.matches(value) } }
    }
    val facets = rows.associateWith { row -> countValues(matching.map { (_, values) -> values[row].orEmpty() }) }
        .filterValues { facets -> facets.isNotEmpty() }
    return LibraryBrowseResult(items = matching.map { (item, _) -> item }, facets = facets)
}

/** Browse's timeline over [items], in time order; a book counts once in each bar it falls in. */
fun libraryEraTimeline(items: List<ReadableItem>): List<LibraryEraBar> {
    val counts = mutableMapOf<LibraryEraSlot, Int>()
    items.forEach { item ->
        item.facetMetadata().browseValues(LibraryBrowseRow.WHEN, null)
            .mapNotNullTo(mutableSetOf(), ::libraryEraSlot)
            .forEach { slot -> counts[slot] = (counts[slot] ?: 0) + 1 }
    }
    return counts.map { (slot, count) -> LibraryEraBar(slot, count) }.sortedBy { bar -> bar.slot.order }
}

/** The When chips that aren't a timeline bar ("World War II", "20th century"), most books first. */
fun libraryEraPeriods(facets: List<LibraryBrowseFacet>): List<LibraryBrowseFacet> =
    facets.filter { facet -> libraryEraSlot(facet.value) == null }

/** Set in's places grouped by region, the regions with most books first; a book counts once per region. */
fun libraryPlaceGroups(items: List<ReadableItem>): List<LibraryPlaceGroup> {
    val regionItems = mutableMapOf<LibraryPlaceRegion, Int>()
    val placeCounts = mutableMapOf<LibraryPlaceRegion, MutableList<List<String>>>()
    items.forEach { item ->
        val places = item.facetMetadata().browseValues(LibraryBrowseRow.SET_IN, null)
        places.groupBy { place -> libraryPlaceRegion(place) ?: LibraryPlaceRegion.ELSEWHERE }
            .forEach { (region, regionPlaces) ->
                regionItems[region] = (regionItems[region] ?: 0) + 1
                placeCounts.getOrPut(region) { mutableListOf() } += regionPlaces
            }
    }
    return regionItems.map { (region, count) ->
        LibraryPlaceGroup(region = region, count = count, places = countValues(placeCounts[region].orEmpty()))
    }.sortedWith(compareByDescending<LibraryPlaceGroup> { group -> group.count }.thenBy { group -> group.region.ordinal })
}

private fun LibraryBrowseChip.matches(value: String): Boolean =
    value.equals(this.value, ignoreCase = true) ||
        (row == LibraryBrowseRow.WHEN && libraryEraSlot(value)?.label.equals(this.value, ignoreCase = true))

private fun ReadableItemFacetMetadata.browseValues(
    row: LibraryBrowseRow,
    readingStatus: LibraryReadingStatus?,
): List<String> = when (row) {
    LibraryBrowseRow.SET_IN -> tags.filter { tag -> libraryTagKind(tag) == LibraryTagKind.PLACE }
    LibraryBrowseRow.WHEN -> tags.filter { tag -> libraryTagKind(tag) == LibraryTagKind.ERA }
    LibraryBrowseRow.ABOUT -> tags.filter { tag -> libraryTagKind(tag) == LibraryTagKind.THEME }
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

private fun countValues(valueLists: List<List<String>>): List<LibraryBrowseFacet> {
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
