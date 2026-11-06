package com.example.unifiedreader

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.feature.lists.matchCbl
import com.example.unifiedreader.feature.lists.parseCbl
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.LocalAppToastState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import a reading order (.cbl): picks the file, matches its issues to the library and makes a list
 * on this device in their order, then opens it ([onCreated]) and says how many were found.
 */
@Composable
internal fun rememberCblImport(
    listsViewModel: AppListsViewModel,
    uiState: AppUiState,
    onCreated: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val toasts = LocalAppToastState.current
    val scope = rememberCoroutineScope()
    val library by rememberUpdatedState(uiState.items)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runSuspendCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        ?.substringBeforeLast('.')
                    parseCbl(text)?.let { cbl -> matchCbl(cbl, library, fallbackName = fileName ?: "Reading order") }
                }.getOrNull()
            }
            if (read == null) {
                toasts?.show(AppToastMessage(text = "That file isn't a reading order (.cbl)", icon = AppToastIcon.Error))
                return@launch
            }
            listsViewModel.createList(read.name, read.items) { listId ->
                onCreated(listId)
                toasts?.show(AppToastMessage(text = read.summary, icon = AppToastIcon.Added))
            }
        }
    }
    // A .cbl has no media type of its own, so any file; the parser says whether it is one.
    return { launcher.launch(arrayOf("*/*")) }
}
