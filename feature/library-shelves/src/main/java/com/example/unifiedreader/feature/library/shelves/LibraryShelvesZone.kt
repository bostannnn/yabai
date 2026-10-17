package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryDeleteImportedItemDialog
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.libraryGridColumns

@Composable
fun LibraryShelvesZone(
    browseContextKey: String = "library-shelves",
    state: LibraryShelvesState,
    gridMetrics: LibraryGridMetrics = LibraryGridMetrics(),
    itemActionContext: LibraryItemActionContext = LibraryItemActionContext(),
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onBrowseShelf: (LibraryShelfBrowserKind) -> Unit = {},
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!state.hasContent) return

    var selectedHistoryTab by rememberSaveable(browseContextKey) {
        mutableStateOf(ShelfHistoryTab.UpNext)
    }
    val historyTab = visibleHistoryTab(state, selectedHistoryTab)
    var deleteCandidate by remember(state) { mutableStateOf<ReadableItem?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = with(LocalDensity.current) {
            libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics)
        }
        val rowLayout = ShelfRowLayout(
            cellWidth = columns.cellWidth,
            spacing = gridMetrics.spacing,
            itemLimit = shelfPreviewItemLimit(columns.count),
            coverImageVariant = gridMetrics.coverImageVariant,
        )
        val previewDatasetSignature = remember(state, rowLayout.itemLimit) {
            previewDatasetSignature(state, rowLayout.itemLimit)
        }
        var previewExpandedMenuItemId by remember(browseContextKey, previewDatasetSignature) {
            mutableStateOf<String?>(null)
        }
        val previewCoverTargets = remember(state, historyTab, rowLayout, coverPrefetchKeyProvider) {
            shelfPreviewCoverPrefetchTargets(
                sections = visibleShelfSections(state, historyTab),
                itemLimitPerSection = rowLayout.itemLimit,
                variant = rowLayout.coverImageVariant,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            )
        }

        LibraryShelvesPreviewCoverPrefetcher(
            browseContextKey = browseContextKey,
            coverLoadingEnabled = true,
            targets = previewCoverTargets,
            variant = rowLayout.coverImageVariant,
            onCoverVisible = onCoverVisible,
        )

        val onExpandedMenuChange: (ReadableItem, Boolean) -> Unit = { item, expanded ->
            previewExpandedMenuItemId = if (expanded) item.id else null
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ShelfSectionSpacing),
        ) {
            if (state.continueReading.isNotEmpty()) {
                ContinueReadingShelfSection(
                    items = state.continueReading,
                    rowLayout = rowLayout,
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onItemSelected = onItemSelected,
                    expandedMenuItemId = previewExpandedMenuItemId,
                    onExpandedMenuChange = onExpandedMenuChange,
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onSeeAll = {
                        onBrowseShelf(LibraryShelfBrowserKind.ContinueReading)
                    },
                )
            }
            if (historyTab != null) {
                ReadingHistoryShelfSection(
                    nextInSeries = state.nextInSeries,
                    recentlyFinished = state.recentlyFinished,
                    selectedTab = historyTab,
                    onTabSelected = { selectedHistoryTab = it },
                    rowLayout = rowLayout,
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onItemSelected = onItemSelected,
                    expandedMenuItemId = previewExpandedMenuItemId,
                    onExpandedMenuChange = onExpandedMenuChange,
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onSeeAll = { tab ->
                        onBrowseShelf(tab.browserKind)
                    },
                )
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

/** Up Next and Recently Finished share one row; this is the one showing, or null when both are empty. */
private fun visibleHistoryTab(
    state: LibraryShelvesState,
    selectedTab: ShelfHistoryTab,
): ShelfHistoryTab? = when {
    state.nextInSeries.isEmpty() && state.recentlyFinished.isEmpty() -> null
    state.nextInSeries.isEmpty() -> ShelfHistoryTab.RecentlyFinished
    state.recentlyFinished.isEmpty() -> ShelfHistoryTab.UpNext
    else -> selectedTab
}

private fun visibleShelfSections(
    state: LibraryShelvesState,
    historyTab: ShelfHistoryTab?,
): List<List<ReadableItem>> = listOf(
    state.continueReading.map(ContinueReadingShelfItem::item),
    when (historyTab) {
        ShelfHistoryTab.UpNext -> state.nextInSeries.map(NextInSeriesShelfItem::item)
        ShelfHistoryTab.RecentlyFinished -> state.recentlyFinished.map(RecentlyFinishedShelfItem::item)
        null -> emptyList()
    },
)

/**
 * A row fills the grid's columns, so it lines up with the grid; on narrow screens with only a couple
 * of columns it still shows a few covers and scrolls sideways.
 */
private fun shelfPreviewItemLimit(
    columnCount: Int,
): Int = maxOf(columnCount, ShelfPreviewMinItemCount)

private fun previewDatasetSignature(
    state: LibraryShelvesState,
    itemLimit: Int,
): List<String> = buildList {
    add("continue")
    addAll(state.continueReading.take(itemLimit).map { it.item.id })
    add("next")
    addAll(state.nextInSeries.take(itemLimit).map { it.item.id })
    add("finished")
    addAll(state.recentlyFinished.take(itemLimit).map { it.item.id })
}

private const val ShelfPreviewMinItemCount = 4
private val ShelfSectionSpacing = 24.dp
