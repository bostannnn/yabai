package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for the org.json the saved EPUB locator is parsed with.
@RunWith(RobolectricTestRunner::class)
class KavitaEpubProgressTest {
    @Test
    fun `epub push sends the saved chapter index instead of the readium position`() {
        val snapshot = ReaderProgressSnapshot(
            itemId = EpubItem.id,
            locatorType = LocatorType.CFI,
            page = 145,
            cfi = savedLocator(readingOrderIndex = 4),
            percent = 0.31,
        )

        assertEquals(4, snapshot.kavitaPageNum(EpubItem))
    }

    @Test
    fun `older epub saves estimate the chapter from the book percent`() {
        val snapshot = ReaderProgressSnapshot(
            itemId = EpubItem.id,
            locatorType = LocatorType.CFI,
            page = 145,
            cfi = savedLocator(readingOrderIndex = null),
            percent = 0.31,
        )

        assertEquals(9, snapshot.kavitaPageNum(EpubItem))
    }

    @Test
    fun `a chapter position pulled from Kavita is pushed back unchanged`() {
        val snapshot = ReaderProgressSnapshot(
            itemId = EpubItem.id,
            locatorType = LocatorType.PAGE,
            page = 6,
            percent = 0.2,
        )

        assertEquals(6, snapshot.kavitaPageNum(EpubItem))
    }

    @Test
    fun `comic push keeps the page index`() {
        val comic = EpubItem.copy(format = FormatType.CBZ, pageCount = 24)
        val snapshot = ReaderProgressSnapshot(
            itemId = comic.id,
            locatorType = LocatorType.PAGE,
            page = 12,
        )

        assertEquals(12, snapshot.kavitaPageNum(comic))
    }

    @Test
    fun `remote position in the local chapter keeps the exact local spot`() {
        val local = localEpubProgress(readingOrderIndex = 4)

        assertTrue(kavitaLocalRefinesRemote(local, remoteChapter(4)))
        assertFalse(kavitaLocalRefinesRemote(local, remoteChapter(5)))
        assertFalse(kavitaLocalRefinesRemote(local, remoteChapter(4).copy(isFinished = true)))
    }

    @Test
    fun `remote unread keeps a local position only in the first chapter`() {
        assertTrue(kavitaLocalRefinesRemote(localEpubProgress(readingOrderIndex = 0), null))
        assertFalse(kavitaLocalRefinesRemote(localEpubProgress(readingOrderIndex = 2), null))
    }

    @Test
    fun `comics and older epub saves never override remote progress`() {
        val comic = ProgressSyncSnapshot(
            itemId = EpubItem.id,
            locatorType = LocatorType.PAGE,
            page = 4,
            syncState = SyncState.CLEAN,
        )

        assertFalse(kavitaLocalRefinesRemote(comic, remoteChapter(4)))
        assertFalse(kavitaLocalRefinesRemote(localEpubProgress(readingOrderIndex = null), remoteChapter(0)))
    }

    @Test
    fun `remote unread keeps a comic open on its first page`() {
        val comic = ProgressSyncSnapshot(
            itemId = EpubItem.id,
            locatorType = LocatorType.PAGE,
            page = 0,
            syncState = SyncState.CLEAN,
        )

        // Kavita stores page index 0 as zero pages read, which reads back as unread.
        assertTrue(kavitaLocalRefinesRemote(comic, null))
        assertFalse(kavitaLocalRefinesRemote(comic.copy(page = 3), null))
        assertFalse(kavitaLocalRefinesRemote(comic.copy(isFinished = true), null))
        assertFalse(kavitaLocalRefinesRemote(comic.copy(isExplicitUnreadReset = true), null))
    }

    private fun localEpubProgress(
        readingOrderIndex: Int?,
    ) = ProgressSyncSnapshot(
        itemId = EpubItem.id,
        locatorType = LocatorType.CFI,
        page = 145,
        cfi = savedLocator(readingOrderIndex),
        percent = 0.31,
        syncState = SyncState.CLEAN,
        updatedAtEpochMillis = 10L,
    )

    private fun remoteChapter(
        index: Int,
    ) = ReadingProgress(
        itemId = EpubItem.id,
        locatorType = LocatorType.PAGE,
        page = index,
        percent = index / 30.0,
        updatedAtEpochMillis = 20L,
    )

    private fun savedLocator(
        readingOrderIndex: Int?,
    ): String {
        val index = readingOrderIndex?.let { ",\"readingOrderIndex\":$it" }.orEmpty()
        return """{"href":"/chapter-5.xhtml","type":"application/xhtml+xml",""" +
            """"locations":{"progression":0.4,"position":145,"totalProgression":0.31$index}}"""
    }

    private companion object {
        val EpubItem = ReadableItem(
            id = "kavita:item:42",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:5",
            remoteId = "42",
            remoteFileId = "7",
            remoteFileType = "12",
            title = "Novel",
            format = FormatType.EPUB,
            pageCount = 30,
        )
    }
}
