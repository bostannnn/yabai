package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow

internal data class ItemMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

internal data class ReadingDirectionMenuState(
    val selectedDirection: ComicReadingDirection,
    val onDirectionSelected: (ComicReadingDirection) -> Unit,
)

internal data class ReadStatusMenuActionState(
    val actions: List<ItemMenuAction>,
)

internal fun buildReadingDirectionMenuState(
    seriesReadingDirection: ComicReadingDirection?,
    onSeriesReadingDirectionSelected: ((ComicReadingDirection) -> Unit)?,
): ReadingDirectionMenuState? {
    if (seriesReadingDirection == null || onSeriesReadingDirectionSelected == null) return null
    return ReadingDirectionMenuState(
        selectedDirection = seriesReadingDirection,
        onDirectionSelected = onSeriesReadingDirectionSelected,
    )
}

internal fun buildLibraryItemMenuActions(
    isDownloading: Boolean,
    readStatusActionState: ReadStatusMenuActionState? = null,
    onDeleteRequested: (() -> Unit)?,
    onDownloadRequested: (() -> Unit)?,
    onDeleteDownloadedRequested: (() -> Unit)?,
): List<ItemMenuAction> = buildList {
    readStatusActionState?.let { state ->
        addAll(state.actions)
    }
    if (isDownloading) {
        add(ItemMenuAction(label = "Downloading...", enabled = false, onClick = {}))
    }
    onDownloadRequested?.let { add(ItemMenuAction(label = "Download file", onClick = it)) }
    onDeleteDownloadedRequested?.let { add(ItemMenuAction(label = "Delete local file", onClick = it)) }
    onDeleteRequested?.let { add(ItemMenuAction(label = "Remove local file", onClick = it)) }
}

@Composable
internal fun LibraryItemOverflowMenu(
    modifier: Modifier = Modifier,
    actions: List<ItemMenuAction>,
    buttonStyle: LibraryItemMenuButtonStyle = LibraryItemMenuButtonStyle.Tonal,
    readingDirectionState: ReadingDirectionMenuState? = null,
    readerModeState: ReaderModeMenuState? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    var localExpanded by rememberSaveable { mutableStateOf(false) }
    val menuExpanded = expanded ?: localExpanded
    val updateExpanded = remember(onExpandedChange) {
        { value: Boolean ->
            onExpandedChange?.invoke(value) ?: run { localExpanded = value }
        }
    }
    Box(modifier = modifier) {
        LibraryItemMenuButton(
            onClick = { updateExpanded(true) },
            style = buttonStyle,
        )
        AppMenu(
            expanded = menuExpanded,
            onDismissRequest = { updateExpanded(false) },
        ) {
            LibraryItemMenuEntries(
                actions = actions,
                readingDirectionState = readingDirectionState,
                readerModeState = readerModeState,
                onActionHandled = { updateExpanded(false) },
            )
        }
    }
}

@Composable
internal fun LibraryItemMenuEntries(
    actions: List<ItemMenuAction>,
    readingDirectionState: ReadingDirectionMenuState?,
    readerModeState: ReaderModeMenuState?,
    onActionHandled: () -> Unit,
) {
    val hasPreferences = readingDirectionState != null || readerModeState != null
    val effectiveActions = if (actions.isEmpty() && !hasPreferences) {
        listOf(ItemMenuAction(label = "No actions available", enabled = false, onClick = {}))
    } else {
        actions
    }

    effectiveActions.forEach { action ->
        AppMenuItem(
            text = { Text(text = action.label) },
            enabled = action.enabled,
            onClick = {
                onActionHandled()
                action.onClick()
            },
        )
    }
    readingDirectionState?.let { state ->
        if (effectiveActions.isNotEmpty()) {
            AppDivider()
        }
        ReadingDirectionMenuRow(
            state = state,
            onDirectionSelected = { direction ->
                onActionHandled()
                state.onDirectionSelected(direction)
            },
        )
    }
    readerModeState?.let { state ->
        if (effectiveActions.isNotEmpty() || readingDirectionState != null) {
            AppDivider()
        }
        ReaderModeMenuRow(
            state = state,
            onModeSelected = { mode ->
                onActionHandled()
                state.onModeSelected(mode)
            },
        )

    }
}

@Composable
private fun ReadingDirectionMenuRow(
    state: ReadingDirectionMenuState,
    onDirectionSelected: (ComicReadingDirection) -> Unit,
) {
    val directions = ComicReadingDirection.entries
    AppSegmentedRow(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        directions.forEach { direction ->
            AppSegment(
                selected = direction == state.selectedDirection,
                onClick = { onDirectionSelected(direction) },
                label = { Text(text = direction.label) },
            )
        }
    }
}
