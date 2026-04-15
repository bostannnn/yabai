package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.feature.library.menu.buildLibraryItemMenuActions
import com.example.unifiedreader.feature.library.menu.buildReaderModeMenuState
import com.example.unifiedreader.feature.library.menu.buildReadStatusMenuActionState
import com.example.unifiedreader.feature.library.menu.buildReadingDirectionMenuState
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemActionsSheet(
    item: ReadableItem,
    actionContext: LibraryItemActionContext,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = buildLibraryItemMenuActions(
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
    )
    val readingDirectionState = buildReadingDirectionMenuState(
        seriesReadingDirection = actionContext.seriesReadingDirectionFor(item),
        onSeriesReadingDirectionSelected = { direction ->
            actionContext.onSeriesDirectionSelected(item, direction)
        },
    )
    val readerModeState = buildReaderModeMenuState(
        seriesReaderMode = actionContext.seriesReaderModeFor(item),
        onSeriesReaderModeSelected = { mode ->
            actionContext.onSeriesReaderModeSelected(item, mode)
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = item.libraryPrimaryText(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            actions.forEach { action ->
                TextButton(
                    enabled = action.enabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                ) {
                    Text(text = action.label)
                }
            }

            readingDirectionState?.let { state ->
                PreferenceSection(label = "Reading direction") {
                    ComicReadingDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = direction == state.selectedDirection,
                            onClick = {
                                onDismiss()
                                state.onDirectionSelected(direction)
                            },
                            label = { Text(text = direction.label) },
                        )
                    }
                }
            }

            readerModeState?.let { state ->
                PreferenceSection(label = "Reader mode") {
                    ComicReaderMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == state.selectedMode,
                            onClick = {
                                onDismiss()
                                state.onModeSelected(mode)
                            },
                            label = { Text(text = mode.label) },
                        )
                    }
                }
            }

            if (actions.isEmpty() && readingDirectionState == null && readerModeState == null) {
                Text(text = "No actions available")
            }
        }
    }
}

@Composable
private fun PreferenceSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
