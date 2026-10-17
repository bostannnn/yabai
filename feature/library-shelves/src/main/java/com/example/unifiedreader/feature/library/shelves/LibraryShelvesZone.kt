package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val previewDatasetSignature = remember(state) { previewDatasetSignature(state) }
    var previewExpandedMenuItemId by remember(browseContextKey, previewDatasetSignature) {
        mutableStateOf<String?>(null)
    }
    var deleteCandidate by remember(state) { mutableStateOf<ReadableItem?>(null) }
    val visibleShelves = visibleShelfCount(state)
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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The zone spans the grid's full width, so its columns give the grid's cover width.
        val covers = ShelfCoverLayout(
            width = with(LocalDensity.current) {
                libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics).cellWidth
            },
            imageVariant = gridMetrics.coverImageVariant,
        )
        if (maxWidth >= ShelvesRowBreakpoint && visibleShelves > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShelfSections(
                    state = state,
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    previewContentEnabled = true,
                    onItemSelected = onItemSelected,
                    expandedMenuItemId = previewExpandedMenuItemId,
                    onExpandedMenuChange = { item, expanded ->
                        previewExpandedMenuItemId = if (expanded) item.id else null
                    },
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onBrowseShelf = onBrowseShelf,
                    coverLoadingEnabled = true,
                    covers = covers,
                    itemModifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShelfSections(
                    state = state,
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    previewContentEnabled = true,
                    onItemSelected = onItemSelected,
                    expandedMenuItemId = previewExpandedMenuItemId,
                    onExpandedMenuChange = { item, expanded ->
                        previewExpandedMenuItemId = if (expanded) item.id else null
                    },
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onBrowseShelf = onBrowseShelf,
                    coverLoadingEnabled = true,
                    covers = covers,
                    itemModifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ShelfSections(
    state: LibraryShelvesState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onBrowseShelf: (LibraryShelfBrowserKind) -> Unit,
    coverLoadingEnabled: Boolean,
    covers: ShelfCoverLayout,
    itemModifier: Modifier,
) {
    if (state.continueReading.isNotEmpty()) {
        ContinueReadingShelfSection(
            items = state.continueReading,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            previewContentEnabled = previewContentEnabled,
            coverLoadingEnabled = coverLoadingEnabled,
            covers = covers,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onExpandedMenuChange = onExpandedMenuChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
            onSeeAll = {
                onBrowseShelf(LibraryShelfBrowserKind.ContinueReading)
            },
            modifier = itemModifier,
        )
    }
    if (state.nextInSeries.isNotEmpty()) {
        NextInSeriesShelfSection(
            items = state.nextInSeries,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            previewContentEnabled = previewContentEnabled,
            coverLoadingEnabled = coverLoadingEnabled,
            covers = covers,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onExpandedMenuChange = onExpandedMenuChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
            onSeeAll = {
                onBrowseShelf(LibraryShelfBrowserKind.NextInSeries)
            },
            modifier = itemModifier,
        )
    }
    if (state.recentlyFinished.isNotEmpty()) {
        RecentlyFinishedShelfSection(
            items = state.recentlyFinished,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            previewContentEnabled = previewContentEnabled,
            coverLoadingEnabled = coverLoadingEnabled,
            covers = covers,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onExpandedMenuChange = onExpandedMenuChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
            onSeeAll = {
                onBrowseShelf(LibraryShelfBrowserKind.RecentlyFinished)
            },
            modifier = itemModifier,
        )
    }
}

private fun visibleShelfCount(
    state: LibraryShelvesState,
): Int = listOf(
    state.continueReading.isNotEmpty(),
    state.nextInSeries.isNotEmpty(),
    state.recentlyFinished.isNotEmpty(),
).count { visible -> visible }

private fun previewDatasetSignature(
    state: LibraryShelvesState,
): List<String> = buildList {
    add("continue")
    addAll(state.continueReading.take(ShelfPreviewDatasetLimit).map { it.item.id })
    add("next")
    addAll(state.nextInSeries.take(ShelfPreviewDatasetLimit).map { it.item.id })
    add("finished")
    addAll(state.recentlyFinished.take(ShelfPreviewDatasetLimit).map { it.item.id })
}

private const val ShelfPreviewDatasetLimit = 4
private val ShelvesRowBreakpoint = 840.dp
