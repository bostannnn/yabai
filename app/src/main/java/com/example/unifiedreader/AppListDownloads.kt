package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.booksToKeepDownloaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Keep the next 3 downloaded: queues the next unread server books of every list that keeps them
 * when the lists, the library or your reading change (a second after the last change). Each book
 * is asked for once per run of the app; the download queue does the rest once its server answers.
 */
@Composable
internal fun KeepListBooksDownloaded(
    listsViewModel: AppListsViewModel,
    uiState: AppUiState,
    onQueue: (List<ReadableItem>) -> Unit,
) {
    val lists by listsViewModel.lists.collectAsStateWithLifecycle()
    if (lists.none(UserList::keepNextDownloaded)) return
    LaunchedEffect(lists, uiState.items, uiState.progressByItemId, uiState.locallyAvailableRemoteItemIds) {
        delay(1_000)
        val asked = listsViewModel.keepDownloadedAsked
        val toQueue = withContext(Dispatchers.Default) {
            booksToKeepDownloaded(
                lists = lists,
                itemsById = uiState.items.associateBy(ReadableItem::id),
                progressByItemId = uiState.progressByItemId,
                onDevice = uiState.locallyAvailableRemoteItemIds,
                asked = asked.toSet(),
            )
        }
        if (toQueue.isEmpty()) return@LaunchedEffect
        asked += toQueue.map(ReadableItem::id)
        onQueue(toQueue)
    }
}
