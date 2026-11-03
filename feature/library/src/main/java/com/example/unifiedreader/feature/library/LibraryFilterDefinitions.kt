package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState

internal fun LibraryFilterPanel.selectedCount(
    filterState: LibraryFilterState,
): Int = when (this) {
    LibraryFilterPanel.Format -> filterState.formats.size
    LibraryFilterPanel.Library -> filterState.libraries.size
    LibraryFilterPanel.Location -> filterState.fileLocations.size
    LibraryFilterPanel.ReadingStatus -> filterState.readingStatuses.size
    LibraryFilterPanel.Pages -> if (filterState.pages != null) 1 else 0
    LibraryFilterPanel.Author -> filterState.authors.size
    LibraryFilterPanel.Series -> filterState.series.size
    LibraryFilterPanel.Publisher -> filterState.publishers.size
    LibraryFilterPanel.Genre -> filterState.genres.size
    LibraryFilterPanel.Tag -> filterState.tags.size
    LibraryFilterPanel.Language -> filterState.languages.size
    LibraryFilterPanel.SetIn -> filterState.places.size
    LibraryFilterPanel.When -> filterState.times.size
    LibraryFilterPanel.Mood -> filterState.moods.size
}

internal fun LibraryFilterPanel.isEnabled(
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
): Boolean = when (this) {
    LibraryFilterPanel.Format -> filterFacets.formats.isNotEmpty() || filterState.formats.isNotEmpty()
    LibraryFilterPanel.Library -> libraryTitlesById.isNotEmpty() &&
        (filterFacets.libraries.isNotEmpty() || filterState.libraries.isNotEmpty())
    LibraryFilterPanel.Location -> filterFacets.fileLocations.isNotEmpty() || filterState.fileLocations.isNotEmpty()
    LibraryFilterPanel.ReadingStatus -> true
    LibraryFilterPanel.Pages -> filterFacets.pages.hasKnownLengths || filterState.pages != null
    LibraryFilterPanel.Author -> filterFacets.authors.isNotEmpty() || filterState.authors.isNotEmpty()
    LibraryFilterPanel.Series -> filterFacets.series.isNotEmpty() || filterState.series.isNotEmpty()
    LibraryFilterPanel.Publisher -> filterFacets.publishers.isNotEmpty() || filterState.publishers.isNotEmpty()
    LibraryFilterPanel.Genre -> filterFacets.genres.isNotEmpty() || filterState.genres.isNotEmpty()
    LibraryFilterPanel.Tag -> filterFacets.tags.isNotEmpty() || filterState.tags.isNotEmpty()
    LibraryFilterPanel.Language -> filterFacets.languages.isNotEmpty() || filterState.languages.isNotEmpty()
    LibraryFilterPanel.SetIn -> filterFacets.places.isNotEmpty() || filterState.places.isNotEmpty()
    LibraryFilterPanel.When -> filterFacets.times.isNotEmpty() || filterState.times.isNotEmpty()
    LibraryFilterPanel.Mood -> filterFacets.moods.isNotEmpty() || filterState.moods.isNotEmpty()
}

/**
 * Whether the overview shows the panel: a section of where and how the books are kept, or of their
 * language, has nothing to choose with one value (one library, every book an EPUB), so it stays out
 * then, unless something is picked in it.
 */
internal fun LibraryFilterPanel.offersChoice(
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
): Boolean = selectedCount(filterState) > 0 || when (this) {
    LibraryFilterPanel.Library -> filterFacets.libraries.size > 1
    LibraryFilterPanel.Format -> filterFacets.formats.size > 1
    LibraryFilterPanel.Location -> filterFacets.fileLocations.size > 1
    LibraryFilterPanel.Language -> filterFacets.languages.distinctBy(String::lowercase).size > 1
    else -> true
}
