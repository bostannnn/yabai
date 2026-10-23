package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton

@Composable
fun LibraryDeleteImportedItemDialog(
    item: ReadableItem,
    onDismiss: () -> Unit,
    onConfirmDelete: (ReadableItem) -> Unit,
    // The book's cover at the dialog's 52 dp leading slot, when the caller can load it.
    coverImageUrlProvider: ((ReadableItem, LibraryCoverImageVariant) -> String?)? = null,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        icon = coverImageUrlProvider?.let { coverUrlFor ->
            {
                LibraryCover(
                    item = item,
                    coverUrl = coverUrlFor(item, LibraryCoverImageVariant.Grid),
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(LibraryCoverAspectRatio),
                )
            }
        },
        title = { Text(text = "Remove local file?") },
        text = {
            Text(
                text = "This removes ${item.libraryDisplayLabel()} from your library and clears local reading state.",
            )
        },
        confirmButton = {
            AppDialogConfirmButton(text = "Remove", onClick = { onConfirmDelete(item) }, destructive = true)
        },
        dismissButton = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
        },
    )
}
