package com.example.unifiedreader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildLibraryFilterFacets
import com.example.unifiedreader.core.model.deriveGridRootItems
import com.example.unifiedreader.core.model.deriveKavitaSeriesGrouping
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteCatalogGridRootsTest {
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
    fun `replaceSourceCatalog persists grid roots matching in-memory derivation`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                kavitaIssueEntity(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
                kavitaBookEntity(id = "kavita:item:book-1", title = "A Standalone Book"),
            ),
        )

        // Roots read back from the persisted table must equal the roots the in-memory index would
        // derive from the same items (fed through the same truncated projection).
        val projectedItems = dao.getLibraryItemsForSourceType(SourceType.KAVITA.name).map { it.toDomain() }
        val expectedRoots = deriveKavitaSeriesGrouping(projectedItems).rootItems.associateBy { it.id }
        val persistedRoots = dao.getGridRootsForSourceType(SourceType.KAVITA.name).map { it.toDomain() }.associateBy { it.id }

        assertEquals(expectedRoots, persistedRoots)
        // Self-documenting: the two issues collapsed into one container; the book stayed a root.
        assertEquals(setOf("kavita:series:libA::s1", "kavita:item:book-1"), persistedRoots.keys)
        assertEquals(2, persistedRoots.getValue("kavita:series:libA::s1").issueCount)
    }

    @Test
    fun `replaceSourceCatalog refreshes grid roots when a source is replaced`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                kavitaIssueEntity(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
            ),
        )

        // A subsequent full replace with a different series must not leave stale roots behind.
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(
                kavitaIssueEntity(id = "kavita:item:9", seriesId = "s2", title = "Series Two #1"),
            ),
        )

        val rootIds = dao.getGridRootsForSourceType(SourceType.KAVITA.name).map { it.id }.toSet()
        assertEquals(setOf("kavita:series:libA::s2"), rootIds)
    }

    @Test
    fun `rebuildAllGridRootsIfEmpty backfills then no-ops`() {
        // Simulate a post-migration state: items present, grid roots empty (bypasses replace's rebuild).
        dao.insertOrReplaceLibraries(listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")))
        dao.insertOrReplaceItems(
            listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                kavitaIssueEntity(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
            ),
        )
        assertEquals(0, dao.countGridRoots())

        dao.rebuildAllGridRootsIfEmpty()
        assertEquals(setOf("kavita:series:libA::s1"), dao.getGridRootsForSourceType(SourceType.KAVITA.name).map { it.id }.toSet())

        // Second call is a no-op because the table is now populated.
        dao.rebuildAllGridRootsIfEmpty()
        assertEquals(1, dao.countGridRoots())
    }

    @Test
    fun `replaceSourceCatalog persists facet values matching the in-memory facets`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                kavitaIssueEntity(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
                kavitaBookEntity(id = "kavita:item:book-1", title = "A Standalone Book"),
            ),
        )

        // Persisted distinct facet values must equal what the in-memory facet builder derives over
        // the same grid roots, per dimension.
        val projectedItems = dao.getLibraryItemsForSourceType(SourceType.KAVITA.name).map { it.toDomain() }
        val roots = deriveGridRootItems(SourceType.KAVITA, projectedItems)
        val expected = buildLibraryFilterFacets(roots)

        assertEquals(expected.genres.toSet(), dao.getDistinctFacetValues(SourceType.KAVITA.name, "GENRE").toSet())
        assertEquals(expected.series.toSet(), dao.getDistinctFacetValues(SourceType.KAVITA.name, "SERIES").toSet())
        assertEquals(expected.libraries.toSet(), dao.getDistinctFacetValues(SourceType.KAVITA.name, "LIBRARY").toSet())
        assertEquals(
            expected.formats.map { it.name }.toSet(),
            dao.getDistinctFacetValues(SourceType.KAVITA.name, "FORMAT").toSet(),
        )
    }

    @Test
    fun `applySourceCatalogDelta refreshes facets for affected libraries`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "One")),
        )
        // Replace the only item with one carrying a different genre; the stale genre must be gone.
        dao.applySourceCatalogDelta(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            upsertItems = listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "One", genre = "Drama"),
            ),
        )

        assertEquals(setOf("Drama"), dao.getDistinctFacetValues(SourceType.KAVITA.name, "GENRE").toSet())
    }

    @Test
    fun `applySourceCatalogDelta grows a series container when an issue is added`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                kavitaIssueEntity(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
            ),
        )

        dao.applySourceCatalogDelta(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            upsertItems = listOf(kavitaIssueEntity(id = "kavita:item:3", seriesId = "s1", title = "Series One #3")),
        )

        val roots = dao.getGridRootsForSourceType(SourceType.KAVITA.name)
        assertEquals(setOf("kavita:series:libA::s1"), roots.map { it.id }.toSet())
        assertEquals(3, roots.single().issueCount)
    }

    @Test
    fun `applySourceCatalogDelta removes a series container when its last issues are removed`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(
                kavitaIssueEntity(id = "kavita:item:1", seriesId = "s1", title = "Series One #1"),
                kavitaIssueEntity(id = "kavita:item:2", seriesId = "s1", title = "Series One #2"),
            ),
        )

        dao.applySourceCatalogDelta(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            upsertItems = emptyList(),
            removeItemIds = listOf("kavita:item:1", "kavita:item:2"),
        )

        assertEquals(emptyList<String>(), dao.getGridRootsForSourceType(SourceType.KAVITA.name).map { it.id })
    }

    @Test
    fun `applySourceCatalogDelta reconciles standalone roots on add and remove`() {
        dao.replaceSourceCatalog(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            items = listOf(kavitaBookEntity(id = "kavita:item:book-1", title = "Book One")),
        )

        dao.applySourceCatalogDelta(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            upsertItems = listOf(kavitaBookEntity(id = "kavita:item:book-2", title = "Book Two")),
        )
        assertEquals(
            setOf("kavita:item:book-1", "kavita:item:book-2"),
            dao.getGridRootsForSourceType(SourceType.KAVITA.name).map { it.id }.toSet(),
        )

        dao.applySourceCatalogDelta(
            sourceType = SourceType.KAVITA.name,
            libraries = listOf(kavitaLibrary(id = "kavita:library:libA", remoteId = "libA")),
            upsertItems = emptyList(),
            removeItemIds = listOf("kavita:item:book-1"),
        )
        assertEquals(
            setOf("kavita:item:book-2"),
            dao.getGridRootsForSourceType(SourceType.KAVITA.name).map { it.id }.toSet(),
        )
    }
}

