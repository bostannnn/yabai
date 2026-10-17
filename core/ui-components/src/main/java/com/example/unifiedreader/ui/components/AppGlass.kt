package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Links the content that chrome floats over ([appGlassSource]) with the chrome that blurs it
 * ([appGlass]). The app shell creates one and provides it through [LocalAppGlassState].
 */
@Stable
class AppGlassState internal constructor(internal val haze: HazeState)

@Composable
fun rememberAppGlassState(): AppGlassState = remember { AppGlassState(HazeState()) }

val LocalAppGlassState = staticCompositionLocalOf<AppGlassState?> { null }

/** Marks the content area that glass chrome floats over. Does nothing without a glass state. */
@Composable
fun Modifier.appGlassSource(): Modifier {
    val state = LocalAppGlassState.current ?: return this
    return hazeSource(state = state.haze)
}

/**
 * The one chrome material: a translucent surface over a blurred backdrop with a hairline edge.
 * Used by the top bar, the drawer, sheets, and reader bars. Without a glass state, or on
 * devices that cannot blur, it falls back to a nearly opaque surface.
 */
@Composable
fun Modifier.appGlass(
    shape: Shape,
    containerColor: Color = MaterialTheme.colorScheme.surface,
): Modifier {
    val state = LocalAppGlassState.current
    val edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AppGlassDefaults.edgeAlpha)
    val base = clip(shape)
    val filled = if (state != null) {
        base.hazeEffect(
            state = state.haze,
            style = HazeStyle(
                backgroundColor = containerColor,
                tints = listOf(HazeTint(containerColor.copy(alpha = AppGlassDefaults.tintAlpha))),
                blurRadius = AppGlassDefaults.blurRadius,
                noiseFactor = 0f,
                fallbackTint = HazeTint(containerColor.copy(alpha = AppGlassDefaults.fallbackAlpha)),
            ),
        )
    } else {
        base.background(containerColor.copy(alpha = AppGlassDefaults.fallbackAlpha))
    }
    return filled.border(width = 1.dp, color = edge, shape = shape)
}

object AppGlassDefaults {
    val blurRadius: Dp = 22.dp
    const val tintAlpha: Float = 0.72f
    const val fallbackAlpha: Float = 0.96f
    const val edgeAlpha: Float = 0.45f
}
