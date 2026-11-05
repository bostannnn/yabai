package com.example.unifiedreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.ui.theme.AppMotion

/**
 * Search in the middle of the screen, like Spotlight (design system, AppSearchPanel): a panel
 * over the dimmed screen whose first row is the search field, then [content] in a list that
 * scrolls, then an optional [footer] under a divider. It is its own window, like a sheet.
 *
 * Its top and centre are [anchor]'s (the bar field it opens out of, in window pixels), so it
 * grows out of that field; without one it sits centred under the status bar. It is
 * [AppSearchPanelDefaults.maxWidth] at most and keeps the gutter from the window's sides, and it
 * ends a gutter above the keyboard or the navigation bar.
 *
 * The field has no Clear of its own: Close sits beside it. While the keyboard is up the footer
 * steps aside so the results get the room.
 *
 * A tap outside, Back and Close play its exit, then call [onDismissRequest]. To close it after
 * something inside was picked, the caller drops it; [AppSearchPanelState.dismiss] plays the exit
 * first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Rect? = null,
    state: AppSearchPanelState = rememberAppSearchPanelState(),
    listState: LazyListState = rememberLazyListState(),
    onSearch: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val visibleState = state.visibleState
    // The caller removes the panel once its exit has played.
    if (!visibleState.targetState && !visibleState.currentState && visibleState.isIdle) {
        LaunchedEffect(Unit) { latestOnDismissRequest() }
    }
    AppDialogHost(
        onDismissRequest = state::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val density = LocalDensity.current
        val gutter = appGutter()
        val statusBarTop = with(density) { WindowInsets.appSystemBars.getTop(this).toDp() }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = state::dismiss,
                ),
        ) {
            val width = min(AppSearchPanelDefaults.maxWidth, maxWidth - gutter * 2)
            val centreX = anchor?.let { with(density) { it.center.x.toDp() } } ?: (maxWidth / 2)
            val start = (centreX - width / 2).coerceIn(gutter, (maxWidth - gutter - width).coerceAtLeast(gutter))
            val top = anchor?.let { with(density) { it.top.toDp() } } ?: (statusBarTop + AppTopBarDefaults.topMargin)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
                    .padding(start = start, top = top, bottom = gutter),
            ) {
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(AppMotion.effects()) +
                        scaleIn(AppMotion.spatial(), initialScale = AppMotion.fadeThroughInitialScale, transformOrigin = TransformOrigin(0.5f, 0f)),
                    exit = fadeOut(AppMotion.effects()) +
                        scaleOut(AppMotion.spatial(), targetScale = AppMotion.fadeThroughInitialScale, transformOrigin = TransformOrigin(0.5f, 0f)),
                ) {
                    AppPanel(
                        modifier = modifier
                            .width(width)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        shape = RoundedCornerShape(AppSideSheetDefaults.cornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = AppSearchPanelDefaults.shadowElevation,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(AppSearchPanelDefaults.fieldRowHeight)
                                    .padding(start = AppSearchPanelDefaults.fieldStartInset, end = AppSearchPanelDefaults.closeEndInset),
                                horizontalArrangement = Arrangement.spacedBy(AppSearchPanelDefaults.closeGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppSearchField(
                                    query = query,
                                    onQueryChange = onQueryChange,
                                    placeholder = placeholder,
                                    modifier = Modifier.weight(1f),
                                    height = AppSearchPanelDefaults.fieldRowHeight,
                                    containerColor = Color.Transparent,
                                    autoFocus = true,
                                    onSearch = onSearch,
                                    showActiveEdge = false,
                                    showClear = false,
                                )
                                Box(modifier = Modifier.height(AppSearchPanelDefaults.fieldRowHeight), contentAlignment = Alignment.Center) {
                                    AppIconButton(onClick = state::dismiss) {
                                        Icon(
                                            imageVector = AppIcons.Close,
                                            contentDescription = "Close search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            AppSheetDivider()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f, fill = false),
                                content = content,
                            )
                            // With the keyboard up the rows need the room; the footer comes back with it down.
                            if (footer != null && !WindowInsets.isImeVisible) {
                                AppSheetDivider()
                                footer()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Whether an [AppSearchPanel] is showing; [dismiss] plays its exit and then closes it. */
class AppSearchPanelState internal constructor() {
    internal val visibleState = MutableTransitionState(false).apply { targetState = true }

    fun dismiss() {
        visibleState.targetState = false
    }
}

@Composable
fun rememberAppSearchPanelState(): AppSearchPanelState = remember { AppSearchPanelState() }

object AppSearchPanelDefaults {
    /** The panel's widest; under it the window's width less a gutter each side. */
    val maxWidth: Dp = 680.dp
    val fieldRowHeight: Dp = 64.dp

    /** The field has no box of its own: its icon sits where a row's leading icon would, 6 in from the inset. */
    val fieldStartInset: Dp = 6.dp
    val closeEndInset: Dp = 8.dp
    val closeGap: Dp = 4.dp
    val shadowElevation: Dp = 16.dp

    /** A result row's highlight sits this far in from the panel's sides... */
    val rowMargin: Dp = 8.dp

    /** ...and its cover and text the rest of the way to the panel's inset (20). */
    val rowPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    /** After the last row, before the footer's divider or the panel's edge. */
    val listBottom: Dp = 8.dp
}
