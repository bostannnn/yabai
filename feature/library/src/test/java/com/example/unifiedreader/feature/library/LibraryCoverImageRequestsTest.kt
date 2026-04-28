package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCoverImageRequestsTest {
    @Test
    fun `libraryCoverImageRequestSize uses high quality minimum dimensions`() {
        assertEquals(
            IntSize(480, 720),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.Grid),
        )
        assertEquals(
            IntSize(720, 1080),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.GridLarge),
        )
        assertEquals(
            IntSize(960, 1440),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.GridExtraLarge),
        )
        assertEquals(
            IntSize(432, 648),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.ShelfPreview),
        )
        assertEquals(
            IntSize(960, 1440),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.Detailed),
        )
    }

    @Test
    fun `libraryCoverImageRequestSize grows for very dense displays`() {
        assertEquals(
            IntSize(960, 1440),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.GridExtraLarge, density = 4f),
        )
        assertEquals(
            IntSize(1280, 1920),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.Detailed, density = 4f),
        )
    }

    @Test
    fun `grid covers keep full quality decode policy`() {
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = true,
                bitmapConfig = null,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.Grid),
        )
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = true,
                bitmapConfig = null,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.GridLarge),
        )
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = true,
                bitmapConfig = null,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.GridExtraLarge),
        )
    }

    @Test
    fun `shelf covers keep full quality decode policy`() {
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = true,
                bitmapConfig = null,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.ShelfPreview),
        )
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = true,
                bitmapConfig = null,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.ShelfRow),
        )
    }

    @Test
    fun `detailed cover keeps hardware quality policy`() {
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = true,
                bitmapConfig = null,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.Detailed),
        )
    }
}
