package com.example.unifiedreader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteCatalogGridPagingTest {
    private lateinit var database: UnifiedReaderDatabase
    private lateinit var dao: RemoteCatalogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.remoteCatalogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `title paging source loads only the requested window, sorted by title`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = (1..5).map { index -> book(id = "kavita:item:$index", title = "Book %02d".format(index)) },
        )

        val firstPage = dao.pagingGridRootsByTitle(SourceType.KAVITA.name).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false),
        )

        // The window is bounded to loadSize (2) even though 5 roots exist — the whole table is not
        // materialized — and it comes back in title order.
        firstPage as PagingSource.LoadResult.Page
        assertEquals(2, firstPage.data.size)
        assertEquals(listOf("Book 01", "Book 02"), firstPage.data.map { it.title })
        assertTrue("more pages should remain", firstPage.nextKey != null)
    }

    @Test
    fun `date-added paging source orders newest first`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                book(id = "kavita:item:old", title = "Old", addedAt = 1_000L),
                book(id = "kavita:item:new", title = "New", addedAt = 3_000L),
                book(id = "kavita:item:mid", title = "Mid", addedAt = 2_000L),
            ),
        )

        val firstPage = dao.pagingGridRootsByDateAdded(SourceType.KAVITA.name).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false),
        )

        firstPage as PagingSource.LoadResult.Page
        assertEquals(listOf("New", "Mid"), firstPage.data.map { it.title })
    }
}

private fun kavitaLibrary(): LibraryEntity = LibraryEntity(
    id = "kavita:library:libA",
    sourceType = SourceType.KAVITA.name,
    title = "Library",
    remoteId = "libA",
    contentType = LibraryContentType.BOOKS.name,
)

private fun book(
    id: String,
    title: String,
    addedAt: Long? = 1_000L,
): ReadableItemEntity = ReadableItemEntity(
    id = id,
    sourceType = SourceType.KAVITA.name,
    libraryId = "libA",
    remoteId = id,
    isLocalImport = false,
    remoteFileId = null,
    remoteFileType = null,
    title = title,
    format = "EPUB",
    kind = "BOOK",
    pageCount = 100,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = addedAt,
    bookAuthor = null,
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
    updatedAtEpochMillis = addedAt ?: 0L,
)
