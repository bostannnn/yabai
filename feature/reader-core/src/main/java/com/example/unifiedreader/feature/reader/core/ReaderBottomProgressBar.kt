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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

@Composable
fun ReaderBottomProgressBar(
    progress: Float,
    percentLabel: String,
    pageLabel: String,
    readingDirection: ComicReadingDirection = ComicReadingDirection.LTR,
    modifier: Modifier = Modifier,
    chapterMarks: List<Float> = emptyList(),
    leadingAction: (@Composable RowScope.() -> Unit)? = null,
    onProgressSelected: ((Float) -> Unit)? = null,
) {
    val tokens = readerOverlayTokens()
    val clampedProgress = progress.coerceIn(0f, 1f)
    val visualProgress = orientReaderProgress(
        progress = clampedProgress,
        readingDirection = readingDirection,
    )
    val fillRange = resolveReaderProgressFillRange(
        progress = clampedProgress,
        readingDirection = readingDirection,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .safeGesturesPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .background(tokens.colors.container, tokens.shapes.controlBar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingAction?.invoke(this)
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.primaryText,
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
                                val selectedProgress = resolveReaderProgressSelection(
                                    horizontalFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                                    readingDirection = readingDirection,
                                )
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
                color = tokens.colors.track,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackStrokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tokens.colors.progress,
                start = Offset(size.width * fillRange.startFraction, centerY),
                end = Offset(size.width * fillRange.endFraction, centerY),
                strokeWidth = trackStrokeWidth,
                cap = StrokeCap.Round,
            )
            chapterMarks
                .asSequence()
                .map { mark ->
                    orientReaderProgress(
                        progress = mark.coerceIn(0f, 1f),
                        readingDirection = readingDirection,
                    )
                }
                .filter { it > 0f && it < 1f }
                .distinct()
                .forEach { mark ->
                    val x = size.width * mark
                    drawLine(
                        color = tokens.colors.primaryText.copy(alpha = 0.8f),
                        start = Offset(x, centerY - markHalfHeight),
                        end = Offset(x, centerY + markHalfHeight),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            drawCircle(
                color = tokens.colors.primaryText,
                radius = 3.5.dp.toPx(),
                center = Offset(size.width * visualProgress, centerY),
            )
        }
        Text(
            text = pageLabel,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

internal fun orientReaderProgress(
    progress: Float,
    readingDirection: ComicReadingDirection,
): Float = when (readingDirection) {
    ComicReadingDirection.LTR -> progress.coerceIn(0f, 1f)
    ComicReadingDirection.RTL -> 1f - progress.coerceIn(0f, 1f)
}

internal data class ReaderProgressFillRange(
    val startFraction: Float,
    val endFraction: Float,
)

internal fun resolveReaderProgressFillRange(
    progress: Float,
    readingDirection: ComicReadingDirection,
): ReaderProgressFillRange {
    val visualProgress = orientReaderProgress(
        progress = progress,
        readingDirection = readingDirection,
    )
    return when (readingDirection) {
        ComicReadingDirection.LTR -> ReaderProgressFillRange(
            startFraction = 0f,
            endFraction = visualProgress,
        )
        ComicReadingDirection.RTL -> ReaderProgressFillRange(
            startFraction = visualProgress,
            endFraction = 1f,
        )
    }
}

internal fun resolveReaderProgressSelection(
    horizontalFraction: Float,
    readingDirection: ComicReadingDirection,
): Float = when (readingDirection) {
    ComicReadingDirection.LTR -> horizontalFraction.coerceIn(0f, 1f)
    ComicReadingDirection.RTL -> 1f - horizontalFraction.coerceIn(0f, 1f)
}
