package com.example.unifiedreader.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Determinate linear progress. */
@Composable
fun AppLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** Indeterminate linear progress. */
@Composable
fun AppLinearProgress(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** Indeterminate circular progress. */
@Composable
fun AppCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
    )
}
