package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.applyLibrarySort
import com.example.unifiedreader.core.model.buildLibraryFilterFacets
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.ThemeMode

internal data class AppCatalogSnapshot(
    val libraries: List<Library>,
    val catalogItems: List<ReadableItem>,
    val items: List<ReadableItem>,
    val downloadedGrimmoryItemIds: Set<String>,
    val progressByItemId: Map<String, ItemProgressSummary>,
    val hydratedSources: Set<SourceType>,
    val themeMode: ThemeMode,
)

enum class LibraryMessageSeverity {
    Info,
    Error,
}

enum class TopLevelDestination {
    Library,
    Settings,
}

data class AppUiState(
    val topLevel: TopLevelDestination = TopLevelDestination.Library,
    val selectedSource: SourceType = SourceType.KAVITA,
    val selectedItemId: String? = null,
    val activeReader: FormatType? = null,
    val libraries: List<Library> = emptyList(),
    val items: List<ReadableItem> = emptyList(),
    val libraryFiltersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val librarySearchQueriesBySource: Map<SourceType, String> = emptyMap(),
    val librarySortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val downloadedGrimmoryItemIds: Set<String> = emptySet(),
    val downloadingGrimmoryItemIds: Set<String> = emptySet(),
    val libraryMessage: String? = null,
    val libraryMessageSeverity: LibraryMessageSeverity = LibraryMessageSeverity.Info,
    val hydratedSources: Set<SourceType> = setOf(SourceType.KAVITA),
    val themeMode: ThemeMode = ThemeMode.System,
    val grimmoryConnection: GrimmoryConnectionUiState = GrimmoryConnectionUiState(),
) {
    val sourceItems: List<ReadableItem>
        get() = items.filter { it.sourceType == selectedSource }

    val selectedLibraryFilters: LibraryFilterState
        get() = libraryFiltersBySource[selectedSource] ?: LibraryFilterState()

    val selectedLibrarySearchQuery: String
        get() = librarySearchQueriesBySource[selectedSource].orEmpty()

    val visibleLibraryErrorMessage: String?
        get() = libraryMessage.takeIf { libraryMessageSeverity == LibraryMessageSeverity.Error }

    val selectedLibraryFilterFacets: LibraryFilterFacets
        get() = buildLibraryFilterFacets(sourceItems, downloadedGrimmoryItemIds)

    val selectedLibrarySortMode: LibrarySortMode
        get() = librarySortsBySource[selectedSource] ?: LibrarySortMode.DATE_ADDED

    val isSelectedSourceHydrated: Boolean
        get() = selectedSource in hydratedSources

    val visibleItems: List<ReadableItem>
        get() = applyLibrarySort(
            items = applyLibrarySearch(
                items = applyLibraryFilters(sourceItems, selectedLibraryFilters, downloadedGrimmoryItemIds),
                query = selectedLibrarySearchQuery,
            ),
            sortMode = selectedLibrarySortMode,
            lastReadAtEpochMillisByItemId = progressByItemId.mapValues { (_, progress) ->
                progress.lastReadAtEpochMillis
            },
        )

    val selectedItem: ReadableItem?
        get() = items.firstOrNull { it.id == selectedItemId }
}
