package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A single-choice row of [AppSegment]s. */
@Composable
fun AppSegmentedRow(
    modifier: Modifier = Modifier,
    content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier,
        content = content,
    )
}

/**
 * One option in an [AppSegmentedRow]. [index] and [count] pick the end-cap shape, so
 * callers never compute segment shapes themselves.
 */
@Composable
fun SingleChoiceSegmentedButtonRowScope.AppSegment(
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
    border: BorderStroke? = null,
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
) {
    val shape = SegmentedButtonDefaults.itemShape(index = index, count = count)
    if (border == null) {
        SegmentedButton(
            selected = selected,
            onClick = onClick,
            shape = shape,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            icon = icon,
            label = label,
        )
    } else {
        SegmentedButton(
            selected = selected,
            onClick = onClick,
            shape = shape,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            border = border,
            icon = icon,
            label = label,
        )
    }
}
