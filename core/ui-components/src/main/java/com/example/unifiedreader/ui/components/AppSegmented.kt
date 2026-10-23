package com.example.unifiedreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** The scope of an [AppSegmentedRow]'s content: every [AppSegment] in it takes an equal share of the row. */
interface AppSegmentedRowScope : RowScope

/**
 * A single-choice row: a pill track holding [AppSegment]s, the selected one filled. Give it a
 * width (`fillMaxWidth()`, a control width) or let a menu size it; segments split it equally.
 */
@Composable
fun AppSegmentedRow(
    modifier: Modifier = Modifier,
    content: @Composable AppSegmentedRowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
            .padding(AppSegmentedDefaults.trackPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSegmentedDefaults.segmentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scope = remember(this) { AppSegmentedRowScopeImpl(this) }
        scope.content()
    }
}

private class AppSegmentedRowScopeImpl(rowScope: RowScope) : AppSegmentedRowScope, RowScope by rowScope

/** One option in an [AppSegmentedRow]: a 34 dp pill, filled while [selected]. */
@Composable
fun AppSegmentedRowScope.AppSegment(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val containerColor by animateColorAsState(
        targetValue = if (selected) semanticColors.surfaceSelected else Color.Transparent,
        animationSpec = AppMotion.effects(),
        label = "appSegmentContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) semanticColors.onSurfaceSelected else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AppMotion.effects(),
        label = "appSegmentContent",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .weight(1f)
            .height(AppSegmentedDefaults.segmentHeight)
            .clip(CircleShape)
            .background(containerColor)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = AppSegmentedDefaults.segmentHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = AppSegmentedDefaults.disabledAlpha),
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            if (icon == null) {
                label()
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon()
                    label()
                }
            }
        }
    }
}

object AppSegmentedDefaults {
    val trackPadding: Dp = 3.dp
    val segmentSpacing: Dp = 2.dp
    val segmentHeight: Dp = 34.dp
    val segmentHorizontalPadding: Dp = 12.dp
    const val disabledAlpha: Float = 0.38f
}
