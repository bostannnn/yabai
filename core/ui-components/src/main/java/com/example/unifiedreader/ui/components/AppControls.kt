package com.example.unifiedreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.AppMotion

/** A checkbox. Pass `null` for [onCheckedChange] when the enclosing row handles the click. */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

/** An on/off switch. Pass `null` for [onCheckedChange] when the enclosing row handles the click. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

/**
 * A continuous slider: an 18 dp pill track filled with the accent up to the value, and a slim
 * 4 x 28 dp handle. Material still handles the drag, the keys and the semantics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else AppSliderDefaults.disabledAlpha
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val handleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        steps = steps,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = AppSliderDefaults.handleWidth, height = AppSliderDefaults.handleHeight)
                    .background(handleColor, RoundedCornerShape(AppSliderDefaults.handleCornerRadius)),
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSliderDefaults.trackHeight),
            ) {
                val pill = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f)))
                }
                drawPath(pill, trackColor)
                clipPath(pill) {
                    val fillWidth = size.width * fraction
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(if (rtl) size.width - fillWidth else 0f, 0f),
                        size = Size(fillWidth, size.height),
                    )
                }
            }
        },
        valueRange = valueRange,
    )
}

object AppSliderDefaults {
    val trackHeight: Dp = 18.dp
    val handleWidth: Dp = 4.dp
    val handleHeight: Dp = 28.dp
    val handleCornerRadius: Dp = 3.dp
    const val disabledAlpha: Float = 0.38f
}

/**
 * A round check for things that toggle as a whole (a list row, a cover); the caller handles the
 * click and the semantics. Checked, it is filled with the accent and carries a check; unchecked,
 * it is a 2 dp ring. [ringBackdrop] fills the ring, so it reads over a cover.
 */
@Composable
fun AppRoundCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = AppRoundCheckDefaults.size,
    ringColor: Color = MaterialTheme.colorScheme.outline,
    ringBackdrop: Color = Color.Transparent,
) {
    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = AppMotion.effects(),
        label = "appRoundCheck",
    )
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val ringWidth = AppRoundCheckDefaults.ringWidth.toPx()
                if (fill < 1f) {
                    drawCircle(color = ringBackdrop)
                    drawCircle(
                        color = ringColor,
                        radius = (this.size.minDimension - ringWidth) / 2f,
                        style = Stroke(ringWidth),
                    )
                }
                if (fill > 0f) drawCircle(color = accent, alpha = fill)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (fill > 0f) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(size * 0.72f)
                    .graphicsLayer { alpha = fill },
            )
        }
    }
}

object AppRoundCheckDefaults {
    val size: Dp = 22.dp
    val ringWidth: Dp = 2.dp
}
