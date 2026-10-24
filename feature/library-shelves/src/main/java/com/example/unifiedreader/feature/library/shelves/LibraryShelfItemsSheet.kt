package com.example.unifiedreader.feature.library.shelves

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryCoverProgressBar
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryItemPageButton
import com.example.unifiedreader.feature.library.defaultLibraryGridCellSizeDp
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.libraryGridCoverImageVariant
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.library.minLibraryGridCellSizeDp
import com.example.unifiedreader.feature.library.sanitizeLibraryGridCellSizeDp
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.AppMotion

enum class LibraryShelfBrowserKind {
    ContinueReading,
    NextInSeries,
    RecentlyFinished,
}

/** The shelf's name, as its tab and the bar say it. */
val LibraryShelfBrowserKind.title: String
    get() = when (this) {
        LibraryShelfBrowserKind.ContinueReading -> "In progress"
        LibraryShelfBrowserKind.NextInSeries -> "Up next"
        LibraryShelfBrowserKind.RecentlyFinished -> "Finished"
    }

/** One shelf as See all shows it; it can be empty while See all is open. */
sealed interface LibraryShelfBrowserState {
    val kind: LibraryShelfBrowserKind
    val books: List<ReadableItem>
    val title: String
        get() = kind.title
    val count: Int
        get() = books.size

    data class ContinueReading(
        val items: List<ContinueReadingShelfItem>,
    ) : LibraryShelfBrowserState {
        override val kind: LibraryShelfBrowserKind = LibraryShelfBrowserKind.ContinueReading
        override val books: List<ReadableItem> = items.map(ContinueReadingShelfItem::item)
    }

    data class NextInSeries(
        val items: List<NextInSeriesShelfItem>,
    ) : LibraryShelfBrowserState {
        override val kind: LibraryShelfBrowserKind = LibraryShelfBrowserKind.NextInSeries
        override val books: List<ReadableItem> = items.map(NextInSeriesShelfItem::item)
    }

    data class RecentlyFinished(
        val items: List<RecentlyFinishedShelfItem>,
    ) : LibraryShelfBrowserState {
        override val kind: LibraryShelfBrowserKind = LibraryShelfBrowserKind.RecentlyFinished
        override val books: List<ReadableItem> = items.map(RecentlyFinishedShelfItem::item)
    }
}

fun LibraryShelvesState.browserState(
    kind: LibraryShelfBrowserKind,
): LibraryShelfBrowserState = when (kind) {
    LibraryShelfBrowserKind.ContinueReading -> LibraryShelfBrowserState.ContinueReading(continueReading)
    LibraryShelfBrowserKind.NextInSeries -> LibraryShelfBrowserState.NextInSeries(nextInSeries)
    LibraryShelfBrowserKind.RecentlyFinished -> LibraryShelfBrowserState.RecentlyFinished(recentlyFinished)
}

/** See all's tabs: the shelves with something on them, and the open one even when it has emptied. */
internal fun LibraryShelvesState.browserTabs(
    selected: LibraryShelfBrowserKind,
): List<Pair<LibraryShelfBrowserKind, Int>> = LibraryShelfBrowserKind.entries
    .map { kind -> kind to browserState(kind).count }
    .filter { (kind, count) -> count > 0 || kind == selected }

/**
 * A shelf's "See all": the home band's tabs at the top, over the shelf's tiles (the strip's tile)
 * in a grid or a list. A tab switches shelves in place; each shelf keeps its sort and layout in
 * [viewState]. The bar above it (LibraryShelfBrowserTopBar) belongs to the app's shell.
 */
