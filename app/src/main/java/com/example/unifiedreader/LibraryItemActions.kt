package com.example.unifiedreader

import android.net.Uri
import android.util.Log
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import javax.inject.Inject
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
    val libraryMessage: String,
    val libraryMessageSeverity: LibraryMessageSeverity,
)

class DownloadRemoteItemUseCase @Inject constructor(
    private val grimmoryContentRepository: GrimmoryContentDataSource,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
) {
    suspend operator fun invoke(
        item: ReadableItem,
        items: List<ReadableItem>,
    ): DownloadRemoteItemResult {
        val downloadedUri = when (item.sourceType) {
            SourceType.GRIMMORY -> grimmoryContentRepository.downloadToLocalStorage(item)
            SourceType.KAVITA -> null
            SourceType.STUMP -> null
            SourceType.LOCAL -> null
        }
        runCatching {
            syncItemProgressIfSupported(item)
        }.onFailure { throwable ->
            Log.w(LOG_TAG, "Unable to sync progress for ${item.id}.", throwable)
        }
        return DownloadRemoteItemResult(
            downloadedRemoteItemIds = libraryStateAssembler.loadDownloadedRemoteItemIds(items),
            libraryMessage = if (downloadedUri != null) {
                "Downloaded ${item.libraryDisplayLabel()} for local reading."
            } else {
                "Unable to download ${item.libraryDisplayLabel()}."
            },
            libraryMessageSeverity = if (downloadedUri != null) {
                LibraryMessageSeverity.Info
            } else {
                LibraryMessageSeverity.Error
            },
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
    val libraryMessage: String,
    val libraryMessageSeverity: LibraryMessageSeverity,
)

class DeleteRemoteLocalFileUseCase @Inject constructor(
    private val grimmoryContentRepository: GrimmoryContentDataSource,
    private val libraryStateAssembler: AppLibraryStateAssembler,
) {
    suspend operator fun invoke(
        item: ReadableItem,
        items: List<ReadableItem>,
    ): DeleteRemoteLocalFileResult {
        val deleted = when (item.sourceType) {
            SourceType.GRIMMORY -> grimmoryContentRepository.deleteStoredDocument(item)
            SourceType.KAVITA -> false
            SourceType.STUMP -> false
            SourceType.LOCAL -> false
        }
        return DeleteRemoteLocalFileResult(
            downloadedRemoteItemIds = libraryStateAssembler.loadDownloadedRemoteItemIds(items),
            libraryMessage = if (deleted) {
                "Deleted local file for ${item.libraryDisplayLabel()}."
            } else {
                "No local file found for ${item.libraryDisplayLabel()}."
            },
            libraryMessageSeverity = if (deleted) {
                LibraryMessageSeverity.Info
            } else {
                LibraryMessageSeverity.Error
            },
        )
    }
}
