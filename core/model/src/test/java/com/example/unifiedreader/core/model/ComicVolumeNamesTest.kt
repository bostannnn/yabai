package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicVolumeNamesTest {
    @Test
    fun `Kavita's volume chapters read as volumes`() {
        // What Kavita sends for a manga volume file (Goodbye, Eri on the user's server).
        val volume = ComicMetadata(series = "Goodbye, Eri", issueTitle = "Chapter v1", issueNumber = "v1")

        assertEquals("1", volume.volumeNumber())
        assertNull(volume.ownIssueTitle())
        assertEquals("Volume 1", comicVolumeLabel("1"))
    }

    @Test
    fun `volume numbers in their usual spellings`() {
        assertEquals("1", comicVolumeNumber("v1"))
        assertEquals("12", comicVolumeNumber("Vol. 12"))
        assertEquals("2.5", comicVolumeNumber("volume 2.5"))
        assertNull(comicVolumeNumber("12"))
        assertNull(comicVolumeNumber("vision"))
        assertNull(comicVolumeNumber(null))
    }

    @Test
    fun `real titles and plain issue numbers are kept`() {
        val issue = ComicMetadata(series = "Swamp Thing", issueTitle = "Issue #52 - Natural Consequences", issueNumber = "52")
        val chapter = ComicMetadata(series = "Berserk", issueTitle = "Chapter 5", issueNumber = "5")

        assertNull(issue.volumeNumber())
        assertEquals("Issue #52 - Natural Consequences", issue.ownIssueTitle())
        assertEquals("Chapter 5", chapter.ownIssueTitle())
        assertTrue(isGeneratedComicVolumeTitle("Issue v2"))
        assertFalse(isGeneratedComicVolumeTitle("Chapter 5"))
        // A volume with a title of its own keeps the title.
        assertEquals("The Beginning", ComicMetadata(issueTitle = "The Beginning", issueNumber = "v1").ownIssueTitle())
    }

    @Test
    fun `the library subtitle names the volume`() {
        val item = ReadableItem(
            id = "kavita:item:1",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:1",
            remoteId = "1",
            title = "Chapter v1",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(series = "Goodbye, Eri", issueTitle = "Chapter v1", issueNumber = "v1"),
        )

        assertEquals("Goodbye, Eri", item.libraryPrimaryText())
        assertEquals("Volume 1", item.librarySecondaryText())
        assertEquals("Goodbye, Eri: Volume 1", item.libraryDisplayLabel())
    }
}
