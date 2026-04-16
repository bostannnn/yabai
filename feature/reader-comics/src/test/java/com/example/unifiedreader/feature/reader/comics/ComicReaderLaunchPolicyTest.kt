package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicReaderLaunchPolicyTest {
    @Test
    fun `start from beginning ignores saved progress and resume position`() {
        val position = resolveComicReaderInitialPosition(
            openMode = ReaderOpenMode.StartFromBeginning,
            savedPage = 24,
            savedResumeAnchorPage = 23,
            savedResumeSegment = ComicPageSegment.Full,
            savedResumeScrollOffsetRatio = 0.75f,
            lastPageIndex = 29,
        )

        assertEquals(0, position.page)
        assertEquals(0, position.resumeAnchorPage)
        assertEquals(null, position.resumeSegment)
        assertEquals(null, position.resumeScrollOffsetRatio)
    }

    @Test
    fun `default open restores saved progress`() {
        val position = resolveComicReaderInitialPosition(
            openMode = ReaderOpenMode.Default,
            savedPage = 24,
            savedResumeAnchorPage = 23,
            savedResumeSegment = ComicPageSegment.Full,
            savedResumeScrollOffsetRatio = 0.75f,
            lastPageIndex = 29,
        )

        assertEquals(24, position.page)
        assertEquals(23, position.resumeAnchorPage)
        assertEquals(ComicPageSegment.Full, position.resumeSegment)
        assertEquals(0.75f, position.resumeScrollOffsetRatio)
    }

    @Test
    fun `continuous end navigation only triggers on user overscroll at list end`() {
        assertTrue(
            shouldNavigatePastContinuousEnd(
                canScrollForward = false,
                availableY = -48f,
                source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
                alreadyTriggered = false,
            ),
        )
        assertFalse(
            shouldNavigatePastContinuousEnd(
                canScrollForward = true,
                availableY = -48f,
                source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
                alreadyTriggered = false,
            ),
        )
        assertFalse(
            shouldNavigatePastContinuousEnd(
                canScrollForward = false,
                availableY = -12f,
                source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
                alreadyTriggered = false,
            ),
        )
        assertFalse(
            shouldNavigatePastContinuousEnd(
                canScrollForward = false,
                availableY = -48f,
                source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.SideEffect,
                alreadyTriggered = false,
            ),
        )
    }

    @Test
    fun `reader display title prefers comic metadata over raw item title`() {
        val item = ReadableItem(
            id = "kavita:item:volume-2",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:1",
            title = "Chapter -100000",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "One Piece",
                volume = "2",
            ),
        )

        assertEquals("One Piece • Volume 2", comicReaderDisplayTitle(item))
    }

    @Test
    fun `reader display title does not synthesize chapter labels from issue number`() {
        val item = ReadableItem(
            id = "kavita:item:volume-2",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:1",
            title = "Volume 2.cbz",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "One Piece",
                issueNumber = "-10000",
            ),
        )

        assertEquals("One Piece • Volume 2.cbz", comicReaderDisplayTitle(item))
    }
}
