package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A selectable pill for filters and sort options: 36 dp, a hairline edge, filled with the
 * selected container and no edge while [selected]. An optional [count] follows the label. It
 * shrinks a little while pressed. With [onLongClick], holding it runs that instead of [onClick]
 * (Browse's tag chips open the tag's sort sheet); [onLongClickLabel] names it for TalkBack.
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
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val longPress = if (onLongClick != null && enabled) Modifier.appChipLongPress(onLongClick, onLongClickLabel) else Modifier
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
                            text = appCount(count),
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
            .then(longPress)
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

/**
 * Watches a press before the chip does: held past the long-press timeout without moving, it runs
 * [onLongClick] and swallows the rest of the gesture, so the chip's own click never fires.
 */
private fun Modifier.appChipLongPress(onLongClick: () -> Unit, label: String?): Modifier = this
    .semantics { onLongClick(label = label) { onLongClick(); true } }
    .pointerInput(onLongClick) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Lifted or moved before the timeout: a tap or a scroll, left to the chip and its list.
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var moved = 0f
                var ended = false
                while (!ended) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
                    moved += change?.positionChange()?.getDistance() ?: 0f
                    ended = change == null || !change.pressed || moved > viewConfiguration.touchSlop
                }
                true
            }
            if (released == null) {
                onLongClick()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { change -> change.consume() }
                } while (event.changes.any { change -> change.pressed })
            }
        }
    }
