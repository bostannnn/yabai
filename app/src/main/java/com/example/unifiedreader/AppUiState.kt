package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.RemoteDownloadActionAvailability
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import com.example.unifiedreader.feature.library.defaultLibrarySortMode
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import com.example.unifiedreader.feature.settings.ThemeMode

internal data class AppCatalogSnapshot(
    val libraries: List<Library>,
    val catalogItems: List<ReadableItem>,
    val items: List<ReadableItem>,
    val downloadedRemoteItemIds: Set<String>,
    val locallyAvailableRemoteItemIds: Set<String>,
    val progressByItemId: Map<String, ItemProgressSummary>,
    val hydratedSources: Set<SourceType>,
    val themeMode: ThemeMode,
)

enum class TopLevelDestination {
    Library,
    Settings,
}

data class AppUiState(
    val topLevel: TopLevelDestination = TopLevelDestination.Library,
    val sourceOrder: List<SourceType> = SourceType.entries.toList(),
    val selectedSource: SourceType = SourceType.KAVITA,
    val selectedItemId: String? = null,
    val activeReader: FormatType? = null,
    val libraries: List<Library> = emptyList(),
    val items: List<ReadableItem> = emptyList(),
    val selectedReaderItem: ReadableItem? = null,
    val libraryFiltersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val librarySearchQueriesBySource: Map<SourceType, String> = emptyMap(),
    val librarySortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val libraryFilterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>> = emptyMap(),
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val downloadedRemoteItemIds: Set<String> = emptySet(),
    val locallyAvailableRemoteItemIds: Set<String> = emptySet(),
    val downloadingRemoteItemIds: Set<String> = emptySet(),
    val hydratedSources: Set<SourceType> = setOf(SourceType.KAVITA),
    val themeMode: ThemeMode = ThemeMode.System,
    val comicReadingDirectionPreferences: ComicReadingDirectionPreferences = ComicReadingDirectionPreferences(),
    val kavitaConnection: KavitaConnectionUiState = KavitaConnectionUiState(),
    val stumpConnection: StumpConnectionUiState = StumpConnectionUiState(),
    val grimmoryConnection: GrimmoryConnectionUiState = GrimmoryConnectionUiState(),
    val selectedKavitaSeriesKey: String? = null,
    val selectedStumpSeriesKey: String? = null,
    val sourceItems: List<ReadableItem> = emptyList(),
    val selectedLibraryFilterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    val selectedSourceLibraryTitlesById: Map<String, String> = emptyMap(),
    val displayProgressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val remoteDownloadActionAvailability: RemoteDownloadActionAvailability = RemoteDownloadActionAvailability(),
    val visibleItems: List<ReadableItem> = emptyList(),
    val libraryBrowseContextKey: String = "",
) {
    val selectedLibraryFilters: LibraryFilterState
        get() = libraryFiltersBySource[selectedSource] ?: LibraryFilterState()

    val selectedLibrarySearchQuery: String
        get() = librarySearchQueriesBySource[selectedSource].orEmpty()

    val selectedLibrarySortMode: LibrarySortMode
        get() = librarySortsBySource[selectedSource] ?: defaultLibrarySortMode

    val selectedLibraryFilterPanels: List<LibraryFilterPanel>
        get() = resolveLibraryFilterPanels(
            sourceType = selectedSource,
            preferredPanels = libraryFilterPanelsBySource[selectedSource],
        )

    val isSelectedSourceHydrated: Boolean
        get() = selectedSource in hydratedSources

    val isSeriesBrowseActive: Boolean
        get() = when (selectedSource) {
            SourceType.KAVITA -> selectedKavitaSeriesKey != null
            SourceType.STUMP -> selectedStumpSeriesKey != null
            SourceType.GRIMMORY,
            SourceType.LOCAL -> false
        }

    val selectedItem: ReadableItem?
        get() = items.firstOrNull { it.id == selectedItemId } ?: selectedReaderItem?.takeIf { item ->
            item.id == selectedItemId
        }

    val selectedKavitaSeriesTitle: String?
        get() = selectedKavitaSeriesKey?.let { seriesKey ->
            items.firstOrNull { item -> item.belongsToKavitaSeries(seriesKey) }?.let { item ->
                item.comicMetadata?.series?.takeIf(String::isNotBlank) ?: item.title
            }
        }

    val selectedStumpSeriesTitle: String?
        get() = selectedStumpSeriesKey?.let { seriesKey ->
            items.firstOrNull { item -> item.belongsToStumpSeries(seriesKey) }?.let { item ->
                item.comicMetadata?.series?.takeIf(String::isNotBlank) ?: item.title
            }
        }

    val selectedSeriesTitle: String?
        get() = when (selectedSource) {
            SourceType.KAVITA -> selectedKavitaSeriesTitle
            SourceType.STUMP -> selectedStumpSeriesTitle
            SourceType.GRIMMORY,
            SourceType.LOCAL -> null
        }
}
