package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.CollectionSeriesRef
import com.example.unifiedreader.core.model.CollectionSummary
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadingListItemRef
import com.example.unifiedreader.core.model.ReadingListSummary
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryRatingBadgeState
import com.example.unifiedreader.feature.library.LibraryReadingListScopeOption
import com.example.unifiedreader.feature.library.LibraryReadingListScopeOptionKind
import com.example.unifiedreader.feature.library.LibraryReadingListScopeState
import com.example.unifiedreader.feature.library.RemoteDownloadActionAvailability
import com.example.unifiedreader.feature.library.defaultLibraryGridCellSizeDp
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesState
import com.example.unifiedreader.journal.JournalSnapshot
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import com.example.unifiedreader.feature.library.defaultLibrarySortMode
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
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
    val includesRemoteItemLocalState: Boolean,
    val progressByItemId: Map<String, ItemProgressSummary>,
    val hydratedSources: Set<SourceType>,
    val themeMode: ThemeMode,
)

enum class TopLevelDestination {
    Library,
    Journal,
    Settings,
}

data class AppReaderCompletionState(
    val itemId: String,
    val nextItemId: String?,
)

data class AppKavitaReadingListState(
    val summaries: List<ReadingListSummary> = emptyList(),
    val itemsByReadingListId: Map<String, List<ReadingListItemRef>> = emptyMap(),
    val selectedReadingListId: String? = null,
    val loadingSelectedReadingListId: String? = null,
    val collections: List<CollectionSummary> = emptyList(),
    val seriesByCollectionId: Map<String, List<CollectionSeriesRef>> = emptyMap(),
    val selectedCollectionId: String? = null,
    val loadingSelectedCollectionId: String? = null,
    val isLoadingSummaries: Boolean = false,
    val isLoadingSelectedItems: Boolean = false,
    val errorMessage: String? = null,
)

