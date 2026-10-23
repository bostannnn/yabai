package com.example.unifiedreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a screen shows when it has nothing to list: a small line [drawing], a bold [title], one
 * plain sentence of [body], and the [actions] that fix it (an [AppButton] first, then
 * [AppTonalButton]s), centred.
 */
@Composable
fun AppEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    drawing: @Composable () -> Unit = { AppEmptyStateBooks() },
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        drawing()
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 24.dp)
                .semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(max = AppEmptyStateDefaults.bodyMaxWidth),
        )
        if (actions != null) {
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * The empty-state drawing: three book outlines leaning on a shelf line, the middle one in front.
 * Each book is filled with [background] so the ones behind are hidden where they overlap.
 */
@Composable
fun AppEmptyStateBooks(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(AppEmptyStateDefaults.drawingWidth, AppEmptyStateDefaults.drawingHeight)) {
        val stroke = Stroke(width = AppEmptyStateDefaults.strokeWidth.toPx(), cap = StrokeCap.Round)
        val book = Size(38.dp.toPx(), 54.dp.toPx())
        val corner = CornerRadius(5.dp.toPx())
        val shelfY = size.height - 14.dp.toPx()
        // Left leans left, the right leans right, the middle stands taller in front of both.
        drawBook(Offset(size.width / 2f - 50.dp.toPx(), shelfY - book.height - 6.dp.toPx()), book, corner, -7f, stroke, color, background)
        drawBook(Offset(size.width / 2f + 12.dp.toPx(), shelfY - book.height - 6.dp.toPx()), book, corner, 6f, stroke, color, background)
        drawBook(Offset(size.width / 2f - 19.dp.toPx(), shelfY - book.height - 14.dp.toPx()), book, corner, 0f, stroke, color, background)
        drawLine(
            color = color,
            start = Offset(size.width / 2f - 64.dp.toPx(), shelfY),
            end = Offset(size.width / 2f + 64.dp.toPx(), shelfY),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBook(
    topLeft: Offset,
    size: Size,
    corner: CornerRadius,
    degrees: Float,
    stroke: Stroke,
    color: Color,
    fill: Color,
) {
    rotate(degrees, pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height)) {
        drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = corner)
        drawRoundRect(color = color, topLeft = topLeft, size = size, cornerRadius = corner, style = stroke)
    }
}

object AppEmptyStateDefaults {
    val drawingWidth: Dp = 170.dp
    val drawingHeight: Dp = 118.dp
    val strokeWidth: Dp = 2.4.dp
    val bodyMaxWidth: Dp = 360.dp
}
