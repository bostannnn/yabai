package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

data class LibraryDerivedStateInput(
    val selectedSource: SourceType,
    val libraries: List<Library>,
    val items: List<ReadableItem>,
    val selectedLibraryFilters: LibraryFilterState = LibraryFilterState(),
    val selectedLibrarySearchQuery: String = "",
    val selectedLibrarySortMode: LibrarySortMode = defaultLibrarySortMode,
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val ratingByItemId: Map<String, Int> = emptyMap(),
    val downloadedRemoteItemIds: Set<String> = emptySet(),
    val locallyAvailableRemoteItemIds: Set<String> = emptySet(),
    val downloadingRemoteItemIds: Set<String> = emptySet(),
    val selectedKavitaSeriesKey: String? = null,
    val selectedStumpSeriesKey: String? = null,
    val orderedItemScope: LibraryOrderedItemScope? = null,
)

data class LibraryDerivedState(
    val sourceItems: List<ReadableItem> = emptyList(),
    val selectedLibraryFilterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    val selectedSourceLibraryTitlesById: Map<String, String> = emptyMap(),
    val displayProgressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val displayRatingByItemId: Map<String, LibraryRatingBadgeState> = emptyMap(),
    val remoteDownloadActionAvailability: RemoteDownloadActionAvailability = RemoteDownloadActionAvailability(),
    val visibleItems: List<ReadableItem> = emptyList(),
    val libraryBrowseContextKey: String = "",
)
