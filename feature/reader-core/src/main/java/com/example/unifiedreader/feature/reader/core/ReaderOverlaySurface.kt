package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ReaderOverlaySurfaceRole {
    ControlBar,
    AttachedPane,
    PreviewStrip,
}

@Composable
fun Modifier.readerOverlaySurface(
    role: ReaderOverlaySurfaceRole,
    containerColor: Color? = null,
): Modifier {
    val tokens = readerOverlayTokens()
    val shape = role.shape(tokens)
    val elevation = role.elevation()
    val color = containerColor ?: when (role) {
        ReaderOverlaySurfaceRole.ControlBar,
        ReaderOverlaySurfaceRole.AttachedPane,
        ReaderOverlaySurfaceRole.PreviewStrip -> tokens.colors.container
    }
    return then(
        Modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .background(
                color = color,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = tokens.colors.selectedBorder,
                shape = shape,
            ),
    )
}

private fun ReaderOverlaySurfaceRole.shape(tokens: ReaderOverlayTokens): Shape = when (this) {
    ReaderOverlaySurfaceRole.ControlBar -> tokens.shapes.controlBar
    ReaderOverlaySurfaceRole.AttachedPane -> tokens.shapes.sheet
    ReaderOverlaySurfaceRole.PreviewStrip -> tokens.shapes.previewStrip
}

private fun ReaderOverlaySurfaceRole.elevation(): Dp = when (this) {
    ReaderOverlaySurfaceRole.ControlBar -> 0.dp
    ReaderOverlaySurfaceRole.AttachedPane -> 18.dp
    ReaderOverlaySurfaceRole.PreviewStrip -> 0.dp
}
