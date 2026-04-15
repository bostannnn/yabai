package com.example.unifiedreader.feature.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel

@Composable
fun LibraryDeleteImportedItemDialog(
    item: ReadableItem,
    onDismiss: () -> Unit,
    onConfirmDelete: (ReadableItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Remove local file?") },
        text = {
            Text(
                text = "This removes ${item.libraryDisplayLabel()} from your library and clears local reading state.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmDelete(item) },
            ) {
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
