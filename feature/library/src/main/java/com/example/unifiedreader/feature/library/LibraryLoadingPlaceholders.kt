package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal const val LibraryGridLoadingTag = "library-grid-loading"

@Composable
internal fun LoadingLibraryState(
    gridCellSizeDp: Int,
    modifier: Modifier = Modifier,
) {
    GridLoadingLibraryState(
        gridCellSizeDp = gridCellSizeDp,
        modifier = modifier,
    )
}

@Composable
private fun GridLoadingLibraryState(
    gridCellSizeDp: Int,
    modifier: Modifier = Modifier,
) {
    val cellSize = sanitizeLibraryGridCellSizeDp(gridCellSizeDp).dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibraryGridLoadingTag),
    ) {
        val columns = max(
            1,
            ((maxWidth + LibraryLoadingItemSpacing) / (cellSize + LibraryLoadingItemSpacing)).toInt(),
        )
        val cardWidth = (maxWidth - (LibraryLoadingItemSpacing * (columns - 1))) / columns
        Column(verticalArrangement = Arrangement.spacedBy(LibraryLoadingItemSpacing)) {
            repeat(GridLoadingRowCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LibraryLoadingItemSpacing),
                ) {
                    repeat(columns) {
                        LibraryGridPlaceholderCard(
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

private val LibraryLoadingItemSpacing = 16.dp
private const val GridLoadingRowCount = 2
