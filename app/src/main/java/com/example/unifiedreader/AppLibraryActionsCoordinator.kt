package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class AppLibraryActionsCoordinator @Inject constructor(
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val deleteImportedItemUseCase: DeleteImportedItemUseCase,
    private val deleteRemoteLocalFileUseCase: DeleteRemoteLocalFileUseCase,
    private val remoteCoverController: RemoteCoverController,
) {
    suspend fun importDocument(
        uri: Uri,
    ): ImportDocumentResult = importDocumentUseCase(uri)

    suspend fun deleteImportedItem(
        item: ReadableItem,
    ): DeleteImportedItemResult = deleteImportedItemUseCase(item)

    suspend fun deleteRemoteLocalFile(
        item: ReadableItem,
        items: List<ReadableItem>,
    ): DeleteRemoteLocalFileResult = deleteRemoteLocalFileUseCase(item, items)

    fun coverImageUrlFor(
        item: ReadableItem,
    ): String? = remoteCoverController.coverImageUrlFor(item)

    fun coverPrefetchKeyFor(
        item: ReadableItem,
    ): String? = remoteCoverController.coverPrefetchKeyFor(item)

    fun ensureCoverCached(
        item: ReadableItem,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        remoteCoverController.ensureCoverCached(
            item = item,
            scope = scope,
            onError = onError,
        )
    }
}
