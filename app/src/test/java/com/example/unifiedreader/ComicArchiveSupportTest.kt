package com.example.unifiedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicArchiveSupportTest {
    @Test
    fun `natural page order sorts numbered filenames as expected`() {
        val sorted = listOf(
            "page10.jpg",
            "page2.jpg",
            "page1.jpg",
            "page11.jpg",
        ).sortedWith(NaturalPageOrderComparator)

        assertEquals(
            listOf("page1.jpg", "page2.jpg", "page10.jpg", "page11.jpg"),
            sorted,
        )
    }

    @Test
    fun `comic archive title strips cbz and cbr suffixes case insensitively`() {
        assertEquals("Akira v01", "Akira v01.cbz".comicArchiveTitle())
        assertEquals("Blame! v02", "Blame! v02.CBR".comicArchiveTitle())
        assertEquals("Planetes", "Planetes".comicArchiveTitle())
    }

    @Test
    fun `cache refresh is skipped for unknown signature while ttl is still fresh`() {
        val shouldRefresh = ComicArchiveCodecsTestAccess.shouldRefreshArchiveCache(
            localFileExists = true,
            localFileLength = 128L,
            currentSignature = "unknown",
            cachedSignature = "unknown",
            localFileLastModified = 10_000L,
            nowEpochMillis = 65_000L,
        )

        assertFalse(shouldRefresh)
    }

    @Test
    fun `cache refresh happens for unknown signature after ttl expires`() {
        val shouldRefresh = ComicArchiveCodecsTestAccess.shouldRefreshArchiveCache(
            localFileExists = true,
            localFileLength = 128L,
            currentSignature = "unknown",
            cachedSignature = "unknown",
            localFileLastModified = 10_000L,
            nowEpochMillis = 71_000L,
        )

        assertTrue(shouldRefresh)
    }

    @Test
    fun `cache refresh happens when signature changes`() {
        val shouldRefresh = ComicArchiveCodecsTestAccess.shouldRefreshArchiveCache(
            localFileExists = true,
            localFileLength = 128L,
            currentSignature = "200:1000",
            cachedSignature = "100:1000",
            localFileLastModified = 10_000L,
            nowEpochMillis = 20_000L,
        )

        assertTrue(shouldRefresh)
    }

    @Test
    fun `build item progress summary clamps page index and returns null for empty page count`() {
        assertNull(AppViewModelTestAccess.buildItemProgressSummary(pageIndex = 3, pageCount = 0))

        val summary = AppViewModelTestAccess.buildItemProgressSummary(pageIndex = 99, pageCount = 12)

        assertEquals("Resume on page 12 of 12", summary?.label)
        assertEquals(1f, summary?.fraction)
    }
}
