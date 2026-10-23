package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A selectable pill for filters and sort options: 36 dp, a hairline edge, filled with the
 * selected container and no edge while [selected]. An optional [count] follows the label. It
 * shrinks a little while pressed.
 */
@Composable
fun AppChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    count: Int? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = CircleShape,
    colors: SelectableChipColors = AppChipDefaults.colors(),
    border: BorderStroke? = AppChipDefaults.border(enabled = enabled, selected = selected),
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            ProvideTextStyle(LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)) {
                if (count == null) {
                    label()
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        label()
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = LocalContentColor.current.copy(alpha = AppChipDefaults.countAlpha),
                        )
                    }
                }
            }
        },
        modifier = modifier
            .appPressScale(interactionSource)
            .height(AppChipDefaults.height),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = interactionSource,
    )
}

object AppChipDefaults {
    val height: Dp = 36.dp
    val iconSize: Dp = 18.dp
    const val countAlpha: Float = 0.75f

    /** Quiet text on a clear pill; the selected chip is the selected container. */
    @Composable
    fun colors(): SelectableChipColors {
        val semanticColors = UnifiedReaderThemeTokens.colors
        return FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = semanticColors.surfaceSelected,
            selectedLabelColor = semanticColors.onSurfaceSelected,
            selectedLeadingIconColor = semanticColors.onSurfaceSelected,
            selectedTrailingIconColor = semanticColors.onSurfaceSelected,
        )
    }

    /** A 1 dp `outlineVariant` edge, none while selected. */
    @Composable
    fun border(enabled: Boolean, selected: Boolean): BorderStroke? = if (selected) {
        null
    } else {
        FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = false,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            borderWidth = 1.dp,
        )
    }
}
