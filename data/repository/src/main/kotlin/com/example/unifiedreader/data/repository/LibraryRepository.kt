package com.example.unifiedreader.data.repository

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
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
                id = SourceType.KAVITA.remoteLibraryId("comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "comics",
                contentType = LibraryContentType.COMICS,
            ),
            Library(
                id = SourceType.KAVITA.remoteLibraryId("magazines"),
                sourceType = SourceType.KAVITA,
                title = "Magazines",
                remoteId = "magazines",
                contentType = LibraryContentType.COMICS,
            ),
            Library(
                id = SourceType.GRIMMORY.remoteLibraryId("books"),
                sourceType = SourceType.GRIMMORY,
                title = "Books",
                remoteId = "books",
                contentType = LibraryContentType.BOOKS,
            ),
            Library(
                id = "local-imports",
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
