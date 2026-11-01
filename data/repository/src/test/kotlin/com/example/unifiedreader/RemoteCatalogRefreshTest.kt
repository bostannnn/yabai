package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogRefreshTest {
    @Test
    fun `plan is null when every book and library matches what is stored`() {
        val stored = storedCatalog(books = listOf(book("1"), book("2")), writtenAt = 100L)

        val plan = planRemoteCatalogRefresh(
            sourceType = SourceType.GRIMMORY,
            libraries = listOf(library().toEntity()),
            items = listOf(book("2"), book("1")).map { it.toEntity(updatedAtEpochMillis = 999L) },
            stored = stored,
        )

        assertNull(plan)
    }

    @Test
    fun `plan writes only the new and changed books and removes the ones the server dropped`() {
        val stored = storedCatalog(books = listOf(book("1"), book("2"), book("3")), writtenAt = 100L)

        val plan = planRemoteCatalogRefresh(
            sourceType = SourceType.GRIMMORY,
            libraries = listOf(library().toEntity()),
            items = listOf(
                book("1"),
                book("2").copy(bookMetadata = BookMetadata(author = "Author 2", genres = listOf("Mystery"))),
                book("4"),
            ).map { it.toEntity(updatedAtEpochMillis = 999L) },
            stored = stored,
        )!!

        assertEquals(listOf(book("2").id, book("4").id), plan.upsertItems.map(ReadableItemEntity::id))
        assertEquals(setOf(book("3").id), plan.removeItemIds)
        assertEquals(listOf(library().toEntity()), plan.libraries)
    }

    @Test
    fun `plan writes the libraries when only a library changed`() {
        val stored = storedCatalog(books = listOf(book("1")), writtenAt = 100L)

        val plan = planRemoteCatalogRefresh(
            sourceType = SourceType.GRIMMORY,
            libraries = listOf(library(title = "Novels").toEntity()),
            items = listOf(book("1").toEntity(updatedAtEpochMillis = 999L)),
            stored = stored,
        )!!

        assertTrue(plan.upsertItems.isEmpty())
        assertTrue(plan.removeItemIds.isEmpty())
        assertEquals("Novels", plan.libraries.single().title)
    }

    @Test
    fun `a refresh that finds nothing new writes nothing and says so`() = runTest {
        val writer = RefreshTestCatalogWriter(stored = storedCatalog(books = listOf(book("1"), book("2")), writtenAt = 100L))
        var pushedItems: List<ReadableItem> = emptyList()

        val outcome = refresh(writer, books = listOf(book("1"), book("2"))) { items -> pushedItems = items }

        assertFalse(outcome.hasCatalogChanges)
        assertEquals(0, writer.replaceCalls)
        assertEquals(emptyList<RemoteCatalogDelta>(), writer.deltas)
        // Progress still goes up for every book, as in a sync.
        assertEquals(listOf(book("1"), book("2")), pushedItems)
        assertEquals(2, outcome.itemCount)
    }

    @Test
    fun `a refresh that finds a new book writes only that book`() = runTest {
        val writer = RefreshTestCatalogWriter(stored = storedCatalog(books = listOf(book("1")), writtenAt = 100L))

        val outcome = refresh(writer, books = listOf(book("1"), book("2")))

        assertTrue(outcome.hasCatalogChanges)
        assertEquals(0, writer.replaceCalls)
        assertEquals(listOf(book("2").id), writer.deltas.single().upsertItems.map(ReadableItemEntity::id))
        assertTrue(writer.deltas.single().removeItemIds.isEmpty())
    }

    @Test
    fun `a refresh replaces the catalog whole when the writer can't give its rows`() = runTest {
        val writer = RefreshTestCatalogWriter(stored = null)

        val outcome = refresh(writer, books = listOf(book("1")))

        assertTrue(outcome.hasCatalogChanges)
        assertEquals(1, writer.replaceCalls)
        assertTrue(writer.deltas.isEmpty())
    }

    @Test
    fun `a sync replaces the catalog whole even when nothing changed`() = runTest {
        val writer = RefreshTestCatalogWriter(stored = storedCatalog(books = listOf(book("1")), writtenAt = 100L))

        val outcome = finalize(writer, books = listOf(book("1")), writeOnlyChanges = false)

        assertTrue(outcome.hasCatalogChanges)
        assertEquals(1, writer.replaceCalls)
        assertEquals(0, writer.storedReads)
    }

    private suspend fun refresh(
        writer: RefreshTestCatalogWriter,
        books: List<ReadableItem>,
        onPush: (List<ReadableItem>) -> Unit = {},
    ) = finalize(writer, books, writeOnlyChanges = true, onPush = onPush)

    private suspend fun finalize(
        writer: RefreshTestCatalogWriter,
        books: List<ReadableItem>,
        writeOnlyChanges: Boolean,
        onPush: (List<ReadableItem>) -> Unit = {},
    ): RemoteCatalogSyncOutcome<Unit> = finalizeRemoteCatalogSync(
        sourceType = SourceType.GRIMMORY,
        fetch = RemoteCatalogFetch(libraries = listOf(library()), entries = books),
        remoteCatalogWriter = writer,
        progressSyncStore = null,
        nowEpochMillis = 999L,
        onProgress = { _, _ -> },
        savingMessage = "Saving",
        applyingProgressMessage = "Applying",
        itemOf = { item -> item },
        progressOf = { null },
        writeOnlyChanges = writeOnlyChanges,
    ) { items ->
        onPush(items)
        RemoteCatalogProgressPushResult(session = Unit, failures = emptyList())
    }
}

private fun library(title: String = "Books"): Library = Library(
    id = SourceType.GRIMMORY.remoteLibraryId("books"),
    sourceType = SourceType.GRIMMORY,
    title = title,
    remoteId = "books",
    contentType = LibraryContentType.BOOKS,
)

private fun book(id: String): ReadableItem = ReadableItem(
    id = SourceType.GRIMMORY.remoteItemId(id),
    sourceType = SourceType.GRIMMORY,
    libraryId = SourceType.GRIMMORY.remoteLibraryId("books"),
    remoteId = id,
    title = "Book $id",
    format = FormatType.EPUB,
    pageCount = 300,
    coverUrl = "https://grimmory.example/api/v1/media/book/$id/cover",
    description = "A long description. ".repeat(60),
    addedAtEpochMillis = 10L,
    bookMetadata = BookMetadata(author = "Author $id", genres = listOf("Fiction"), tags = listOf("Paris")),
)

private fun storedCatalog(
    books: List<ReadableItem>,
    writtenAt: Long,
): StoredRemoteCatalog = StoredRemoteCatalog(
    libraries = listOf(library().toEntity()),
    items = books.map { it.toEntity(updatedAtEpochMillis = writtenAt) },
)

private class RefreshTestCatalogWriter(
    private val stored: StoredRemoteCatalog?,
) : RemoteCatalogWriter {
    var replaceCalls = 0
    var storedReads = 0
    val deltas = mutableListOf<RemoteCatalogDelta>()

    override suspend fun getItemsForSourceType(sourceType: SourceType): List<ReadableItem> = emptyList()

    override suspend fun getStoredSourceCatalog(sourceType: SourceType): StoredRemoteCatalog? {
        storedReads += 1
        return stored
    }

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
        syncGroups: List<RemoteCatalogSyncGroupEntity>,
    ) {
        replaceCalls += 1
    }

    override suspend fun applySourceCatalogDelta(delta: RemoteCatalogDelta) {
        deltas += delta
    }
}
