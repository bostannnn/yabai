package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGridConfigurationTest {
    @Test
    fun `grid cell size snaps to supported values`() {
        assertEquals(minLibraryGridCellSizeDp, sanitizeLibraryGridCellSizeDp(90))
        assertEquals(140, sanitizeLibraryGridCellSizeDp(130))
        assertEquals(140, sanitizeLibraryGridCellSizeDp(149))
        assertEquals(maxLibraryGridCellSizeDp, sanitizeLibraryGridCellSizeDp(999))
    }

    @Test
    fun `grid size options keep only values that change the layout`() {
        val options = availableLibraryGridSizeOptions(
            maxWidth = 900.dp,
            endPadding = libraryGridEndPaddingDp,
        )

        assertEquals(listOf(160, 200, 240), options.map(LibraryGridSizeOption::cellSizeDp))
        assertEquals(listOf(5, 4, 3), options.map(LibraryGridSizeOption::columnCount))
    }

    @Test
    fun `compact layouts reduce scrolling prefetch`() {
        val presentation = libraryGridPresentation(
            maxWidth = 660.dp,
            endPadding = libraryGridEndPaddingDp,
            requestedGridCellSizeDp = 140,
        )

        assertEquals(140, presentation.selectedGridSize.cellSizeDp)
        assertEquals(2, presentation.scrollingPrefetchItemLimit)
    }

    @Test
    fun `wider layouts keep overflow button visible`() {
        val presentation = libraryGridPresentation(
            maxWidth = 900.dp,
            endPadding = libraryGridEndPaddingDp,
            requestedGridCellSizeDp = 200,
        )

        assertEquals(200, presentation.selectedGridSize.cellSizeDp)
        assertEquals(4, presentation.scrollingPrefetchItemLimit)
    }
}
