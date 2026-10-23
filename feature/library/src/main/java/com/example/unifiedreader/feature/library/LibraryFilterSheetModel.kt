package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterFacetCounts
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.authorFacetKey

// What the filter sheet shows, apart from how it draws it: the open chip groups, the facet rows'
// summaries and the facet pages' grouped values.

/** Short choices stay open in the sheet as chips; the long lists open as a page of their own. */
internal val LibraryFilterPanel.opensAsPage: Boolean
    get() = when (this) {
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Location -> false
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Language -> true
    }

/** The facet's name in a sentence: "Search 13 authors". */
internal val LibraryFilterPanel.pluralLabel: String
    get() = when (this) {
        LibraryFilterPanel.Format -> "formats"
        LibraryFilterPanel.Library -> "libraries"
        LibraryFilterPanel.Location -> "locations"
        LibraryFilterPanel.ReadingStatus -> "statuses"
        LibraryFilterPanel.Author -> "authors"
        LibraryFilterPanel.Series -> "series"
        LibraryFilterPanel.Publisher -> "publishers"
        LibraryFilterPanel.Genre -> "genres"
        LibraryFilterPanel.Tag -> "tags"
        LibraryFilterPanel.Language -> "languages"
    }

/** One chip of an open group. [count] is null when the facets were not counted. */
internal data class LibraryFilterChipOption(
    val key: String,
    val label: String,
    val count: Int?,
    val selected: Boolean,
    /** The filter state with this chip toggled. */
    val toggled: LibraryFilterState,
)

/**
 * The chips of an open group: every value the source has (so the chips stay put while other
 * filters change), plus any still-selected value it no longer has. Empty for a facet page.
 */
internal fun libraryFilterChipOptions(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
): List<LibraryFilterChipOption> {
    val counts = facets.counts.takeUnless { it.isEmpty }
    return when (panel) {
        LibraryFilterPanel.ReadingStatus -> LibraryReadingStatus.entries.map { status ->
            LibraryFilterChipOption(
                key = status.name,
                label = status.label,
                count = counts?.let { it.readingStatuses[status] ?: 0 },
                selected = status in state.readingStatuses,
                toggled = state.copy(readingStatuses = state.readingStatuses.toggle(status)),
            )
        }
        LibraryFilterPanel.Format -> ((counts?.formats?.keys ?: facets.formats) + state.formats)
            .distinct()
            .sortedBy { it.ordinal }
            .map { format ->
                LibraryFilterChipOption(
                    key = format.name,
                    label = format.name,
                    count = counts?.let { it.formats[format] ?: 0 },
                    selected = format in state.formats,
                    toggled = state.copy(formats = state.formats.toggle(format)),
                )
            }
        LibraryFilterPanel.Location -> ((counts?.fileLocations?.keys ?: facets.fileLocations) + state.fileLocations)
            .distinct()
            .sortedBy { it.ordinal }
            .map { location ->
                LibraryFilterChipOption(
                    key = location.name,
                    label = location.label,
                    count = counts?.let { it.fileLocations[location] ?: 0 },
                    selected = location in state.fileLocations,
                    toggled = state.copy(fileLocations = state.fileLocations.toggle(location)),
                )
            }
        else -> emptyList()
    }
}

/** The values a facet page's panel has selected (library ids for [LibraryFilterPanel.Library]). */
internal fun LibraryFilterState.selectedValues(panel: LibraryFilterPanel): Set<String> = when (panel) {
    LibraryFilterPanel.Library -> libraries
    LibraryFilterPanel.Author -> authors
    LibraryFilterPanel.Series -> series
    LibraryFilterPanel.Publisher -> publishers
    LibraryFilterPanel.Genre -> genres
    LibraryFilterPanel.Tag -> tags
    LibraryFilterPanel.Language -> languages
    LibraryFilterPanel.Format,
    LibraryFilterPanel.Location,
    LibraryFilterPanel.ReadingStatus -> emptySet()
}

/** The state with one value of a facet page's panel toggled. */
internal fun LibraryFilterState.toggling(panel: LibraryFilterPanel, value: String): LibraryFilterState = when (panel) {
    LibraryFilterPanel.Library -> copy(libraries = libraries.toggle(value))
    LibraryFilterPanel.Author -> copy(authors = authors.toggle(value))
    LibraryFilterPanel.Series -> copy(series = series.toggle(value))
    LibraryFilterPanel.Publisher -> copy(publishers = publishers.toggle(value))
    LibraryFilterPanel.Genre -> copy(genres = genres.toggle(value))
    LibraryFilterPanel.Tag -> copy(tags = tags.toggle(value))
    LibraryFilterPanel.Language -> copy(languages = languages.toggle(value))
    LibraryFilterPanel.Format,
    LibraryFilterPanel.Location,
    LibraryFilterPanel.ReadingStatus -> this
}

