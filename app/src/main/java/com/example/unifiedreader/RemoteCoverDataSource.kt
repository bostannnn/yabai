package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

data class CachedRemoteCover(
    val uri: Uri,
    val isPersistent: Boolean,
    val matchesRequestedVariant: Boolean = true,
)

interface RemoteCoverDataSource {
    val sourceType: SourceType

    val usesRemoteDisplayFallback: Boolean
        get() = true

    fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover?

    suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri?

    suspend fun warmCacheIndexes() = Unit

    suspend fun clearCachedCovers()
}
