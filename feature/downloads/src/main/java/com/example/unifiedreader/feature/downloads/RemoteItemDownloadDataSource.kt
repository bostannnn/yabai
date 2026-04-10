package com.example.unifiedreader.feature.downloads

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

interface RemoteItemDownloadDataSource {
    val sourceType: SourceType

    fun supports(
        item: ReadableItem,
    ): Boolean = item.sourceType == sourceType

    fun storedDocumentUri(
        item: ReadableItem,
    ): Uri?

    suspend fun getDocumentUri(
        item: ReadableItem,
    ): Uri? = storedDocumentUri(item) ?: downloadToLocalStorage(item)

    suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri?

    suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String>

    suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean
}