private fun LibraryFilterFacets.values(panel: LibraryFilterPanel): List<String> = when (panel) {
    LibraryFilterPanel.Library -> libraries
    LibraryFilterPanel.Author -> authors
    LibraryFilterPanel.Series -> series
    LibraryFilterPanel.Publisher -> publishers
    LibraryFilterPanel.Genre -> genres
    LibraryFilterPanel.Tag -> tags
    LibraryFilterPanel.Language -> languages
    LibraryFilterPanel.Format,
    LibraryFilterPanel.Location,
    LibraryFilterPanel.ReadingStatus -> emptyList()
}

private fun LibraryFilterPanel.valueLabel(value: String, libraryTitlesById: Map<String, String>): String =
    if (this == LibraryFilterPanel.Library) libraryTitlesById[value] ?: value else value

/**
 * What a facet row shows at its end: null for nothing picked ("Any"), else the picks by name,
 * two at most and then how many more ("Русский, English +2").
 */
internal fun libraryFacetSelectionSummary(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    libraryTitlesById: Map<String, String>,
): String? {
    val labels = state.selectedValues(panel)
        .map { value -> panel.valueLabel(value, libraryTitlesById) }
        .sortedBy(String::lowercase)
    if (labels.isEmpty()) return null
    val shown = labels.take(FacetSummaryMaxNames).joinToString(", ")
    val more = labels.size - FacetSummaryMaxNames
    return if (more > 0) "$shown +$more" else shown
}

/** One value on a facet page. [count] is null when the facets were not counted. */
internal data class LibraryFacetPageValue(
    val value: String,
    val label: String,
    val count: Int?,
    val selected: Boolean,
)

/** A titled run of a facet page: "Selected" on top, then one per letter. */
internal data class LibraryFacetPageGroup(
    val title: String,
    val values: List<LibraryFacetPageValue>,
)

/**
 * A facet page's list: the picks pinned under "Selected", then every other value under its
 * letter (an author under the surname's), in the facets' order. A [query] keeps only the values
 * whose name contains it.
 */
internal fun libraryFacetPageGroups(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    query: String = "",
): List<LibraryFacetPageGroup> {
    val selectedValues = state.selectedValues(panel)
    val counts = facets.counts.takeUnless { it.isEmpty }
    val label = { value: String -> panel.valueLabel(value, libraryTitlesById) }
    val letterKey = { value: String ->
        val name = label(value)
        if (panel == LibraryFilterPanel.Author) name.authorFacetKey() else name
    }
    val trimmedQuery = query.trim()
    val values = searchableFacetDisplayValues(
        values = facets.values(panel),
        selectedValues = selectedValues,
        valueKey = { value -> label(value).lowercase() },
        valueLabel = label,
        letterKey = letterKey,
    )
        .filter { value -> trimmedQuery.isEmpty() || label(value).contains(trimmedQuery, ignoreCase = true) }
        .map { value ->
            LibraryFacetPageValue(
                value = value,
                label = label(value),
                count = counts?.countOf(panel, value),
                selected = value in selectedValues,
            )
        }
    val (picked, rest) = values.partition(LibraryFacetPageValue::selected)
    val groups = mutableListOf<LibraryFacetPageGroup>()
    if (picked.isNotEmpty()) groups += LibraryFacetPageGroup(SelectedGroupTitle, picked)
    rest.groupBy { value -> facetLetter(letterKey(value.value)) }
        .forEach { (letter, letterValues) -> groups += LibraryFacetPageGroup(letter, letterValues) }
    return groups
}

/** How many values a facet page has, for its search field ("Search 13 authors"). */
internal fun libraryFacetValueCount(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
): Int = (facets.values(panel).map(String::lowercase) + state.selectedValues(panel).map(String::lowercase))
    .distinct()
    .size

/**
 * How many items the filters leave, read off the facet counts: every item has one format, and
 * each format is counted under all the other filters, so the formats the filter keeps (all of them
 * when it has none) add up to the matches. The paged grid can't say this itself: without
 * placeholders its item count is the pages loaded so far. Null while the counts are not built.
 */
internal fun libraryFilterMatchingCount(state: LibraryFilterState, counts: LibraryFilterFacetCounts): Int? {
    if (counts.formats.isEmpty()) return null
    return counts.formats
        .filterKeys { format -> state.formats.isEmpty() || format in state.formats }
        .values
        .sum()
}

/** "Showing 14 of 21", or "Showing all 21" while nothing is filtered out. */
internal fun libraryFilterShowingLabel(shownCount: Int, totalCount: Int?): String = when {
    totalCount == null -> "Showing $shownCount"
    shownCount >= totalCount -> "Showing all $totalCount"
    else -> "Showing $shownCount of $totalCount"
}

/** The sheet's closing button: "Show 14 books". */
internal fun libraryFilterShowButtonLabel(shownCount: Int): String =
    if (shownCount == 1) "Show 1 book" else "Show $shownCount books"

private fun facetLetter(key: String): String {
    val first = key.trim().firstOrNull() ?: return OtherLetterTitle
    return if (first.isLetter()) first.uppercase() else OtherLetterTitle
}

internal const val SelectedGroupTitle = "Selected"
private const val OtherLetterTitle = "#"
private const val FacetSummaryMaxNames = 2
