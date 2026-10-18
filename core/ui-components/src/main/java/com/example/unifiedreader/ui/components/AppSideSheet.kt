package com.example.unifiedreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A modal panel docked to the end edge for tasks with several controls (filters, options).
 * On compact widths it becomes the bottom sheet. Like every sheet it is its own window, so it
 * is an opaque surface rather than glass. It slides in from the end edge like the drawer and
 * keeps the drawer's margin from the screen edges and the system bars.
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        // On Android 15+ the dialog's content is laid out at the full screen height while the
        // window manager fits the window between the system bars, so the bottom of a
        // fill-height panel ends up under the gesture bar. Clamp the content to the window's
        // visible frame; where the two already agree this is a no-op.
        val view = LocalView.current
        val density = LocalDensity.current
        var overflow by remember { mutableStateOf(0.dp) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val frame = android.graphics.Rect()
                    view.getWindowVisibleDisplayFrame(frame)
                    val extra = coordinates.size.height - frame.height()
                    overflow = if (extra > 0) with(density) { extra.toDp() } else 0.dp
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .padding(bottom = overflow)
                .padding(AppSideSheetDefaults.margin),
            contentAlignment = Alignment.CenterEnd,
        ) {

            AnimatedVisibility(
                visible = shown,
                enter = slideInHorizontally(
                    animationSpec = tween(AppSideSheetDefaults.enterMillis),
                    initialOffsetX = { fullWidth -> fullWidth / 3 },
                ) + fadeIn(animationSpec = tween(AppSideSheetDefaults.enterMillis)),
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
}

object AppSideSheetDefaults {
    val width: Dp = 400.dp
    val margin: Dp = 12.dp
    const val compactWidthDp: Int = 600
    const val enterMillis: Int = 220
}
