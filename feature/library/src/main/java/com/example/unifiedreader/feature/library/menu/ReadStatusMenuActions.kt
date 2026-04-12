package com.example.unifiedreader.feature.library.menu

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.supportsManualReadStatusAction
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.isComplete

internal fun buildReadStatusMenuActionState(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    onSetReadStatus: (ReadableItem, Boolean) -> Unit,
): ReadStatusMenuActionState? {
    if (!item.supportsManualReadStatusAction()) return null
    val actions = when {
        progress == null -> listOf(markAsReadAction(item, onSetReadStatus))
        progress.isComplete -> listOf(markAsUnreadAction(item, onSetReadStatus))
        else -> listOf(
            markAsReadAction(item, onSetReadStatus),
            markAsUnreadAction(item, onSetReadStatus),
        )
    }
    return ReadStatusMenuActionState(actions = actions)
}

private fun markAsReadAction(
    item: ReadableItem,
    onSetReadStatus: (ReadableItem, Boolean) -> Unit,
): ItemMenuAction = ItemMenuAction(
    label = "Mark as read",
    onClick = { onSetReadStatus(item, true) },
)

private fun markAsUnreadAction(
    item: ReadableItem,
    onSetReadStatus: (ReadableItem, Boolean) -> Unit,
): ItemMenuAction = ItemMenuAction(
    label = "Mark as unread",
    onClick = { onSetReadStatus(item, false) },
)
