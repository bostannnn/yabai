package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val SettingsScreenHorizontalPaddingDp = 20
internal const val SettingsSidePaneWidthDp = 320
internal const val SettingsSidePaneSpacingDp = 20
internal const val SettingsSidePaneMinPageWidthDp = 480
internal const val SettingsServerFormTwoColumnBreakpointDp = 560
internal const val SettingsInlineChoiceControlsBreakpointDp = 520

internal val SettingsPageMaxWidth: Dp = 800.dp

/**
 * Wide windows keep the hub as a side column next to the open page; narrow ones push the page
 * over the hub.
 */
fun settingsUsesSidePane(windowWidthDp: Int): Boolean {
    val contentWidth = windowWidthDp - (SettingsScreenHorizontalPaddingDp * 2)
    return contentWidth >= SettingsSidePaneWidthDp + SettingsSidePaneSpacingDp + SettingsSidePaneMinPageWidthDp
}

internal data class SettingsAdaptiveLayout(
    val useTwoColumnServerForm: Boolean,
    val useInlineChoiceControls: Boolean,
)

internal fun resolveSettingsAdaptiveLayout(widthDp: Int): SettingsAdaptiveLayout = SettingsAdaptiveLayout(
    useTwoColumnServerForm = widthDp >= SettingsServerFormTwoColumnBreakpointDp,
    useInlineChoiceControls = widthDp >= SettingsInlineChoiceControlsBreakpointDp,
)

/** The scrolling column every settings page and the hub sit in, capped at a readable width. */
@Composable
internal fun SettingsPageContainer(
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SettingsScreenHorizontalPaddingDp.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SettingsPageMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = contentTopInset + 8.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}
