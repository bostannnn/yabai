package com.example.unifiedreader.core.model

/**
 * How many items each filter value would leave in the library, as the filter sheet shows them:
 * counted with every other dimension's filters applied and the value's own dimension left out,
 * so picking a second value in a dimension adds to the first. Text values (and library ids) are
 * keyed by their lowercase form, the way [LibraryFilterFacets] dedupes them; read them through
 * [countOf]. The enum maps hold every value the unfiltered items have, at 0 when the other
 * filters leave none, so the sheet's chips stay put as filters change.
 */
data class LibraryFilterFacetCounts(
    val formats: Map<FormatType, Int> = emptyMap(),
    val fileLocations: Map<LibraryFileLocation, Int> = emptyMap(),
    val readingStatuses: Map<LibraryReadingStatus, Int> = emptyMap(),
    val libraries: Map<String, Int> = emptyMap(),
    val authors: Map<String, Int> = emptyMap(),
    val series: Map<String, Int> = emptyMap(),
    val publishers: Map<String, Int> = emptyMap(),
    val genres: Map<String, Int> = emptyMap(),
    val tags: Map<String, Int> = emptyMap(),
    val languages: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() && fileLocations.isEmpty() && readingStatuses.isEmpty() &&
            libraries.isEmpty() && authors.isEmpty() && series.isEmpty() && publishers.isEmpty() &&
            genres.isEmpty() && tags.isEmpty() && languages.isEmpty()

    /** The count of a text [value] (a library id for [LibraryFilterPanel.Library]), 0 when it has none. */
    fun countOf(panel: LibraryFilterPanel, value: String): Int {
        val counts = when (panel) {
            LibraryFilterPanel.Library -> libraries
            LibraryFilterPanel.Author -> authors
            LibraryFilterPanel.Series -> series
            LibraryFilterPanel.Publisher -> publishers
            LibraryFilterPanel.Genre -> genres
            LibraryFilterPanel.Tag -> tags
            LibraryFilterPanel.Language -> languages
            LibraryFilterPanel.Format,
            LibraryFilterPanel.Location,
            LibraryFilterPanel.ReadingStatus -> return 0
        }
        return counts[value.lowercase()] ?: 0
    }
}

/**
 * The distinct values of one text dimension across [items] (first spelling seen wins, as
 * `distinctBy(lowercase)` keeps it) and how many items carry each one; an item that lists a value
 * twice counts once.
 */
internal class TextFacetTally(
    val values: List<String>,
    val counts: Map<String, Int>,
)

internal inline fun tallyTextFacet(
    items: List<ReadableItem>,
    valuesOf: (ReadableItem) -> List<String>,
): TextFacetTally {
    val firstSpellingByKey = LinkedHashMap<String, String>()
    val counts = HashMap<String, Int>()
    val keysOfItem = HashSet<String>()
    items.forEach { item ->
        keysOfItem.clear()
        valuesOf(item).forEach { value ->
            val key = value.lowercase()
            if (keysOfItem.add(key)) {
                firstSpellingByKey.putIfAbsent(key, value)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
    }
    return TextFacetTally(values = firstSpellingByKey.values.toList(), counts = counts)
}

/** Counts per enum value, with every value of [domain] present (0 when [items] have none). */
internal inline fun <T : Enum<T>> tallyEnumFacet(
    items: List<ReadableItem>,
    domain: Collection<T>,
    valueOf: (ReadableItem) -> T?,
): Map<T, Int> {
    val counts = LinkedHashMap<T, Int>()
    domain.sortedBy { it.ordinal }.forEach { value -> counts[value] = 0 }
    items.forEach { item ->
        val value = valueOf(item) ?: return@forEach
        counts[value] = (counts[value] ?: 0) + 1
    }
    return counts
}