@Composable
fun LibraryShelfItemsBrowser(
    shelves: LibraryShelvesState,
    kind: LibraryShelfBrowserKind,
    onKindSelected: (LibraryShelfBrowserKind) -> Unit,
    viewState: LibraryShelfBrowserViewState,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onBack: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    browseContextKey: String = kind.name,
    gridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    onGridCellSizeDpChanged: (Int) -> Unit = {},
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val browser = remember(shelves, kind) { shelves.browserState(kind) }
    val allEntries = remember(browser) { browser.browserEntries(nowEpochMillis = System.currentTimeMillis()) }
    val availableSortModes = remember(browser) { shelfBrowserSortModes(kind = kind, items = browser.books) }
    val sortMode = viewState.sortMode(kind).takeIf { it in availableSortModes } ?: ShelfBrowserSortMode.ShelfOrder
    val showListView = viewState.showsList(kind)
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    val visibleEntries = remember(allEntries, searchQuery, sortMode) {
        allEntries.searchedBy(searchQuery).sortedFor(sortMode)
    }
    // When a tab last switched shelves: tiles that appear just after slide in, like the home strip's.
    var switchedAtMillis by remember { mutableLongStateOf(Long.MIN_VALUE) }
    val selectKind: (LibraryShelfBrowserKind) -> Unit = { next ->
        if (next != kind) {
            switchedAtMillis = SystemClock.uptimeMillis()
            onKindSelected(next)
        }
    }

    LaunchedEffect(kind, searchQuery, sortMode, showListView, sanitizedGridCellSizeDp) {
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
        // The tabs are the first row of the grid or list, so the whole page scrolls under the glass
        // bar like the library home. On a phone the options are in the bar instead.
        val showOptionsInRow = currentAppWindowWidth() != AppWindowWidth.Compact
        val header: @Composable () -> Unit = {
            ShelfBrowserHeader(
                tabs = shelves.browserTabs(kind),
                kind = kind,
                onKindSelected = selectKind,
                options = if (showOptionsInRow) {
                    {
                        LibraryShelfBrowserOptionsButton(
                            shelves = shelves,
                            kind = kind,
                            viewState = viewState,
                            gridCellSizeDp = sanitizedGridCellSizeDp,
                            onGridCellSizeDpChanged = onGridCellSizeDpChanged,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
        if (visibleEntries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp + contentTopInset, start = 16.dp, end = 16.dp),
            ) {
                header()
                ShelfBrowserEmptyState(
                    kind = kind,
                    searchQuery = searchQuery,
                    onClearSearch = { onSearchQueryChanged("") },
                    onBack = onBack,
                )
            }
        } else if (showListView) {
            ShelfBrowserList(
                kind = kind,
                entries = visibleEntries,
                header = header,
                contentTopInset = contentTopInset,
                listState = listState,
                switchedAtMillis = switchedAtMillis,
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onItemSelected = onItemSelected,
            )
        } else {
            ShelfBrowserGrid(
                kind = kind,
                entries = visibleEntries,
                header = header,
                contentTopInset = contentTopInset,
                gridCellSizeDp = sanitizedGridCellSizeDp,
                gridState = gridState,
                switchedAtMillis = switchedAtMillis,
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                onItemSelected = onItemSelected,
            )
        }
    }
}

/** The shelves as the home band's pill tabs, with the options pill at the end on a tablet. */
@Composable
private fun ShelfBrowserHeader(
    tabs: List<Pair<LibraryShelfBrowserKind, Int>>,
    kind: LibraryShelfBrowserKind,
    onKindSelected: (LibraryShelfBrowserKind) -> Unit,
    options: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (tabKind, count) ->
                LibraryShelfTabPill(
                    label = tabKind.title,
                    count = count,
                    selected = tabKind == kind,
                    onClick = { onKindSelected(tabKind) },
                )
            }
        }
        options?.invoke()
    }
}

@Composable
private fun ShelfBrowserGrid(
    kind: LibraryShelfBrowserKind,
    entries: List<LibraryShelfBrowserEntry>,
    header: @Composable () -> Unit,
    contentTopInset: Dp,
    gridCellSizeDp: Int,
    gridState: LazyGridState,
    switchedAtMillis: Long,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: (ReadableItem) -> Unit,
) {
    val compactGrid = gridCellSizeDp <= minLibraryGridCellSizeDp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(gridCellSizeDp.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + contentTopInset, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactGrid) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = ShelfBrowserHeaderKey, span = { GridItemSpan(maxLineSpan) }) { header() }
        items(
            items = entries,
            // Per shelf, so switching tabs replaces the tiles (they fade) instead of moving them.
            key = { entry -> "${kind.name}:${entry.item.id}" },
        ) { entry ->
            ShelfBrowserGridCard(
                entry = entry,
                compact = compactGrid,
                imageVariant = libraryGridCoverImageVariant(gridCellSizeDp),
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                onItemSelected = { onItemSelected(entry.item) },
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = AppMotion.effects(),
                        placementSpec = null,
                        fadeOutSpec = tween(durationMillis = ShelfSwitchFadeOutMillis),
                    )
                    .shelfSwitchEntrance(switchedAtMillis),
            )
        }
    }
}