private fun kavitaLibrary(
    id: String,
    remoteId: String,
): LibraryEntity = LibraryEntity(
    id = id,
    sourceType = SourceType.KAVITA.name,
    title = "Library $remoteId",
    remoteId = remoteId,
    contentType = LibraryContentType.COMICS.name,
)

private fun kavitaIssueEntity(
    id: String,
    seriesId: String,
    title: String,
    genre: String = "Action",
): ReadableItemEntity = ReadableItemEntity(
    id = id,
    sourceType = SourceType.KAVITA.name,
    libraryId = "libA",
    remoteId = id,
    isLocalImport = false,
    remoteFileId = null,
    remoteFileType = seriesId,
    title = title,
    format = "CBZ",
    kind = "COMIC",
    pageCount = 24,
    coverUrl = "https://kavita.example/api/Image/cover?seriesId=$seriesId&apiKey=abc",
    description = null,
    addedAtEpochMillis = 1_000L,
    bookAuthor = null,
    bookAuthorsJson = null,
    bookSeriesName = null,
    bookPublishersJson = null,
    bookGenresJson = null,
    bookTagsJson = null,
    bookLanguagesJson = null,
    comicSeries = "Series $seriesId",
    comicIssueTitle = title,
    comicVolume = null,
    comicIssueNumber = null,
    comicPublishersJson = null,
    comicGenresJson = genre,
    comicTagsJson = null,
    comicLanguagesJson = null,
    updatedAtEpochMillis = 1_000L,
)

private fun kavitaBookEntity(
    id: String,
    title: String,
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
    pageCount = 300,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = 2_000L,
    bookAuthor = "Jane Roe",
    bookAuthorsJson = "Jane Roe",
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
    updatedAtEpochMillis = 2_000L,
)
