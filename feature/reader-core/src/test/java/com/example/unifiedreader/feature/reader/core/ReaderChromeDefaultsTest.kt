package com.example.unifiedreader.feature.reader.core

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChromeDefaultsTest {
    @Test
    fun `strip thumbnails follow the window`() {
        assertEquals(110.dp, ReaderChromeDefaults.stripThumbnailHeight(screenWidthDp = 800, screenHeightDp = 1280))
        assertEquals(96.dp, ReaderChromeDefaults.stripThumbnailHeight(screenWidthDp = 1280, screenHeightDp = 800))
        assertEquals(70.dp, ReaderChromeDefaults.stripThumbnailHeight(screenWidthDp = 411, screenHeightDp = 891))
    }
}
