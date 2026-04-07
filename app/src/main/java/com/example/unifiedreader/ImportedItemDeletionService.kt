package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

sealed interface ImportedItemDeletionOutcome {
    data object Removed : ImportedItemDeletionOutcome
    data object NotImportedItem : ImportedItemDeletionOutcome
    data object NotFound : ImportedItemDeletionOutcome
}

interface ImportedItemDeletionService {
    suspend fun delete(item: ReadableItem): ImportedItemDeletionOutcome
}

class DefaultImportedItemDeletionService(
    private val localDocumentDataSource: LocalDocumentDataSource,
    private val readerSessionStore: ReaderSessionStore,
) : ImportedItemDeletionService {
    override suspend fun delete(item: ReadableItem): ImportedItemDeletionOutcome {
        if (!item.isLocalImport) {
            return ImportedItemDeletionOutcome.NotImportedItem
        }
        val removed = localDocumentDataSource.removeById(item.id)
        if (!removed) {
            return ImportedItemDeletionOutcome.NotFound
        }
        readerSessionStore.clearItem(item.id)
        return ImportedItemDeletionOutcome.Removed
    }
}
