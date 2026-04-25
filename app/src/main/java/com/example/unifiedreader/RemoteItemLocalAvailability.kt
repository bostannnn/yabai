package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

class RemoteItemLocalAvailability @Inject constructor(
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
) {
    fun hasStoredDocument(
        item: ReadableItem,
    ): Boolean = remoteDownloadDataSources.downloadDataSourceFor(item)
        ?.storedDocumentUri(item) != null

    fun withoutStoredDocuments(
        items: List<ReadableItem>,
    ): List<ReadableItem> = items.filterNot(::hasStoredDocument)
}
