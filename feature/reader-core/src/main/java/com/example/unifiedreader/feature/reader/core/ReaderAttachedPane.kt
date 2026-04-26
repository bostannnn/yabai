package com.example.unifiedreader.feature.reader.core

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ReaderAttachedPaneAnchor {
    Top,
    Bottom,
}

@Composable
fun ReaderAttachedPane(
    visible: Boolean,
    anchor: ReaderAttachedPaneAnchor,
    maxPaneHeight: Dp,
    modifier: Modifier = Modifier,
    maxPaneWidth: Dp = 360.dp,
    minPaneWidth: Dp = 220.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    horizontalPadding: Dp = 10.dp,
    onDismissRequest: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onDismissRequest != null) {
        BackHandler(enabled = visible) {
            onDismissRequest()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onDismissRequest != null) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismissRequest,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            val availableWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val sheetWidth = availableWidth.coerceAtMost(maxPaneWidth)
            val sheetMinWidth = minPaneWidth.coerceAtMost(sheetWidth)
            val boxAlignment = when (horizontalAlignment) {
                Alignment.Start -> if (anchor == ReaderAttachedPaneAnchor.Top) Alignment.TopStart else Alignment.BottomStart
                Alignment.End -> if (anchor == ReaderAttachedPaneAnchor.Top) Alignment.TopEnd else Alignment.BottomEnd
                else -> if (anchor == ReaderAttachedPaneAnchor.Top) Alignment.TopCenter else Alignment.BottomCenter
            }
            Box(
                modifier = Modifier
                    .align(boxAlignment)
                    .padding(horizontal = horizontalPadding),
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = paneSlideIn(anchor) + scaleIn(
                        initialScale = if (anchor == ReaderAttachedPaneAnchor.Top) 0.96f else 0.92f,
                        transformOrigin = paneTransformOrigin(anchor, horizontalAlignment),
                    ),
                    exit = paneSlideOut(anchor) + scaleOut(
                        targetScale = if (anchor == ReaderAttachedPaneAnchor.Top) 0.97f else 0.94f,
                        transformOrigin = paneTransformOrigin(anchor, horizontalAlignment),
                    ),
                ) {
                    val tokens = readerOverlayTokens()
                    Column(
                        modifier = Modifier
                            .widthIn(min = sheetMinWidth, max = sheetWidth)
                            .heightIn(max = maxPaneHeight)
                            .shadow(
                                elevation = 18.dp,
                                shape = tokens.shapes.sheet,
                            )
                            .background(
                                color = tokens.colors.elevatedContainer,
                                shape = tokens.shapes.sheet,
                            )
                            .border(
                                width = 1.dp,
                                color = tokens.colors.selectedBorder,
                                shape = tokens.shapes.sheet,
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {},
                            )
                            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                        content = content,
                    )
                }
            }
        }
    }
}

private fun paneSlideIn(anchor: ReaderAttachedPaneAnchor) = slideInVertically(
    initialOffsetY = { height ->
        when (anchor) {
            ReaderAttachedPaneAnchor.Top -> -height / 3
            ReaderAttachedPaneAnchor.Bottom -> height / 3
        }
    },
)

private fun paneSlideOut(anchor: ReaderAttachedPaneAnchor) = slideOutVertically(
    targetOffsetY = { height ->
        when (anchor) {
            ReaderAttachedPaneAnchor.Top -> -height / 4
            ReaderAttachedPaneAnchor.Bottom -> height / 4
        }
    },
)

private fun paneTransformOrigin(
    anchor: ReaderAttachedPaneAnchor,
    horizontalAlignment: Alignment.Horizontal,
): TransformOrigin {
    val pivotX = when (horizontalAlignment) {
        Alignment.Start -> 0.12f
        Alignment.End -> 0.88f
        else -> 0.5f
    }
    val pivotY = when (anchor) {
        ReaderAttachedPaneAnchor.Top -> 0f
        ReaderAttachedPaneAnchor.Bottom -> 1f
    }
    return TransformOrigin(pivotX, pivotY)
}
