package com.example.unifiedreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.LibraryAlphabetSection
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.KavitaScopeLandingItem
import com.example.unifiedreader.feature.library.KavitaScopeLandingScreen
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryReadingListScopeState
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.LibraryWindowBadges
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.feature.library.shelves.LibraryShelfItemsBrowser
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesTopSlot
import com.example.unifiedreader.feature.library.shelves.browserState
import com.example.unifiedreader.feature.library.withWindowBadges
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppBackground
import kotlinx.coroutines.flow.Flow

@Composable
internal fun LibraryRoute(
    uiState: AppUiState,
    viewMode: LibraryViewMode,
    pagedGridFlow: Flow<PagingData<ReadableItem>>? = null,
    pagedWindowBadgeResolver: (suspend (List<ReadableItem>) -> LibraryWindowBadges)? = null,
    pagedAlphabetSectionsProvider: (suspend (LibrarySortMode) -> List<LibraryAlphabetSection>)? = null,
    browseContextKey: String,
    initialScrollPosition: LibraryScrollPosition,
    scrollToTopRequest: Long,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onPrepareCoverSource: (SourceType) -> Unit,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onCoverViewportScrollingChanged: (Boolean) -> Unit,
    onScrollPositionChanged: (LibraryScrollPosition) -> Unit,
    isDownloadSelectionMode: Boolean,
    selectedDownloadItemIds: List<String>,
    onDownloadSelectionModeChanged: (Boolean) -> Unit,
    onSelectedDownloadItemIdsChanged: (List<String>) -> Unit,
    onSelectableDownloadItemsChanged: (List<ReadableItem>) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    seriesReadingDirectionFor: (ReadableItem) -> ComicReadingDirection?,
    onSeriesDirectionSelected: (ReadableItem, ComicReadingDirection) -> Unit,
    seriesReaderModeFor: (ReadableItem) -> ComicReaderMode?,
    onSeriesReaderModeSelected: (ReadableItem, ComicReaderMode) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
    onDownloadRemoteItem: (ReadableItem) -> Unit,
    onDeleteRemoteLocalFile: (ReadableItem) -> Unit,
    onSetReadStatus: (ReadableItem, Boolean) -> Unit,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onReadingListSelected: (String?) -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onViewModeChanged: (LibraryViewMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
    showSortControl: Boolean,
    activeShelfBrowserKind: LibraryShelfBrowserKind? = null,
    onActiveShelfBrowserKindChanged: (LibraryShelfBrowserKind?) -> Unit = {},
    shelfSearchQuery: String = "",
    onShelfSearchQueryChanged: (String) -> Unit = {},
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.selectedSource) {
        onPrepareCoverSource(uiState.selectedSource)
    }
    val activeShelfBrowser = activeShelfBrowserKind?.let { kind ->
        uiState.libraryShelvesState.browserState(kind)
    }
    LaunchedEffect(activeShelfBrowserKind, activeShelfBrowser) {
        if (activeShelfBrowserKind != null && activeShelfBrowser == null) {
            onActiveShelfBrowserKindChanged(null)
        }
    }
    // The shelves (and the "See all" browser) render from this context. Its download/read maps come
    // from the late in-memory recompute, so at cold start the shelves' badges lag ~1 s behind the grid.
    // Resolve the bounded shelf item set's badges from the same early window resolver as the grid and
    // overlay them, so the shelf badges commit early too.
    val shelfItems = remember(uiState.libraryShelvesState) { uiState.libraryShelvesState.allItems() }
    val latestShelfBadgeResolver by rememberUpdatedState(pagedWindowBadgeResolver)
    val shelfWindowBadges by produceState(LibraryWindowBadges(), shelfItems) {
        val resolver = latestShelfBadgeResolver
        value = if (resolver != null && shelfItems.isNotEmpty()) resolver(shelfItems) else LibraryWindowBadges()
    }
    val itemActionContext = LibraryItemActionContext(
        progressByItemId = uiState.displayProgressByItemId,
        locallyAvailableItemIds = uiState.locallyAvailableRemoteItemIds,
        downloadableItemIds = uiState.remoteDownloadActionAvailability.downloadableItemIds,
        deletableDownloadedItemIds = uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
        downloadingItemIds = uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
        seriesReadingDirectionFor = seriesReadingDirectionFor,
        onSeriesDirectionSelected = onSeriesDirectionSelected,
        seriesReaderModeFor = seriesReaderModeFor,
        onSeriesReaderModeSelected = onSeriesReaderModeSelected,
        onDownloadItem = onDownloadRemoteItem,
        onDeleteDownloadedItem = onDeleteRemoteLocalFile,
        onSetReadStatus = onSetReadStatus,
    ).withWindowBadges(shelfWindowBadges)
    AppBackground(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (activeShelfBrowser != null) {
            LibraryShelfItemsBrowser(
                browser = activeShelfBrowser,
                browseContextKey = "$browseContextKey:shelf:${activeShelfBrowser.kind.name}",
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                gridCellSizeDp = uiState.libraryGridCellSizeDp,
                onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                searchQuery = shelfSearchQuery,
                onSearchQueryChanged = onShelfSearchQueryChanged,
                showEmbeddedTopBar = false,
                contentTopInset = contentTopInset,
                onCoverVisible = onCoverVisible,
                onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                onBack = { onActiveShelfBrowserKindChanged(null) },
                onItemSelected = { item ->
                    onActiveShelfBrowserKindChanged(null)
                    onItemSelected(item)
                },
                onDeleteImportedItem = onDeleteImportedItem,
            )
        } else if (uiState.isKavitaVisualSectionActive) {
            KavitaScopeLandingScreen(
                contentTopInset = contentTopInset,
                title = uiState.kavitaLibrarySection.displayName(),
                items = uiState.kavitaScopeLandingItems(),
                isLoading = !uiState.isSelectedSourceHydrated || uiState.kavitaReadingLists.isLoadingSummaries,
                errorMessage = uiState.kavitaReadingLists.errorMessage,
                searchQuery = uiState.selectedLibrarySearchQuery,
                gridCellSizeDp = uiState.libraryGridCellSizeDp,
                browseContextKey = browseContextKey,
                emptyMessage = when (uiState.kavitaLibrarySection) {
                    KavitaLibrarySection.Collections -> "No Kavita collections yet"
                    KavitaLibrarySection.ReadingLists -> "No Kavita reading lists yet"
                    KavitaLibrarySection.Home -> "No Kavita lists yet"
                },
                coverImageUrlProvider = coverImageUrlProvider,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                onCoverVisible = onCoverVisible,
                onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                onItemSelected = onReadingListSelected,
            )
        } else {
            LibraryScreen(
                contentTopInset = contentTopInset,
                selectedSource = uiState.selectedSource,
                browseContextKey = browseContextKey,
                items = uiState.visibleItems,
                viewMode = viewMode,
                progressByItemId = uiState.displayProgressByItemId,
                ratingByItemId = uiState.displayRatingByItemId,
                fadeReadItems = uiState.libraryFadeReadItems,
                locallyAvailableItemIds = uiState.locallyAvailableRemoteItemIds,
                downloadableItemIds = uiState.remoteDownloadActionAvailability.downloadableItemIds,
                deletableDownloadedItemIds = uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
                downloadingItemIds = uiState.remoteDownloadActionAvailability.activeDownloadingItemIds,
                isLoadingSource = !uiState.isSelectedSourceHydrated ||
                    (uiState.selectedSource == SourceType.KAVITA && uiState.kavitaReadingLists.isLoadingSelectedItems) ||
                    uiState.isSelectedLibraryLocalAvailabilityPending,
                filterState = uiState.selectedLibraryFilters,
                filterPanels = uiState.selectedLibraryFilterPanels,
                filterFacets = uiState.selectedLibraryFilterFacets,
                libraryTitlesById = uiState.selectedSourceLibraryTitlesById,
                searchQuery = uiState.selectedLibrarySearchQuery,
                sortMode = uiState.selectedLibrarySortMode,
                gridCellSizeDp = uiState.libraryGridCellSizeDp,
                showSortControl = showSortControl,
                preferIssueLabels = uiState.isSeriesBrowseActive || uiState.isKavitaReadingListActive,
                includeSeriesInIssueLabels = uiState.isKavitaReadingListActive,
                lockedSortLabel = "List order".takeIf { uiState.isKavitaReadingListActive },
                readingListScopeState = if (uiState.selectedSource == SourceType.KAVITA) {
                    uiState.kavitaReadingListScopeState()
                } else {
                    LibraryReadingListScopeState()
                },
                coverImageUrlProvider = coverImageUrlProvider,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                onCoverVisible = onCoverVisible,
                onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
                topContent = if (uiState.shouldShowLibraryShelves) {
                    @Composable { gridMetrics: LibraryGridMetrics ->
                        LibraryShelvesTopSlot(
                            browseContextKey = browseContextKey,
                            shouldReserveSlot = uiState.shouldReserveLibraryShelvesSlot,
                            state = uiState.libraryShelvesState,
                            gridMetrics = gridMetrics,
                            itemActionContext = itemActionContext,
                            coverImageUrlProvider = coverImageUrlProvider,
                            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                            onCoverVisible = onCoverVisible,
                            onItemSelected = onItemSelected,
                            onBrowseShelf = onActiveShelfBrowserKindChanged,
                            onDeleteImportedItem = onDeleteImportedItem,
                        )
                    }
                } else {
                    null
                },
                onFilterStateChanged = onFilterStateChanged,
                onClearFilters = onClearFilters,
                onReadingListSelected = onReadingListSelected,
                onSortModeChanged = onSortModeChanged,
                onViewModeChanged = onViewModeChanged,
                onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                onFadeReadItemsChanged = onFadeReadItemsChanged,
                initialScrollPosition = initialScrollPosition,
                scrollToTopRequest = scrollToTopRequest,
                onScrollPositionChanged = onScrollPositionChanged,
                isDownloadSelectionMode = isDownloadSelectionMode,
                selectedDownloadItemIds = selectedDownloadItemIds,
                onDownloadSelectionModeChanged = onDownloadSelectionModeChanged,
                onSelectedDownloadItemIdsChanged = onSelectedDownloadItemIdsChanged,
                onSelectableDownloadItemsChanged = onSelectableDownloadItemsChanged,
                onItemSelected = onItemSelected,
                seriesReadingDirectionFor = seriesReadingDirectionFor,
                onSeriesDirectionSelected = onSeriesDirectionSelected,
                seriesReaderModeFor = seriesReaderModeFor,
                onSeriesReaderModeSelected = onSeriesReaderModeSelected,
                onDeleteImportedItem = onDeleteImportedItem,
                onDownloadItem = onDownloadRemoteItem,
                onDeleteDownloadedItem = onDeleteRemoteLocalFile,
                onSetReadStatus = onSetReadStatus,
                pagedGridItems = pagedGridFlow,
                pagedWindowBadgeResolver = pagedWindowBadgeResolver,
                pagedAlphabetSectionsProvider = pagedAlphabetSectionsProvider,
                // Series browse is non-paged but bounded (one series' issues); resolve its badges early
                // too so download status commits with the grid instead of waiting for the async recompute.
                resolveWindowBadgesForVisibleItems = uiState.isSeriesBrowseActive,
            )
        }
    }
}

private fun AppUiState.kavitaScopeLandingItems(): List<KavitaScopeLandingItem> = when (kavitaLibrarySection) {
    KavitaLibrarySection.Collections -> kavitaReadingLists.collections.map { collection ->
        KavitaScopeLandingItem(
            id = collection.id,
            title = collection.title,
            itemCount = collection.itemCount,
            summary = collection.summary,
            coverUrl = collection.coverUrl,
            sourceType = collection.sourceType,
            remoteId = collection.remoteId,
            coverCacheScope = "collection",
        )
    }

    KavitaLibrarySection.ReadingLists -> kavitaReadingLists.summaries.map { summary ->
        KavitaScopeLandingItem(
            id = summary.id,
            title = summary.title,
            itemCount = summary.itemCount,
            summary = summary.summary,
            coverUrl = summary.coverUrl,
            sourceType = summary.sourceType,
            remoteId = summary.remoteId,
            coverCacheScope = "reading-list",
        )
    }

    KavitaLibrarySection.Home -> emptyList()
}
