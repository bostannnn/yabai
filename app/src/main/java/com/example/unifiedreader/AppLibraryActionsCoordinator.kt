package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class AppLibraryActionsCoordinator @Inject constructor(
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val deleteImportedItemUseCase: DeleteImportedItemUseCase,
    private val deleteRemoteLocalFileUseCase: DeleteRemoteLocalFileUseCase,
    private val stumpCoverCacheController: StumpCoverCacheController,
    private val grimmoryCoverCacheController: GrimmoryCoverCacheController,
    private val kavitaCredentialStore: KavitaCredentialStore,
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
    ): String? = when (item.sourceType) {
        SourceType.STUMP -> stumpCoverCacheController.coverImageUrlFor(item)
        SourceType.GRIMMORY -> grimmoryCoverCacheController.coverImageUrlFor(item)
        SourceType.KAVITA -> rewriteRemoteAssetUrlForActiveServer(
            url = item.coverUrl,
            session = kavitaCredentialStore.activeSessionFor(item),
        )
        SourceType.LOCAL -> item.coverUrl
    }

    fun ensureCoverCached(
        item: ReadableItem,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        when (item.sourceType) {
            SourceType.STUMP -> stumpCoverCacheController.ensureCoverCached(
                item = item,
                scope = scope,
                onError = onError,
            )
            SourceType.GRIMMORY -> grimmoryCoverCacheController.ensureCoverCached(
                item = item,
                scope = scope,
                onError = onError,
            )
            SourceType.KAVITA,
            SourceType.LOCAL -> Unit
        }
    }
}
