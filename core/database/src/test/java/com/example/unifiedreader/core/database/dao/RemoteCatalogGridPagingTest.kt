package com.example.unifiedreader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryAlphabetSection
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.assembleLibraryAlphabetSections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
    fun `last-read order leaves out the rows mark as unread writes, in every query`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                book(id = "kavita:item:read", title = "B Read"),
                book(id = "kavita:item:unread", title = "A Marked Unread"),
                book(id = "kavita:item:never", title = "C Never Opened"),
            ),
        )
        database.readingProgressDao().insertOrReplace(progress(itemId = "kavita:item:read", updatedAt = 5_000L))
        // "Mark as unread" writes a reset row stamped now, newer than any real read.
        database.readingProgressDao().insertOrReplace(
            progress(itemId = "kavita:item:unread", updatedAt = 9_000L).copy(
                percent = null,
                isExplicitUnreadReset = true,
            ),
        )
        // Like a root with no progress: after the read roots, by title.
        val expected = listOf("kavita:item:read", "kavita:item:unread", "kavita:item:never")
        val loadParams = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 10, placeholdersEnabled = false)

        val paged = dao.pagingGridRootsByLastRead(SourceType.KAVITA.name).load(loadParams)
        val filtered = dao.pagingGridRootsFiltered(
            buildFilteredGridRootsQuery(
                sourceType = SourceType.KAVITA.name,
                sortMode = LibrarySortMode.LAST_READ,
                filters = LibraryFilterState(formats = setOf(FormatType.EPUB)),
            ),
        ).load(loadParams)

        assertEquals(expected, (paged as PagingSource.LoadResult.Page).data.map { it.id })
        assertEquals(expected, dao.getGridRootIdsByLastRead(SourceType.KAVITA.name, limit = 10))
        assertEquals(expected, (filtered as PagingSource.LoadResult.Page).data.map { it.id })
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
    fun `member badge projections for roots return each container's issues and each standalone root itself`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                comicIssue(id = "kavita:item:1", seriesId = "s1", title = "S1 #1").copy(remoteFileId = "rf-1"),
                comicIssue(id = "kavita:item:2", seriesId = "s1", title = "S1 #2"),
                comicIssue(id = "kavita:item:3", seriesId = "s1", title = "S1 #3"),
                book(id = "kavita:item:book-1", title = "A Book"),
                // Not in the requested window → must not be returned.
                comicIssue(id = "kavita:item:9", seriesId = "s2", title = "S2 #1"),
            ),
        )

        // The container root + the standalone book root pull in every s1 issue and the book itself.
        val projections = dao.getMemberBadgeProjectionsForRoots(
            sourceType = SourceType.KAVITA.name,
            rootIds = listOf("kavita:series:libA::s1", "kavita:item:book-1"),
        )
        assertEquals(
            setOf("kavita:item:1", "kavita:item:2", "kavita:item:3", "kavita:item:book-1"),
            projections.map { it.id }.toSet(),
        )
        // The narrow projection still carries the columns the badge rollups need — including remoteFileId,
        // which is part of the downloaded-file name so the window's download scan can find members' files.
        val issue = projections.first { it.id == "kavita:item:1" }
        assertEquals("s1", issue.remoteFileType)
        assertEquals("CBZ", issue.format)
        assertEquals("rf-1", issue.remoteFileId)

        // A container-only window returns just that series' issues, nothing from other roots.
        assertEquals(
            setOf("kavita:item:1", "kavita:item:2", "kavita:item:3"),
            dao.getMemberBadgeProjectionsForRoots(
                sourceType = SourceType.KAVITA.name,
                rootIds = listOf("kavita:series:libA::s1"),
            ).map { it.id }.toSet(),
        )
    }

    @Test
    fun `title paging orders by sort key and the alphabet buckets give per-letter offsets`() = runBlocking {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary()),
            items = listOf(
                book(id = "kavita:item:g", title = "Gamma"),
                book(id = "kavita:item:a", title = "Alpha"),
                book(id = "kavita:item:b1", title = "Beta One"),
                book(id = "kavita:item:b2", title = "Beta Two"),
            ),
        )

        val page = dao.pagingGridRootsByTitle(SourceType.KAVITA.name).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals(
            listOf("kavita:item:a", "kavita:item:b1", "kavita:item:b2", "kavita:item:g"),
            page.data.map { it.id },
        )

        // A@0, B@1 (two Betas), G@3 — offsets into the sorted set for the rail to scroll to.
        assertEquals(
            listOf(
                LibraryAlphabetSection(letter = "A", itemOffset = 0),
                LibraryAlphabetSection(letter = "B", itemOffset = 1),
                LibraryAlphabetSection(letter = "G", itemOffset = 3),
            ),
            assembleLibraryAlphabetSections(dao.observeTitleAlphabetBuckets(SourceType.KAVITA.name).first()),
        )
    }

    @Test
    fun `the alphabet rail buckets follow writes to the grid roots`() = runBlocking {
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            dao.observeTitleAlphabetBuckets(SourceType.KAVITA.name).collect { buckets ->
                emissions.send(buckets.map { bucket -> bucket.firstChar.orEmpty() })
            }
        }
        try {
            // As on the first launch after the v28 migration, before the rebuild writes any roots.
            assertEquals(emptyList<String>(), withTimeout(5_000L) { emissions.receive() })

            dao.replaceSourceCatalog(
                sourceType = SourceType.KAVITA.name,
                libraries = listOf(kavitaLibrary()),
                items = listOf(book(id = "kavita:item:1", title = "Akira"), book(id = "kavita:item:2", title = "Berserk")),
            )
            assertEquals(listOf("a", "b"), withTimeout(5_000L) { emissions.receiveUntilChanged(emptyList()) })

            // A later sync adds a letter.
            dao.replaceSourceCatalog(
                sourceType = SourceType.KAVITA.name,
                libraries = listOf(kavitaLibrary()),
                items = listOf(
                    book(id = "kavita:item:1", title = "Akira"),
                    book(id = "kavita:item:2", title = "Berserk"),
                    book(id = "kavita:item:3", title = "Claymore"),
                ),
            )
            assertEquals(listOf("a", "b", "c"), withTimeout(5_000L) { emissions.receiveUntilChanged(listOf("a", "b")) })
        } finally {
            collector.cancel()
        }
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

private suspend fun <T> Channel<T>.receiveUntilChanged(previous: T): T {
    while (true) {
        val next = receive()
        if (next != previous) return next
    }
}
