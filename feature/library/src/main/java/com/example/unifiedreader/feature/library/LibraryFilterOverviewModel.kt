package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageHistogram
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryPageStops
import com.example.unifiedreader.core.model.libraryEraPile

// What the overview's sections show, apart from how they draw it: the values of a tiles or checks section,
// the letters Author and Publisher open at, When's timeline, and the four lengths.

/**
 * The values a tiles or checks section shows: Reading status, Format and Location all of theirs; the others
 * their values with most books, [limit] of them plus any picked one.
 */
internal fun libraryFilterSectionOptions(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    limit: Int,
): List<LibraryFilterChipOption> = when (panel) {
    LibraryFilterPanel.ReadingStatus,
    LibraryFilterPanel.Format,
    LibraryFilterPanel.Location -> libraryFilterChipOptions(panel, state, facets)
    else -> libraryFilterTopChips(panel, state, facets, limit, libraryTitlesById)
}

/**
 * A tiles section's tiles and its "N more" tile: up to [maxTiles] values fit; past that, the ones with most
 * books less one, then a tile naming how many more there are and the first of them.
 */
internal data class LibraryFilterTiles(
    val tiles: List<LibraryFilterChipOption>,
    val moreCount: Int,
    val moreNames: List<String>,
)

internal fun libraryFilterTiles(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    maxTiles: Int,
): LibraryFilterTiles {
    val all = libraryFilterSectionOptions(panel, state, facets, libraryTitlesById, limit = Int.MAX_VALUE)
    // Mood keeps every value: its words are short and there is no page to open for the rest.
    if (panel == LibraryFilterPanel.Mood || all.size <= maxTiles) return LibraryFilterTiles(all, moreCount = 0, moreNames = emptyList())
    val shown = (all.take(maxTiles - 1) + all.filter(LibraryFilterChipOption::selected)).distinctBy(LibraryFilterChipOption::key)
    val rest = all.filter { option -> shown.none { it.key == option.key } }
    return LibraryFilterTiles(shown, moreCount = rest.size, moreNames = rest.take(MoreTileNames).map(LibraryFilterChipOption::label))
}

/** The letters a section's page has a group for (an author under the surname's), the ones its letter grid opens. */
internal fun libraryFilterLetters(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
): Set<String> = libraryFacetPageGroups(panel, state.clearing(panel), facets, libraryTitlesById)
    .map(LibraryFacetPageGroup::title)
    .toSet()

/** One stop of When's timeline: a pile, or a named period placed by its years. */
internal data class LibraryFilterTimelineRow(
    val value: String,
    val count: Int?,
    val selected: Boolean,
    val period: Boolean,
)

/**
 * When as one line in time order: every pile, and the named periods where their first year falls (just
 * after that year's pile). A period with no known year comes after the rest, most books first.
 */
internal fun libraryFilterTimelineRows(state: LibraryFilterState, facets: LibraryFilterFacets): List<LibraryFilterTimelineRow> {
    val counts = facets.counts.takeUnless { it.isEmpty }?.times
    val picked = state.times.map(String::lowercase).toSet()
    val values = (facets.times + state.times).distinctBy(String::lowercase)
    val placed = values.map { value ->
        val pile = libraryEraPile(value)
        val century = libraryWholeCenturyOrder(value)
        val row = LibraryFilterTimelineRow(
            value = value,
            count = counts?.get(value.lowercase()),
            selected = value.lowercase() in picked,
            period = pile == null && century == null,
        )
        row to (pile?.order?.toDouble() ?: century ?: libraryNamedPeriodOrder(value))
    }
    val (known, unknown) = placed.partition { (_, order) -> order != null }
    return known.sortedWith(compareBy<Pair<LibraryFilterTimelineRow, Double?>> { it.second }.thenBy { it.first.value.lowercase() }).map { it.first } +
        unknown.map { it.first }.sortedWith(compareByDescending<LibraryFilterTimelineRow> { it.count ?: 0 }.thenBy { it.value.lowercase() })
}

/**
 * Where a named period sits among the piles: just after the pile its first year falls in. The piles'
 * orders come from `libraryEraSlot`: Ancient 100, Middle Ages 200, Renaissance 300, the 1500s to 1800s
 * 1000 + their century, from 1900 each decade 2000 + its year.
 */
