package com.example.unifiedreader

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ViewModelScoped
class GrimmoryCoverCacheController @Inject constructor(
    private val grimmoryCoverRepository: GrimmoryCoverDataSource,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) {
    private val coverUrisByItemId = mutableStateMapOf<String, String>()
    private val pendingCoverIds = mutableSetOf<String>()
    private var lastErrorMessage: String? = null

    fun coverImageUrlFor(
        item: ReadableItem,
    ): String? = coverUrisByItemId[item.id] ?: rewriteRemoteAssetUrlForActiveServer(
        url = item.coverUrl,
        session = grimmoryCredentialStore.activeSessionFor(item),
    )

    fun ensureCoverCached(
        item: ReadableItem,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        if (item.sourceType != SourceType.GRIMMORY) return
        if (item.id in pendingCoverIds) return
        val currentCoverUrl = coverUrisByItemId[item.id]
        if (currentCoverUrl?.startsWith("file:", ignoreCase = true) == true) return
        pendingCoverIds += item.id
        scope.launch {
            runCatching {
                grimmoryCoverRepository.getCoverUri(item)?.toString()
            }.onSuccess { cachedUri ->
                if (!cachedUri.isNullOrBlank()) {
                    Log.d(LOG_TAG, "Cached Grimmory cover for ${item.id}: $cachedUri")
                    coverUrisByItemId[item.id] = cachedUri
                    lastErrorMessage = null
                } else {
                    Log.w(LOG_TAG, "Grimmory cover cache returned no URI for ${item.id}.")
                }
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Unable to cache Grimmory cover for ${item.id}.", throwable)
                val message = "Unable to load Grimmory covers: ${throwable.userFacingMessage()}"
                if (lastErrorMessage != message) {
                    lastErrorMessage = message
                    onError(message)
                }
            }.also {
                pendingCoverIds -= item.id
            }
        }
    }

    fun removeMissingItems(
        activeItemIds: Set<String>,
    ) {
        coverUrisByItemId.keys
            .toList()
            .filterNot(activeItemIds::contains)
            .forEach(coverUrisByItemId::remove)
    }

    fun clear() {
        coverUrisByItemId.clear()
        pendingCoverIds.clear()
        lastErrorMessage = null
    }
}

private const val LOG_TAG = "UnifiedReader"
