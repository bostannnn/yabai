package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

@Composable
fun JournalScreen(
    snapshot: JournalSnapshot,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onDeleteItemJournal: (String) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<JournalDeleteTarget?>(null) }
    JournalStatsContent(
        snapshot = snapshot,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onDeleteItemJournal = { itemId -> deleteTarget = JournalDeleteTarget.Item(itemId) },
        onSaveJournalEntry = onSaveJournalEntry,
        modifier = modifier,
    )
    deleteTarget?.let { target ->
        JournalDeleteConfirmationDialog(
            target = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                when (target) {
                    is JournalDeleteTarget.Item -> onDeleteItemJournal(target.itemId)
                }
                deleteTarget = null
            },
        )
    }
}

@Composable
internal fun EmptyJournalMessage(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun JournalDeleteConfirmationDialog(
    target: JournalDeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = target.title) },
        text = { Text(text = target.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

private sealed interface JournalDeleteTarget {
    val title: String
    val body: String

    data class Item(
        val itemId: String,
    ) : JournalDeleteTarget {
        override val title: String = "Remove item journal?"
        override val body: String = "This removes the item reading history, rating, and private review from the Journal."
    }
}
