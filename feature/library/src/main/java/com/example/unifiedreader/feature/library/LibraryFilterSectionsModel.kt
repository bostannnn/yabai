package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.libraryEraPile
import com.example.unifiedreader.core.model.libraryPlaceCountry
import com.example.unifiedreader.ui.components.appCount
import kotlin.math.roundToInt
import kotlin.math.sqrt

// What the filter sheet's richer sections show, apart from how they draw it: Set in's places and When's
// piles (on their pages), the chips of the values with most books, a row's top values, and Find's matches.

/** One row of Set in: a place, how many books it has, and whether it is a city under its picked country. */
internal data class LibraryFilterPlaceRow(
    val value: String,
    val count: Int?,
    val selected: Boolean,
    val city: Boolean,
)

/**
 * Set in's page: the places with most books, [limit] of them, plus any picked one; a city counts under
 * its country, so it shows on its own only while picked without it. A picked country lists its cities
 * under it.
 */
internal fun libraryFilterPlaceRows(
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    limit: Int = PlaceRowLimit,
): List<LibraryFilterPlaceRow> {
    val counts = facets.counts.takeUnless { it.isEmpty }?.places
    val countOf = { value: String -> counts?.get(value.lowercase()) }
    val values = (facets.places + state.places).distinctBy(String::lowercase)
    val picked = state.places
    val byCount = compareByDescending<String> { value -> countOf(value) ?: 0 }.thenBy(String::lowercase)
    val tops = values
        .filter { value ->
            val country = libraryPlaceCountry(value)
            country == null || country !in values || (value in picked && country !in picked)
        }
        .sortedWith(byCount)
    val shown = (tops.take(limit) + tops.filter { it in picked }).distinct().sortedWith(byCount)
    return shown.flatMap { value ->
        val row = LibraryFilterPlaceRow(value = value, count = countOf(value), selected = value in picked, city = false)
        if (value !in picked) return@flatMap listOf(row)
        listOf(row) + values
            .filter { city -> libraryPlaceCountry(city) == value }
            .sortedWith(byCount)
            .map { city -> LibraryFilterPlaceRow(value = city, count = countOf(city), selected = city in picked, city = true) }
    }
}

/** One row of When: a pile (a century or decade) and how many of its books stand as spines. */
internal data class LibraryFilterPileRow(
    val value: String,
    val count: Int?,
    val selected: Boolean,
    val spines: Int,
    val order: Int,
)

/**
 * When's page: every pile in time order. A named period has no pile; it shows only while picked
 * (found with Find), after the piles. A pile's spines are as many as the square root of its count
 * allows, [PileMaxSpines] at most.
 */
internal fun libraryFilterPileRows(state: LibraryFilterState, facets: LibraryFilterFacets): List<LibraryFilterPileRow> {
    val counts = facets.counts.takeUnless { it.isEmpty }?.times
    val values = (facets.times + state.times).distinctBy(String::lowercase)
    return values
        .mapNotNull { value ->
            val pile = libraryEraPile(value)
            if (pile == null && value !in state.times) return@mapNotNull null
            val count = counts?.get(value.lowercase())
            LibraryFilterPileRow(
                value = value,
                count = count,
                selected = value in state.times,
                spines = sqrt((count ?: 1).toFloat()).roundToInt().coerceIn(1, PileMaxSpines),
                order = pile?.order ?: Int.MAX_VALUE,
            )
        }
        .sortedWith(compareBy<LibraryFilterPileRow> { it.order }.thenBy { it.value.lowercase() })
}

/** When's named periods (World War II, Cold War), which have no pile: the ones not picked, most books first. */
internal fun libraryFilterNamedPeriods(state: LibraryFilterState, facets: LibraryFilterFacets): List<LibraryFilterPileRow> {
    val counts = facets.counts.takeUnless { it.isEmpty }?.times
    val picked = state.times.map(String::lowercase).toSet()
    return facets.times
        .filter { value -> libraryEraPile(value) == null && value.lowercase() !in picked }
        .distinctBy(String::lowercase)
        .map { value ->
            LibraryFilterPileRow(value = value, count = counts?.get(value.lowercase()), selected = false, spines = 0, order = Int.MAX_VALUE)
        }
        .sortedWith(compareByDescending<LibraryFilterPileRow> { it.count ?: 0 }.thenBy { it.value.lowercase() })
}

/**
 * The chips of a top-chips section (Library, Language, Genre, About, Mood): the values with most books,
 * [limit] of them, then any picked one past them, each with its count and its name (a library's title).
 */
