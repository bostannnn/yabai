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

    /**
     * Whether an item with these [pageCounts] fits: one of them is in the range (a series tile
     * carries its issues' counts), or it has none and [includeUnknown] keeps it.
     */
    fun matches(pageCounts: List<Int>): Boolean =
        if (pageCounts.isEmpty()) includeUnknown else pageCounts.any { count -> count in this }
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
 * stop up), [unknown] the items with none. An item with several counts (a series' issues) counts
 * once in every bar one of them falls in.
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
    pageCountsOf: (ReadableItem) -> List<Int>,
): LibraryPageHistogram {
    val bars = IntArray(LibraryPageStops.size - 1)
    val barsOfItem = BooleanArray(bars.size)
    var unknown = 0
    items.forEach { item ->
        val pageCounts = pageCountsOf(item)
        if (pageCounts.isEmpty()) {
            unknown++
            return@forEach
        }
        barsOfItem.fill(false)
        pageCounts.forEach { count -> barsOfItem[libraryPageBarIndex(count)] = true }
        barsOfItem.forEachIndexed { index, hit -> if (hit) bars[index]++ }
    }
    return LibraryPageHistogram(bars = bars.toList(), unknown = unknown)
}

/** The histogram bar a page count falls in. */
fun libraryPageBarIndex(pageCount: Int): Int =
    LibraryPageStops.indexOfLast { stop -> pageCount >= stop }.coerceIn(0, LibraryPageStops.size - 2)
