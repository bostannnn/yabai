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
class StumpCoverCacheController @Inject constructor(
    private val stumpCoverRepository: StumpCoverDataSource,
) {
    private val coverUrisByItemId = mutableStateMapOf<String, String>()
    private val pendingCoverIds = mutableSetOf<String>()
    private var lastErrorMessage: String? = null

    fun coverImageUrlFor(
        item: ReadableItem,
    ): String? = coverUrisByItemId[item.id] ?: item.coverUrl

    fun ensureCoverCached(
        item: ReadableItem,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        if (item.sourceType != SourceType.STUMP) return
        if (item.id in pendingCoverIds) return
        val currentCoverUrl = coverUrisByItemId[item.id]
        if (currentCoverUrl?.startsWith("file:", ignoreCase = true) == true) return
        pendingCoverIds += item.id
        scope.launch {
            runCatching {
                stumpCoverRepository.getCoverUri(item)?.toString()
            }.onSuccess { cachedUri ->
                if (!cachedUri.isNullOrBlank()) {
                    Log.d(STUMP_COVER_LOG_TAG, "Cached Stump cover for ${item.id}: $cachedUri")
                    coverUrisByItemId[item.id] = cachedUri
                    lastErrorMessage = null
                } else {
                    Log.w(STUMP_COVER_LOG_TAG, "Stump cover cache returned no URI for ${item.id}.")
                }
            }.onFailure { throwable ->
                Log.w(STUMP_COVER_LOG_TAG, "Unable to cache Stump cover for ${item.id}.", throwable)
                val message = "Unable to load Stump covers: ${throwable.userFacingMessage()}"
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

private const val STUMP_COVER_LOG_TAG = "UnifiedReaderStumpCover"
