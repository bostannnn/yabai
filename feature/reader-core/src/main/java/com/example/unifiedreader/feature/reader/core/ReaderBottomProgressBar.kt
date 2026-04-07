package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ReaderBottomProgressBarShape = RoundedCornerShape(8.dp)

@Composable
fun ReaderBottomProgressBar(
    progress: Float,
    percentLabel: String,
    pageLabel: String,
    modifier: Modifier = Modifier,
    chapterMarks: List<Float> = emptyList(),
    leadingAction: (@Composable RowScope.() -> Unit)? = null,
    onProgressSelected: ((Float) -> Unit)? = null,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .safeGesturesPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .background(Color(0xF2111418), ReaderBottomProgressBarShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingAction?.invoke(this)
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF5F7FA),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .then(
                    if (onProgressSelected != null) {
                        Modifier.pointerInput(onProgressSelected) {
                            detectTapGestures { offset ->
                                if (size.width <= 0) return@detectTapGestures
                                val selectedProgress = (offset.x / size.width.toFloat())
                                    .coerceIn(0f, 1f)
                                onProgressSelected(selectedProgress)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val centerY = size.height / 2f
            val trackStrokeWidth = 2.dp.toPx()
            val markHalfHeight = 5.dp.toPx()
            drawLine(
                color = Color(0x55F5F7FA),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackStrokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFE9EEF3),
                start = Offset(0f, centerY),
                end = Offset(size.width * clampedProgress, centerY),
                strokeWidth = trackStrokeWidth,
                cap = StrokeCap.Round,
            )
            chapterMarks
                .asSequence()
                .map { it.coerceIn(0f, 1f) }
                .filter { it > 0f && it < 1f }
                .distinct()
                .forEach { mark ->
                    val x = size.width * mark
                    drawLine(
                        color = Color(0xCCF5F7FA),
                        start = Offset(x, centerY - markHalfHeight),
                        end = Offset(x, centerY + markHalfHeight),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            drawCircle(
                color = Color(0xFFF5F7FA),
                radius = 3.5.dp.toPx(),
                center = Offset(size.width * clampedProgress, centerY),
            )
        }
        Text(
            text = pageLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFD6E0E8),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
