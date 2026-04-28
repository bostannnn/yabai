package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
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
