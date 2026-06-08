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
        val issue = item(id = "issue", title = "Chapter v3").copy(
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
            comicMetadata = ComicMetadata(
                series = "GIGANT",
                issueTitle = "Chapter v3",
            ),
        )

        assertEquals("GIGANT", issue.libraryPrimaryText())
        assertEquals("Chapter v3", issue.librarySecondaryText())
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
