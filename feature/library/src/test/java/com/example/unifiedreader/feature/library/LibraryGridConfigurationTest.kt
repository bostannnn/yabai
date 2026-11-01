package com.example.unifiedreader.feature.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGridConfigurationTest {
    @Test
    fun `grid cell size snaps to supported values`() {
        assertEquals(minLibraryGridCellSizeDp, sanitizeLibraryGridCellSizeDp(10))
        assertEquals(70, sanitizeLibraryGridCellSizeDp(83))
        assertEquals(96, sanitizeLibraryGridCellSizeDp(90))
        assertEquals(96, sanitizeLibraryGridCellSizeDp(118))
        assertEquals(140, sanitizeLibraryGridCellSizeDp(119))
        assertEquals(140, sanitizeLibraryGridCellSizeDp(130))
        assertEquals(140, sanitizeLibraryGridCellSizeDp(149))
        assertEquals(maxLibraryGridCellSizeDp, sanitizeLibraryGridCellSizeDp(999))
    }

    @Test
    fun `the old 160 and 200 sizes read as Large`() {
        assertEquals(LibraryCoverSize.Large, LibraryCoverSize.of(160))
        assertEquals(LibraryCoverSize.Large, LibraryCoverSize.of(200))
        assertEquals(LibraryCoverSize.Medium, LibraryCoverSize.of(140))
        assertEquals(LibraryCoverSize.Huge, LibraryCoverSize.of(240))
        assertEquals(listOf(70, 96, 140, 176, 240), supportedLibraryGridCellSizesDp)
    }

    @Test
    fun `every size reads back as itself`() {
        LibraryCoverSize.entries.forEach { size -> assertEquals(size, LibraryCoverSize.of(size.cellSizeDp)) }
    }

    @Test
    fun `on a phone Tiny gives four columns and Small three`() {
        fun columns(size: LibraryCoverSize, width: Int): Int = libraryGridPresentation(
            maxWidth = width.dp,
            startPadding = 16.dp,
            endPadding = 16.dp,
            requestedGridCellSizeDp = size.cellSizeDp,
        ).selectedGridSize.columnCount

        listOf(360, 411).forEach { width ->
            assertEquals(4, columns(LibraryCoverSize.Tiny, width))
            assertEquals(3, columns(LibraryCoverSize.Small, width))
            assertEquals(2, columns(LibraryCoverSize.Medium, width))
            assertEquals(1, columns(LibraryCoverSize.Huge, width))
        }
        assertEquals(1, columns(LibraryCoverSize.Large, 360))
        assertEquals(2, columns(LibraryCoverSize.Large, 411))
    }

    @Test
    fun `only Tiny tiles leave the title out`() {
        assertEquals(
            listOf(LibraryCoverSize.Tiny),
            LibraryCoverSize.entries.filterNot(LibraryCoverSize::showsTitle),
        )
    }

    @Test
    fun `docked filters keep the covers near their undocked width instead of stretching`() {
        // Beside the 88 dp rail at 1280 dp, then with the 400 dp panel and its 24 dp gutter docked.
        val undockedWidth = (1280 - 88).dp
        val dockedWidth = undockedWidth - 424.dp
        fun presentation(size: LibraryCoverSize, docked: Boolean) = libraryGridPresentation(
            maxWidth = if (docked) dockedWidth else undockedWidth,
            startPadding = 24.dp,
            endPadding = 24.dp,
            requestedGridCellSizeDp = size.cellSizeDp,
            undockedMaxWidth = undockedWidth,
        )

        val undocked = presentation(LibraryCoverSize.Large, docked = false)
        assertEquals(false, undocked.fixedColumns)
        assertEquals(6, undocked.selectedGridSize.columnCount)

        // The adaptive grid gave Large 3 columns (229 dp covers) and Huge 2 (352 dp).
        val large = presentation(LibraryCoverSize.Large, docked = true)
        assertEquals(true, large.fixedColumns)
        assertEquals(176, large.selectedGridSize.cellSizeDp)
        assertEquals(4, large.selectedGridSize.columnCount)
        assertEquals(3, presentation(LibraryCoverSize.Huge, docked = true).selectedGridSize.columnCount)
        assertEquals(4, presentation(LibraryCoverSize.Medium, docked = true).selectedGridSize.columnCount)
        assertEquals(6, presentation(LibraryCoverSize.Small, docked = true).selectedGridSize.columnCount)
        assertEquals(8, presentation(LibraryCoverSize.Tiny, docked = true).selectedGridSize.columnCount)
    }

    @Test
    fun `grid size options keep only values that change the layout`() {
        val options = availableLibraryGridSizeOptions(
            maxWidth = 900.dp,
            startPadding = 16.dp,
            endPadding = 16.dp,
        )

        assertEquals(listOf(70, 96, 140, 176, 240), options.map(LibraryGridSizeOption::cellSizeDp))
        assertEquals(listOf(10, 7, 5, 4, 3), options.map(LibraryGridSizeOption::columnCount))
    }

    @Test
    fun `large covers, the default, keep their column counts with the tablet gutter`() {
        fun mediumColumns(width: Int, gutter: Int): Int = libraryGridPresentation(
            maxWidth = width.dp,
            startPadding = gutter.dp,
            endPadding = gutter.dp,
            requestedGridCellSizeDp = defaultLibraryGridCellSizeDp,
        ).selectedGridSize.columnCount

        assertEquals(2, mediumColumns(width = 411, gutter = 16))
        // 800 dp portrait, the tablet's 914 dp portrait, then beside the 88 dp rail: 1280 dp and the tablet's 1292 dp landscape.
        assertEquals(4, mediumColumns(width = 800, gutter = 24))
        assertEquals(4, mediumColumns(width = 914, gutter = 24))
        assertEquals(6, mediumColumns(width = 1280 - 88, gutter = 24))
        assertEquals(6, mediumColumns(width = 1292 - 88, gutter = 24))
    }

    @Test
    fun `compact layouts reduce scrolling prefetch`() {
        val presentation = libraryGridPresentation(
            maxWidth = 660.dp,
            startPadding = 16.dp,
            endPadding = 16.dp,
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
            startPadding = 16.dp,
            endPadding = 16.dp,
            requestedGridCellSizeDp = 200,
        )

        assertEquals(176, presentation.selectedGridSize.cellSizeDp)
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
