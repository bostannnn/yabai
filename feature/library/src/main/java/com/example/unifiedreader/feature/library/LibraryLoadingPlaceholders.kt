package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal const val LibraryGridLoadingTag = "library-grid-loading"
internal const val LibraryDetailedLoadingTag = "library-detailed-loading"

@Composable
internal fun LoadingLibraryState(
    viewMode: LibraryViewMode,
    gridCellSizeDp: Int,
    modifier: Modifier = Modifier,
) {
    when (viewMode) {
        LibraryViewMode.Grid -> GridLoadingLibraryState(
            gridCellSizeDp = gridCellSizeDp,
            modifier = modifier,
        )

        LibraryViewMode.Detailed -> DetailedLoadingLibraryState(
            modifier = modifier,
        )
    }
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
                        GridLoadingCard(
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedLoadingLibraryState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibraryDetailedLoadingTag),
        verticalArrangement = Arrangement.spacedBy(LibraryLoadingItemSpacing),
    ) {
        repeat(DetailedLoadingItemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LoadingLine(
                        width = 144.dp,
                        height = 16.dp,
                    )
                    LoadingLine(
                        width = 112.dp,
                        height = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    LoadingLine(
                        width = 96.dp,
                        height = 11.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    LoadingLine(
                        width = 72.dp,
                        height = 10.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridLoadingCard(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(LibraryLoadingCoverAspectRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        LoadingLine(
            width = 92.dp,
            height = 12.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        LoadingLine(
            width = 68.dp,
            height = 10.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun LoadingLine(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(containerColor),
    )
}

private val LibraryLoadingItemSpacing = 16.dp
private const val GridLoadingRowCount = 2
private const val DetailedLoadingItemCount = 5
private const val LibraryLoadingCoverAspectRatio = 0.72f
