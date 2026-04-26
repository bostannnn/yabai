package com.example.unifiedreader.feature.library

import android.graphics.Bitmap
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
            IntSize(480, 720),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.GridLarge),
        )
        assertEquals(
            IntSize(720, 1080),
            libraryCoverImageRequestSize(LibraryCoverImageVariant.GridExtraLarge),
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
    fun `shelf covers use compact software decode policy`() {
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = false,
                bitmapConfig = Bitmap.Config.RGB_565,
            ),
            libraryCoverImageDecodePolicy(LibraryCoverImageVariant.ShelfPreview),
        )
        assertEquals(
            LibraryCoverImageDecodePolicy(
                allowHardware = false,
                bitmapConfig = Bitmap.Config.RGB_565,
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
