package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryAnchoredItemMenu
import com.example.unifiedreader.feature.library.LibraryBrowseOption
import com.example.unifiedreader.feature.library.LibraryBrowseOptionSection
import com.example.unifiedreader.feature.library.LibraryBrowseOptionsMenu
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryCoverProgressBar
import com.example.unifiedreader.feature.library.LibraryDeleteImportedItemDialog
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LocalLibraryCoverGeneration
import com.example.unifiedreader.feature.library.availableLibraryGridSizeOptions
import com.example.unifiedreader.feature.library.defaultLibraryGridCellSizeDp
import com.example.unifiedreader.feature.library.hasMenuContent
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.libraryGridCoverImageVariant
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.library.minLibraryGridCellSizeDp
import com.example.unifiedreader.feature.library.sanitizeLibraryGridCellSizeDp
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

enum class LibraryShelfBrowserKind {
    ContinueReading,
    NextInSeries,
    RecentlyFinished,
}

sealed interface LibraryShelfBrowserState {
    val kind: LibraryShelfBrowserKind
    val title: String
    val count: Int

    data class ContinueReading(
        val items: List<ContinueReadingShelfItem>,
    ) : LibraryShelfBrowserState {
        override val kind: LibraryShelfBrowserKind = LibraryShelfBrowserKind.ContinueReading
        override val title: String = "Continue Reading"
        override val count: Int = items.size
    }

    data class NextInSeries(
        val items: List<NextInSeriesShelfItem>,
    ) : LibraryShelfBrowserState {
        override val kind: LibraryShelfBrowserKind = LibraryShelfBrowserKind.NextInSeries
        override val title: String = "Up Next"
        override val count: Int = items.size
    }

    data class RecentlyFinished(
        val items: List<RecentlyFinishedShelfItem>,
    ) : LibraryShelfBrowserState {
        override val kind: LibraryShelfBrowserKind = LibraryShelfBrowserKind.RecentlyFinished
        override val title: String = "Recently Finished"
        override val count: Int = items.size
    }
}

fun LibraryShelvesState.browserState(
    kind: LibraryShelfBrowserKind,
): LibraryShelfBrowserState? = when (kind) {
    LibraryShelfBrowserKind.ContinueReading -> continueReading
        .takeIf(List<ContinueReadingShelfItem>::isNotEmpty)
        ?.let(LibraryShelfBrowserState::ContinueReading)

    LibraryShelfBrowserKind.NextInSeries -> nextInSeries
        .takeIf(List<NextInSeriesShelfItem>::isNotEmpty)
        ?.let(LibraryShelfBrowserState::NextInSeries)

    LibraryShelfBrowserKind.RecentlyFinished -> recentlyFinished
        .takeIf(List<RecentlyFinishedShelfItem>::isNotEmpty)
        ?.let(LibraryShelfBrowserState::RecentlyFinished)
}

