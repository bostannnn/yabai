package com.example.unifiedreader.feature.reader.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.example.unifiedreader.ui.components.AppGlassDefaults
import com.example.unifiedreader.ui.components.appGlass

enum class ReaderOverlaySurfaceRole {
    ControlBar,
    AttachedPane,
    PreviewStrip,
}

/** The reader's chrome material: the kit's glass on the role's shape. No extra shadow; the glass edge separates it. */
@Composable
fun Modifier.readerOverlaySurface(
    role: ReaderOverlaySurfaceRole,
    containerColor: Color? = null,
): Modifier {
    val tokens = readerOverlayTokens()
    return appGlass(
        shape = role.shape(tokens),
        containerColor = containerColor ?: tokens.colors.container,
        tintAlpha = when (role) {
            ReaderOverlaySurfaceRole.AttachedPane -> AppGlassDefaults.paneTintAlpha
            ReaderOverlaySurfaceRole.ControlBar,
            ReaderOverlaySurfaceRole.PreviewStrip -> AppGlassDefaults.tintAlpha
        },
    )

}

private fun ReaderOverlaySurfaceRole.shape(tokens: ReaderOverlayTokens): Shape = when (this) {
    ReaderOverlaySurfaceRole.ControlBar -> tokens.shapes.controlBar
    ReaderOverlaySurfaceRole.AttachedPane -> tokens.shapes.sheet
    ReaderOverlaySurfaceRole.PreviewStrip -> tokens.shapes.previewStrip
}
