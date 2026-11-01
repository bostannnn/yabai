package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppReorderHandleIcon
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.appReorderableItem
import com.example.unifiedreader.ui.components.movedItem
import com.example.unifiedreader.ui.components.rememberAppReorderState

/**
 * Rows: every row Browse can show, as switches. Shown and Off hold the rows some book has; a row
 * no book has yet waits under "Waiting for metadata" and turns up in Browse once one does. A shown
 * row's handle drags it up or down, and Browse lists its rows in that order.
 */
@Composable
internal fun LibraryBrowseRowsSheet(
    settings: LibraryBrowseRowSettings,
    coverage: Map<LibraryBrowseRow, Int>,
    onRowChanged: (LibraryBrowseRow, Boolean) -> Unit,
    onRowsArranged: (List<LibraryBrowseRow>) -> Unit,
    onDismiss: () -> Unit,
) {
    val withData = settings.ordered.filter { row -> (coverage[row] ?: 0) > 0 }
    val shown = withData.filter { it in settings.shown }
    val off = withData.filterNot { it in settings.shown }
    val waiting = settings.ordered.filterNot { it in withData }
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(title = "Rows", subtitle = "What Browse shows", onClose = { dismiss() })
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "intro") {
                Text(
                    text = "A row shows once some book has its field, so new metadata from your server turns up by itself. " +
                        "Drag a shown row to move it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppLayout.inset),
                )
            }
            if (shown.isNotEmpty()) {
                item(key = "label:Shown") { AppSheetLabel(text = "Shown") }
                // One item, a plain column: dragging measures the rows against each other.
                item(key = "shown") {
                    LibraryBrowseShownRows(
                        rows = shown,
                        coverage = coverage,
                        onRowChanged = onRowChanged,
                        onRowsArranged = onRowsArranged,
                    )
                }
            }
            listOf("Off" to off, "Waiting for metadata" to waiting).filter { (_, rows) -> rows.isNotEmpty() }.forEach { (label, rows) ->
                item(key = "label:$label") { AppSheetLabel(text = label) }
                rows.forEach { row ->
                    item(key = row.name) {
                        AppSettingsRow(
                            title = row.label,
                            supporting = row.rowsSheetSupporting(coverage[row] ?: 0),
                            onClick = { onRowChanged(row, true) },
                            showChevron = false,
                            trailing = { AppSwitch(checked = false, onCheckedChange = null) },
                        )
                    }
                }
            }
        }
    }
}

/** The shown rows, each with a drag handle before its name and its switch at the end. */
@Composable
private fun LibraryBrowseShownRows(
    rows: List<LibraryBrowseRow>,
    coverage: Map<LibraryBrowseRow, Int>,
    onRowChanged: (LibraryBrowseRow, Boolean) -> Unit,
    onRowsArranged: (List<LibraryBrowseRow>) -> Unit,
) {
    // Rows draw from this copy, so the moves of one fast drag build on each other before the
    // settings come back.
    var order by remember(rows) { mutableStateOf(rows) }
    val reorderState = rememberAppReorderState { fromIndex, toIndex ->
        order = order.movedItem(fromIndex, toIndex)
        onRowsArranged(order)
    }
    Column {
        order.forEachIndexed { index, row ->
            key(row) {
                AppSettingsRow(
                    title = row.label,
                    supporting = row.rowsSheetSupporting(coverage[row] ?: 0),
                    onClick = { onRowChanged(row, false) },
                    showChevron = false,
                    modifier = Modifier.appReorderableItem(reorderState, index),
                    leading = { AppReorderHandleIcon(state = reorderState, index = index, label = row.label) },
                    trailing = { AppSwitch(checked = true, onCheckedChange = null) },
                )
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
