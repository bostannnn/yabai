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
    LibraryFilterPanel.Author -> filterState.authors.size
    LibraryFilterPanel.Series -> filterState.series.size
    LibraryFilterPanel.Publisher -> filterState.publishers.size
    LibraryFilterPanel.Genre -> filterState.genres.size
    LibraryFilterPanel.Tag -> filterState.tags.size
    LibraryFilterPanel.Language -> filterState.languages.size
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
    LibraryFilterPanel.Author -> filterFacets.authors.isNotEmpty() || filterState.authors.isNotEmpty()
    LibraryFilterPanel.Series -> filterFacets.series.isNotEmpty() || filterState.series.isNotEmpty()
    LibraryFilterPanel.Publisher -> filterFacets.publishers.isNotEmpty() || filterState.publishers.isNotEmpty()
    LibraryFilterPanel.Genre -> filterFacets.genres.isNotEmpty() || filterState.genres.isNotEmpty()
    LibraryFilterPanel.Tag -> filterFacets.tags.isNotEmpty() || filterState.tags.isNotEmpty()
    LibraryFilterPanel.Language -> filterFacets.languages.isNotEmpty() || filterState.languages.isNotEmpty()
}
