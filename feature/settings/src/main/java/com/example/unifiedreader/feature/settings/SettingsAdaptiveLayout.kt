package com.example.unifiedreader.feature.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal const val SettingsScreenHorizontalPaddingDp = 20
internal const val SettingsOverviewColumnSpacingDp = 16
internal const val SettingsOverviewMinColumnWidthDp = 420
internal const val SettingsFilterSourceMinColumnWidthDp = 300
internal const val SettingsServerFormTwoColumnBreakpointDp = 560
internal const val SettingsInlineChoiceControlsBreakpointDp = 420

internal val SettingsMaxContentWidth: Dp = 1120.dp

internal data class SettingsAdaptiveLayout(
    val filterSourceColumns: Int,
    val useTwoColumnServerForm: Boolean,
    val useInlineChoiceControls: Boolean,
)

internal fun resolveSettingsAdaptiveLayout(widthDp: Int): SettingsAdaptiveLayout {
    val filterSourceColumns = if (widthDp >= (SettingsFilterSourceMinColumnWidthDp * 2) + 12) 2 else 1
    return SettingsAdaptiveLayout(
        filterSourceColumns = filterSourceColumns,
        useTwoColumnServerForm = widthDp >= SettingsServerFormTwoColumnBreakpointDp,
        useInlineChoiceControls = widthDp >= SettingsInlineChoiceControlsBreakpointDp,
    )
}

internal fun resolveSettingsOverviewContentWidth(containerWidthDp: Int): Int {
    val boundedWidth = minOf(containerWidthDp, SettingsMaxContentWidth.value.toInt())
    return max(0, boundedWidth - (SettingsScreenHorizontalPaddingDp * 2))
}
