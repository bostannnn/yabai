package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCoverImageRequestsTest {
    @Test
    fun `libraryCoverImageRequestSize uses fixed thumbnail dimensions`() {
        assertEquals(
            IntSize(240, 360),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.Grid),
        )
        assertEquals(
            IntSize(144, 216),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.ShelfPreview),
        )
        assertEquals(
            IntSize(320, 480),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.Detailed),
        )
    }
}
