package com.example.unifiedreader

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.settings.ThemeMode
import javax.inject.Inject

data class RemoteItemLocalState(
    val downloadedItemIds: Set<String>,
    val locallyAvailableItemIds: Set<String>,
)

class AppLibraryStateAssembler @Inject constructor(
    private val localDocumentRepository: LocalDocumentDataSource,
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
) {
    fun buildProgressSummaries(
        items: List<ReadableItem>,
        progressByItemId: Map<String, ReaderProgressSnapshot>,
        sessionByItemId: Map<String, ReaderSessionSnapshot>,
    ): Map<String, ItemProgressSummary> = buildProgressSummaries(
        items = items,
        progressByItemId = progressByItemId,
        sessionByItemId = sessionByItemId,
        localPageCountProvider = localDocumentRepository::getPageCount,
    )

    suspend fun loadRemoteItemLocalState(
        items: List<ReadableItem>,
    ): RemoteItemLocalState {
        val downloadedItemIds = buildSet {
            remoteDownloadDataSources.forEach { dataSource ->
                addAll(dataSource.storedDocumentItemIds(items))
            }
        }
        return RemoteItemLocalState(
            downloadedItemIds = downloadedItemIds,
            locallyAvailableItemIds = downloadedItemIds + downloadedItemIds.downloadedSeriesContainerIds(items),
        )
    }

    private fun Set<String>.downloadedSeriesContainerIds(
        items: List<ReadableItem>,
    ): Set<String> = buildSet {
        items.forEach { item ->
            if (item.id !in this@downloadedSeriesContainerIds) return@forEach
            if (remoteDownloadDataSources.downloadDataSourceFor(item) == null) return@forEach
            when (item.sourceType) {
                SourceType.KAVITA -> item.kavitaSeriesGroupKey()?.let(::kavitaSeriesContainerId)
                SourceType.STUMP -> item.stumpSeriesGroupKey()?.let(::stumpSeriesContainerId)
                SourceType.GRIMMORY,
                SourceType.LOCAL -> null
            }?.let(::add)
        }
    }

    internal suspend fun buildSnapshot(
        libraries: List<Library>,
        catalogItems: List<ReadableItem>,
        importedItems: List<ReadableItem>,
        progressByItemId: Map<String, ReaderProgressSnapshot>,
        sessionByItemId: Map<String, ReaderSessionSnapshot>,
        isRemoteCatalogHydrated: Boolean,
        isLocalCatalogHydrated: Boolean,
        themeMode: ThemeMode,
    ): AppCatalogSnapshot {
        val items = catalogItems + importedItems
        val remoteItemLocalState = loadRemoteItemLocalState(items)
        return AppCatalogSnapshot(
            libraries = libraries,
            catalogItems = catalogItems,
            items = items,
            downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
            locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
            progressByItemId = buildProgressSummaries(
                items = items,
                progressByItemId = progressByItemId,
                sessionByItemId = sessionByItemId,
            ),
            hydratedSources = buildSet {
                add(SourceType.KAVITA)
                if (isRemoteCatalogHydrated) {
                    add(SourceType.STUMP)
                    add(SourceType.GRIMMORY)
                }
                if (isLocalCatalogHydrated) {
                    add(SourceType.LOCAL)
                }
            },
            themeMode = themeMode,
        )
    }
}
