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
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryDeleteImportedItemDialog
import com.example.unifiedreader.feature.library.LibraryItemActionContext

@Composable
fun LibraryShelvesZone(
    state: LibraryShelvesState,
    itemActionContext: LibraryItemActionContext = LibraryItemActionContext(),
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!state.hasContent) return

    var activeSheet by remember(state) { mutableStateOf<LibraryShelfSheetState?>(null) }
    var previewExpandedMenuItemId by remember(state) { mutableStateOf<String?>(null) }
    var deleteCandidate by remember(state) { mutableStateOf<ReadableItem?>(null) }
    val visibleShelves = visibleShelfCount(state)
    val previewCoverItems = remember(state, coverImageUrlProvider) {
        shelfPreviewCoverPrefetchItems(
            state = state,
            coverImageUrlProvider = coverImageUrlProvider,
        )
    }

    LibraryShelvesPreviewCoverPrefetcher(
        coverLoadingEnabled = true,
        items = previewCoverItems,
        onCoverVisible = onCoverVisible,
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
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
                    onMenuExpandedChange = { item, expanded ->
                        previewExpandedMenuItemId = if (expanded) item.id else null
                    },
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onOpenSheet = { activeSheet = it },
                    coverLoadingEnabled = true,
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
                    onMenuExpandedChange = { item, expanded ->
                        previewExpandedMenuItemId = if (expanded) item.id else null
                    },
                    onDeleteImportedRequested = { deleteCandidate = it },
                    onOpenSheet = { activeSheet = it },
                    coverLoadingEnabled = true,
                    itemModifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    activeSheet?.let { sheet ->
        LibraryShelfItemsSheet(
            sheet = sheet,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onDismiss = { activeSheet = null },
            onItemSelected = { item ->
                activeSheet = null
                onItemSelected(item)
            },
            onDeleteImportedItem = { deleteCandidate = it },
        )
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
    onMenuExpandedChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onOpenSheet: (LibraryShelfSheetState) -> Unit,
    coverLoadingEnabled: Boolean,
    itemModifier: Modifier,
) {
    if (state.continueReading.isNotEmpty()) {
        ContinueReadingShelfSection(
            items = state.continueReading,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            previewContentEnabled = previewContentEnabled,
            coverLoadingEnabled = coverLoadingEnabled,
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onMenuExpandedChange = onMenuExpandedChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
            onSeeAll = {
                onOpenSheet(LibraryShelfSheetState.ContinueReading(state.continueReading))
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
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onMenuExpandedChange = onMenuExpandedChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
            onSeeAll = {
                onOpenSheet(LibraryShelfSheetState.NextInSeries(state.nextInSeries))
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
            onItemSelected = onItemSelected,
            expandedMenuItemId = expandedMenuItemId,
            onMenuExpandedChange = onMenuExpandedChange,
            onDeleteImportedRequested = onDeleteImportedRequested,
            onSeeAll = {
                onOpenSheet(LibraryShelfSheetState.RecentlyFinished(state.recentlyFinished))
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

private val ShelvesRowBreakpoint = 840.dp
