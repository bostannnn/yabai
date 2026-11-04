package com.example.unifiedreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * A tab as a pill: text only until selected, then on `surfaceContainerHigh`. Quieter than an
 * [AppChip], so a row of tabs reads as "which list" and the chips under it as "which value".
 * The library's shelf tabs and Browse's rows on narrow windows.
 */
@Composable
fun AppTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    // The pill fades between tabs, as the segmented control and the rail do, and shrinks under a press.
    val background by animateColorAsState(
        targetValue = if (selected) colors.surfaceContainerHigh else colors.surfaceContainerHigh.copy(alpha = 0f),
        animationSpec = AppMotion.effects(),
        label = "appTabPillBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = AppMotion.effects(),
        label = "appTabPillContent",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .appPressScale(interactionSource)
            .height(AppTabPillDefaults.height)
            .clip(CircleShape)
            .background(background)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(AppTabPillDefaults.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AppTabPillDefaults.countGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            if (count != null) {
                Text(
                    text = appCount(count),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                    color = LocalContentColor.current.copy(alpha = AppTabPillDefaults.countAlpha),
                )
            }
        }
    }
}

object AppTabPillDefaults {
    val height: Dp = 36.dp
    val contentPadding = PaddingValues(horizontal = 16.dp)
    val countGap: Dp = 6.dp
    const val countAlpha: Float = 0.8f
}

/** A count as the app writes numbers: digits with a separator, "1,474". */
fun appCount(count: Int): String = String.format(Locale.US, "%,d", count)
