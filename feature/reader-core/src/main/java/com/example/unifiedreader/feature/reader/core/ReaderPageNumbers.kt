package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppChoiceRow
import kotlin.math.roundToInt

/** One number of the page indicator, centred at [centerFraction] of its width; a spread has one per page. */
@Immutable
data class ReaderPageNumber(
    val label: String,
    val centerFraction: Float = 0.5f,
)

@Immutable
sealed interface ReaderPageNumberStyle {
    /** Plain text on the page's own paper, for a reader that keeps a blank margin under the text. */
    data class OnPaper(val color: Color) : ReaderPageNumberStyle

    /** A small dark pill, for pages whose art fills the screen. */
    data object OnImage : ReaderPageNumberStyle
}

/**
 * The page numbers shown while the reader's menu is hidden. Each is centred at its
 * [ReaderPageNumber.centerFraction] of the width, kept on screen, and centred in the height the
 * caller gives. It has no pointer input, so taps reach the page under it, and nothing animates:
 * it redraws only when a number changes.
 */
@Composable
fun ReaderPageNumbers(
    numbers: List<ReaderPageNumber>,
    style: ReaderPageNumberStyle,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            numbers.forEach { number ->
                ReaderPageNumberLabel(label = number.label, style = style)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.sumOf { it.width }
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(
                    x = readerPageNumberLeft(numbers[index].centerFraction, width, placeable.width),
                    y = (height - placeable.height) / 2,
                )
            }
        }
    }
}

/** The left edge that centres a [labelWidth] label at [centerFraction] of [containerWidth], kept inside it. */
internal fun readerPageNumberLeft(
    centerFraction: Float,
    containerWidth: Int,
    labelWidth: Int,
): Int = (centerFraction.coerceIn(0f, 1f) * containerWidth - labelWidth / 2f)
    .roundToInt()
    .coerceIn(0, (containerWidth - labelWidth).coerceAtLeast(0))

@Composable
private fun ReaderPageNumberLabel(
    label: String,
    style: ReaderPageNumberStyle,
) {
    // Tabular figures, so the label keeps its width as the number changes.
    val textStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    when (style) {
        is ReaderPageNumberStyle.OnPaper -> Text(
            text = label,
            style = textStyle,
            color = style.color,
            maxLines = 1,
        )

        ReaderPageNumberStyle.OnImage -> Text(
            text = label,
            style = textStyle,
            color = ReaderPageNumberPillText,
            maxLines = 1,
            modifier = Modifier
                .background(ReaderPageNumberPillContainer, readerOverlayTokens().shapes.button)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** The numbers over pages whose art fills the screen: pills just above the bottom edge. */
@Composable
fun BoxScope.ReaderPageNumberPills(
    numbers: List<ReaderPageNumber>,
    modifier: Modifier = Modifier,
) {
    ReaderPageNumbers(
        numbers = numbers,
        style = ReaderPageNumberStyle.OnImage,
        // Bottom padding only: the centre fractions are of the full width.
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}

/** The reader settings row for the page number, shared by the readers that have a settings panel. */
@Composable
fun ReaderPageNumberChoiceRow(
    visible: Boolean,
    onVisibleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChoiceRow(
        label = "Page number",
        options = ReaderPageNumberChoices,
        selected = visible,
        onSelected = onVisibleChanged,
        optionLabel = { shown -> if (shown) "Show" else "Hide" },
        modifier = modifier,
    )
}

private val ReaderPageNumberChoices = listOf(true, false)

private val ReaderPageNumberPillContainer = Color.Black.copy(alpha = 0.6f)
private val ReaderPageNumberPillText = Color.White.copy(alpha = 0.92f)