data class AppUiState(
    val topLevel: TopLevelDestination = TopLevelDestination.Library,
    val sourceOrder: List<SourceType> = SourceType.entries.toList(),
    val selectedSource: SourceType = SourceType.KAVITA,
    val kavitaLibrarySection: KavitaLibrarySection = KavitaLibrarySection.Home,
    val selectedItemId: String? = null,
    val activeReader: FormatType? = null,
    val selectedReaderOpenMode: ReaderOpenMode = ReaderOpenMode.Default,
    val libraries: List<Library> = emptyList(),
    val items: List<ReadableItem> = emptyList(),
    val selectedReaderItem: ReadableItem? = null,
    val libraryFiltersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val librarySearchQueriesBySource: Map<SourceType, String> = emptyMap(),
    val librarySortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val libraryFilterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>> = emptyMap(),
    val libraryGridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    val libraryFadeReadItems: Boolean = false,
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val downloadedRemoteItemIds: Set<String> = emptySet(),
    val locallyAvailableRemoteItemIds: Set<String> = emptySet(),
    val remoteItemLocalStateItemIds: Set<String> = emptySet(),
    val remoteItemLocalAvailabilityScopeIds: Set<String> = emptySet(),
    val isRemoteItemLocalStateKnown: Boolean = false,
    val isRemoteItemLocalStateRefreshing: Boolean = false,
    val downloadingRemoteItemIds: Set<String> = emptySet(),
    val hydratedSources: Set<SourceType> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SystemDynamic,
    val comicReadingDirectionPreferences: ComicReadingDirectionPreferences = ComicReadingDirectionPreferences(),
    val comicReaderModePreferences: ComicReaderModePreferences = ComicReaderModePreferences(),
    val kavitaConnection: KavitaConnectionUiState = KavitaConnectionUiState(),
    val stumpConnection: StumpConnectionUiState = StumpConnectionUiState(),
    val grimmoryConnection: GrimmoryConnectionUiState = GrimmoryConnectionUiState(),
    val selectedKavitaSeriesKey: String? = null,
    val selectedStumpSeriesKey: String? = null,
    val kavitaReadingLists: AppKavitaReadingListState = AppKavitaReadingListState(),
    val sourceItems: List<ReadableItem> = emptyList(),
    val selectedLibraryFilterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    val selectedSourceLibraryTitlesById: Map<String, String> = emptyMap(),
    val displayProgressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val displayRatingByItemId: Map<String, LibraryRatingBadgeState> = emptyMap(),
    val remoteDownloadActionAvailability: RemoteDownloadActionAvailability = RemoteDownloadActionAvailability(),
    val visibleItems: List<ReadableItem> = emptyList(),
    val libraryBrowseContextKey: String = "",
    val libraryScrollToTopRequest: Long = 0L,
    val libraryShelvesState: LibraryShelvesState = LibraryShelvesState(),
    val journalSnapshot: JournalSnapshot = JournalSnapshot(),
    val progressSyncNotice: AppProgressSyncNotice? = null,
    val pendingRemoteSyncSummary: AppPendingRemoteSyncSummary = AppPendingRemoteSyncSummary(),
    val sourceConnectivityBySource: Map<SourceType, SourceConnectivityState> = emptyMap(),
    val readerCompletionState: AppReaderCompletionState? = null,
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

    val isSelectedLibraryLocalAvailabilityPending: Boolean
        get() = selectedSource != SourceType.LOCAL &&
            LibraryFileLocation.LOCAL in selectedLibraryFilters.fileLocations &&
            (!isRemoteItemLocalStateKnown || isRemoteItemLocalStateRefreshing)

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
            SourceType.KAVITA -> selectedKavitaSeriesTitle ?: selectedKavitaCollectionTitle ?: selectedKavitaReadingListTitle
            SourceType.STUMP -> selectedStumpSeriesTitle
            SourceType.GRIMMORY,
            SourceType.LOCAL -> null
        }

    val selectedLibraryContextTitle: String?
        get() = selectedSeriesTitle ?: selectedKavitaLibrarySectionTitle

    val selectedKavitaLibrarySectionTitle: String?
        get() = kavitaLibrarySection
            .takeIf { selectedSource == SourceType.KAVITA && it != KavitaLibrarySection.Home }
            ?.displayName()

    val selectedKavitaReadingListTitle: String?
        get() = kavitaReadingLists.selectedReadingListId?.let { readingListId ->
            kavitaReadingLists.summaries.firstOrNull { summary -> summary.id == readingListId }?.title
        }

    val selectedKavitaCollectionTitle: String?
        get() = kavitaReadingLists.selectedCollectionId?.let { collectionId ->
            kavitaReadingLists.collections.firstOrNull { collection -> collection.id == collectionId }?.title
        }

    val isKavitaReadingListActive: Boolean
        get() = selectedSource == SourceType.KAVITA && kavitaReadingLists.selectedReadingListId != null

    val isKavitaCollectionActive: Boolean
        get() = selectedSource == SourceType.KAVITA && kavitaReadingLists.selectedCollectionId != null

    val isKavitaScopeActive: Boolean
        get() = isKavitaReadingListActive || isKavitaCollectionActive

    val isKavitaVisualSectionActive: Boolean
        get() = selectedSource == SourceType.KAVITA &&
            kavitaLibrarySection != KavitaLibrarySection.Home &&
            !isSeriesBrowseActive &&
            !isKavitaScopeActive

    val selectedKavitaReadingListItemIds: List<String>
        get() = kavitaReadingLists.selectedReadingListId
            ?.let { readingListId -> kavitaReadingLists.itemsByReadingListId[readingListId] }
            .orEmpty()
            .sortedBy(ReadingListItemRef::order)
            .map(ReadingListItemRef::itemId)

    val selectedKavitaCollectionSeriesKeys: Set<String>
        get() = kavitaReadingLists.selectedCollectionId
            ?.let { collectionId -> kavitaReadingLists.seriesByCollectionId[collectionId] }
            .orEmpty()
            .sortedBy(CollectionSeriesRef::order)
            .mapTo(linkedSetOf(), CollectionSeriesRef::seriesKey)

    val shouldShowLibraryShelves: Boolean
        get() = shouldReserveLibraryShelvesSlot &&
            (libraryShelvesState.hasContent || libraryShelvesState.isLoading)

    val shouldReserveLibraryShelvesSlot: Boolean
        get() = selectedLibrarySearchQuery.isBlank() &&
            !isSeriesBrowseActive &&
            !isKavitaScopeActive &&
            !isKavitaVisualSectionActive

    val selectedSourceConnectivity: SourceConnectivityState
        get() = sourceConnectivityBySource[selectedSource] ?: SourceConnectivityState.Unknown
}

