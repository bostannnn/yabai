package com.example.unifiedreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.ui.theme.AppMotion

/** Shrinks the element slightly while [interactionSource] is pressed and springs it back. */
@Composable
fun Modifier.appPressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) AppMotion.pressedScale else 1f,
        animationSpec = AppMotion.spatialFast(),
        label = "appPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A pill that rounds toward a square ([AppMotion.pressedCornerRadius]) while
 * [interactionSource] is pressed: the press shape of the kit's buttons.
 */
@Composable
internal fun rememberAppPressShape(interactionSource: InteractionSource): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val toSquare by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = AppMotion.spatialFast(),
        label = "appPressShape",
    )
    return RoundedCornerShape(PillToSquareCorner(toSquare, AppMotion.pressedCornerRadius))
}

/** Half the shorter side (a pill) at 0, [squareRadius] at 1. */
@Immutable
private data class PillToSquareCorner(
    val toSquare: Float,
    val squareRadius: Dp,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val pill = shapeSize.minDimension / 2f
        val square = with(density) { squareRadius.toPx() }.coerceAtMost(pill)
        return pill + (square - pill) * toSquare
    }
}
