package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppSwitch

/**
 * Rows: every row Browse can show, as switches. Shown and Off hold the rows some book has; a row
 * no book has yet waits under "Waiting for metadata" and turns up in Browse once one does.
 */
@Composable
internal fun LibraryBrowseRowsSheet(
    settings: LibraryBrowseRowSettings,
    coverage: Map<LibraryBrowseRow, Int>,
    onRowChanged: (LibraryBrowseRow, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = settings.shown
    val withData = LibraryBrowseRow.entries.filter { row -> (coverage[row] ?: 0) > 0 }
    val sections = listOf(
        "Shown" to withData.filter { it in shown },
        "Off" to withData.filterNot { it in shown },
        "Waiting for metadata" to LibraryBrowseRow.entries.filterNot { it in withData },
    ).filter { (_, rows) -> rows.isNotEmpty() }
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(title = "Rows", subtitle = "What Browse shows", onClose = { dismiss() })
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "intro") {
                Text(
                    text = "A row shows once some book has its field, so new metadata from your server turns up by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppLayout.inset),
                )
            }
            sections.forEach { (label, rows) ->
                item(key = "label:$label") { AppSheetLabel(text = label) }
                rows.forEach { row ->
                    item(key = row.name) {
                        val on = row in shown
                        AppSettingsRow(
                            title = row.label,
                            supporting = row.rowsSheetSupporting(coverage[row] ?: 0),
                            onClick = { onRowChanged(row, !on) },
                            showChevron = false,
                            trailing = { AppSwitch(checked = on, onCheckedChange = null) },
                        )
                    }
                }
            }
        }
    }
}

private fun LibraryBrowseRow.rowsSheetSupporting(books: Int): String {
    if (books == 0) {
        return when (this) {
            LibraryBrowseRow.MOOD -> "No book has a mood yet"
            LibraryBrowseRow.SHELF -> "No book is on a server shelf yet"
            else -> "No book has one yet"
        }
    }
    val count = bookCountLabel(books)
    return when (this) {
        LibraryBrowseRow.SET_IN, LibraryBrowseRow.WHEN, LibraryBrowseRow.ABOUT -> "From tags · $count"
        LibraryBrowseRow.GENRE -> "From genres · $count"
        LibraryBrowseRow.MOOD -> "From moods · $count"
        LibraryBrowseRow.SHELF -> "From the server's shelves · $count"
        LibraryBrowseRow.READING -> "Not started, In progress, Finished"
        LibraryBrowseRow.AUTHOR,
        LibraryBrowseRow.SERIES,
        LibraryBrowseRow.PUBLISHER,
        LibraryBrowseRow.LANGUAGE -> count
    }
}
