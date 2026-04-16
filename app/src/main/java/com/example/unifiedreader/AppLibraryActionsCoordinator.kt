package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
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
        variant: LibraryCoverImageVariant,
    ): String? = remoteCoverController.coverImageUrlFor(item, variant)

    fun prepareCoverSource(
        sourceType: SourceType,
    ) {
        remoteCoverController.prepareSource(sourceType)
    }

    fun coverPrefetchKeyFor(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): String? = remoteCoverController.coverPrefetchKeyFor(item, variant)

    fun ensureCoverCached(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        remoteCoverController.ensureCoverCached(
            item = item,
            variant = variant,
            scope = scope,
            onError = onError,
        )
    }
}