@Composable
fun LibraryShelfItemsBrowser(
    browser: LibraryShelfBrowserState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onBack: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
    browseContextKey: String = browser.kind.name,
    gridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    onGridCellSizeDpChanged: (Int) -> Unit = {},
    searchQuery: String? = null,
    onSearchQueryChanged: ((String) -> Unit)? = null,
    showEmbeddedTopBar: Boolean = true,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var localSearchQuery by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf("") }
    val effectiveSearchQuery = searchQuery ?: localSearchQuery
    val updateSearchQuery = onSearchQueryChanged ?: { query: String -> localSearchQuery = query }
    var sortMode by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf(ShelfBrowserSortMode.ShelfOrder) }
    var showListView by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf(false) }
    var expandedMenuItemId by remember(browseContextKey, browser.kind, browser.count) { mutableStateOf<String?>(null) }
    var deleteCandidate by remember(browseContextKey, browser.kind) { mutableStateOf<ReadableItem?>(null) }
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    val allEntries = remember(browser) {
        browser.browserEntries(nowEpochMillis = System.currentTimeMillis())
    }
    val availableSortModes = remember(browser.kind, allEntries) {
        shelfBrowserSortModes(
            kind = browser.kind,
            entries = allEntries,
        )
    }
    LaunchedEffect(availableSortModes, sortMode) {
        if (sortMode !in availableSortModes) {
            sortMode = ShelfBrowserSortMode.ShelfOrder
        }
    }
    val visibleEntries = remember(allEntries, effectiveSearchQuery, sortMode) {
        allEntries
            .searchedBy(effectiveSearchQuery)
            .sortedFor(sortMode)
    }

    LaunchedEffect(browser.kind, effectiveSearchQuery, sortMode, showListView, sanitizedGridCellSizeDp) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }
    ShelfBrowserVisibleCoverPrefetcher(
        browseContextKey = browseContextKey,
        showListView = showListView,
        gridState = gridState,
        listState = listState,
        entries = visibleEntries,
        variant = shelfBrowserCoverImageVariant(
            showListView = showListView,
            gridCellSizeDp = sanitizedGridCellSizeDp,
        ),
        coverPrefetchKeyProvider = coverPrefetchKeyProvider,
        onCoverVisible = onCoverVisible,
        onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
    )

    AppBackground(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // The controls strip is the first row of the grid or list, so the whole browser
        // scrolls under the glass top bar like the library home.
        val controls: @Composable () -> Unit = {
            ShelfBrowserControls(
                sortMode = sortMode,
                showListView = showListView,
                gridCellSizeDp = sanitizedGridCellSizeDp,
                availableSortModes = availableSortModes,
                shelfKind = browser.kind,
                onSortModeChanged = { sortMode = it },
                onListViewChanged = { showListView = it },
                onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            if (showEmbeddedTopBar) {
                LibraryShelfBrowserTopBar(
                    title = browser.title,
                    count = browser.count,
                    searchQuery = effectiveSearchQuery,
                    onSearchQueryChanged = updateSearchQuery,
                    onBack = onBack,
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (visibleEntries.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp + contentTopInset, start = 16.dp, end = 16.dp),
                    ) {
                        controls()
                        ShelfBrowserEmptyState(
                            searchActive = effectiveSearchQuery.isNotBlank(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    ShelfBrowserItems(
                        entries = visibleEntries,
                        header = controls,
                        contentTopInset = contentTopInset,
                        showListView = showListView,
                        gridCellSizeDp = sanitizedGridCellSizeDp,
                        gridState = gridState,
                        listState = listState,
                        itemActionContext = itemActionContext,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onItemSelected = onItemSelected,
                        expandedMenuItemId = expandedMenuItemId,
                        onExpandedMenuChange = { item, expanded ->
                            expandedMenuItemId = if (expanded) item.id else null
                        },
                        onDeleteImportedRequested = { deleteCandidate = it },
                    )
                }
            }
        }
    }

    deleteCandidate?.let { item ->
        LibraryDeleteImportedItemDialog(
            item = item,
            onDismiss = { deleteCandidate = null },
            onConfirmDelete = {
                onDeleteImportedItem(it)
                deleteCandidate = null
            },
        )
    }
}

@Composable
private fun ShelfBrowserControls(
    sortMode: ShelfBrowserSortMode,
    showListView: Boolean,
    gridCellSizeDp: Int,
    availableSortModes: List<ShelfBrowserSortMode>,
    shelfKind: LibraryShelfBrowserKind,
    onSortModeChanged: (ShelfBrowserSortMode) -> Unit,
    onListViewChanged: (Boolean) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val gridSizeOptions = availableLibraryGridSizeOptions(
            maxWidth = maxWidth,
            endPadding = 16.dp,
        )
        val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)

        @Composable
        fun BrowseMenu(
            modifier: Modifier = Modifier,
        ) {
            Row(
                modifier = modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryBrowseOptionsMenu(
                    label = sortMode.label(shelfKind),
                    selected = sortMode != ShelfBrowserSortMode.ShelfOrder ||
                        showListView ||
                        sanitizedGridCellSizeDp != defaultLibraryGridCellSizeDp,
                    optionSections = listOf(
                        shelfBrowserSortOptionSection(
                            sortMode = sortMode,
                            availableSortModes = availableSortModes,
                            shelfKind = shelfKind,
                        ),
                        shelfBrowserLayoutOptionSection(showListView = showListView),
                    ),
                    gridCellSizeDp = sanitizedGridCellSizeDp,
                    gridSizeOptions = gridSizeOptions,
                    fadeReadItems = false,
                    showFadeReadItems = false,
                    showGridSizeOptions = !showListView,
                    onOptionSelected = { sectionId, optionId ->
                        when (sectionId) {
                            ShelfBrowserSortSectionId -> {
                                availableSortModes
                                    .firstOrNull { mode -> mode.name == optionId }
                                    ?.let(onSortModeChanged)
                            }
                            ShelfBrowserLayoutSectionId -> {
                                when (optionId) {
                                    ShelfBrowserLayoutGridOptionId -> onListViewChanged(false)
                                    ShelfBrowserLayoutListOptionId -> onListViewChanged(true)
                                }
                            }
                        }
                    },
                    onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                    onFadeReadItemsChanged = {},
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            BrowseMenu()
        }
    }
}

@Composable
private fun ShelfBrowserItems(
    entries: List<LibraryShelfBrowserEntry>,
    header: @Composable () -> Unit,
    contentTopInset: Dp,
    showListView: Boolean,
    gridCellSizeDp: Int,
    gridState: LazyGridState,
    listState: LazyListState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    if (showListView) {
        ShelfBrowserList(
            entries = entries,
            header = header,
            contentTopInset = contentTopInset,
            listState = listState,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onExpandedMenuChange = onExpandedMenuChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
        )
    } else {
        ShelfBrowserGrid(
            entries = entries,
            header = header,
            contentTopInset = contentTopInset,
            gridCellSizeDp = gridCellSizeDp,
            gridState = gridState,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onExpandedMenuChange = onExpandedMenuChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
        )
    }
}

@Composable
private fun ShelfBrowserGrid(
    entries: List<LibraryShelfBrowserEntry>,
    header: @Composable () -> Unit,
    contentTopInset: Dp,
    gridCellSizeDp: Int,
    gridState: LazyGridState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    val compactGrid = sanitizedGridCellSizeDp <= minLibraryGridCellSizeDp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(sanitizedGridCellSizeDp.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + contentTopInset, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactGrid) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = ShelfBrowserHeaderKey, span = { GridItemSpan(maxLineSpan) }) { header() }
        items(
            items = entries,
            key = { it.item.id },
        ) { entry ->
            ShelfBrowserGridCard(
                entry = entry,
                compact = compactGrid,
                imageVariant = libraryGridCoverImageVariant(sanitizedGridCellSizeDp),
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                onItemSelected = { onItemSelected(entry.item) },
                expandedMenuItemId = expandedMenuItemId,
                onExpandedMenuChange = onExpandedMenuChange,
                onDeleteImportedRequested = onDeleteImportedRequested,
            )
        }
    }
}

