package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogRepositoriesTest {
    @Test
    fun `room remote catalog repository seeds cached libraries and items asynchronously`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = listOf(
                LibraryEntity(
                    id = SourceType.GRIMMORY.remoteLibraryId("books"),
                    sourceType = SourceType.GRIMMORY.name,
                    title = "Books",
                    remoteId = "books",
                    contentType = LibraryContentType.BOOKS.name,
                ),
            ),
            items = listOf(
                ReadableItemEntity(
                    id = SourceType.GRIMMORY.remoteItemId("book-1"),
                    sourceType = SourceType.GRIMMORY.name,
                    libraryId = SourceType.GRIMMORY.remoteLibraryId("books"),
                    remoteId = "book-1",
                    isLocalImport = false,
                    remoteFileId = "11",
                    remoteFileType = FormatType.EPUB.name,
                    title = "Book 1",
                    format = FormatType.EPUB.name,
                    kind = ReadableItemKind.BOOK.name,
                    pageCount = 200,
                    coverUrl = null,
                    description = null,
                    addedAtEpochMillis = 10L,
                    bookAuthor = "Author",
                    bookAuthorsJson = null,
                    bookSeriesName = null,
                    bookPublishersJson = null,
                    bookGenresJson = null,
                    bookTagsJson = null,
                    bookLanguagesJson = null,
                    comicSeries = null,
                    comicIssueTitle = null,
                    comicVolume = null,
                    comicIssueNumber = null,
                    comicPublishersJson = null,
                    comicGenresJson = null,
                    comicTagsJson = null,
                    comicLanguagesJson = null,
                    updatedAtEpochMillis = 10L,
                ),
            ),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomRemoteCatalogRepository(
                remoteCatalogDao = dao,
                ioDispatcher = Dispatchers.Unconfined,
                scope = scope,
            )

            val libraries = withTimeout(1_000L) {
                repository.observeLibraries()
                    .dropWhile { it.isEmpty() }
                    .first()
            }
            val items = withTimeout(1_000L) {
                repository.observeItems()
                    .dropWhile { it.isEmpty() }
                    .first()
            }

            assertEquals(listOf("Books"), libraries.map { it.title })
            assertEquals(listOf("Book 1"), items.map { it.title })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `room remote catalog repository still reports hydrated when initial item seed fails`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = emptyList(),
            items = emptyList(),
            getItemsFailure = IllegalStateException("boom"),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomRemoteCatalogRepository(
                remoteCatalogDao = dao,
                ioDispatcher = Dispatchers.Unconfined,
                scope = scope,
            )

            assertTrue(withTimeout(1_000L) {
                repository.isHydratedFlow.first { it }
            })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `room remote catalog repository persists cached remote libraries by source and account`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = emptyList(),
            items = emptyList(),
        )
        val repository = RoomRemoteCatalogRepository(
            remoteCatalogDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        repository.replaceCachedLibraries(
            sourceType = SourceType.KAVITA,
            baseUrl = "https://kavita.example",
            username = "reader",
            libraries = listOf(
                com.example.unifiedreader.core.model.Library(
                    id = SourceType.KAVITA.remoteLibraryId("comics"),
                    sourceType = SourceType.KAVITA,
                    title = "Comics",
                    remoteId = "comics",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
        )

        val cachedLibraries = repository.getCachedLibraries(
            sourceType = SourceType.KAVITA,
            baseUrl = "https://kavita.example",
            username = "reader",
        )

        assertEquals(listOf("Comics"), cachedLibraries.map { it.title })
    }
}

private class FakeRemoteCatalogDao(
    libraries: List<LibraryEntity>,
    items: List<ReadableItemEntity>,
    private val getItemsFailure: Throwable? = null,
) : RemoteCatalogDao() {
    private val librariesState = MutableStateFlow(libraries)
    private val itemsState = MutableStateFlow(items)
    private val cachedLibrariesByKey = mutableMapOf<Triple<String, String, String>, List<RemoteLibraryCacheEntity>>()

    override fun getLibraries(): List<LibraryEntity> = librariesState.value

    override fun observeLibraries(): Flow<List<LibraryEntity>> = librariesState

    override fun getItems(): List<ReadableItemEntity> {
        getItemsFailure?.let { throw it }
        return itemsState.value
    }

    override fun observeItems(): Flow<List<ReadableItemEntity>> = itemsState

    override fun getItemsForSourceType(sourceType: String): List<ReadableItemEntity> =
        itemsState.value.filter { it.sourceType == sourceType }

    override fun getCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
    ): List<RemoteLibraryCacheEntity> = cachedLibrariesByKey[Triple(sourceType, baseUrl, username)].orEmpty()

    override fun insertOrReplaceLibraries(libraries: List<LibraryEntity>) {
        librariesState.value = libraries
    }

    override fun insertOrReplaceItems(items: List<ReadableItemEntity>) {
        itemsState.value = items
    }

    override fun insertOrReplaceCachedLibraries(entries: List<RemoteLibraryCacheEntity>) {
        entries.groupBy { Triple(it.sourceType, it.baseUrl, it.username) }
            .forEach { (key, value) ->
                cachedLibrariesByKey[key] = value
            }
    }

    protected override fun deleteItemsForSourceType(sourceType: String) {
        itemsState.value = itemsState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteLibrariesForSourceType(sourceType: String) {
        librariesState.value = librariesState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
    ) {
        cachedLibrariesByKey.remove(Triple(sourceType, baseUrl, username))
    }
}
