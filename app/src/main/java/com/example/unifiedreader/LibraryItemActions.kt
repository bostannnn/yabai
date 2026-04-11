package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.resolveRemoteDownloadActionItems
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

data class ImportDocumentResult(
    val importedItem: ReadableItem?,
)

class ImportDocumentUseCase @Inject constructor(
    private val localDocumentRepository: LocalDocumentDataSource,
) {
    suspend operator fun invoke(
        uri: Uri,
    ): ImportDocumentResult = ImportDocumentResult(
        importedItem = localDocumentRepository.import(uri),
    )
}

data class DeleteImportedItemResult(
    val outcome: ImportedItemDeletionOutcome,
) {
    val removed: Boolean = outcome == ImportedItemDeletionOutcome.Removed
}

class DeleteImportedItemUseCase @Inject constructor(
    private val importedItemDeletionService: ImportedItemDeletionService,
) {
    suspend operator fun invoke(
        item: ReadableItem,
    ): DeleteImportedItemResult = DeleteImportedItemResult(
        outcome = importedItemDeletionService.delete(item),
    )
}

data class DeleteRemoteLocalFileResult(
    val downloadedRemoteItemIds: Set<String>,
    val locallyAvailableRemoteItemIds: Set<String>,
)

class DeleteRemoteLocalFileUseCase @Inject constructor(
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val libraryStateAssembler: AppLibraryStateAssembler,
) {
    suspend operator fun invoke(
        item: ReadableItem,
        items: List<ReadableItem>,
    ): DeleteRemoteLocalFileResult {
        val actionItems = resolveRemoteDownloadActionItems(
            selectedItem = item,
            items = items,
        )
        actionItems.forEach { actionItem ->
            remoteDownloadDataSources.downloadDataSourceFor(actionItem)
                ?.deleteStoredDocument(actionItem)
        }
        val remoteItemLocalState = libraryStateAssembler.loadRemoteItemLocalState(items)
        return DeleteRemoteLocalFileResult(
            downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
            locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
        )
    }
}