@Composable
private fun ShelfBrowserList(
    kind: LibraryShelfBrowserKind,
    entries: List<LibraryShelfBrowserEntry>,
    header: @Composable () -> Unit,
    contentTopInset: Dp,
    listState: LazyListState,
    switchedAtMillis: Long,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
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
            key = { entry -> "${kind.name}:${entry.item.id}" },
        ) { entry ->
            Box(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = AppMotion.effects(),
                        placementSpec = null,
                        fadeOutSpec = tween(durationMillis = ShelfSwitchFadeOutMillis),
                    )
                    .shelfSwitchEntrance(switchedAtMillis),
            ) {
                ShelfListRow(
                    item = entry.item,
                    title = entry.title,
                    supportingText = entry.supportingText,
                    trailingText = entry.trailingText,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(entry.item) },
                    menuContent = {
                        LibraryItemPageButton(
                            item = entry.item,
                            actionContext = itemActionContext,
                            buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                        )
                    },
                    progressFraction = entry.progressFraction,
                    isDownloaded = itemActionContext.isDownloaded(entry.item),
                    isRead = entry.isRead,
                    notifyCoverVisibleOnCompose = false,
                )
            }
        }
    }
}

/** The home strip's tile, at the grid's cover size. */
@Composable
private fun ShelfBrowserGridCard(
    entry: LibraryShelfBrowserEntry,
    compact: Boolean,
    imageVariant: LibraryCoverImageVariant,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryCoverActionCard(
        item = entry.item,
        title = entry.title,
        supportingText = entry.supportingText,
        trailingText = entry.trailingText,
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
        metadataTopPadding = 8.dp,
        menuContent = {
            LibraryItemPageButton(
                item = entry.item,
                actionContext = itemActionContext,
                buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
            )
        },
        modifier = modifier.fillMaxWidth(),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
        coverOverlay = {
            entry.progressFraction?.let { fraction ->
                LibraryCoverProgressBar(
                    fraction = fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        },
    )
}

/** A search that matched nothing, or a shelf that emptied while See all was open. */
@Composable
private fun ShelfBrowserEmptyState(
    kind: LibraryShelfBrowserKind,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onBack: () -> Unit,
) {
    if (searchQuery.isNotBlank()) {
        AppEmptyState(
            title = "Nothing matches “${searchQuery.trim()}”",
            body = "See all searches titles, authors and series.",
            actions = {
                AppTonalButton(onClick = onClearSearch) {
                    Text(text = "Clear search", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    } else {
        AppEmptyState(
            title = when (kind) {
                LibraryShelfBrowserKind.ContinueReading -> "Nothing in progress"
                LibraryShelfBrowserKind.NextInSeries -> "Up next is empty"
                LibraryShelfBrowserKind.RecentlyFinished -> "Nothing finished yet"
            },
            body = when (kind) {
                LibraryShelfBrowserKind.ContinueReading -> "Books you've started show up here."
                LibraryShelfBrowserKind.NextInSeries -> "The next issue of a series you're reading shows up here."
                LibraryShelfBrowserKind.RecentlyFinished -> "Books you finish show up here."
            },
            actions = {
                AppButton(onClick = onBack) {
                    Text(text = "Back to library", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

/** A tile that appears just after a tab switch slides in from 12 dp, on spatial. */
@Composable
private fun Modifier.shelfSwitchEntrance(switchedAtMillis: Long): Modifier {
    val slide = with(LocalDensity.current) { ShelfSwitchSlide.toPx() }
    val offset = remember {
        val justSwitched = SystemClock.uptimeMillis() - switchedAtMillis < ShelfSwitchWindowMillis
        Animatable(if (justSwitched) slide else 0f)
    }
    LaunchedEffect(offset) {
        offset.animateTo(0f, AppMotion.spatial())
    }
    return graphicsLayer { translationX = offset.value }
}

private const val ShelfBrowserHeaderKey = "shelf-browser-tabs"
private val ShelfSwitchSlide = 12.dp
private const val ShelfSwitchWindowMillis = 300L
private const val ShelfSwitchFadeOutMillis = 90
