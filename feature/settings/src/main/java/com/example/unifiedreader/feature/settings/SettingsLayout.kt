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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val SettingsServerFormTwoColumnBreakpointDp = 560
internal const val SettingsInlineChoiceControlsBreakpointDp = 640

internal val SettingsPageMaxWidth: Dp = 800.dp

/**
 * Expanded windows keep the hub as a side column next to the open page; narrower ones push the
 * page over the hub.
 */
fun settingsUsesSidePane(windowWidth: AppWindowWidth): Boolean = windowWidth == AppWindowWidth.Expanded

internal data class SettingsAdaptiveLayout(
    val useTwoColumnServerForm: Boolean,
    val useInlineChoiceControls: Boolean,
)

internal fun resolveSettingsAdaptiveLayout(widthDp: Int): SettingsAdaptiveLayout = SettingsAdaptiveLayout(
    useTwoColumnServerForm = widthDp >= SettingsServerFormTwoColumnBreakpointDp,
    useInlineChoiceControls = widthDp >= SettingsInlineChoiceControlsBreakpointDp,
)

/**
 * Tells the settings screen whether the column it is given has scrolled, so the top bar stays
 * see-through until something is under it.
 */
internal class SettingsScrollReporter(
    val onScrolledChanged: (Boolean) -> Unit,
)

internal val LocalSettingsScrollReporter = staticCompositionLocalOf<SettingsScrollReporter?> { null }

/** The scrolling column every settings page and the hub sit in, capped at a readable width. */
@Composable
internal fun SettingsPageContainer(
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = appGutter(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    LocalSettingsScrollReporter.current?.let { reporter ->
        val report by rememberUpdatedState(reporter.onScrolledChanged)
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value > 0 }.distinctUntilChanged().collect { report(it) }
        }
        DisposableEffect(Unit) { onDispose { report(false) } }
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SettingsPageMaxWidth)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = contentTopInset + AppLayout.contentTop,
                    bottom = AppLayout.contentBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
            content = content,
        )
    }
}
