package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln

/**
 * Large covers, the default. 176, not a round 180: with the 24 dp gutter from 600 dp the grid keeps the column
 * counts it had at 16 (4 at 800 dp portrait, 6 beside the rail at 1280 dp and on the tablet in
 * landscape, so the reading card still sits beside the shelves there).
 */
const val defaultLibraryGridCellSizeDp: Int = 176
const val minLibraryGridCellSizeDp: Int = 70
const val maxLibraryGridCellSizeDp: Int = 240

internal val supportedLibraryGridCellSizesDp: List<Int> = LibraryCoverSize.entries.map(LibraryCoverSize::cellSizeDp)

/**
 * The cover sizes the sort menu's slider offers, smallest first. Grids saved at 160 or 200 dp, sizes it no
 * longer has, read as Large. Tiny is half of Medium and gives a phone 4 columns, Small 3, Medium 2,
 * Large 1 or 2, Huge 1.
 */
enum class LibraryCoverSize(
    val label: String,
    val cellSizeDp: Int,
) {
    Tiny("Tiny", minLibraryGridCellSizeDp),
    Small("Small", 96),
    Medium("Medium", 140),
    Large("Large", defaultLibraryGridCellSizeDp),
    Huge("Huge", maxLibraryGridCellSizeDp),
    ;

    /** Tiny tiles leave the title out: beside the ••• it would be a few letters. */
    val showsTitle: Boolean
        get() = this != Tiny

    companion object {
        fun of(gridCellSizeDp: Int): LibraryCoverSize {
            val sanitized = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
            return entries.first { size -> size.cellSizeDp == sanitized }
        }
    }
}

internal val libraryGridHorizontalSpacingDp: Dp = AppLayout.gridGap

/** The grid's end padding while the alphabet rail shows: the rail takes the gutter's place. */
internal val libraryGridNavigationEndPaddingDp: Dp = 44.dp

const val LibraryCoverAspectRatio: Float = 2f / 3f

data class LibraryGridSizeOption(
    val cellSizeDp: Int,
    val columnCount: Int,
)

/**
 * The library grid's column setup, handed to content drawn above the grid (the shelves) so its covers
 * match the grid's covers in size and line up with its columns.
 */
data class LibraryGridMetrics(
    val cellSizeDp: Int = defaultLibraryGridCellSizeDp,
) {
    val spacing: Dp
        get() = libraryGridHorizontalSpacingDp

    val coverImageVariant: LibraryCoverImageVariant
        get() = libraryGridCoverImageVariant(cellSizeDp)

    companion object {
        /** The reading card and the shelves never go below Medium: at Small and Tiny they keep Medium's covers. */
        fun aboveGrid(gridCellSizeDp: Int): LibraryGridMetrics =
            LibraryGridMetrics(cellSizeDp = maxOf(gridCellSizeDp, LibraryCoverSize.Medium.cellSizeDp))
    }
}

data class LibraryGridColumns(
    val count: Int,
    val cellWidth: Dp,
)

/**
 * The columns the grid lays out across [availableWidth] (its width inside the content padding).
 * Mirrors `GridCells.Adaptive`, which the grid uses, so a full-span row can reproduce its cells.
 */
fun Density.libraryGridColumns(
    availableWidth: Dp,
    metrics: LibraryGridMetrics,
): LibraryGridColumns {
    val availableWidthPx = availableWidth.roundToPx()
    val spacingPx = metrics.spacing.roundToPx()
    val minCellWidthPx = sanitizeLibraryGridCellSizeDp(metrics.cellSizeDp).dp.roundToPx()
    val count = maxOf((availableWidthPx + spacingPx) / (minCellWidthPx + spacingPx), 1)
    val cellWidthPx = ((availableWidthPx - spacingPx * (count - 1)) / count).coerceAtLeast(0)
    return LibraryGridColumns(
        count = count,
        cellWidth = cellWidthPx.toDp(),
    )
}

internal data class LibraryGridPresentation(
    val selectedGridSize: LibraryGridSizeOption,
    val availableGridSizes: List<LibraryGridSizeOption>,
    val scrollingPrefetchItemLimit: Int,
    // While a docked panel narrows the grid it lays out exactly `selectedGridSize.columnCount` columns.
    val fixedColumns: Boolean = false,
)

fun sanitizeLibraryGridCellSizeDp(
    value: Int,
): Int = when {
    value <= TinyCoverUpperBoundDp -> LibraryCoverSize.Tiny.cellSizeDp
    value <= SmallCoverUpperBoundDp -> LibraryCoverSize.Small.cellSizeDp
    value <= MediumCoverUpperBoundDp -> LibraryCoverSize.Medium.cellSizeDp
    value >= HugeCoverLowerBoundDp -> maxLibraryGridCellSizeDp
    // Large, the default, including the 160 and 200 dp sizes the menu used to offer.
    else -> defaultLibraryGridCellSizeDp
}

/**
 * [undockedMaxWidth] is the grid's width without a docked panel beside it. While a panel narrows the grid, the
 * covers keep the width they have there: the grid drops columns, and the ones left come as close to that width as
 * whole columns allow, instead of stretching to fill the way the adaptive grid would (Large covers growing by a
 * third, Huge to a single column).
 */
