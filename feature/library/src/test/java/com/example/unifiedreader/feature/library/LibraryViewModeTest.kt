package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewModeTest {
    @Test
    fun `grid cell size defaults to the balanced preset`() {
        assertEquals(180, defaultLibraryGridCellSizeDp)
    }

    @Test
    fun `grid cell size stays within the supported range`() {
        assertEquals(minLibraryGridCellSizeDp, sanitizeLibraryGridCellSizeDp(90))
        assertEquals(maxLibraryGridCellSizeDp, sanitizeLibraryGridCellSizeDp(999))
    }
}
