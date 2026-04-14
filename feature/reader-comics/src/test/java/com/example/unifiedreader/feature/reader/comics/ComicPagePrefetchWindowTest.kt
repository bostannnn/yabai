package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPagePrefetchWindowTest {
    @Test
    fun `candidate indexes bias forward pages for continuous reading`() {
        assertEquals(
            listOf(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 4, 3, 2),
            continuousReaderPrefetchWindow().candidateIndexes(
                settledPageIndex = 5,
                pageCount = 20,
            ),
        )
    }

    @Test
    fun `candidate indexes clamp to valid page bounds`() {
        assertEquals(
            listOf(1, 2),
            ComicPagePrefetchWindow(forwardCount = 4, backwardCount = 3).candidateIndexes(
                settledPageIndex = 0,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun `placeholder height follows page metadata aspect ratio`() {
        assertEquals(
            960.dp,
            continuousReaderPlaceholderHeight(
                contentWidth = 480.dp,
                metadata = ComicPageMetadata(width = 1200, height = 2400),
            ),
        )
    }

    @Test
    fun `fallback placeholder reserves at least most of the viewport`() {
        assertEquals(
            680.dp,
            continuousReaderFallbackPlaceholderHeight(
                contentWidth = 420.dp,
                viewportHeight = 800.dp,
            ),
        )
    }
}
