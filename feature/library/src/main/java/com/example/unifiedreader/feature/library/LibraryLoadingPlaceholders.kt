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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
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
private fun GridLoadingCard(
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(LibraryLoadingCoverAspectRatio)
                .clip(RoundedCornerShape(presentation.coverCornerRadius))
                .background(semanticColors.surfaceOverlay),
        )
        LoadingLine(
            width = 92.dp,
            height = 12.dp,
            containerColor = semanticColors.surfaceSelected.copy(alpha = 0.54f),
        )
        LoadingLine(
            width = 68.dp,
            height = 10.dp,
            containerColor = semanticColors.sourceAccent.copy(alpha = 0.26f),
        )
    }
}

@Composable
private fun LoadingLine(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    containerColor: androidx.compose.ui.graphics.Color,
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
private const val LibraryLoadingCoverAspectRatio = 0.72f
