package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.libraryGridCoverImageVariant
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.appGutter
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Something picked on an Expanded window: the 320 dp pane on the gutter, each row in a compact form
 * of its shape, and the results beside it.
 */
@Composable
internal fun LibraryBrowseTwoPanes(
    rows: List<LibraryBrowseRow>,
    context: LibraryBrowseRowContext,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    gridCellSizeDp: Int,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
) {
    val view = context.view
    val actions = context.actions
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
        // A picked row's fill reaches past the pane's edges, so the list is that much wider and
        // padded back to the pane's line; a list clips at its own bounds.
        LazyColumn(
            state = paneState,
            modifier = Modifier
                .width(AppLayout.listPaneWidth)
                .browseBleed(PaneRowBleed)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                start = PaneRowBleed,
                end = PaneRowBleed,
                top = contentTopInset + AppLayout.contentTop,
                bottom = AppLayout.contentBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
        ) {
            items(items = rows.filter { row -> view.result.facets[row].orEmpty().isNotEmpty() }, key = { row -> row.name }) { row ->
                LibraryBrowsePaneGroup(row = row, context = context)
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

/** Something picked on a Medium or Compact window: one column, the rows as tabs above the results. */
@Composable
internal fun LibraryBrowseOnePane(
    rows: List<LibraryBrowseRow>,
    context: LibraryBrowseRowContext,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    gridCellSizeDp: Int,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
) {
    val view = context.view
    val actions = context.actions
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
            LibraryBrowseRowTabs(rows = rows, context = context)
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

private val PaneRowBleed = 12.dp
