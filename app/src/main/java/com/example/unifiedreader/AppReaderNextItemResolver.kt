package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

class AppReaderNextItemResolver @Inject constructor(
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val readerSessionStore: ReaderSessionStore,
    private val seriesContinuationResolver: LibrarySeriesContinuationResolver,
) {
    fun nextUnreadAfter(
        currentItem: ReadableItem,
        items: List<ReadableItem>,
    ): ReadableItem? {
        val progressByItemId = libraryStateAssembler.buildProgressSummaries(
            items = items,
            progressByItemId = readerSessionStore.progressByItemIdFlow.value,
            sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
        )
        return seriesContinuationResolver.nextUnreadAfter(
            currentItem = currentItem,
            items = items,
            progressByItemId = progressByItemId,
        )
    }
}
