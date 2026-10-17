package com.example.unifiedreader.feature.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
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
    fun `large grid cells use larger cover thumbnails`() {
        assertEquals(LibraryCoverImageVariant.Grid, libraryGridCoverImageVariant(160))
        assertEquals(LibraryCoverImageVariant.GridLarge, libraryGridCoverImageVariant(180))
        assertEquals(LibraryCoverImageVariant.GridExtraLarge, libraryGridCoverImageVariant(200))
        assertEquals(LibraryCoverImageVariant.GridExtraLarge, libraryGridCoverImageVariant(240))
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

    @Test
    fun `grid columns match the adaptive grid cells for the same width`() {
        val density = Density(density = 2f)
        val metrics = LibraryGridMetrics(cellSizeDp = 140)
        val availableWidth = 1264.dp

        val columns = with(density) {
            libraryGridColumns(availableWidth = availableWidth, metrics = metrics)
        }
        val adaptiveCellWidthsPx = with(GridCells.Adaptive(minSize = 140.dp)) {
            density.calculateCrossAxisCellSizes(
                availableSize = with(density) { availableWidth.roundToPx() },
                spacing = with(density) { metrics.spacing.roundToPx() },
            )
        }

        assertEquals(adaptiveCellWidthsPx.size, columns.count)
        assertEquals(adaptiveCellWidthsPx.min(), with(density) { columns.cellWidth.roundToPx() })
    }

    @Test
    fun `grid columns never drop below one column`() {
        val columns = with(Density(density = 2f)) {
            libraryGridColumns(availableWidth = 100.dp, metrics = LibraryGridMetrics(cellSizeDp = 240))
        }

        assertEquals(1, columns.count)
        assertEquals(100.dp, columns.cellWidth)
    }

    @Test
    fun `grid metrics pick the grid cover variant for their cell size`() {
        assertEquals(LibraryCoverImageVariant.Grid, LibraryGridMetrics(cellSizeDp = 140).coverImageVariant)
        assertEquals(LibraryCoverImageVariant.GridExtraLarge, LibraryGridMetrics(cellSizeDp = 240).coverImageVariant)
    }
}
