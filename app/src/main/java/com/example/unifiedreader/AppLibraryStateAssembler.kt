package com.example.unifiedreader

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.settings.ThemeMode
import javax.inject.Inject

class AppLibraryStateAssembler @Inject constructor(
    private val localDocumentRepository: LocalDocumentDataSource,
    private val grimmoryContentRepository: GrimmoryContentDataSource,
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

    suspend fun loadDownloadedRemoteItemIds(
        items: List<ReadableItem>,
    ): Set<String> = buildSet {
        addAll(grimmoryContentRepository.storedDocumentItemIds(items))
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
        return AppCatalogSnapshot(
            libraries = libraries,
            catalogItems = catalogItems,
            items = items,
            downloadedRemoteItemIds = loadDownloadedRemoteItemIds(items),
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
