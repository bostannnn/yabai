package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseResult
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryEraBar
import com.example.unifiedreader.core.model.LibraryPlaceGroup
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryBrowse
import com.example.unifiedreader.core.model.libraryBrowseValues
import com.example.unifiedreader.core.model.libraryEraTimeline
import com.example.unifiedreader.core.model.libraryPlaceGroups
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.isSeriesContainer
import com.example.unifiedreader.feature.library.libraryGridCoverImageVariant
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * A source's books by what they are about. With nothing picked, the landing page: places by
 * region, a timeline, then the other rows' chips. With chips picked, the books that have all of
 * them, and each row's chips counted among those books: a 320 dp pane beside the grid on Expanded
 * windows, one column with the rows as tabs on narrower ones.
 */
@Composable
fun LibraryBrowsePage(
    source: SourceType,
    items: List<ReadableItem>,
    request: LibraryBrowseRequest,
    onRequestChanged: (LibraryBrowseRequest) -> Unit,
    showRows: Boolean,
    onShowRowsChanged: (Boolean) -> Unit,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    gridCellSizeDp: Int,
    contentTopInset: Dp,
    onClose: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = rememberLibraryBrowseRows(source)
    val shownRows = rows.settings.shown
    val progress = actionContext.progressByItemId
    // Thousands of books to sort into chips, so not on the main thread. The last view stays up
    // until the next is ready, so the chips and the grid change together.
    val view by produceState<LibraryBrowseView?>(null, items, request.picked, shownRows, progress) {
        value = withContext(Dispatchers.Default) {
            libraryBrowseView(items, request.picked, shownRows) { item -> libraryBrowseReadingStatus(progress[item.id]) }
        }
    }
    // How many books have each row's field, for Rows: the source's, whatever is picked.
    val coverage by produceState(emptyMap<LibraryBrowseRow, Int>(), items, progress) {
        value = withContext(Dispatchers.Default) {
            libraryBrowseCoverage(items) { item -> libraryBrowseReadingStatus(progress[item.id]) }
        }
    }
    val latestRequest by rememberUpdatedState(request)
    val actions = LibraryBrowseActions(
        onPick = { chip -> onRequestChanged(latestRequest.adding(chip)) },
        onToggle = { chip -> onRequestChanged(latestRequest.toggling(chip)) },
        onRemove = { chip -> onRequestChanged(latestRequest.removing(chip)) },
        onClear = { onRequestChanged(LibraryBrowseRequest()) },
        // A series opens as a screen of its own, so Browse steps aside for it.
        onItemSelected = { item ->
            if (item.isSeriesContainer) onClose()
            onItemSelected(item)
        },
    )
    Box(modifier = modifier.fillMaxSize()) {
        val shown = view
        when {
            shown == null -> Unit
            shown.picked.isEmpty() -> LibraryBrowseLanding(
                view = shown,
                rows = shownRows,
                contentTopInset = contentTopInset,
                onPick = actions.onPick,
                onScrolledChanged = onScrolledChanged,
            )
            currentAppWindowWidth() == AppWindowWidth.Expanded -> LibraryBrowseTwoPanes(
                view = shown,
                rows = shownRows,
                actions = actions,
                actionContext = actionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                gridCellSizeDp = gridCellSizeDp,
                contentTopInset = contentTopInset,
                onScrolledChanged = onScrolledChanged,
            )
            else -> LibraryBrowseOnePane(
                view = shown,
                rows = shownRows,
                actions = actions,
                actionContext = actionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                gridCellSizeDp = gridCellSizeDp,
                contentTopInset = contentTopInset,
                onScrolledChanged = onScrolledChanged,
            )
        }
    }
    if (showRows) {
        LibraryBrowseRowsSheet(
            settings = rows.settings,
            coverage = coverage,
            onRowChanged = rows::set,
            onDismiss = { onShowRowsChanged(false) },
        )
    }
}

/** What Browse draws, worked out together from the source's books and the [picked] chips. */
@Immutable
internal data class LibraryBrowseView(
    val picked: List<LibraryBrowseChip>,
    val result: LibraryBrowseResult,
    val timeline: List<LibraryEraBar> = emptyList(),
    val places: List<LibraryPlaceGroup> = emptyList(),
    /** On the landing page, how many books have each row's field, for its headings. */
    val coverage: Map<LibraryBrowseRow, Int> = emptyMap(),
)

