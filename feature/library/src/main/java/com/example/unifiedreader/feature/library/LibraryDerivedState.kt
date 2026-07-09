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
    val selectedKavitaCollectionId: String? = null,
    val selectedKavitaCollectionSeriesKeys: Set<String> = emptySet(),
    val orderedItemScope: LibraryOrderedItemScope? = null,
    // When true, the grid is served by Room paging, so the in-memory last-read/date-added sort for
    // `visibleItems` is redundant (Room already sorts the window). The computer then leaves
    // `visibleItems` as the unsorted source set — same membership, no per-progress-update re-sort.
    // A pure function of the other keyed inputs (source/sort/filters/search/scope), so it doesn't
    // affect cache identity.
    val pagedGridActive: Boolean = false,
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
