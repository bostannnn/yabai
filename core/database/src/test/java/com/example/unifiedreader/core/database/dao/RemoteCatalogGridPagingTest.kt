package com.example.unifiedreader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
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

    @Test
    fun `last-read paging orders roots by max member progress, newest first, no-progress last`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                // Series s1: two issues; only issue 2 has (recent) progress → container is "most recent".
                comicIssue(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                comicIssue(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
                // A standalone book with older progress.
                book(id = "kavita:item:book-1", title = "A Book"),
                // Series s2: one issue, no progress → sorts last.
                comicIssue(id = "kavita:item:9", seriesId = "s2", title = "Series Two #1"),
            ),
        )
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:2", updatedAt = 9_000L))
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:book-1", updatedAt = 5_000L))

        val firstPage = dao.pagingGridRootsByLastRead(SourceType.KAVITA.name).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )

        firstPage as PagingSource.LoadResult.Page
        assertEquals(
            listOf("kavita:series:libA::s1", "kavita:item:book-1", "kavita:series:libA::s2"),
            firstPage.data.map { it.id },
        )
    }

    @Test
    fun `last-read root ids match paged order and honour the limit`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                comicIssue(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                comicIssue(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
                book(id = "kavita:item:book-1", title = "A Book"),
                comicIssue(id = "kavita:item:9", seriesId = "s2", title = "Series Two #1"),
            ),
        )
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:2", updatedAt = 9_000L))
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:book-1", updatedAt = 5_000L))

        // Same order as pagingGridRootsByLastRead — the cold-start capture records exactly the paged view.
        assertEquals(
            listOf("kavita:series:libA::s1", "kavita:item:book-1", "kavita:series:libA::s2"),
            dao.getGridRootIdsByLastRead(SourceType.KAVITA.name, limit = 10),
        )
        // The limit bounds the returned window, still in order.
        assertEquals(
            listOf("kavita:series:libA::s1", "kavita:item:book-1"),
            dao.getGridRootIdsByLastRead(SourceType.KAVITA.name, limit = 2),
        )
    }

    @Test
    fun `date-added root ids order newest first`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                book(id = "kavita:item:old", title = "Old", addedAt = 1_000L),
                book(id = "kavita:item:new", title = "New", addedAt = 3_000L),
                book(id = "kavita:item:mid", title = "Mid", addedAt = 2_000L),
            ),
        )

        assertEquals(
            listOf("kavita:item:new", "kavita:item:mid", "kavita:item:old"),
            dao.getGridRootIdsByDateAdded(SourceType.KAVITA.name, limit = 10),
        )
    }

    @Test
    fun `shelves input items are progressed items plus their series siblings only`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                comicIssue(id = "kavita:item:1", seriesId = "s1", title = "S1 #1"),
                comicIssue(id = "kavita:item:2", seriesId = "s1", title = "S1 #2"),
                comicIssue(id = "kavita:item:3", seriesId = "s1", title = "S1 #3"),
                book(id = "kavita:item:book-1", title = "A Book"),
                // Untouched series and standalone: no progress anywhere in them → excluded entirely.
                comicIssue(id = "kavita:item:9", seriesId = "s2", title = "S2 #1"),
                comicIssue(id = "kavita:item:10", seriesId = "s2", title = "S2 #2"),
                book(id = "kavita:item:book-2", title = "B Book"),
            ),
        )
        // Progress on ONE issue of s1 and on the standalone book-1.
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:2", updatedAt = 5_000L))
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:book-1", updatedAt = 6_000L))

        val ids = dao.getShelvesInputItems(SourceType.KAVITA.name).map { entity -> entity.id }.toSet()

        // The whole s1 issue list (progress on #2 pulls in its siblings for Next-In-Series) plus the
        // progressed standalone; nothing from the untouched s2 series or book-2.
        assertEquals(
            setOf("kavita:item:1", "kavita:item:2", "kavita:item:3", "kavita:item:book-1"),
            ids,
        )
    }

    @Test
    fun `filtered paging returns only roots matching the content filter, sorted`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                book(id = "kavita:item:epub", title = "Zed Epub"),
                book(id = "kavita:item:pdf-b", title = "Beta Pdf").copy(format = "PDF"),
                book(id = "kavita:item:pdf-a", title = "Alpha Pdf").copy(format = "PDF"),
            ),
        )

        val query = buildFilteredGridRootsQuery(
            sourceType = SourceType.KAVITA.name,
            sortMode = LibrarySortMode.TITLE,
            filters = LibraryFilterState(formats = setOf(FormatType.PDF)),
        )
        val page = dao.pagingGridRootsFiltered(query).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )

        // Only the two PDF roots, in title order; the EPUB root is filtered out.
        page as PagingSource.LoadResult.Page
        assertEquals(listOf("kavita:item:pdf-a", "kavita:item:pdf-b"), page.data.map { it.id })
    }
}

private fun comicIssue(
    id: String,
    seriesId: String,
    title: String,
): ReadableItemEntity = book(id = id, title = title).copy(
    remoteFileType = seriesId,
    format = "CBZ",
    kind = "COMIC",
    comicSeries = "Series $seriesId",
)

private fun progress(
    itemId: String,
    updatedAt: Long,
): ReadingProgressEntity = ReadingProgressEntity(
    itemId = itemId,
    locatorType = "PERCENT",
    page = null,
    cfi = null,
    percent = 0.5,
    isFinished = false,
    isExplicitUnreadReset = false,
    syncState = "CLEAN",
    updatedAtEpochMillis = updatedAt,
)

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
