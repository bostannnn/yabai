package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryReadingOrderCard
import com.example.unifiedreader.feature.lists.HomeReadingOrder
import com.example.unifiedreader.feature.lists.homeReadingOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The library home's card when it follows a reading order (see [homeReadingOrder]), for the
 * library on screen; null when it shows the book read last.
 */
@Composable
internal fun rememberHomeReadingOrderCard(
    listsViewModel: AppListsViewModel,
    viewModel: AppViewModel,
    uiState: AppUiState,
    onOpenList: (String) -> Unit,
): LibraryReadingOrderCard? {
    val lists by listsViewModel.lists.collectAsStateWithLifecycle()
    val source = uiState.selectedSource
    val progress = uiState.progressByItemId
    // A pass over the whole catalog, so off the main thread.
    val home by produceState<Pair<HomeReadingOrder, ReadableItem>?>(null, lists, uiState.items, progress, source) {
        value = withContext(Dispatchers.Default) {
            if (lists.none { it.isReadingOrder && it.lastOpenedAtEpochMillis > 0L }) return@withContext null
            homeReadingOrder(lists, uiState.items.associateBy(ReadableItem::id), progress, source)
                ?.let { order -> order to (order.underWay.next.item ?: return@withContext null) }
        }
    }
    val (order, next) = home ?: return null
    val list = order.underWay.list
    return LibraryReadingOrderCard(
        item = next,
        progress = progress[next.id],
        overline = order.overline,
        readLabel = if ((progress[next.id]?.fraction ?: 0f) > 0f) "Continue" else "Read",
        thenLine = order.thenLine,
        onRead = { openBookFromList(viewModel, listsViewModel, list, next) },
        onOpenList = { onOpenList(list.id) },
    )
}