internal fun libraryBrowseView(
    items: List<ReadableItem>,
    picked: List<LibraryBrowseChip>,
    rows: List<LibraryBrowseRow>,
    readingStatus: (ReadableItem) -> LibraryReadingStatus?,
): LibraryBrowseView {
    val landing = picked.isEmpty()
    return LibraryBrowseView(
        picked = picked,
        result = libraryBrowse(items, picked, rows, readingStatus),
        timeline = if (landing) libraryEraTimeline(items) else emptyList(),
        places = if (landing) libraryPlaceGroups(items) else emptyList(),
        coverage = if (landing) libraryBrowseCoverage(items, readingStatus) else emptyMap(),
    )
}

/** How many of [items] have each row's field; a row at 0 waits for metadata. */
internal fun libraryBrowseCoverage(
    items: List<ReadableItem>,
    readingStatus: (ReadableItem) -> LibraryReadingStatus?,
): Map<LibraryBrowseRow, Int> = LibraryBrowseRow.entries.associateWith { row ->
    items.count { item -> item.libraryBrowseValues(row, readingStatus(item)).isNotEmpty() }
}

internal class LibraryBrowseActions(
    val onPick: (LibraryBrowseChip) -> Unit,
    val onToggle: (LibraryBrowseChip) -> Unit,
    val onRemove: (LibraryBrowseChip) -> Unit,
    val onClear: () -> Unit,
    val onItemSelected: (ReadableItem) -> Unit,
)

@Composable
private fun LibraryBrowseTwoPanes(
    view: LibraryBrowseView,
    rows: List<LibraryBrowseRow>,
    actions: LibraryBrowseActions,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    gridCellSizeDp: Int,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
) {
    val paneState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val latestOnScrolledChanged by rememberUpdatedState(onScrolledChanged)
    LaunchedEffect(paneState, gridState) {
        snapshotFlow { paneState.canScrollBackward || gridState.canScrollBackward }
            .distinctUntilChanged()
            .collect { scrolled -> latestOnScrolledChanged(scrolled) }
    }
    val gutter = appGutter()
    val panePadding = PaddingValues(top = contentTopInset + AppLayout.contentTop, bottom = AppLayout.contentBottom)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(gutter),
    ) {
        LazyColumn(
            state = paneState,
            modifier = Modifier
                .width(AppLayout.listPaneWidth)
                .fillMaxHeight(),
            contentPadding = panePadding,
            verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
        ) {
            items(items = rows.filter { row -> view.result.facets[row].orEmpty().isNotEmpty() }, key = { row -> row.name }) { row ->
                LibraryBrowseChipGroup(
                    row = row,
                    facets = view.result.facets[row].orEmpty(),
                    picked = view.picked,
                    onToggle = actions.onToggle,
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridCellSizeDp.dp),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = panePadding,
            verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        ) {
            item(key = "picked", span = { GridItemSpan(maxLineSpan) }) {
                LibraryBrowsePickedHeader(view = view, actions = actions)
            }
            items(items = view.result.items, key = { item -> item.id }) { item ->
                LibraryBrowseCover(
                    item = item,
                    actionContext = actionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    imageVariant = libraryGridCoverImageVariant(gridCellSizeDp),
                    onClick = { actions.onItemSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun LibraryBrowseOnePane(
    view: LibraryBrowseView,
    rows: List<LibraryBrowseRow>,
    actions: LibraryBrowseActions,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    gridCellSizeDp: Int,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val latestOnScrolledChanged by rememberUpdatedState(onScrolledChanged)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.canScrollBackward }
            .distinctUntilChanged()
            .collect { scrolled -> latestOnScrolledChanged(scrolled) }
    }
    val gutter = appGutter()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(gridCellSizeDp.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = contentTopInset + AppLayout.contentTop,
            bottom = AppLayout.contentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        item(key = "picked", span = { GridItemSpan(maxLineSpan) }) {
            LibraryBrowseRowTabs(
                view = view,
                rows = rows,
                actions = actions,
            )
        }
        items(items = view.result.items, key = { item -> item.id }) { item ->
            LibraryBrowseCover(
                item = item,
                actionContext = actionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                imageVariant = libraryGridCoverImageVariant(gridCellSizeDp),
                onClick = { actions.onItemSelected(item) },
            )
        }
    }
}