@Composable
private fun ShelfBrowserList(
    entries: List<LibraryShelfBrowserEntry>,
    header: @Composable () -> Unit,
    contentTopInset: Dp,
    listState: LazyListState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + contentTopInset, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = ShelfBrowserHeaderKey) { header() }
        items(
            items = entries,
            key = { it.item.id },
        ) { entry ->
            val hasMenuContent = itemActionContext.hasMenuContent(entry.item)
            ShelfListRow(
                item = entry.item,
                title = entry.title,
                supportingText = entry.supportingText,
                trailingText = entry.trailingText,
                metadataText = entry.metadataText,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onClick = { onItemSelected(entry.item) },
                menuContent = if (hasMenuContent) {
                    {
                        LibraryAnchoredItemMenu(
                            item = entry.item,
                            actionContext = itemActionContext,
                            expanded = expandedMenuItemId == entry.item.id,
                            onExpandedChange = { expanded ->
                                onExpandedMenuChange(entry.item, expanded)
                            },
                            onDeleteImportedRequested = onDeleteImportedRequested,
                        )
                    }
                } else {
                    null
                },
                progressFraction = entry.progressFraction,
                isDownloaded = itemActionContext.isDownloaded(entry.item),
                isRead = entry.isRead,
                compact = false,
                notifyCoverVisibleOnCompose = false,
            )
        }
    }
}

