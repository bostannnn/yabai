package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryFiltersTest {
    @Test
    fun `date added sort orders newest first and puts missing dates last`() {
        val older = item(id = "older", title = "Older").copy(addedAtEpochMillis = 1_000L)
        val missing = item(id = "missing", title = "Missing")
        val newer = item(id = "newer", title = "Newer").copy(addedAtEpochMillis = 3_000L)

        val sortedItems = applyLibrarySort(
            items = listOf(older, missing, newer),
            sortMode = LibrarySortMode.DATE_ADDED,
        )

        assertEquals(listOf(newer, older, missing), sortedItems)
    }

    @Test
    fun `last read sort falls back to title for items without progress`() {
        val recentlyRead = item(id = "recently-read", title = "Zenith")
        val unreadBeta = item(id = "unread-beta", title = "Beta")
        val unreadAlpha = item(id = "unread-alpha", title = "alpha")
        val olderRead = item(id = "older-read", title = "Atlas")

        val sortedItems = applyLibrarySort(
            items = listOf(unreadBeta, recentlyRead, unreadAlpha, olderRead),
            sortMode = LibrarySortMode.LAST_READ,
            lastReadAtEpochMillisByItemId = mapOf(
                recentlyRead.id to 2_000L,
                olderRead.id to 1_000L,
            ),
        )

        assertEquals(
            listOf(recentlyRead, olderRead, unreadAlpha, unreadBeta),
            sortedItems,
        )
    }

    @Test
    fun `last read sort uses title as tie breaker for matching progress timestamps`() {
        val beta = item(id = "beta", title = "Beta")
        val alpha = item(id = "alpha", title = "alpha")

        val sortedItems = applyLibrarySort(
            items = listOf(beta, alpha),
            sortMode = LibrarySortMode.LAST_READ,
            lastReadAtEpochMillisByItemId = mapOf(
                beta.id to 1_000L,
                alpha.id to 1_000L,
            ),
        )

        assertEquals(listOf(alpha, beta), sortedItems)
    }

    @Test
    fun `issue count sort orders largest series first and puts missing counts last`() {
        val singleIssue = item(id = "single", title = "Zulu Single").copy(issueCount = 1)
        val missing = item(id = "missing", title = "Missing")
        val longSeries = item(id = "long", title = "Long Series").copy(issueCount = 12)
        val tieByTitle = item(id = "tie", title = "Alpha Tie").copy(issueCount = 1)

        val sortedItems = applyLibrarySort(
            items = listOf(singleIssue, missing, longSeries, tieByTitle),
            sortMode = LibrarySortMode.ISSUE_COUNT,
        )

        assertEquals(listOf(longSeries, tieByTitle, singleIssue, missing), sortedItems)
    }

    @Test
    fun `comic secondary text keeps issue title when raw title is the issue title`() {
        val issue = item(id = "issue", title = "Chapter 3").copy(
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
            comicMetadata = ComicMetadata(
                series = "GIGANT",
                issueTitle = "Chapter 3",
            ),
        )

        assertEquals("GIGANT", issue.libraryPrimaryText())
        assertEquals("Chapter 3", issue.librarySecondaryText())
    }

    @Test
    fun `comic secondary text names a Kavita volume rather than its Chapter v3`() {
        val volume = item(id = "volume", title = "Chapter v3").copy(
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
            comicMetadata = ComicMetadata(series = "GIGANT", issueTitle = "Chapter v3"),
        )

        assertEquals("Volume 3", volume.librarySecondaryText())
    }

    @Test
    fun `comic secondary text hides issue title when it duplicates series title`() {
        val issue = item(id = "issue", title = "GIGANT").copy(
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
            comicMetadata = ComicMetadata(
                series = "GIGANT",
                issueTitle = "GIGANT",
            ),
        )

        assertNull(issue.librarySecondaryText())
    }

    @Test
    fun `assembleLibraryFilterFacets reproduces buildLibraryFilterFacets for an unfiltered view`() {
        val bookItem = item(id = "b1", title = "A Wizard of Earthsea").copy(
            format = FormatType.EPUB,
            libraryId = "lib-b",
            bookMetadata = BookMetadata(
                author = "Ursula K. Le Guin",
                seriesName = "Earthsea",
                publishers = listOf("Ace"),
                genres = listOf("Fantasy"),
                tags = listOf("classic"),
                languages = listOf("English"),
            ),
        )
        val comicItem = item(id = "c1", title = "Berserk #1").copy(
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
            libraryId = "lib-a",
            comicMetadata = ComicMetadata(
                series = "Berserk",
                publishers = listOf("Dark Horse"),
                genres = listOf("Action", "Fantasy"),
                tags = listOf("seinen"),
                languages = listOf("Japanese"),
            ),
        )
        val pdfItem = item(id = "p1", title = "Starry Night").copy(
            format = FormatType.PDF,
            libraryId = "lib-a",
            bookMetadata = BookMetadata(author = "Vincent van Gogh", genres = listOf("Art")),
        )
        val items = listOf(bookItem, comicItem, pdfItem)

        val expected = buildLibraryFilterFacets(items)
        // Feed the raw per-dimension values in REVERSED order to prove the assembly sorts them itself
        // (SQL SELECT DISTINCT is unordered); fileLocations is the caller-provided runtime dimension.
        val actual = assembleLibraryFilterFacets(
            formatNames = items.map { item -> item.format.name }.reversed(),
            libraries = items.map { item -> item.libraryId }.reversed(),
            fileLocations = expected.fileLocations,
            authors = items.flatMap { item -> item.facetMetadata().authors }.reversed(),
            series = items.flatMap { item -> item.facetMetadata().series }.reversed(),
            publishers = items.flatMap { item -> item.facetMetadata().publishers }.reversed(),
            genres = items.flatMap { item -> item.facetMetadata().genres }.reversed(),
            tags = items.flatMap { item -> item.facetMetadata().tags }.reversed(),
            languages = items.flatMap { item -> item.facetMetadata().languages }.reversed(),
        )

        assertEquals(expected, actual)
    }
}

private fun item(
    id: String,
    title: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.LOCAL,
    libraryId = "local",
    isLocalImport = true,
    title = title,
    format = FormatType.EPUB,
)
