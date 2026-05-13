package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryOrderedItemContinuationResolver
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

class AppReaderNextItemResolver @Inject constructor(
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val readerSessionStore: ReaderSessionStore,
    private val seriesContinuationResolver: LibrarySeriesContinuationResolver,
    private val orderedItemContinuationResolver: LibraryOrderedItemContinuationResolver,
) {
    fun nextUnreadAfter(
        currentItem: ReadableItem,
        items: List<ReadableItem>,
        orderedItemIds: List<String> = emptyList(),
    ): ReadableItem? {
        val progressByItemId = libraryStateAssembler.buildProgressSummaries(
            items = items,
            progressByItemId = readerSessionStore.progressByItemIdFlow.value,
            sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
        )
        if (orderedItemIds.isNotEmpty()) {
            val itemsById = items.associateBy(ReadableItem::id)
            return orderedItemContinuationResolver.nextUnreadAfter(
                currentItem = currentItem,
                orderedItems = orderedItemIds.mapNotNull(itemsById::get),
                progressByItemId = progressByItemId,
            )
        }
        return seriesContinuationResolver.nextUnreadAfter(
            currentItem = currentItem,
            items = items,
            progressByItemId = progressByItemId,
        )
    }
}
