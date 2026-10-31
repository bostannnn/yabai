package com.example.unifiedreader.core.model

/**
 * The Pages filter: items between [min] and [max] pages, both inclusive, null for no bound.
 * [includeUnknown] keeps the items whose length the library doesn't know as well.
 */
data class LibraryPageRange(
    val min: Int? = null,
    val max: Int? = null,
    val includeUnknown: Boolean = false,
) {
    operator fun contains(pageCount: Int): Boolean =
        (min == null || pageCount >= min) && (max == null || pageCount <= max)

    /** Whether an item this long fits; one whose length is unknown (null) fits when [includeUnknown] keeps it. */
    fun matches(pageCount: Int?): Boolean = if (pageCount == null) includeUnknown else pageCount in this
}

/**
 * An item's length in pages as the library knows it, the same the book page shows: a comic's or a
 * PDF's page count, an EPUB's printed pages (only Grimmory's metadata has them). Null when unknown.
 */
fun ReadableItem.libraryPageCount(): Int? =
    if (format == FormatType.EPUB) printedPageCount() else pageCount?.takeIf { count -> count > 0 }

/**
 * The page counts the Pages slider stops at, closer together where most books and issues are.
 * The last one means "and longer": a range that ends there has no upper bound.
 */
val LibraryPageStops: List<Int> = listOf(0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000)

/**
 * How long a view's items are, for the Pages filter: [bars] counts the items with a page count
 * between each pair of neighbouring [LibraryPageStops] (the last bar holds everything from its
 * stop up), [unknown] the items with none.
 */
data class LibraryPageHistogram(
    val bars: List<Int> = emptyList(),
    val unknown: Int = 0,
) {
    /** Whether any item has a known length: without one the Pages filter has nothing to offer. */
    val hasKnownLengths: Boolean
        get() = bars.any { count -> count > 0 }
}

fun buildLibraryPageHistogram(
    items: List<ReadableItem>,
    pageCountOf: (ReadableItem) -> Int?,
): LibraryPageHistogram {
    val bars = IntArray(LibraryPageStops.size - 1)
    var unknown = 0
    items.forEach { item ->
        val pageCount = pageCountOf(item)
        if (pageCount == null) unknown++ else bars[libraryPageBarIndex(pageCount)]++
    }
    return LibraryPageHistogram(bars = bars.toList(), unknown = unknown)
}

/** The histogram bar a page count falls in. */
fun libraryPageBarIndex(pageCount: Int): Int =
    LibraryPageStops.indexOfLast { stop -> pageCount >= stop }.coerceIn(0, LibraryPageStops.size - 2)
