package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

interface RemoteCatalogWriter {
    suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem>

    suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    )
}
