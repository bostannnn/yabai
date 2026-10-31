package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Keeps the list a book was opened from across the app being stopped in the background: the
 * context goes into the screen's saved state, and comes back once the same book is open again
 * after a restart, so Read next still follows the list.
 */
@Composable
internal fun KeepReaderListContext(uiState: AppUiState, viewModel: AppViewModel) {
    var saved by rememberSaveable(stateSaver = ReaderListContextSaver) { mutableStateOf<AppReaderListContext?>(null) }
    // Only the first reader after the screen came back can be the one the app was stopped in.
    var restoreChecked by remember { mutableStateOf(false) }
    val current = uiState.readerListContext
    val reading = uiState.activeReader != null
    LaunchedEffect(current, uiState.selectedItemId, reading) {
        when {
            current != null -> {
                saved = current
                restoreChecked = true
            }
            !reading -> Unit
            !restoreChecked -> {
                restoreChecked = true
                saved?.let(viewModel::restoreReaderListContext)
            }
            // A book opened from anywhere else: the list no longer leads.
            else -> saved = null
        }
    }
}

private val ReaderListContextSaver = listSaver<AppReaderListContext?, String>(
    save = { context -> context?.let { listOf(it.listId, it.title) + it.itemIds }.orEmpty() },
    restore = { saved ->
        if (saved.size < 2) null else AppReaderListContext(listId = saved[0], title = saved[1], itemIds = saved.drop(2))
    },
)
