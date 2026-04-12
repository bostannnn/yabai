package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

data class CachedRemoteCover(
    val uri: Uri,
    val isPersistent: Boolean,
)

interface RemoteCoverDataSource {
    val sourceType: SourceType

    fun cachedCover(
        item: ReadableItem,
    ): CachedRemoteCover?

    suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri?

    suspend fun clearCachedCovers()
}
