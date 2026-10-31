package com.example.unifiedreader.feature.journal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.LocalAppToastState

@Composable
fun JournalScreen(
    snapshot: JournalSnapshot,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onDeleteItemJournal: (String) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    // Opens a book's page in the library, from its journal page.
    onOpenInLibrary: ((ReadableItem) -> Unit)? = null,
    contentTopInset: Dp = 0.dp,
    // Whether anything has scrolled under the bar (it then needs its glass).
    onScrolledChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    // Lists read to the end, kept by the journal, and how their covers are drawn.
    finishedLists: List<JournalFinishedList> = emptyList(),
    listCover: JournalListCover? = null,
) {
    var deleteTarget by remember { mutableStateOf<JournalDeleteTarget?>(null) }
    JournalStatsContent(
        snapshot = snapshot,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onDeleteItemJournal = { itemId -> deleteTarget = JournalDeleteTarget.Item(itemId) },
        onSaveJournalEntry = onSaveJournalEntry,
        onOpenInLibrary = onOpenInLibrary,
        contentTopInset = contentTopInset,
        onScrolledChanged = onScrolledChanged,
        modifier = modifier,
        finishedLists = finishedLists,
        listCover = listCover,
    )
    val toasts = LocalAppToastState.current
    deleteTarget?.let { target ->
        JournalDeleteConfirmationDialog(
            target = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                when (target) {
                    is JournalDeleteTarget.Item -> onDeleteItemJournal(target.itemId)
                }
                deleteTarget = null
                toasts?.show(AppToastMessage(text = "Removed from your journal", icon = AppToastIcon.Removed))
            },
        )
    }
}

@Composable
private fun JournalDeleteConfirmationDialog(
    target: JournalDeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = target.title) },
        text = { Text(text = target.body) },
        confirmButton = {
            AppDialogConfirmButton(text = "Remove", onClick = onConfirm, destructive = true)
        },
        dismissButton = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
        },
    )
}

private sealed interface JournalDeleteTarget {
    val title: String
    val body: String

    data class Item(
        val itemId: String,
    ) : JournalDeleteTarget {
        override val title: String = "Remove from journal?"
        override val body: String = "This removes its rating, review and reading history from this device. Ratings and reviews on your server stay."
    }
}
