package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderProgressFlushTest {
    private val storedProgress = ReaderProgressSnapshot(
        itemId = "grimmory:item:7",
        locatorType = LocatorType.CFI,
        cfi = "{\"href\":\"chapter-3.xhtml\"}",
        page = 40,
        percent = 0.4,
        syncState = SyncState.CLEAN,
        updatedAtEpochMillis = 100L,
    )

    @Test
    fun `a book that never opened does not re-save its stored progress`() {
        // The reader closed on "Opening EPUB…" or an error screen, e.g. a missing file.
        assertFalse(
            shouldFlushEpubReaderProgress(
                navigatorShowedBook = false,
                restoreProgressSnapshot = storedProgress,
                currentLocator = null,
                currentPage = 40,
                currentProgress = 0.4,
            ),
        )
    }

    @Test
    fun `a shown book saves its position on close`() {
        assertTrue(
            shouldFlushEpubReaderProgress(
                navigatorShowedBook = true,
                restoreProgressSnapshot = storedProgress,
                currentLocator = null,
                currentPage = 41,
                currentProgress = 0.41,
            ),
        )
    }

    @Test
    fun `a shown book with no position saves nothing`() {
        assertFalse(
            shouldFlushEpubReaderProgress(
                navigatorShowedBook = true,
                restoreProgressSnapshot = null,
                currentLocator = null,
                currentPage = 0,
                currentProgress = 0.0,
            ),
        )
    }
}
