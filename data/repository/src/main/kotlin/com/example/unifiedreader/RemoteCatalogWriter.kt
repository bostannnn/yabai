package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

data class RemoteCatalogSyncGroup(
    val groupId: String,
    val libraryId: String?,
    val fingerprint: String,
)

data class RemoteCatalogDelta(
    val sourceType: SourceType,
    val libraries: List<LibraryEntity>,
    val upsertItems: List<ReadableItemEntity>,
    val removeItemIds: Set<String> = emptySet(),
    val syncGroups: List<RemoteCatalogSyncGroupEntity> = emptyList(),
    val removeSyncGroupIds: Set<String> = emptySet(),
)

interface RemoteCatalogWriter {
    suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem>

    suspend fun getCatalogSyncGroups(
        sourceType: SourceType,
    ): List<RemoteCatalogSyncGroup> = emptyList()

    suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
        syncGroups: List<RemoteCatalogSyncGroupEntity> = emptyList(),
    )

    suspend fun applySourceCatalogDelta(
        delta: RemoteCatalogDelta,
    )
}
