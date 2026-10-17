package com.example.unifiedreader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A modal panel docked to the end edge for tasks with several controls (filters, options).
 * On compact widths it becomes the bottom sheet. Like every sheet it is its own window, so it
 * is an opaque surface rather than glass.
 */
@Composable
fun AppSideSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = AppSideSheetDefaults.width,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < AppSideSheetDefaults.compactWidthDp
    if (compact) {
        AppSheet(onDismissRequest = onDismissRequest, content = content)
        return
    }
    AppDialogHost(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .safeDrawingPadding()
                .padding(AppSideSheetDefaults.margin),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AppPanel(
                modifier = modifier
                    .width(width)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                color = UnifiedReaderThemeTokens.colors.surfaceBase,
                shadowElevation = 16.dp,
                content = content,
            )
        }
    }
}

object AppSideSheetDefaults {
    val width: Dp = 400.dp
    val margin: Dp = 12.dp
    const val compactWidthDp: Int = 600
}
