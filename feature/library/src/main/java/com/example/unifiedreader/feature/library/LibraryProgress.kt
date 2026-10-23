package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.appProgressFill
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

internal val ItemProgressSummary.isComplete: Boolean
    get() = isFinished || fraction >= 0.999f

internal val ItemProgressSummary.hasStarted: Boolean
    get() = fraction > 0f

internal val ItemProgressSummary.shouldShowCoverProgress: Boolean
    get() = hasStarted && !isComplete

internal val ItemProgressSummary.shouldShowSummaryProgress: Boolean
    get() = hasStarted && !isComplete

internal val ItemProgressSummary.progressDetailLabel: String
    get() = if (isComplete) {
        label.takeIf { isFinished && it.startsWith("Finished") } ?: "Finished"
    } else {
        detailLabel
    }

/**
 * The reading-progress bar on a cover, in the grid, the shelves, and the shelf browser alike: a
 * 4 dp pill inset 7 dp from the cover's sides and bottom, the accent
 * over a dark track. Callers align it to the bottom and give it the cover's width.
 */
@Composable
fun LibraryCoverProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    Box(
        modifier = modifier
            .padding(
                start = LibraryCoverProgressBarInset,
                end = LibraryCoverProgressBarInset,
                bottom = LibraryCoverProgressBarInset,
            )
            .height(LibraryCoverProgressBarHeight)
            .clip(CircleShape)
            .appProgressFill(
                fraction = fraction.coerceIn(0f, 1f),
                progressColor = semanticColors.readerAccent,
                trackColor = Color.Black.copy(alpha = 0.42f),
            ),
    )
}

private val LibraryCoverProgressBarHeight = 4.dp
private val LibraryCoverProgressBarInset = 7.dp