internal fun libraryNamedPeriodOrder(label: String): Double? {
    val year = NamedPeriodStartYears[label.trim().lowercase()] ?: return null
    return when {
        year >= 1900 -> 2000.0 + year / 10 * 10 + 0.5
        year >= 1500 -> 1000.0 + (year / 100 + 1) + 0.5
        year >= 1400 -> 300.5
        year >= 500 -> 200.5
        else -> 100.5
    }
}

/**
 * Where a whole century that has no pile of its own ("20th century", too wide beside its decades) sits: just
 * before its first pile, so the 20th century leads the 1900s.
 */
internal fun libraryWholeCenturyOrder(label: String): Double? {
    val match = WholeCenturyPattern.matchEntire(label.trim().lowercase()) ?: return null
    val century = match.groupValues[1].toInt()
    val firstYear = (century - 1) * 100
    return if (firstYear >= 1900) 2000.0 + firstYear - 0.5 else 1000.0 + century - 0.5
}

private val WholeCenturyPattern = Regex("""(\d{1,2})(?:st|nd|rd|th) century""")

/** The four lengths of the Length section, on the Pages slider's stops so their counts come from its bars. */
internal enum class LibraryLengthBucket(val label: String, val min: Int, val max: Int?, val pagesLabel: String) {
    Short("Short", 0, 149, "under 150"),
    Medium("Medium", 150, 299, "150–299"),
    Long("Long", 300, 499, "300–499"),
    VeryLong("Very long", 500, null, "500 and more"),
    ;

    /** How many books the histogram has in this length. */
    fun count(histogram: LibraryPageHistogram): Int = histogram.bars.withIndex().sumOf { (index, count) ->
        val start = LibraryPageStops[index]
        val inside = start >= min && (max == null || start <= max)
        if (inside) count else 0
    }
}

/** The lengths [range] covers exactly (a run of neighbours), or null when it is no run of them (a slider pick). */
internal fun libraryLengthBuckets(range: LibraryPageRange?): Set<LibraryLengthBucket>? {
    if (range == null || (range.min == null && range.max == null)) return emptySet()
    val entries = LibraryLengthBucket.entries
    val first = entries.indexOfFirst { it.min == (range.min ?: 0) }
    val last = entries.indexOfFirst { it.max == range.max }
    if (first < 0 || last < 0 || last < first) return null
    return entries.subList(first, last + 1).toSet()
}

/**
 * The range after a tap on [bucket]: a length picked drops (the run shrinks or ends), one not picked joins,
 * and the run stretches over any gap, since a range has no holes. A slider pick gives way to the tapped one.
 */
internal fun libraryLengthRangeToggling(range: LibraryPageRange?, bucket: LibraryLengthBucket): LibraryPageRange? {
    val current = libraryLengthBuckets(range).orEmpty()
    val next = if (bucket in current) current - bucket else current + bucket
    if (next.isEmpty()) return null
    val first = next.minBy { it.ordinal }
    val last = next.maxBy { it.ordinal }
    return LibraryPageRange(
        min = first.min.takeIf { it > 0 },
        max = last.max,
        includeUnknown = range?.includeUnknown ?: false,
    )
}

private const val MoreTileNames = 2

private val NamedPeriodStartYears: Map<String, Int> = mapOf(
    "heian period" to 794, "crusades" to 1096, "hundred years' war" to 1337, "tudor era" to 1485,
    "elizabethan era" to 1558, "edo period" to 1603, "thirty years' war" to 1618, "georgian era" to 1714,
    "enlightenment" to 1715, "industrial revolution" to 1760, "american revolution" to 1775,
    "french revolution" to 1789, "napoleonic wars" to 1803, "regency era" to 1811, "victorian era" to 1837,
    "american civil war" to 1861, "reconstruction era" to 1865, "wild west" to 1865, "meiji era" to 1868,
    "gilded age" to 1870, "belle époque" to 1871, "belle epoque" to 1871, "edwardian era" to 1901,
    "world war i" to 1914, "first world war" to 1914, "russian revolution" to 1917, "russian civil war" to 1918,
    "interwar period" to 1919, "roaring twenties" to 1920, "jazz age" to 1920, "soviet era" to 1922,
    "great depression" to 1929, "spanish civil war" to 1936, "world war ii" to 1939, "second world war" to 1939,
    "holocaust" to 1941, "cold war" to 1947, "korean war" to 1950, "vietnam war" to 1955, "space age" to 1957,
    "perestroika" to 1985,
)
