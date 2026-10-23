package com.example.unifiedreader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A series tile's stack: the edges of two more covers behind the first, raised above its top
 * (into the grid's row gap, so the tile keeps the grid's cover size) and inset from its sides.
 */
@Composable
internal fun Modifier.librarySeriesStack(): Modifier {
    val near = MaterialTheme.colorScheme.surfaceContainerHigh
    val far = MaterialTheme.colorScheme.surfaceContainerHighest
    val edge = MaterialTheme.colorScheme.outlineVariant
    val radius = UnifiedReaderThemeTokens.presentation.coverCornerRadius
    return drawBehind {
        drawStackEdge(inset = 14.dp, raise = 12.dp, fill = far, edge = edge, radius = radius)
        drawStackEdge(inset = 7.dp, raise = 6.dp, fill = near, edge = edge, radius = radius)
    }
}

private fun DrawScope.drawStackEdge(
    inset: Dp,
    raise: Dp,
    fill: Color,
    edge: Color,
    radius: Dp,
) {
    val left = inset.toPx()
    val top = -raise.toPx()
    // The strip runs down behind the cover; only its raised part shows.
    val size = Size(size.width - 2 * left, raise.toPx() + 2 * radius.toPx())
    val corner = CornerRadius(radius.toPx())
    drawRoundRect(color = fill, topLeft = Offset(left, top), size = size, cornerRadius = corner)
    drawRoundRect(color = edge, topLeft = Offset(left, top), size = size, cornerRadius = corner, style = Stroke(width = 1.dp.toPx()))
}
