package com.example.unifiedreader.data.repository

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface LibraryRepository {
    fun getLibraries(): List<Library>

    fun observeLibraries(): Flow<List<Library>>
}

interface ReadableItemRepository {
    fun getItems(): List<ReadableItem>

    fun observeItems(): Flow<List<ReadableItem>>
}

class SampleCatalogRepository : LibraryRepository, ReadableItemRepository {
    private val librariesState = MutableStateFlow(
        listOf(
            Library(
                id = "k-comics",
                serverId = "local-kavita",
                sourceType = SourceType.KAVITA,
                title = "Comics",
            ),
            Library(
                id = "k-magazines",
                serverId = "local-kavita",
                sourceType = SourceType.KAVITA,
                title = "Magazines",
            ),
            Library(
                id = "g-books",
                serverId = "local-grimmory",
                sourceType = SourceType.GRIMMORY,
                title = "Books",
            ),
            Library(
                id = "local-imports",
                serverId = "local-device",
                sourceType = SourceType.LOCAL,
                title = "Local imports",
            ),
        )
    )

    private val itemsState = MutableStateFlow(emptyList<ReadableItem>())

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState
}