internal fun libraryGridPresentation(
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
    requestedGridCellSizeDp: Int,
    undockedMaxWidth: Dp = maxWidth,
): LibraryGridPresentation {
    val docked = undockedMaxWidth > maxWidth
    val layoutWidth = if (docked) undockedMaxWidth else maxWidth
    val availableGridSizes = availableLibraryGridSizeOptions(
        maxWidth = layoutWidth,
        startPadding = startPadding,
        endPadding = endPadding,
    )
    val undockedGridSize = selectedLibraryGridSizeOption(
        options = availableGridSizes,
        maxWidth = layoutWidth,
        startPadding = startPadding,
        endPadding = endPadding,
        requestedGridCellSizeDp = requestedGridCellSizeDp,
    )
    val selectedGridSize = if (docked) {
        undockedGridSize.copy(
            columnCount = libraryGridColumnCountNearest(
                maxWidth = maxWidth,
                startPadding = startPadding,
                endPadding = endPadding,
                cellWidthDp = libraryGridCellWidthDp(
                    maxWidth = undockedMaxWidth,
                    startPadding = startPadding,
                    endPadding = endPadding,
                    columnCount = undockedGridSize.columnCount,
                ),
            ),
        )
    } else {
        undockedGridSize
    }
    val compactGrid = selectedGridSize.cellSizeDp <= LibraryCoverSize.Medium.cellSizeDp
    return LibraryGridPresentation(
        selectedGridSize = selectedGridSize,
        availableGridSizes = availableGridSizes,
        scrollingPrefetchItemLimit = if (compactGrid) 2 else 4,
        fixedColumns = docked,
    )
}

fun libraryGridCoverImageVariant(
    gridCellSizeDp: Int,
): LibraryCoverImageVariant = when {
    gridCellSizeDp >= extraLargeGridCoverCellSizeDp -> LibraryCoverImageVariant.GridExtraLarge
    gridCellSizeDp >= largeGridCoverCellSizeDp -> LibraryCoverImageVariant.GridLarge
    else -> LibraryCoverImageVariant.Grid
}

fun availableLibraryGridSizeOptions(
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
): List<LibraryGridSizeOption> = supportedLibraryGridCellSizesDp
    .map { cellSizeDp ->
        LibraryGridSizeOption(
            cellSizeDp = cellSizeDp,
            columnCount = libraryGridColumnCount(
                maxWidth = maxWidth,
                startPadding = startPadding,
                endPadding = endPadding,
                minCellSizeDp = cellSizeDp,
            ),
        )
    }
    .groupBy(LibraryGridSizeOption::columnCount)
    .values
    .map { optionsForColumnCount ->
        optionsForColumnCount.maxBy(LibraryGridSizeOption::cellSizeDp)
    }
    .sortedBy(LibraryGridSizeOption::cellSizeDp)

private fun selectedLibraryGridSizeOption(
    options: List<LibraryGridSizeOption>,
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
    requestedGridCellSizeDp: Int,
): LibraryGridSizeOption {
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(requestedGridCellSizeDp)
    val requestedColumnCount = libraryGridColumnCount(
        maxWidth = maxWidth,
        startPadding = startPadding,
        endPadding = endPadding,
        minCellSizeDp = sanitizedGridCellSizeDp,
    )
    return options.firstOrNull { option -> option.columnCount == requestedColumnCount }
        ?: options.minBy { option -> abs(option.cellSizeDp - sanitizedGridCellSizeDp) }
}

private fun libraryGridColumnCount(
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
    minCellSizeDp: Int,
): Int {
    val availableWidthDp = libraryGridAvailableWidthDp(maxWidth, startPadding, endPadding)
    val minCellWidthDp = minCellSizeDp.toFloat()
    val spacingDp = libraryGridHorizontalSpacingDp.value
    return floor((availableWidthDp + spacingDp) / (minCellWidthDp + spacingDp))
        .toInt()
        .coerceAtLeast(1)
}

/** The column count whose cells come closest to [cellWidthDp], measured as a ratio so a step down weighs like a step up. */
private fun libraryGridColumnCountNearest(
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
    cellWidthDp: Float,
): Int {
    val atLeastAsWide = libraryGridColumnCount(
        maxWidth = maxWidth,
        startPadding = startPadding,
        endPadding = endPadding,
        minCellSizeDp = floor(cellWidthDp).toInt(),
    )
    return listOf(atLeastAsWide, atLeastAsWide + 1).minBy { columnCount ->
        abs(ln(libraryGridCellWidthDp(maxWidth, startPadding, endPadding, columnCount) / cellWidthDp))
    }
}

private fun libraryGridCellWidthDp(
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
    columnCount: Int,
): Float {
    val spacingDp = libraryGridHorizontalSpacingDp.value
    return (libraryGridAvailableWidthDp(maxWidth, startPadding, endPadding) - spacingDp * (columnCount - 1)) / columnCount
}

private fun libraryGridAvailableWidthDp(
    maxWidth: Dp,
    startPadding: Dp,
    endPadding: Dp,
): Float = (maxWidth.value - startPadding.value - endPadding.value).coerceAtLeast(minLibraryGridCellSizeDp.toFloat())

private const val TinyCoverUpperBoundDp = 83
private const val SmallCoverUpperBoundDp = 118
private const val MediumCoverUpperBoundDp = 150
private const val HugeCoverLowerBoundDp = 220
private const val largeGridCoverCellSizeDp = defaultLibraryGridCellSizeDp
private const val extraLargeGridCoverCellSizeDp = 200
