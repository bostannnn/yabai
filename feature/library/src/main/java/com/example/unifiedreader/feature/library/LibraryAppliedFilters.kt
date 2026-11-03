package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState

/** One applied filter value as the filter row shows it: "EPUB", "In progress", an author. */
internal data class LibraryAppliedFilter(
    val panel: LibraryFilterPanel,
    val label: String,
    /** The filter state with only this value removed, for the chip's ×. */
    val without: LibraryFilterState,
)

/**
 * Every value [state] applies within [panels], panel by panel in the order given, each panel's
 * values in a stable order (the enum order for formats, statuses and locations, by label for the
 * rest).
 */
internal fun appliedLibraryFilters(
    state: LibraryFilterState,
    panels: List<LibraryFilterPanel>,
    libraryTitlesById: Map<String, String>,
): List<LibraryAppliedFilter> = panels.flatMap { panel ->
    when (panel) {
        LibraryFilterPanel.Format -> state.formats.sortedBy { it.ordinal }.map { format ->
            LibraryAppliedFilter(panel, format.name, state.copy(formats = state.formats - format))
        }
        LibraryFilterPanel.ReadingStatus -> state.readingStatuses.sortedBy { it.ordinal }.map { status ->
            LibraryAppliedFilter(panel, status.label, state.copy(readingStatuses = state.readingStatuses - status))
        }
        LibraryFilterPanel.Pages -> listOfNotNull(
            state.pages?.let { range -> LibraryAppliedFilter(panel, libraryPageRangeChipLabel(range), state.copy(pages = null)) },
        )
        LibraryFilterPanel.Location -> state.fileLocations.sortedBy { it.ordinal }.map { location ->
            LibraryAppliedFilter(panel, location.label, state.copy(fileLocations = state.fileLocations - location))
        }
        LibraryFilterPanel.Library -> state.libraries
            .map { id -> id to (libraryTitlesById[id] ?: id) }
            .sortedBy { (_, title) -> title.lowercase() }
            .map { (id, title) -> LibraryAppliedFilter(panel, title, state.copy(libraries = state.libraries - id)) }
        LibraryFilterPanel.Author -> state.authors.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(authors = state.authors - value))
        }
        LibraryFilterPanel.Series -> state.series.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(series = state.series - value))
        }
        LibraryFilterPanel.Publisher -> state.publishers.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(publishers = state.publishers - value))
        }
        LibraryFilterPanel.Genre -> state.genres.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(genres = state.genres - value))
        }
        LibraryFilterPanel.Tag -> state.tags.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(tags = state.tags - value))
        }
        LibraryFilterPanel.Language -> state.languages.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(languages = state.languages - value))
        }
        LibraryFilterPanel.SetIn -> state.places.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(places = state.places - value))
        }
        LibraryFilterPanel.When -> state.times.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(times = state.times - value))
        }
        LibraryFilterPanel.Mood -> state.moods.byLabel().map { value ->
            LibraryAppliedFilter(panel, value, state.copy(moods = state.moods - value))
        }
    }
}

private fun Set<String>.byLabel(): List<String> = sortedBy(String::lowercase)

/** How many values [state] applies within [panels]: the Filters chip's badge. */
fun libraryAppliedFilterCount(state: LibraryFilterState, panels: List<LibraryFilterPanel>): Int =
    appliedLibraryFilters(state, panels, libraryTitlesById = emptyMap()).size
