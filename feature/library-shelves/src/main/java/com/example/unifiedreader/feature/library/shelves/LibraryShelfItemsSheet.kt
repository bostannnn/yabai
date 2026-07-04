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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryAnchoredItemMenu
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryDeleteImportedItemDialog
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.hasMenuContent
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderProgressTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern
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
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val pageRecipe = UnifiedReaderThemeTokens.components.sheet
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf("") }
    var sortMode by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf(ShelfBrowserSortMode.ShelfOrder) }
    var viewMode by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf(ShelfBrowserViewMode.Grid) }
    var density by rememberSaveable(browseContextKey, browser.kind) { mutableStateOf(ShelfBrowserDensity.Comfortable) }
    var expandedMenuItemId by remember(browseContextKey, browser.kind, browser.count) { mutableStateOf<String?>(null) }
    var deleteCandidate by remember(browseContextKey, browser.kind) { mutableStateOf<ReadableItem?>(null) }
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
    val visibleEntries = remember(allEntries, searchQuery, sortMode) {
        allEntries
            .searchedBy(searchQuery)
            .sortedFor(sortMode)
    }

    LaunchedEffect(browser.kind, searchQuery, sortMode, viewMode, density) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }
    ShelfBrowserVisibleCoverPrefetcher(
        browseContextKey = browseContextKey,
        viewMode = viewMode,
        gridState = gridState,
        listState = listState,
        entries = visibleEntries,
        variant = shelfBrowserCoverImageVariant(
            viewMode = viewMode,
            density = density,
        ),
        coverPrefetchKeyProvider = coverPrefetchKeyProvider,
        onCoverVisible = onCoverVisible,
        onCoverViewportScrollingChanged = onCoverViewportScrollingChanged,
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .unifiedReaderSurfacePattern(
                    treatment = pageRecipe.surfaceTreatment,
                    accentColor = browser.kind.accentColor(),
                    overlayColor = MaterialTheme.colorScheme.onSurface,
                    intensity = pageRecipe.patternIntensity,
                ),
        ) {
            ShelfBrowserTopBar(
                title = browser.title,
                count = browser.count,
                visibleCount = visibleEntries.size,
                searchActive = searchQuery.isNotBlank(),
                onBack = onBack,
            )
            ShelfBrowserControls(
                searchQuery = searchQuery,
                sortMode = sortMode,
                viewMode = viewMode,
                density = density,
                availableSortModes = availableSortModes,
                shelfKind = browser.kind,
                onSearchQueryChanged = { searchQuery = it },
                onSortModeChanged = { sortMode = it },
                onViewModeChanged = { viewMode = it },
                onDensityChanged = { density = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(semanticColors.surfaceOverlay.copy(alpha = 0.36f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (visibleEntries.isEmpty()) {
                    ShelfBrowserEmptyState(
                        searchActive = searchQuery.isNotBlank(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ShelfBrowserItems(
                        entries = visibleEntries,
                        viewMode = viewMode,
                        density = density,
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

@Deprecated("Use LibraryShelfItemsBrowser for the full-screen shelf browser.")
@Composable
internal fun LibraryShelfItemsSheet(
    sheet: LibraryShelfBrowserState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onDismiss: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
) {
    LibraryShelfItemsBrowser(
        browser = sheet,
        itemActionContext = itemActionContext,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onBack = onDismiss,
        onItemSelected = onItemSelected,
        onDeleteImportedItem = onDeleteImportedItem,
    )
}

@Composable
private fun ShelfBrowserTopBar(
    title: String,
    count: Int,
    visibleCount: Int,
    searchActive: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(start = 4.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (searchActive) {
                    "${visibleCount.itemCountLabel()} shown from ${count.itemCountLabel()}"
                } else {
                    count.itemCountLabel()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShelfBrowserControls(
    searchQuery: String,
    sortMode: ShelfBrowserSortMode,
    viewMode: ShelfBrowserViewMode,
    density: ShelfBrowserDensity,
    availableSortModes: List<ShelfBrowserSortMode>,
    shelfKind: LibraryShelfBrowserKind,
    onSearchQueryChanged: (String) -> Unit,
    onSortModeChanged: (ShelfBrowserSortMode) -> Unit,
    onViewModeChanged: (ShelfBrowserViewMode) -> Unit,
    onDensityChanged: (ShelfBrowserDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth < ShelfBrowserWideControlsBreakpoint) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShelfBrowserSearchField(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
                ShelfBrowserOptionChips(
                    sortMode = sortMode,
                    viewMode = viewMode,
                    density = density,
                    availableSortModes = availableSortModes,
                    shelfKind = shelfKind,
                    onSortModeChanged = onSortModeChanged,
                    onViewModeChanged = onViewModeChanged,
                    onDensityChanged = onDensityChanged,
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShelfBrowserSearchField(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    modifier = Modifier.weight(1f),
                )
                ShelfBrowserOptionChips(
                    sortMode = sortMode,
                    viewMode = viewMode,
                    density = density,
                    availableSortModes = availableSortModes,
                    shelfKind = shelfKind,
                    onSortModeChanged = onSortModeChanged,
                    onViewModeChanged = onViewModeChanged,
                    onDensityChanged = onDensityChanged,
                )
            }
        }
    }
}

@Composable
private fun ShelfBrowserSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                    )
                }
            }
        } else {
            null
        },
        label = { Text(text = "Search shelf") },
        shape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius),
    )
}

@Composable
private fun ShelfBrowserOptionChips(
    sortMode: ShelfBrowserSortMode,
    viewMode: ShelfBrowserViewMode,
    density: ShelfBrowserDensity,
    availableSortModes: List<ShelfBrowserSortMode>,
    shelfKind: LibraryShelfBrowserKind,
    onSortModeChanged: (ShelfBrowserSortMode) -> Unit,
    onViewModeChanged: (ShelfBrowserViewMode) -> Unit,
    onDensityChanged: (ShelfBrowserDensity) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfBrowserSortChip(
            sortMode = sortMode,
            availableSortModes = availableSortModes,
            shelfKind = shelfKind,
            onSortModeChanged = onSortModeChanged,
        )
        ShelfBrowserViewChip(
            viewMode = viewMode,
            density = density,
            onViewModeChanged = onViewModeChanged,
            onDensityChanged = onDensityChanged,
        )
    }
}

@Composable
private fun ShelfBrowserSortChip(
    sortMode: ShelfBrowserSortMode,
    availableSortModes: List<ShelfBrowserSortMode>,
    shelfKind: LibraryShelfBrowserKind,
    onSortModeChanged: (ShelfBrowserSortMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = sortMode != ShelfBrowserSortMode.ShelfOrder,
            onClick = { expanded = true },
            colors = FilterChipDefaults.filterChipColors(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            label = {
                Text(
                    text = sortMode.label(shelfKind),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownSectionHeader(text = "Sort")
            availableSortModes.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.label(shelfKind),
                            fontWeight = if (mode == sortMode) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortModeChanged(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun ShelfBrowserViewChip(
    viewMode: ShelfBrowserViewMode,
    density: ShelfBrowserDensity,
    onViewModeChanged: (ShelfBrowserViewMode) -> Unit,
    onDensityChanged: (ShelfBrowserDensity) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = viewMode != ShelfBrowserViewMode.Grid || density != ShelfBrowserDensity.Comfortable,
            onClick = { expanded = true },
            colors = FilterChipDefaults.filterChipColors(),
            leadingIcon = {
                Icon(
                    imageVector = if (viewMode == ShelfBrowserViewMode.Grid) {
                        Icons.Default.GridView
                    } else {
                        Icons.AutoMirrored.Filled.ViewList
                    },
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            label = {
                Text(
                    text = "${viewMode.label} - ${density.label}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownSectionHeader(text = "Layout")
            ShelfBrowserViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.label,
                            fontWeight = if (mode == viewMode) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (mode == ShelfBrowserViewMode.Grid) {
                            Icons.Default.GridView
                        } else {
                            Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onViewModeChanged(mode)
                    },
                )
            }
            HorizontalDivider()
            DropdownSectionHeader(text = "Density")
            ShelfBrowserDensity.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (option == density) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onDensityChanged(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun DropdownSectionHeader(
    text: String,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = {},
        enabled = false,
    )
}

@Composable
private fun ShelfBrowserItems(
    entries: List<LibraryShelfBrowserEntry>,
    viewMode: ShelfBrowserViewMode,
    density: ShelfBrowserDensity,
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
    when (viewMode) {
        ShelfBrowserViewMode.Grid -> ShelfBrowserGrid(
            entries = entries,
            density = density,
            gridState = gridState,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onExpandedMenuChange = onExpandedMenuChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
        )

        ShelfBrowserViewMode.List -> ShelfBrowserList(
            entries = entries,
            density = density,
            listState = listState,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
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
    density: ShelfBrowserDensity,
    gridState: LazyGridState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    val cellMinSize = when (density) {
        ShelfBrowserDensity.Comfortable -> 150.dp
        ShelfBrowserDensity.Compact -> 118.dp
    }
    val imageVariant = when (density) {
        ShelfBrowserDensity.Comfortable -> LibraryCoverImageVariant.GridLarge
        ShelfBrowserDensity.Compact -> LibraryCoverImageVariant.Grid
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellMinSize),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (density == ShelfBrowserDensity.Compact) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = entries,
            key = { it.item.id },
        ) { entry ->
            ShelfBrowserGridCard(
                entry = entry,
                density = density,
                imageVariant = imageVariant,
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
    density: ShelfBrowserDensity,
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (density == ShelfBrowserDensity.Compact) 6.dp else 10.dp),
    ) {
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
                compact = density == ShelfBrowserDensity.Compact,
                notifyCoverVisibleOnCompose = false,
            )
        }
    }
}

@Composable
private fun ShelfBrowserGridCard(
    entry: LibraryShelfBrowserEntry,
    density: ShelfBrowserDensity,
    imageVariant: LibraryCoverImageVariant,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: () -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val hasMenuContent = itemActionContext.hasMenuContent(entry.item)
    val metadata = if (density == ShelfBrowserDensity.Compact) {
        entry.trailingText
    } else {
        entry.metadataText ?: entry.trailingText
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(presentation.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = semanticColors.surfaceRaised.copy(alpha = 0.76f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .unifiedReaderSurfacePattern(
                    treatment = UnifiedReaderThemeTokens.personality.surfaceTreatment,
                    accentColor = entry.accentColor(),
                )
                .padding(if (density == ShelfBrowserDensity.Compact) 8.dp else 10.dp),
        ) {
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
                titleStyle = if (density == ShelfBrowserDensity.Compact) {
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
}

@Composable
private fun BoxScope.ShelfBrowserProgressOverlay(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val personality = UnifiedReaderThemeTokens.personality
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(
                RoundedCornerShape(
                    bottomStart = presentation.coverCornerRadius,
                    bottomEnd = presentation.coverCornerRadius,
                ),
            )
            .unifiedReaderProgressTreatment(
                treatment = personality.progressTreatment,
                fraction = fraction,
                progressColor = semanticColors.readerAccent,
                trackColor = semanticColors.surfaceSelected.copy(alpha = 0.36f),
            ),
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
    viewMode: ShelfBrowserViewMode,
    gridState: LazyGridState,
    listState: LazyListState,
    entries: List<LibraryShelfBrowserEntry>,
    variant: LibraryCoverImageVariant,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onCoverViewportScrollingChanged: (Boolean) -> Unit,
) {
    val signaledPrefetchKeys = remember(browseContextKey, variant) { mutableSetOf<String>() }

    LaunchedEffect(viewMode, gridState, listState, onCoverViewportScrollingChanged) {
        snapshotFlow {
            when (viewMode) {
                ShelfBrowserViewMode.Grid -> gridState.isScrollInProgress
                ShelfBrowserViewMode.List -> listState.isScrollInProgress
            }
        }
            .distinctUntilChanged()
            .collect { isScrolling -> onCoverViewportScrollingChanged(isScrolling) }
    }
    DisposableEffect(viewMode, onCoverViewportScrollingChanged) {
        onDispose {
            onCoverViewportScrollingChanged(false)
        }
    }

    LaunchedEffect(
        browseContextKey,
        viewMode,
        gridState,
        listState,
        entries,
        variant,
        coverPrefetchKeyProvider,
        onCoverVisible,
    ) {
        snapshotFlow {
            when (viewMode) {
                ShelfBrowserViewMode.Grid -> ShelfBrowserCoverPrefetchRequest(
                    visibleItemIndices = gridState.layoutInfo.visibleItemsInfo.map { visibleItem -> visibleItem.index },
                    isScrollInProgress = gridState.isScrollInProgress,
                    scrollDirection = when {
                        gridState.lastScrolledForward -> ShelfBrowserCoverPrefetchScrollDirection.Forward
                        gridState.lastScrolledBackward -> ShelfBrowserCoverPrefetchScrollDirection.Backward
                        else -> ShelfBrowserCoverPrefetchScrollDirection.Idle
                    },
                )

                ShelfBrowserViewMode.List -> ShelfBrowserCoverPrefetchRequest(
                    visibleItemIndices = listState.layoutInfo.visibleItemsInfo.map { visibleItem -> visibleItem.index },
                    isScrollInProgress = listState.isScrollInProgress,
                    scrollDirection = when {
                        listState.lastScrolledForward -> ShelfBrowserCoverPrefetchScrollDirection.Forward
                        listState.lastScrolledBackward -> ShelfBrowserCoverPrefetchScrollDirection.Backward
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
    viewMode: ShelfBrowserViewMode,
    density: ShelfBrowserDensity,
): LibraryCoverImageVariant = when (viewMode) {
    ShelfBrowserViewMode.List -> LibraryCoverImageVariant.ShelfRow
    ShelfBrowserViewMode.Grid -> when (density) {
        ShelfBrowserDensity.Comfortable -> LibraryCoverImageVariant.GridLarge
        ShelfBrowserDensity.Compact -> LibraryCoverImageVariant.Grid
    }
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

private data class LibraryShelfBrowserEntry(
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

private fun Int.itemCountLabel(): String = when (this) {
    1 -> "1 item"
    else -> "$this items"
}

private val com.example.unifiedreader.feature.library.ItemProgressSummary.isShelfComplete: Boolean
    get() = isFinished || fraction >= 0.999f

private enum class ShelfBrowserSortMode {
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

private enum class ShelfBrowserViewMode(
    val label: String,
) {
    Grid("Grid"),
    List("List"),
}

private enum class ShelfBrowserDensity(
    val label: String,
) {
    Comfortable("Comfortable"),
    Compact("Compact"),
}

private val ShelfBrowserWideControlsBreakpoint = 720.dp
private const val ShelfBrowserCoverPrefetchMaxTargetsWhileScrolling = 6
private const val ShelfBrowserCoverPrefetchDelayMillis = 48L
private const val ShelfBrowserCoverPrefetchStaggerMillis = 16L
private const val ShelfBrowserCoverPrefetchScrollDelayMillis = 96L
private const val ShelfBrowserCoverPrefetchScrollStaggerMillis = 32L
