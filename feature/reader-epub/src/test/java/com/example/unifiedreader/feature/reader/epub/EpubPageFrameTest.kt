package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPageFrameTest {
    @Test
    fun `strips are 56 dp on a tablet and 40 on a phone`() {
        assertEquals(56.dp, epubViewportSpacing(isLandscape = false, isCompact = false).containerInset)
        assertEquals(56.dp, epubViewportSpacing(isLandscape = true, isCompact = false).containerInset)
        assertEquals(40.dp, epubViewportSpacing(isLandscape = false, isCompact = true).containerInset)
    }

    @Test
    fun `a page is headed by its chapter, a spread by the book and the chapter`() {
        assertEquals(listOf("Chapter 1"), buildEpubPageHeads("The Lantern Keepers", " Chapter 1 ", isSpread = false, isChapterStart = false))
        assertEquals(
            listOf("The Lantern Keepers", "Chapter 1"),
            buildEpubPageHeads("The Lantern Keepers", "Chapter 1", isSpread = true, isChapterStart = false),
        )
    }

    @Test
    fun `no heads on a chapter's first page or without a chapter`() {
        assertEquals(emptyList<String>(), buildEpubPageHeads("The Lantern Keepers", "Chapter 1", isSpread = false, isChapterStart = true))
        assertEquals(emptyList<String>(), buildEpubPageHeads("The Lantern Keepers", null, isSpread = true, isChapterStart = false))
        assertEquals(emptyList<String>(), buildEpubPageHeads("The Lantern Keepers", "  ", isSpread = false, isChapterStart = false))
    }
}
