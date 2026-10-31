package com.example.unifiedreader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.journal.JournalFinishedList
import com.example.unifiedreader.feature.journal.JournalListCover
import com.example.unifiedreader.feature.journal.JournalListFinishedDialog
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.VisibleResolvedLibraryCover
import com.example.unifiedreader.feature.lists.FinishedList
import com.example.unifiedreader.feature.lists.alreadyKeeps
import com.example.unifiedreader.feature.lists.isReadToTheEnd
import com.example.unifiedreader.feature.lists.toFinishedList

/**
 * The list-finished card over Finished: shown when [finishedItem], opened from a reading order,
 * was that order's last unread book. The journal keeps the list from then on, so the card shows
 * once for the list as it is.
 */
@Composable
internal fun ListFinishedOverFinished(
    uiState: AppUiState,
    finishedItem: ReadableItem,
    listsViewModel: AppListsViewModel,
    viewModel: AppViewModel,
) {
    val context = uiState.readerListContext?.takeIf { finishedItem.id in it.itemIds } ?: return
    val lists by listsViewModel.lists.collectAsStateWithLifecycle()
    val kept by listsViewModel.finishedLists.collectAsStateWithLifecycle()
    val list = lists.firstOrNull { it.id == context.listId }
    var card by remember(finishedItem.id) { mutableStateOf<FinishedList?>(null) }
    var dismissed by rememberSaveable(finishedItem.id) { mutableStateOf(false) }
    val itemsById = remember(uiState.items) { uiState.items.associateBy(ReadableItem::id) }
    LaunchedEffect(list, kept, finishedItem.id) {
        val keptLists = kept ?: return@LaunchedEffect
        if (card != null || list == null || keptLists.alreadyKeeps(list)) return@LaunchedEffect
        if (!list.isReadToTheEnd(finishedItem.id, itemsById, uiState.progressByItemId)) return@LaunchedEffect
        val finished = list.toFinishedList(itemsById, uiState.journalSnapshot.sessions, System.currentTimeMillis())
        card = finished
        listsViewModel.recordFinished(finished)
    }
    val shown = card ?: return
    if (dismissed) return
    JournalListFinishedDialog(
        list = shown.toJournal(itemsById),
        cover = rememberJournalListCover(viewModel),
        onDeleteList = list?.let { stillThere ->
            {
                listsViewModel.deleteList(stillThere)
                dismissed = true
            }
        },
        onDone = { dismissed = true },
    )
}

/** The lists the journal keeps as finished, with the books still in the library for their covers. */
@Composable
internal fun rememberJournalFinishedLists(
    listsViewModel: AppListsViewModel,
    uiState: AppUiState,
): List<JournalFinishedList> {
    val kept by listsViewModel.finishedLists.collectAsStateWithLifecycle()
    return remember(kept, uiState.items) {
        val itemsById = uiState.items.associateBy(ReadableItem::id)
        kept.orEmpty().map { finished -> finished.toJournal(itemsById) }
    }
}

/** The library's cover for the journal's list mosaics and rows of covers. */
@Composable
internal fun rememberJournalListCover(viewModel: AppViewModel): JournalListCover = remember(viewModel) {
    { item, modifier ->
        VisibleResolvedLibraryCover(
            item = item,
            coverImageUrlProvider = viewModel::coverImageUrlFor,
            onCoverVisible = viewModel::ensureCoverCached,
            imageVariant = LibraryCoverImageVariant.ShelfRow,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private fun FinishedList.toJournal(itemsById: Map<String, ReadableItem>): JournalFinishedList = JournalFinishedList(
    id = id,
    name = name,
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
    bookCount = bookCount,
    seriesCount = seriesCount,
    readMillis = readMillis,
    books = itemIds.mapNotNull(itemsById::get),
)
