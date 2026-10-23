package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryDeleteImportedItemDialog
import com.example.unifiedreader.feature.library.LibraryGridColumns
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryReadingCard
import com.example.unifiedreader.feature.library.isLibraryCompactWindow
import com.example.unifiedreader.feature.library.libraryGridColumns

/**
 * The library home's top: the reading card for the book read last, and the shelves as tabs over
 * one strip of covers. With six grid columns or more the two sit side by side, the card over
 * three columns; otherwise the card spans the width above the tabs.
 */
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

    val reading = state.continueReading.firstOrNull()
    val tabs = remember(state) { libraryShelfTabCounts(state, readingCardItemId = reading?.item?.id) }
    var selectedTabName by rememberSaveable(browseContextKey) { mutableStateOf(LibraryShelfTab.InProgress.name) }
    val selectedTab = tabs.firstOrNull { (tab, _) -> tab.name == selectedTabName }?.first ?: tabs.firstOrNull()?.first
    var expandedMenuItemId by remember(browseContextKey, state) { mutableStateOf<String?>(null) }
    var deleteCandidate by remember(state) { mutableStateOf<ReadableItem?>(null) }
    val previewCoverTargets = remember(state, gridMetrics, coverPrefetchKeyProvider) {
        shelfPreviewCoverPrefetchTargets(
            sections = listOf(
                state.continueReading.map(ContinueReadingShelfItem::item),
                state.nextInSeries.map(NextInSeriesShelfItem::item),
                state.recentlyFinished.map(RecentlyFinishedShelfItem::item),
            ),
            itemLimitPerSection = ShelfPreviewItemLimit,
            variant = gridMetrics.coverImageVariant,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
        )
    }

    LibraryShelvesPreviewCoverPrefetcher(
        browseContextKey = browseContextKey,
        coverLoadingEnabled = true,
        targets = previewCoverTargets,
        variant = gridMetrics.coverImageVariant,
        onCoverVisible = onCoverVisible,
    )

    val compact = isLibraryCompactWindow()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The zone spans the grid's full width, so its columns give the grid's cover width.
        val columns = with(LocalDensity.current) { libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics) }
        val covers = ShelfCoverLayout(width = columns.cellWidth, imageVariant = gridMetrics.coverImageVariant)
        val card = @Composable { cardModifier: Modifier ->
            reading?.let { shelfItem ->
                LibraryReadingCard(
                    item = shelfItem.item,
                    progress = shelfItem.progress,
                    overline = "Continue reading",
                    coverWidth = covers.width,
                    compact = compact,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    imageVariant = covers.imageVariant,
                    onContinue = { onItemSelected(shelfItem.item) },
                    actionContext = itemActionContext,
                    onDeleteImportedRequested = { deleteCandidate = it },
                    modifier = cardModifier,
                )
            }
        }
        val band = @Composable { bandModifier: Modifier ->
            if (selectedTab != null) {
                LibraryShelfTabsBand(
                    state = state,
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> selectedTabName = tab.name },
                    readingCardItemId = reading?.item?.id,
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    covers = covers,
                    spacing = gridMetrics.spacing,
                    onItemSelected = onItemSelected,
                    expandedMenuItemId = expandedMenuItemId,
                    onExpandedMenuChange = { item, expanded -> expandedMenuItemId = if (expanded) item.id else null },
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onSeeAll = onBrowseShelf,
                    modifier = bandModifier,
                )
            }
        }
        if (reading != null && selectedTab != null && columns.count >= SideBySideColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(gridMetrics.spacing)) {
                card(Modifier.width(columns.span(SideBySideColumns / 2, gridMetrics.spacing)))
                band(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(StackedSpacing)) {
                card(Modifier.fillMaxWidth())
                band(Modifier.fillMaxWidth())
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

/** The width of [cells] grid cells and the gaps between them. */
internal fun LibraryGridColumns.span(cells: Int, spacing: Dp): Dp = cellWidth * cells + spacing * (cells - 1)

/** At this many grid columns or more, the reading card and the tabs sit side by side. */
internal const val SideBySideColumns = 6

internal val StackedSpacing = 24.dp
