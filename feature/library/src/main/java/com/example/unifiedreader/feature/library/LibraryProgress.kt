package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
internal fun ProgressSummary(
    progress: ItemProgressSummary,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = progress.progressDetailLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (progress.shouldShowSummaryProgress) {
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.92f),
                color = semanticColors.readerAccent,
                trackColor = semanticColors.surfaceSelected.copy(alpha = 0.34f),
            )
        }
    }
}

@Composable
internal fun LibraryCoverProgressBar(
    progress: ItemProgressSummary,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val clampedFraction = progress.fraction.coerceIn(0f, 1f)
    val barShape = androidx.compose.foundation.shape.RoundedCornerShape(
        bottomStart = 4.dp,
        bottomEnd = 4.dp,
    )

        Box(
            modifier = modifier
                .height(7.dp)
                .clip(barShape)
                .background(semanticColors.readerScrim.copy(alpha = 0.42f)),
        ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedFraction)
                .background(semanticColors.readerAccent),
        )
    }
}
