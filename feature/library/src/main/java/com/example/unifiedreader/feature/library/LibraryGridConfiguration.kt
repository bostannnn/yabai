package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

const val defaultLibraryGridCellSizeDp: Int = 180
const val minLibraryGridCellSizeDp: Int = 140
const val maxLibraryGridCellSizeDp: Int = 240

internal val supportedLibraryGridCellSizesDp: List<Int> = listOf(
    140,
    160,
    180,
    200,
    240,
)

internal val libraryGridHorizontalSpacingDp: Dp = 16.dp
internal val libraryGridStartPaddingDp: Dp = 16.dp
internal val libraryGridEndPaddingDp: Dp = 16.dp
internal val libraryGridNavigationEndPaddingDp: Dp = 56.dp

internal data class LibraryGridSizeOption(
    val cellSizeDp: Int,
    val columnCount: Int,
)

internal data class LibraryGridPresentation(
    val selectedGridSize: LibraryGridSizeOption,
    val availableGridSizes: List<LibraryGridSizeOption>,
    val scrollingPrefetchItemLimit: Int,
)

fun sanitizeLibraryGridCellSizeDp(
    value: Int,
): Int {
    val clampedValue = value.coerceIn(minLibraryGridCellSizeDp, maxLibraryGridCellSizeDp)
    return supportedLibraryGridCellSizesDp.minWith(
        compareBy<Int>({ abs(it - clampedValue) }, { if (it > clampedValue) 1 else 0 }),
    )
}

internal fun libraryGridPresentation(
    maxWidth: Dp,
    endPadding: Dp,
    requestedGridCellSizeDp: Int,
): LibraryGridPresentation {
    val availableGridSizes = availableLibraryGridSizeOptions(
        maxWidth = maxWidth,
        endPadding = endPadding,
    )
    val selectedGridSize = selectedLibraryGridSizeOption(
        options = availableGridSizes,
        maxWidth = maxWidth,
        endPadding = endPadding,
        requestedGridCellSizeDp = requestedGridCellSizeDp,
    )
    val compactGrid = selectedGridSize.cellSizeDp <= 140
    return LibraryGridPresentation(
        selectedGridSize = selectedGridSize,
        availableGridSizes = availableGridSizes,
        scrollingPrefetchItemLimit = if (compactGrid) 2 else 4,
    )
}

internal fun libraryGridCoverImageVariant(
    gridCellSizeDp: Int,
): LibraryCoverImageVariant = when {
    gridCellSizeDp >= extraLargeGridCoverCellSizeDp -> LibraryCoverImageVariant.GridExtraLarge
    gridCellSizeDp >= largeGridCoverCellSizeDp -> LibraryCoverImageVariant.GridLarge
    else -> LibraryCoverImageVariant.Grid
}

internal fun availableLibraryGridSizeOptions(
    maxWidth: Dp,
    endPadding: Dp,
): List<LibraryGridSizeOption> = supportedLibraryGridCellSizesDp
    .map { cellSizeDp ->
        LibraryGridSizeOption(
            cellSizeDp = cellSizeDp,
            columnCount = libraryGridColumnCount(
                maxWidth = maxWidth,
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
    endPadding: Dp,
    requestedGridCellSizeDp: Int,
): LibraryGridSizeOption {
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(requestedGridCellSizeDp)
    val requestedColumnCount = libraryGridColumnCount(
        maxWidth = maxWidth,
        endPadding = endPadding,
        minCellSizeDp = sanitizedGridCellSizeDp,
    )
    return options.firstOrNull { option -> option.columnCount == requestedColumnCount }
        ?: options.minBy { option -> abs(option.cellSizeDp - sanitizedGridCellSizeDp) }
}

private fun libraryGridColumnCount(
    maxWidth: Dp,
    endPadding: Dp,
    minCellSizeDp: Int,
): Int {
    val availableWidthDp = (
        maxWidth.value -
            libraryGridStartPaddingDp.value -
            endPadding.value
        ).coerceAtLeast(minLibraryGridCellSizeDp.toFloat())
    val minCellWidthDp = minCellSizeDp.toFloat()
    val spacingDp = libraryGridHorizontalSpacingDp.value
    return floor((availableWidthDp + spacingDp) / (minCellWidthDp + spacingDp))
        .toInt()
        .coerceAtLeast(1)
}

private const val largeGridCoverCellSizeDp = 180
private const val extraLargeGridCoverCellSizeDp = 200
