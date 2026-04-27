package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalEntryEditorSheet(
    item: JournalItemRef,
    existingEntry: ItemJournalEntry?,
    onDismiss: () -> Unit,
    onSave: (Int?, String) -> Unit,
) {
    var selectedRating by rememberSaveable(item.itemId) { mutableStateOf(existingEntry?.rating) }
    var reviewText by rememberSaveable(item.itemId) { mutableStateOf(existingEntry?.reviewText.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = item.displayTitle, style = MaterialTheme.typography.titleLarge)
            item.displaySubtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { rating ->
                    FilterChip(
                        selected = selectedRating == rating,
                        onClick = { selectedRating = rating },
                        label = { Text(text = rating.toString()) },
                    )
                }
            }
            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text(text = "Private review") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
                Button(onClick = { onSave(selectedRating, reviewText) }) {
                    Text(text = "Save")
                }
            }
        }
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
