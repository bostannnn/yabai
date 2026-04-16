package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButton
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.library.menu.LibraryItemOverflowMenu
import com.example.unifiedreader.feature.library.menu.buildLibraryItemMenuActions
import com.example.unifiedreader.feature.library.menu.buildReaderModeMenuState
import com.example.unifiedreader.feature.library.menu.buildReadStatusMenuActionState
import com.example.unifiedreader.feature.library.menu.buildReadingDirectionMenuState

@Composable
fun LibraryAnchoredItemMenu(
    item: ReadableItem,
    actionContext: LibraryItemActionContext,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) {
        Box(modifier = modifier) {
            LibraryItemMenuButton(
                onClick = { onExpandedChange(true) },
                style = LibraryItemMenuButtonStyle.CompactOverlay,
            )
        }
        return
    }

    LibraryItemOverflowMenu(
        modifier = modifier,
        buttonStyle = LibraryItemMenuButtonStyle.CompactOverlay,
        actions = buildLibraryItemMenuActions(
            isDownloading = item.id in actionContext.downloadingItemIds,
            readStatusActionState = buildReadStatusMenuActionState(
                item = item,
                progress = actionContext.progressByItemId[item.id],
                onSetReadStatus = actionContext.onSetReadStatus,
            ),
            onDeleteRequested = itemDeleteRequest(item, onDeleteImportedRequested),
            onDownloadRequested = itemDownloadRequest(
                item = item,
                downloadableItemIds = actionContext.downloadableItemIds,
                onDownloadItem = actionContext.onDownloadItem,
            ),
            onDeleteDownloadedRequested = itemDownloadedDeleteRequest(
                item = item,
                deletableDownloadedItemIds = actionContext.deletableDownloadedItemIds,
                onDeleteDownloadedItem = actionContext.onDeleteDownloadedItem,
            ),
        ),
        readingDirectionState = buildReadingDirectionMenuState(
            seriesReadingDirection = actionContext.seriesReadingDirectionFor(item),
            onSeriesReadingDirectionSelected = { direction ->
                actionContext.onSeriesDirectionSelected(item, direction)
            },
        ),
        readerModeState = buildReaderModeMenuState(
            seriesReaderMode = actionContext.seriesReaderModeFor(item),
            onSeriesReaderModeSelected = { mode ->
                actionContext.onSeriesReaderModeSelected(item, mode)
            },
        ),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    )
}
