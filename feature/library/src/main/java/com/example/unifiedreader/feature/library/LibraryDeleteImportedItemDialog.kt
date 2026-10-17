package com.example.unifiedreader.feature.library

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppTextButton

@Composable
fun LibraryDeleteImportedItemDialog(
    item: ReadableItem,
    onDismiss: () -> Unit,
    onConfirmDelete: (ReadableItem) -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Remove local file?") },
        text = {
            Text(
                text = "This removes ${item.libraryDisplayLabel()} from your library and clears local reading state.",
            )
        },
        confirmButton = {
            AppTextButton(
                onClick = { onConfirmDelete(item) },
            ) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
