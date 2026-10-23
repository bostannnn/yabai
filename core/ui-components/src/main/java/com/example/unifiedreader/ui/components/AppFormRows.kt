package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One setting: a label (with optional supporting text) and its [control]. The control sits at
 * the end of the row; below [AppFormRowDefaults.stackBelowWidth], or when [stacked] is set
 * (a control too wide to share the line), it moves under the label and takes the full width,
 * which is how the phone sheets lay out.
 */
@Composable
fun AppFormRow(
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    stacked: Boolean = false,
    controlWidth: Dp = AppFormRowDefaults.controlWidth,
    control: @Composable (Modifier) -> Unit,
) {
    val contentColor = if (enabled) {
        LocalContentColor.current
    } else {
        LocalContentColor.current.copy(alpha = AppFormRowDefaults.disabledAlpha)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppFormRowDefaults.horizontalPadding,
                    vertical = AppFormRowDefaults.verticalPadding,
                ),
        ) {
            if (stacked || maxWidth < AppFormRowDefaults.stackBelowWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFormRowLabel(label = label, supportingText = supportingText)
                    control(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppFormRowLabel(
                        label = label,
                        supportingText = supportingText,
                        modifier = Modifier.weight(1f),
                    )
                    control(Modifier.width(controlWidth))
                }
            }
        }
    }
}

@Composable
private fun AppFormRowLabel(
    label: String,
    supportingText: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A two- or three-way setting on one line: the label and an [AppSegmentedRow] of [options].
 * Segments show no check icon, so short labels fit in the row's control width.
 */
@Composable
fun <T> AppChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    stacked: Boolean = false,
    controlWidth: Dp = AppFormRowDefaults.controlWidth,
    optionIcon: (@Composable (T) -> Unit)? = null,
) {
    AppFormRow(
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        enabled = enabled,
        stacked = stacked,
        controlWidth = controlWidth,
    ) { controlModifier ->
        AppSegmentedRow(modifier = controlModifier) {
            options.forEach { option ->
                AppSegment(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    icon = optionIcon?.let { optionIcon -> { optionIcon(option) } },
                    label = {
                        Text(
                            text = optionLabel(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

/** A continuous setting: label and current value on one line, the slider underneath. */
@Composable
fun AppSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppFormRowDefaults.horizontalPadding,
                vertical = AppFormRowDefaults.verticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

/**
 * A stepped setting: the label, then minus, the current value, plus. The [decrementIcon] and
 * [incrementIcon] slots let a text-size row show a small and a large "A".
 */
@Composable
fun AppStepperRow(
    label: String,
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    decrementIcon: @Composable () -> Unit = { Text(text = "−", style = MaterialTheme.typography.titleMedium) },
    incrementIcon: @Composable () -> Unit = { Icon(imageVector = AppIcons.Add, contentDescription = null) },
) {
    AppFormRow(
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        enabled = enabled,
        controlWidth = AppFormRowDefaults.stepperWidth,
    ) { controlModifier ->
        Row(
            modifier = controlModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppTonalIconButton(
                onClick = onDecrement,
                enabled = enabled && canDecrement,
                content = decrementIcon,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AppTonalIconButton(
                onClick = onIncrement,
                enabled = enabled && canIncrement,
                content = incrementIcon,
            )
        }
    }
}

object AppFormRowDefaults {
    val horizontalPadding: Dp = 20.dp
    val verticalPadding: Dp = 6.dp

    /** Width of the control at the end of a row (fits three short segments). */
    val controlWidth: Dp = 220.dp
    val stepperWidth: Dp = 160.dp

    /** Rows narrower than this stack the control under the label. */
    val stackBelowWidth: Dp = 360.dp
    const val disabledAlpha: Float = 0.42f
}