internal fun AppUiState.withRemoteItemLocalState(
    downloadedRemoteItemIds: Set<String>,
    locallyAvailableRemoteItemIds: Set<String>,
    itemIds: Set<String> = items.remoteItemLocalStateScopeItemIds(),
    availabilityScopeIds: Set<String> = items.remoteItemLocalAvailabilityScopeIds(),
    isKnown: Boolean = true,
    isRefreshing: Boolean = false,
): AppUiState = copy(
    downloadedRemoteItemIds = downloadedRemoteItemIds,
    locallyAvailableRemoteItemIds = locallyAvailableRemoteItemIds,
    remoteItemLocalStateItemIds = itemIds,
    remoteItemLocalAvailabilityScopeIds = availabilityScopeIds,
    isRemoteItemLocalStateKnown = isKnown,
    isRemoteItemLocalStateRefreshing = isRefreshing,
)

internal fun AppUiState.withRemoteItemLocalStateRefreshPending(
    itemIds: Set<String> = items.remoteItemLocalStateScopeItemIds(),
    availabilityScopeIds: Set<String> = items.remoteItemLocalAvailabilityScopeIds(),
): AppUiState {
    val hasKnownStateForScope = isRemoteItemLocalStateKnown &&
        remoteItemLocalStateItemIds == itemIds &&
        remoteItemLocalAvailabilityScopeIds == availabilityScopeIds
    return copy(
        remoteItemLocalStateItemIds = itemIds,
        remoteItemLocalAvailabilityScopeIds = availabilityScopeIds,
        isRemoteItemLocalStateKnown = hasKnownStateForScope,
        isRemoteItemLocalStateRefreshing = true,
    )
}

internal fun List<ReadableItem>.remoteItemLocalStateScopeItemIds(): Set<String> = buildSet {
    this@remoteItemLocalStateScopeItemIds.forEach { item ->
        if (item.sourceType != SourceType.LOCAL) {
            add(item.id)
        }
    }
}

internal fun List<ReadableItem>.remoteItemLocalAvailabilityScopeIds(): Set<String> = buildSet {
    addAll(remoteItemLocalStateScopeItemIds())
    this@remoteItemLocalAvailabilityScopeIds.forEach { item ->
        when (item.sourceType) {
            SourceType.KAVITA -> item.kavitaSeriesGroupKey()?.let(::kavitaSeriesContainerId)
            SourceType.STUMP -> item.stumpSeriesGroupKey()?.let(::stumpSeriesContainerId)
            SourceType.GRIMMORY,
            SourceType.LOCAL -> null
        }?.let(::add)
    }
}

fun AppUiState.kavitaReadingListScopeState(): LibraryReadingListScopeState = LibraryReadingListScopeState(
    options = kavitaReadingLists.collections.map { collection ->
        LibraryReadingListScopeOption(
            id = collection.id,
            title = collection.title,
            itemCount = collection.itemCount,
            kind = LibraryReadingListScopeOptionKind.Collection,
        )
    } + kavitaReadingLists.summaries.map { summary ->
        LibraryReadingListScopeOption(
            id = summary.id,
            title = summary.title,
            itemCount = summary.itemCount,
            kind = LibraryReadingListScopeOptionKind.ReadingList,
        )
    },
    selectedOptionId = kavitaReadingLists.selectedCollectionId ?: kavitaReadingLists.selectedReadingListId,
    isLoadingOptions = kavitaReadingLists.isLoadingSummaries,
    isLoadingSelectedItems = kavitaReadingLists.isLoadingSelectedItems,
    errorMessage = kavitaReadingLists.errorMessage,
)
