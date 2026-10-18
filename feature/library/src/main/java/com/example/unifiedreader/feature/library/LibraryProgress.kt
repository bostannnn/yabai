package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * The thin reading-progress bar drawn along the bottom edge of a cover, in the grid, the
 * shelves, and the shelf browser alike.
 */
@Composable
fun LibraryCoverProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val barShape = androidx.compose.foundation.shape.RoundedCornerShape(
        bottomStart = presentation.coverCornerRadius,
        bottomEnd = presentation.coverCornerRadius,
    )
    Box(
        modifier = modifier
            .height(LibraryCoverProgressBarHeight)
            .clip(barShape)
            .appProgressFill(
                fraction = fraction.coerceIn(0f, 1f),
                progressColor = semanticColors.readerAccent,
                trackColor = Color.Black.copy(alpha = 0.35f),
            ),
    )
}

private val LibraryCoverProgressBarHeight = 4.dp