internal fun libraryFilterTopChips(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    limit: Int,
    libraryTitlesById: Map<String, String> = emptyMap(),
): List<LibraryFilterChipOption> {
    val counts = facets.counts.takeUnless { it.isEmpty }
    val selected = state.selectedValues(panel)
    val values = (facets.values(panel) + selected).distinctBy(String::lowercase)
        .sortedWith(compareByDescending<String> { value -> counts?.countOf(panel, value) ?: 0 }.thenBy(String::lowercase))
    return (values.take(limit) + values.filter { it in selected }).distinct().map { value ->
        LibraryFilterChipOption(
            key = value.lowercase(),
            label = panel.valueLabel(value, libraryTitlesById),
            count = counts?.countOf(panel, value),
            selected = value in selected,
            toggled = state.toggling(panel, value),
        )
    }
}

/** How many chips a top-chips section shows before "All N". */
internal val LibraryFilterPanel.topChipLimit: Int
    get() = when (this) {
        LibraryFilterPanel.Library -> LibraryChipLimit
        LibraryFilterPanel.Language -> LanguageChipLimit
        LibraryFilterPanel.Tag -> AboutChipLimit
        LibraryFilterPanel.Genre -> GenreChipLimit
        else -> OtherChipLimit
    }

/**
 * The line under a row's name: its values with most books and their counts, three at most
 * ("Ryu Murakami 5 · Yukio Mishima 4 · Edogawa Rampo 4"); null when nothing counted them.
 */
internal fun libraryFilterTopValuesLine(
    panel: LibraryFilterPanel,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    limit: Int = TopValuesLimit,
): String? {
    val counts = facets.counts.takeUnless { it.isEmpty } ?: return null
    return facets.values(panel)
        .map { value -> value to counts.countOf(panel, value) }
        .filter { (_, count) -> count > 0 }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        .take(limit)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ") { (value, count) -> "${panel.valueLabel(value, libraryTitlesById)} ${appCount(count)}" }
}

/** One match of Find: a value of [panel], where the words matched in its label, and whether it is picked. */
internal data class LibraryFilterFindMatch(
    val panel: LibraryFilterPanel,
    val value: String,
    val label: String,
    val count: Int?,
    val selected: Boolean,
    val matchStart: Int,
    val matchEnd: Int,
)

/** Find's matches under one section, most books first. */
internal data class LibraryFilterFindGroup(
    val panel: LibraryFilterPanel,
    val matches: List<LibraryFilterFindMatch>,
)

/**
 * Find across every section that lists values: a value matches when a word of its label starts with
 * [query] ("war" finds "Cold War" and "Ruth Ware", not "Edward"). Sections in [panels]' order, each
 * with at most [perSection] matches; picked values first.
 */
internal fun libraryFilterFindGroups(
    query: String,
    panels: List<LibraryFilterPanel>,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    perSection: Int = FindPerSection,
): List<LibraryFilterFindGroup> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val counts = facets.counts.takeUnless { it.isEmpty }
    return panels.filter { it.opensAsPage }.mapNotNull { panel ->
        val selected = state.selectedValues(panel)
        val matches = (facets.values(panel) + selected).distinctBy(String::lowercase).mapNotNull { value ->
            val label = panel.valueLabel(value, libraryTitlesById)
            val start = wordStartMatch(label, needle) ?: return@mapNotNull null
            LibraryFilterFindMatch(
                panel = panel,
                value = value,
                label = label,
                count = counts?.countOf(panel, value),
                selected = value in selected,
                matchStart = start,
                matchEnd = start + needle.length,
            )
        }
            .sortedWith(
                compareByDescending<LibraryFilterFindMatch> { it.selected }
                    .thenByDescending { it.count ?: 0 }
                    .thenBy { it.label.lowercase() },
            )
            .take(perSection)
        matches.takeIf { it.isNotEmpty() }?.let { LibraryFilterFindGroup(panel, it) }
    }
}

private fun wordStartMatch(label: String, needle: String): Int? {
    var index = label.indexOf(needle, ignoreCase = true)
    while (index >= 0) {
        if (index == 0 || !label[index - 1].isLetterOrDigit()) return index
        index = label.indexOf(needle, startIndex = index + 1, ignoreCase = true)
    }
    return null
}

/** A book's reading status from the progress under its cover, for the sections counted on screen. */
internal fun libraryFilterReadingStatus(progress: ItemProgressSummary?): LibraryReadingStatus = when {
    progress == null -> LibraryReadingStatus.NOT_STARTED
    progress.isComplete -> LibraryReadingStatus.FINISHED
    progress.hasStarted -> LibraryReadingStatus.IN_PROGRESS
    else -> LibraryReadingStatus.NOT_STARTED
}

internal const val PlaceRowLimit = 6
internal const val PileMaxSpines = 14
private const val LibraryChipLimit = 6
private const val LanguageChipLimit = 5
private const val AboutChipLimit = 8
private const val GenreChipLimit = 10
private const val OtherChipLimit = 20
private const val TopValuesLimit = 3
private const val FindPerSection = 8
