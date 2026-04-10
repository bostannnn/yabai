package com.example.unifiedreader

import android.net.Uri
import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.resolveRemoteDownloadActionItems
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.evaluateRemoteDownloadBatchOutcome
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlin.jvm.JvmSuppressWildcards

data class ImportDocumentResult(
    val importedItem: ReadableItem?,
) {
    val libraryMessage: String =
        if (importedItem != null) {
            "Imported ${importedItem.libraryDisplayLabel()} (${importedItem.format.name})"
        } else {
            "Unable to import that file as a supported local document."
        }

    val libraryMessageSeverity: LibraryMessageSeverity =
        if (importedItem != null) LibraryMessageSeverity.Info else LibraryMessageSeverity.Error
}

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

data class DownloadRemoteItemResult(
    val downloadedRemoteItemIds: Set<String>,
    val locallyAvailableRemoteItemIds: Set<String>,
    val libraryMessage: String,
    val libraryMessageSeverity: LibraryMessageSeverity,
)

class DownloadRemoteItemUseCase @Inject constructor(
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
) {
    suspend operator fun invoke(
        item: ReadableItem,
        items: List<ReadableItem>,
    ): DownloadRemoteItemResult {
        val actionItems = resolveRemoteDownloadActionItems(
            selectedItem = item,
            items = items,
        )
        val initialRemoteItemLocalState = libraryStateAssembler.loadRemoteItemLocalState(items)
        var firstFailureMessage: String? = null
        actionItems.forEach { actionItem ->
            runCatching {
                remoteDownloadDataSources.downloadDataSourceFor(actionItem)
                    ?.downloadToLocalStorage(actionItem)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (firstFailureMessage == null) {
                    firstFailureMessage = throwable.userFacingMessage()
                }
                Log.w(LOG_TAG, "Unable to download ${actionItem.id}.", throwable)
            }
        }
        val remoteItemLocalState = libraryStateAssembler.loadRemoteItemLocalState(items)
        val batchOutcome = evaluateRemoteDownloadBatchOutcome(
            requestedItems = actionItems,
            initiallyDownloadedItemIds = initialRemoteItemLocalState.downloadedItemIds,
            downloadedItemIdsAfterRequest = remoteItemLocalState.downloadedItemIds,
        )
        actionItems.forEach { actionItem ->
            if (actionItem.id !in remoteItemLocalState.downloadedItemIds) return@forEach
            runCatching {
                syncItemProgressIfSupported(actionItem)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.w(LOG_TAG, "Unable to sync progress for ${actionItem.id}.", throwable)
            }
        }
        return DownloadRemoteItemResult(
            downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
            locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
            libraryMessage = if (
                actionItems.size == 1 &&
                batchOutcome.failedCount == 1 &&
                batchOutcome.newlyDownloadedCount == 0 &&
                batchOutcome.alreadyAvailableCount == 0 &&
                firstFailureMessage != null
            ) {
                "Unable to download ${item.libraryDisplayLabel()}: $firstFailureMessage"
            } else {
                batchOutcome.libraryMessage(item.libraryDisplayLabel())
            },
            libraryMessageSeverity = if (batchOutcome.hasFailures) LibraryMessageSeverity.Error else LibraryMessageSeverity.Info,
        )
    }

    private suspend fun syncItemProgressIfSupported(
        item: ReadableItem,
    ) {
        sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
            ?.syncItem(item)
    }
}

private const val LOG_TAG = "UnifiedReader"

data class DeleteRemoteLocalFileResult(
    val downloadedRemoteItemIds: Set<String>,
    val locallyAvailableRemoteItemIds: Set<String>,
    val libraryMessage: String,
    val libraryMessageSeverity: LibraryMessageSeverity,
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
        val deletedCount = actionItems.count { actionItem ->
            remoteDownloadDataSources.downloadDataSourceFor(actionItem)
                ?.deleteStoredDocument(actionItem) == true
        }
        val remoteItemLocalState = libraryStateAssembler.loadRemoteItemLocalState(items)
        return DeleteRemoteLocalFileResult(
            downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
            locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
            libraryMessage = if (deletedCount > 0) {
                if (actionItems.size == 1) {
                    "Deleted local file for ${item.libraryDisplayLabel()}."
                } else {
                    "Deleted $deletedCount local files."
                }
            } else {
                if (actionItems.size == 1) {
                    "No local file found for ${item.libraryDisplayLabel()}."
                } else {
                    "No local files found for ${item.libraryDisplayLabel()}."
                }
            },
            libraryMessageSeverity = if (deletedCount > 0) {
                LibraryMessageSeverity.Info
            } else {
                LibraryMessageSeverity.Error
            },
        )
    }
}
