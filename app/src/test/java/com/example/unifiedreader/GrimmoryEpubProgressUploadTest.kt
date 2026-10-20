package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json and Uri: the saved EPUB locator is parsed for Grimmory's CFI and chapter.
@RunWith(RobolectricTestRunner::class)
class GrimmoryEpubProgressUploadTest {
    private val item = ReadableItem(
        id = SourceType.GRIMMORY.remoteItemId("epub-book"),
        sourceType = SourceType.GRIMMORY,
        libraryId = SourceType.GRIMMORY.remoteLibraryId("books"),
        remoteId = "epub-book",
        remoteFileId = "epub-book-file",
        remoteFileType = FormatType.EPUB.name,
        title = "Девица Кристина",
        format = FormatType.EPUB,
    )

    @Test
    fun `sends the CFI even when the chapter anchor comes first`() {
        val update = checkNotNull(
            epubSnapshot(
                """{"href":"OEBPS/text/part03.xhtml","type":"application/xhtml+xml",""" +
                    """"locations":{"fragments":["ch3","epubcfi(/6/12!/4/2[ch3]/120/1:0)"],"totalProgression":0.39}}""",
            ).toGrimmoryProgressUpdate(item),
        )

        assertEquals("epubcfi(/6/12!/4/2[ch3]/120/1:0)", update.positionData)
        assertEquals("OEBPS/text/part03.xhtml", update.positionHref)
    }

    @Test
    fun `sends the chapter and its progress instead of the reader locator when there is no CFI`() {
        // Grimmory's web reader would take any non-CFI position data for a file path and lose the position.
        val update = checkNotNull(
            epubSnapshot(
                """{"href":"OEBPS\/Text\/%D0%93%D0%BB%D0%B0%D0%B2%D0%B0%206.xhtml","type":"application\/xhtml+xml",""" +
                    """"locations":{"readingOrderIndex":8,"progression":0.4,"position":99,"totalProgression":0.4689}}""",
            ).toGrimmoryProgressUpdate(item),
        )

        assertNull(update.positionData)
        assertEquals("OEBPS/Text/Глава 6.xhtml", update.positionHref)
        assertEquals(40.0, checkNotNull(update.contentSourceProgressPercent), 0.0001)
        assertEquals(0.39, update.percent, 0.0001)
    }

    @Test
    fun `sends a bare CFI as it is and drops anything else`() {
        val cfi = checkNotNull(epubSnapshot("epubcfi(/6/14!/4/2/10)").toGrimmoryProgressUpdate(item))
        val other = checkNotNull(epubSnapshot("chapter-2").toGrimmoryProgressUpdate(item))

        assertEquals("epubcfi(/6/14!/4/2/10)", cfi.positionData)
        assertNull(other.positionData)
        assertEquals(0.39, other.percent, 0.0001)
    }

    @Test
    fun `sends only the percent for an EPUB without a locator`() {
        val update = checkNotNull(
            ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = LocatorType.PERCENT,
                percent = 0.55,
                syncState = SyncState.DIRTY,
                updatedAtEpochMillis = 20L,
            ).toGrimmoryProgressUpdate(item),
        )

        assertNull(update.positionData)
        assertNull(update.positionHref)
        assertEquals(0.55, update.percent, 0.0001)
    }

    private fun epubSnapshot(
        cfi: String,
    ): ReaderProgressSnapshot = ReaderProgressSnapshot(
        itemId = item.id,
        locatorType = LocatorType.CFI,
        cfi = cfi,
        percent = 0.39,
        syncState = SyncState.DIRTY,
        updatedAtEpochMillis = 20L,
    )
}
