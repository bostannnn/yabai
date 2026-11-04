package com.example.unifiedreader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.AppGroundPattern
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The active theme's ground pattern (design system: `ground-pattern`), drawn behind whatever
 * this modifier is on: one rectangle filled from a small repeating tile, made once per size.
 * Quiet themes have none and get the modifier back unchanged. Only the screen ground
 * ([AppBackground]) and the rail carry it.
 */
@Composable
fun Modifier.appGroundPattern(): Modifier {
    val character = UnifiedReaderThemeTokens.character
    val pattern = character.groundPattern
    if (pattern == AppGroundPattern.None) return this
    val color = character.patternColor
    return drawWithCache {
        val brush = ShaderBrush(
            ImageShader(groundTile(pattern, color, density = this), TileMode.Repeated, TileMode.Repeated),
        )
        onDrawBehind { drawRect(brush) }
    }
}

/**
 * The selected rail or drawer item's fill and content colour: `primary` and `on-primary` in a
 * character theme, the selected container in a quiet one.
 */
@Composable
internal fun appNavigationSelectedColors(): Pair<Color, Color> {
    val semanticColors = UnifiedReaderThemeTokens.colors
    return if (UnifiedReaderThemeTokens.character.accentSelection) {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    } else {
        semanticColors.surfaceSelected to semanticColors.onSurfaceSelected
    }
}

/** `label-accent`: a character theme's label colour, `on-surface` in a quiet theme. */
@Composable
internal fun appLabelColor(): Color =
    UnifiedReaderThemeTokens.character.labelColor.takeOrElse { MaterialTheme.colorScheme.onSurface }

internal fun groundTile(pattern: AppGroundPattern, color: Color, density: Density): ImageBitmap {
    fun px(dp: Float) = with(density) { dp.dp.toPx() }
    val (width, height) = when (pattern) {
        AppGroundPattern.DotGrid -> px(16f) to px(16f)
        AppGroundPattern.Scanlines -> 4f to max(2f, px(2f))
        AppGroundPattern.Grid -> px(24f) to px(24f)
        AppGroundPattern.Grain -> px(20f) to px(20f)
        AppGroundPattern.None -> 1f to 1f
    }
    val bitmap = ImageBitmap(max(1, width.roundToInt()), max(1, height.roundToInt()))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        drawTile(pattern, color, ::px)
    }
    return bitmap
}

private fun DrawScope.drawTile(pattern: AppGroundPattern, color: Color, px: (Float) -> Float) {
    when (pattern) {
        AppGroundPattern.DotGrid -> drawCircle(color, radius = px(1f), center = center)
        AppGroundPattern.Scanlines -> drawRect(color, size = Size(size.width, 1f))
        AppGroundPattern.Grid -> {
            drawRect(color, size = Size(size.width, 1f))
            drawRect(color, topLeft = Offset(0f, 1f), size = Size(1f, size.height - 1f))
        }
        AppGroundPattern.Grain -> GrainSpecks.forEach { (x, y, radius) ->
            drawCircle(color, radius = px(radius), center = Offset(x * size.width, y * size.height))
        }
        AppGroundPattern.None -> Unit
    }
}

/** Fixed specks (x and y as fractions of the tile, radius in dp), so the grain never shifts. */
private val GrainSpecks = listOf(
    Triple(0.07f, 0.12f, 0.6f), Triple(0.31f, 0.05f, 0.5f), Triple(0.58f, 0.19f, 0.7f),
    Triple(0.86f, 0.09f, 0.5f), Triple(0.18f, 0.38f, 0.7f), Triple(0.44f, 0.31f, 0.5f),
    Triple(0.71f, 0.44f, 0.6f), Triple(0.93f, 0.35f, 0.8f), Triple(0.05f, 0.63f, 0.5f),
    Triple(0.36f, 0.57f, 0.8f), Triple(0.62f, 0.69f, 0.5f), Triple(0.82f, 0.74f, 0.6f),
    Triple(0.14f, 0.86f, 0.7f), Triple(0.47f, 0.91f, 0.6f), Triple(0.75f, 0.95f, 0.5f),
)
