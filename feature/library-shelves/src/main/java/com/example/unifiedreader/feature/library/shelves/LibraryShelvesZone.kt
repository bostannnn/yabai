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

@Composable
fun LibraryShelvesZone(
    state: LibraryShelvesState,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasContent) return

    var activeSheet by remember(state) { mutableStateOf<LibraryShelfSheetState?>(null) }
    val visibleShelves = visibleShelfCount(state)
    val coverLoadingReady = rememberShelvesCoverLoadingReady(hasContent = state.hasContent)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= ShelvesRowBreakpoint && visibleShelves > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShelfSections(
                    state = state,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onItemSelected = onItemSelected,
                    onOpenSheet = { activeSheet = it },
                    coverLoadingEnabled = coverLoadingReady,
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
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onItemSelected = onItemSelected,
                    onOpenSheet = { activeSheet = it },
                    coverLoadingEnabled = coverLoadingReady,
                    itemModifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    activeSheet?.let { sheet ->
        LibraryShelfItemsSheet(
            sheet = sheet,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onDismiss = { activeSheet = null },
            onItemSelected = { item ->
                activeSheet = null
                onItemSelected(item)
            },
        )
    }
}

@Composable
private fun ShelfSections(
    state: LibraryShelvesState,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onOpenSheet: (LibraryShelfSheetState) -> Unit,
    coverLoadingEnabled: Boolean,
    itemModifier: Modifier,
) {
    if (state.continueReading.isNotEmpty()) {
        ContinueReadingShelfSection(
            items = state.continueReading,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            coverLoadingEnabled = coverLoadingEnabled,
            onItemSelected = onItemSelected,
            onSeeAll = {
                onOpenSheet(LibraryShelfSheetState.ContinueReading(state.continueReading))
            },
            modifier = itemModifier,
        )
    }
    if (state.nextInSeries.isNotEmpty()) {
        NextInSeriesShelfSection(
            items = state.nextInSeries,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            coverLoadingEnabled = coverLoadingEnabled,
            onItemSelected = onItemSelected,
            onSeeAll = {
                onOpenSheet(LibraryShelfSheetState.NextInSeries(state.nextInSeries))
            },
            modifier = itemModifier,
        )
    }
    if (state.recentlyFinished.isNotEmpty()) {
        RecentlyFinishedShelfSection(
            items = state.recentlyFinished,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            coverLoadingEnabled = coverLoadingEnabled,
            onItemSelected = onItemSelected,
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
