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
    val knownSourceLibraryScopeIds: Set<String> = emptySet(),
)

class AppLibraryStateAssembler @Inject constructor(
    private val localDocumentRepository: LocalDocumentDataSource,
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val remoteItemLocalStateCache: RemoteItemLocalStateCache = EmptyRemoteItemLocalStateCache,
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
        val state = RemoteItemLocalState(
            downloadedItemIds = downloadedItemIds,
            locallyAvailableItemIds = downloadedItemIds + downloadedItemIds.downloadedSeriesContainerIds(items),
            knownSourceLibraryScopeIds = items.remoteItemLocalStateSourceLibraryScopeIds(),
        )
        remoteItemLocalStateCache.store(
            items = items,
            state = state,
        )
        return state
    }

    fun loadCachedRemoteItemLocalState(
        items: List<ReadableItem>,
    ): RemoteItemLocalState? {
        return remoteItemLocalStateCache.load(items)
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

    /**
     * Synchronous, main-thread-safe snapshot for the first frame / recreation. It reads only the
     * already-in-memory catalog plus the synchronous local-state cache (no fresh download scan, no
     * catalog disk read), so it is safe to call from ViewModel construction without blocking.
     */
    internal fun buildInitialSnapshot(
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
        val cachedRemoteItemLocalState = loadCachedRemoteItemLocalState(items)
        return assembleSnapshot(
            libraries = libraries,
            catalogItems = catalogItems,
            items = items,
            remoteItemLocalState = cachedRemoteItemLocalState ?: RemoteItemLocalState(
                downloadedItemIds = emptySet(),
                locallyAvailableItemIds = emptySet(),
                knownSourceLibraryScopeIds = emptySet(),
            ),
            remoteItemLocalStateKnownSourceLibraryScopeIds =
                cachedRemoteItemLocalState?.knownSourceLibraryScopeIds ?: emptySet(),
            progressByItemId = progressByItemId,
            sessionByItemId = sessionByItemId,
            isRemoteCatalogHydrated = isRemoteCatalogHydrated,
            isLocalCatalogHydrated = isLocalCatalogHydrated,
            themeMode = themeMode,
        )
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
        includeRemoteItemLocalState: Boolean = false,
    ): AppCatalogSnapshot {
        if (!includeRemoteItemLocalState) {
            return buildInitialSnapshot(
                libraries = libraries,
                catalogItems = catalogItems,
                importedItems = importedItems,
                progressByItemId = progressByItemId,
                sessionByItemId = sessionByItemId,
                isRemoteCatalogHydrated = isRemoteCatalogHydrated,
                isLocalCatalogHydrated = isLocalCatalogHydrated,
                themeMode = themeMode,
            )
        }
        val items = catalogItems + importedItems
        return assembleSnapshot(
            libraries = libraries,
            catalogItems = catalogItems,
            items = items,
            remoteItemLocalState = loadRemoteItemLocalState(items),
            remoteItemLocalStateKnownSourceLibraryScopeIds = items.remoteItemLocalStateSourceLibraryScopeIds(),
            progressByItemId = progressByItemId,
            sessionByItemId = sessionByItemId,
            isRemoteCatalogHydrated = isRemoteCatalogHydrated,
            isLocalCatalogHydrated = isLocalCatalogHydrated,
            themeMode = themeMode,
        )
    }

    private fun assembleSnapshot(
        libraries: List<Library>,
        catalogItems: List<ReadableItem>,
        items: List<ReadableItem>,
        remoteItemLocalState: RemoteItemLocalState,
        remoteItemLocalStateKnownSourceLibraryScopeIds: Set<String>,
        progressByItemId: Map<String, ReaderProgressSnapshot>,
        sessionByItemId: Map<String, ReaderSessionSnapshot>,
        isRemoteCatalogHydrated: Boolean,
        isLocalCatalogHydrated: Boolean,
        themeMode: ThemeMode,
    ): AppCatalogSnapshot {
        return AppCatalogSnapshot(
            libraries = libraries,
            catalogItems = catalogItems,
            items = items,
            downloadedRemoteItemIds = remoteItemLocalState.downloadedItemIds,
            locallyAvailableRemoteItemIds = remoteItemLocalState.locallyAvailableItemIds,
            includesRemoteItemLocalState = remoteItemLocalStateKnownSourceLibraryScopeIds.isNotEmpty(),
            remoteItemLocalStateKnownSourceLibraryScopeIds = remoteItemLocalStateKnownSourceLibraryScopeIds,
            progressByItemId = buildProgressSummaries(
                items = items,
                progressByItemId = progressByItemId,
                sessionByItemId = sessionByItemId,
            ),
            hydratedSources = buildSet {
                if (isRemoteCatalogHydrated) {
                    add(SourceType.KAVITA)
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