@Composable
private fun ShelfBrowserGridCard(
    entry: LibraryShelfBrowserEntry,
    compact: Boolean,
    imageVariant: LibraryCoverImageVariant,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: () -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    val hasMenuContent = itemActionContext.hasMenuContent(entry.item)
    val metadata = if (compact) {
        entry.trailingText
    } else {
        entry.metadataText ?: entry.trailingText
    }
    // A bare tile, like the library grid's and the shelves'.
    Box(modifier = Modifier.fillMaxWidth()) {
            LibraryCoverActionCard(
                item = entry.item,
                title = entry.title,
                supportingText = entry.supportingText,
                trailingText = metadata,
                coverImageUrlProvider = coverImageUrlProvider,
                imageVariant = imageVariant,
                isDownloaded = itemActionContext.isDownloaded(entry.item),
                isRead = entry.isRead,
                onClick = onItemSelected,
                titleStyle = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.titleSmall
                },
                supportingStyle = MaterialTheme.typography.bodySmall,
                trailingColor = entry.accentColor(),
                metadataTopPadding = 8.dp,
                menuContent = if (hasMenuContent) {
                    {
                        LibraryAnchoredItemMenu(
                            item = entry.item,
                            actionContext = itemActionContext,
                            expanded = expandedMenuItemId == entry.item.id,
                            onExpandedChange = { expanded ->
                                onExpandedMenuChange(entry.item, expanded)
                            },
                            onDeleteImportedRequested = onDeleteImportedRequested,
                            buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                        )
                    }
                } else {
                    null
                },
                coverModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                coverOverlay = {
                    entry.progressFraction?.let { fraction ->
                        ShelfBrowserProgressOverlay(
                            fraction = fraction,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                },
            )
    }
}

@Composable
private fun BoxScope.ShelfBrowserProgressOverlay(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    LibraryCoverProgressBar(
        fraction = fraction,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShelfBrowserEmptyState(
    searchActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (searchActive) {
                "No shelf items match this search"
            } else {
                "No shelf items"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShelfBrowserVisibleCoverPrefetcher(
    browseContextKey: String,
    showListView: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    entries: List<LibraryShelfBrowserEntry>,
    variant: LibraryCoverImageVariant,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onCoverViewportScrollingChanged: (Boolean) -> Unit,
) {
    val signaledPrefetchKeys = remember(browseContextKey, variant) { mutableSetOf<String>() }

    LaunchedEffect(showListView, gridState, listState, onCoverViewportScrollingChanged) {
        snapshotFlow {
            if (showListView) {
                listState.isScrollInProgress
            } else {
                gridState.isScrollInProgress
            }
        }
            .distinctUntilChanged()
            .collect { isScrolling -> onCoverViewportScrollingChanged(isScrolling) }
    }
    DisposableEffect(showListView, onCoverViewportScrollingChanged) {
        onDispose {
            onCoverViewportScrollingChanged(false)
        }
    }

    LaunchedEffect(
        browseContextKey,
        showListView,
        gridState,
        listState,
        entries,
        variant,
        coverPrefetchKeyProvider,
        onCoverVisible,
        LocalLibraryCoverGeneration.current,
    ) {
        snapshotFlow {
            if (showListView) {
                ShelfBrowserCoverPrefetchRequest(
                    visibleItemIndices = listState.layoutInfo.visibleItemsInfo.map { visibleItem -> visibleItem.index },
                    isScrollInProgress = listState.isScrollInProgress,
                    scrollDirection = when {
                        listState.lastScrolledForward -> ShelfBrowserCoverPrefetchScrollDirection.Forward
                        listState.lastScrolledBackward -> ShelfBrowserCoverPrefetchScrollDirection.Backward
                        else -> ShelfBrowserCoverPrefetchScrollDirection.Idle
                    },
                )
            } else {
                ShelfBrowserCoverPrefetchRequest(
                    visibleItemIndices = gridState.layoutInfo.visibleItemsInfo.map { visibleItem -> visibleItem.index },
                    isScrollInProgress = gridState.isScrollInProgress,
                    scrollDirection = when {
                        gridState.lastScrolledForward -> ShelfBrowserCoverPrefetchScrollDirection.Forward
                        gridState.lastScrolledBackward -> ShelfBrowserCoverPrefetchScrollDirection.Backward
                        else -> ShelfBrowserCoverPrefetchScrollDirection.Idle
                    },
                )
            }
        }
            .distinctUntilChanged()
            .collectLatest { request ->
                val targets = shelfBrowserCoverPrefetchTargets(
                    visibleItemIndices = request.visibleItemIndices,
                    entries = entries,
                    variant = variant,
                    isScrollInProgress = request.isScrollInProgress,
                    scrollDirection = request.scrollDirection,
                    maxTargetsWhileScrolling = ShelfBrowserCoverPrefetchMaxTargetsWhileScrolling,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                )
                    .filterNot { target -> target.prefetchKey in signaledPrefetchKeys }
                if (targets.isEmpty()) return@collectLatest
                delay(
                    if (request.isScrollInProgress) {
                        ShelfBrowserCoverPrefetchScrollDelayMillis
                    } else {
                        ShelfBrowserCoverPrefetchDelayMillis
                    },
                )
                targets.forEach { target ->
                    signaledPrefetchKeys += target.prefetchKey
                    onCoverVisible(target.item, target.variant)
                    delay(
                        if (request.isScrollInProgress) {
                            ShelfBrowserCoverPrefetchScrollStaggerMillis
                        } else {
                            ShelfBrowserCoverPrefetchStaggerMillis
                        },
                    )
                }
            }
    }
}

private fun shelfBrowserCoverPrefetchTargets(
    visibleItemIndices: List<Int>,
    entries: List<LibraryShelfBrowserEntry>,
    variant: LibraryCoverImageVariant,
    isScrollInProgress: Boolean,
    scrollDirection: ShelfBrowserCoverPrefetchScrollDirection,
    maxTargetsWhileScrolling: Int,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<ShelfBrowserCoverPrefetchTarget> = visibleItemIndices
    .asSequence()
    .filter { index -> index in entries.indices }
    .map { index -> entries[index].item }
    .mapNotNull { item ->
        val prefetchKey = coverPrefetchKeyProvider(item, variant)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        if (item.sourceType == SourceType.LOCAL) return@mapNotNull null
        ShelfBrowserCoverPrefetchTarget(
            item = item,
            prefetchKey = prefetchKey,
            variant = variant,
        )
    }
    .distinctBy(ShelfBrowserCoverPrefetchTarget::prefetchKey)
    .toList()
    .let { targets ->
        if (!isScrollInProgress || maxTargetsWhileScrolling >= targets.size) {
            targets
        } else {
            when (scrollDirection) {
                ShelfBrowserCoverPrefetchScrollDirection.Forward -> targets.takeLast(maxTargetsWhileScrolling)
                ShelfBrowserCoverPrefetchScrollDirection.Backward -> targets.take(maxTargetsWhileScrolling)
                ShelfBrowserCoverPrefetchScrollDirection.Idle -> targets.take(maxTargetsWhileScrolling)
            }
        }
    }

private fun shelfBrowserCoverImageVariant(
    showListView: Boolean,
    gridCellSizeDp: Int,
): LibraryCoverImageVariant = if (showListView) {
    LibraryCoverImageVariant.ShelfRow
} else {
    libraryGridCoverImageVariant(gridCellSizeDp)
}

private data class ShelfBrowserCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
    val variant: LibraryCoverImageVariant,
)

private data class ShelfBrowserCoverPrefetchRequest(
    val visibleItemIndices: List<Int>,
    val isScrollInProgress: Boolean,
    val scrollDirection: ShelfBrowserCoverPrefetchScrollDirection,
)

private enum class ShelfBrowserCoverPrefetchScrollDirection {
    Forward,
    Backward,
    Idle,
}

internal data class LibraryShelfBrowserEntry(
    val item: ReadableItem,
    val title: String,
    val supportingText: String,
    val trailingText: String,
    val metadataText: String?,
    val progressFraction: Float?,
    val isRead: Boolean,
    val activityAtEpochMillis: Long,
    val originalIndex: Int,
    val kind: LibraryShelfBrowserKind,
)

internal fun LibraryShelfBrowserState.visibleShelfBrowserEntries(
    nowEpochMillis: Long,
    searchQuery: String,
    sortMode: ShelfBrowserSortMode,
): List<LibraryShelfBrowserEntry> = browserEntries(nowEpochMillis = nowEpochMillis)
    .searchedBy(searchQuery)
    .sortedFor(sortMode)

private fun LibraryShelfBrowserState.browserEntries(
    nowEpochMillis: Long,
): List<LibraryShelfBrowserEntry> = when (this) {
    is LibraryShelfBrowserState.ContinueReading -> items.mapIndexed { index, shelfItem ->
        val item = shelfItem.item
        LibraryShelfBrowserEntry(
            item = item,
            title = item.libraryPrimaryText(),
            supportingText = continueReadingSupportingText(item),
            trailingText = shelfItem.progress.detailLabel,
            metadataText = listOfNotNull(
                shelfItem.progress.label.takeIf { label -> label != shelfItem.progress.detailLabel },
                item.compactMetadataText(),
            ).joinedMetadata(),
            progressFraction = shelfItem.progress.fraction,
            isRead = shelfItem.progress.isShelfComplete,
            activityAtEpochMillis = shelfItem.progress.lastReadAtEpochMillis,
            originalIndex = index,
            kind = kind,
        )
    }

    is LibraryShelfBrowserState.NextInSeries -> items.mapIndexed { index, shelfItem ->
        val item = shelfItem.item
        val relativeActivity = shelfItem.lastActivityAtEpochMillis.relativeTimeLabel(nowEpochMillis)
        LibraryShelfBrowserEntry(
            item = item,
            title = item.libraryPrimaryText(),
            supportingText = item.librarySecondaryText() ?: item.title,
            trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
            metadataText = listOfNotNull(
                relativeActivity?.let { "Last read $it" },
                item.compactMetadataText(),
            ).joinedMetadata(),
            progressFraction = null,
            isRead = false,
            activityAtEpochMillis = shelfItem.lastActivityAtEpochMillis,
            originalIndex = index,
            kind = kind,
        )
    }

    is LibraryShelfBrowserState.RecentlyFinished -> items.mapIndexed { index, shelfItem ->
        val item = shelfItem.item
        val finishedLabel = shelfItem.progress.lastReadAtEpochMillis.relativeTimeLabel(nowEpochMillis)
            ?: shelfItem.progress.label
        LibraryShelfBrowserEntry(
            item = item,
            title = item.libraryPrimaryText(),
            supportingText = item.librarySecondaryText() ?: shelfItem.progress.label,
            trailingText = finishedLabel,
            metadataText = listOfNotNull(
                "Finished $finishedLabel",
                item.compactMetadataText(),
            ).joinedMetadata(),
            progressFraction = null,
            isRead = true,
            activityAtEpochMillis = shelfItem.progress.lastReadAtEpochMillis,
            originalIndex = index,
            kind = kind,
        )
    }
}

private fun List<LibraryShelfBrowserEntry>.searchedBy(
    query: String,
): List<LibraryShelfBrowserEntry> {
    if (query.isBlank()) return this
    val byId = associateBy { entry -> entry.item.id }
    return applyLibrarySearch(
        items = map(LibraryShelfBrowserEntry::item),
        query = query,
    ).mapNotNull { item -> byId[item.id] }
}

private fun List<LibraryShelfBrowserEntry>.sortedFor(
    sortMode: ShelfBrowserSortMode,
): List<LibraryShelfBrowserEntry> = when (sortMode) {
    ShelfBrowserSortMode.ShelfOrder -> this
    ShelfBrowserSortMode.RecentActivity -> sortedWith(
        compareByDescending<LibraryShelfBrowserEntry> { entry -> entry.activityAtEpochMillis }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Progress -> sortedWith(
        compareByDescending<LibraryShelfBrowserEntry> { entry -> entry.progressFraction ?: -1f }
            .thenByDescending { entry -> entry.activityAtEpochMillis }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Title -> sortedWith(
        compareBy<LibraryShelfBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { entry -> entry.title }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.item.title }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Series -> sortedWith(
        compareBy<LibraryShelfBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { entry ->
            entry.item.facetMetadata().series.firstOrNull().orEmpty()
        }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.title }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Author -> sortedWith(
        compareBy<LibraryShelfBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { entry ->
            entry.item.facetMetadata().authors.firstOrNull()
                ?: entry.item.facetMetadata().writers.firstOrNull()
                ?: ""
        }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.title }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.DateAdded -> sortedWith(
        compareByDescending<LibraryShelfBrowserEntry> { entry -> entry.item.addedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )
}

private fun shelfBrowserSortModes(
    kind: LibraryShelfBrowserKind,
    entries: List<LibraryShelfBrowserEntry>,
): List<ShelfBrowserSortMode> = buildList {
    add(ShelfBrowserSortMode.ShelfOrder)
    add(ShelfBrowserSortMode.RecentActivity)
    if (kind == LibraryShelfBrowserKind.ContinueReading) {
        add(ShelfBrowserSortMode.Progress)
    }
    add(ShelfBrowserSortMode.Title)
    if (entries.any { entry -> entry.item.facetMetadata().series.isNotEmpty() }) {
        add(ShelfBrowserSortMode.Series)
    }
    if (entries.any { entry ->
            val metadata = entry.item.facetMetadata()
            metadata.authors.isNotEmpty() || metadata.writers.isNotEmpty()
        }
    ) {
        add(ShelfBrowserSortMode.Author)
    }
    if (entries.any { entry -> entry.item.addedAtEpochMillis != null }) {
        add(ShelfBrowserSortMode.DateAdded)
    }
}

@Composable
private fun LibraryShelfBrowserEntry.accentColor(): Color = when (kind) {
    LibraryShelfBrowserKind.ContinueReading -> UnifiedReaderThemeTokens.colors.readerAccent
    LibraryShelfBrowserKind.NextInSeries -> UnifiedReaderThemeTokens.colors.sourceAccent
    LibraryShelfBrowserKind.RecentlyFinished -> UnifiedReaderThemeTokens.colors.syncAccent
}

@Composable
private fun LibraryShelfBrowserKind.accentColor(): Color = when (this) {
    LibraryShelfBrowserKind.ContinueReading -> UnifiedReaderThemeTokens.colors.readerAccent
    LibraryShelfBrowserKind.NextInSeries -> UnifiedReaderThemeTokens.colors.sourceAccent
    LibraryShelfBrowserKind.RecentlyFinished -> UnifiedReaderThemeTokens.colors.syncAccent
}

private fun ReadableItem.compactMetadataText(): String? = buildList {
    pageCount?.takeIf { count -> count > 0 }?.let { count -> add("$count pages") }
    add(format.name)
    if (isLocalImport) {
        add("Local")
    }
}.joinedMetadata()

private fun List<String>.joinedMetadata(): String? = filter(String::isNotBlank)
    .takeIf(List<String>::isNotEmpty)
    ?.joinToString(" - ")

private fun Long.relativeTimeLabel(
    nowEpochMillis: Long,
): String? = takeIf { timestamp -> timestamp > 0L }?.let { timestamp ->
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        nowEpochMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

private const val ShelfBrowserHeaderKey = "shelf-browser-controls"
private val com.example.unifiedreader.feature.library.ItemProgressSummary.isShelfComplete: Boolean
    get() = isFinished || fraction >= 0.999f

internal enum class ShelfBrowserSortMode {
    ShelfOrder,
    RecentActivity,
    Progress,
    Title,
    Series,
    Author,
    DateAdded,
}

private fun ShelfBrowserSortMode.label(
    kind: LibraryShelfBrowserKind,
): String = when (this) {
    ShelfBrowserSortMode.ShelfOrder -> when (kind) {
        LibraryShelfBrowserKind.ContinueReading -> "Recently read"
        LibraryShelfBrowserKind.NextInSeries -> "Shelf order"
        LibraryShelfBrowserKind.RecentlyFinished -> "Recently finished"
    }

    ShelfBrowserSortMode.RecentActivity -> "Recent activity"
    ShelfBrowserSortMode.Progress -> "Progress"
    ShelfBrowserSortMode.Title -> "Title"
    ShelfBrowserSortMode.Series -> "Series"
    ShelfBrowserSortMode.Author -> "Author"
    ShelfBrowserSortMode.DateAdded -> "Date added"
}

private fun shelfBrowserSortOptionSection(
    sortMode: ShelfBrowserSortMode,
    availableSortModes: List<ShelfBrowserSortMode>,
    shelfKind: LibraryShelfBrowserKind,
): LibraryBrowseOptionSection = LibraryBrowseOptionSection(
    id = ShelfBrowserSortSectionId,
    title = "Sort",
    options = availableSortModes.map { mode ->
        LibraryBrowseOption(
            id = mode.name,
            label = mode.label(shelfKind),
            selected = mode == sortMode,
        )
    },
)

private fun shelfBrowserLayoutOptionSection(
    showListView: Boolean,
): LibraryBrowseOptionSection = LibraryBrowseOptionSection(
    id = ShelfBrowserLayoutSectionId,
    title = "Layout",
    options = listOf(
        LibraryBrowseOption(
            id = ShelfBrowserLayoutGridOptionId,
            label = ShelfBrowserLayoutGridLabel,
            selected = !showListView,
        ),
        LibraryBrowseOption(
            id = ShelfBrowserLayoutListOptionId,
            label = ShelfBrowserLayoutListLabel,
            selected = showListView,
        ),
    ),
)

private const val ShelfBrowserSortSectionId = "sort"
private const val ShelfBrowserLayoutSectionId = "layout"
private const val ShelfBrowserLayoutGridOptionId = "grid"
private const val ShelfBrowserLayoutListOptionId = "list"
private const val ShelfBrowserLayoutGridLabel = "Grid"
private const val ShelfBrowserLayoutListLabel = "List"
private const val ShelfBrowserCoverPrefetchMaxTargetsWhileScrolling = 6
private const val ShelfBrowserCoverPrefetchDelayMillis = 48L
private const val ShelfBrowserCoverPrefetchStaggerMillis = 16L
private const val ShelfBrowserCoverPrefetchScrollDelayMillis = 96L
private const val ShelfBrowserCoverPrefetchScrollStaggerMillis = 32L
