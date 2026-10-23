package com.example.unifiedreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The navigation of expanded windows: an 88 dp column at the start edge, on the screen's own
 * background with a hairline at its end. [content] (the destinations) runs from the top and
 * scrolls when the window is short; [footer] sits at the bottom.
 */
@Composable
fun AppNavigationRail(
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                val stroke = AppNavigationRailDefaults.lineWidth.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) size.width - stroke / 2f else stroke / 2f
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
            }
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
            )
            .width(AppNavigationRailDefaults.width)
            .padding(vertical = AppNavigationRailDefaults.verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppNavigationRailDefaults.itemSpacing),
            content = content,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppNavigationRailDefaults.itemSpacing),
            content = footer,
        )
    }
}

/**
 * One destination in an [AppNavigationRail]: a 24 dp icon in a 56 x 32 dp pill over a 12 sp
 * label. The selected destination's pill is the selected container, as a selected drawer row
 * or chip is. [badged] puts a dot on the icon for something waiting there (an update behind
 * Settings), read out as [badgeDescription].
 */
@Composable
fun AppNavigationRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badged: Boolean = false,
    badgeDescription: String? = null,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) semanticColors.surfaceSelected else Color.Transparent,
        animationSpec = AppMotion.effects(),
        label = "appRailIndicator",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) semanticColors.onSurfaceSelected else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AppMotion.effects(),
        label = "appRailIcon",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AppMotion.effects(),
        label = "appRailLabel",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(AppNavigationRailDefaults.itemWidth)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = AppNavigationRailDefaults.itemVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .appPressScale(interactionSource)
                .size(AppNavigationRailDefaults.indicatorWidth, AppNavigationRailDefaults.indicatorHeight)
                .clip(CircleShape)
                .background(indicatorColor)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (badged) badgeDescription else null,
                tint = iconColor,
                modifier = Modifier
                    .size(AppNavigationRailDefaults.iconSize)
                    .appBadgeDot(visible = badged, color = MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(modifier = Modifier.height(AppNavigationRailDefaults.labelSpacing))
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

object AppNavigationRailDefaults {
    val width: Dp = 88.dp
    val itemWidth: Dp = 80.dp
    val indicatorWidth: Dp = 56.dp
    val indicatorHeight: Dp = 32.dp
    val iconSize: Dp = 24.dp
    val labelSpacing: Dp = 4.dp
    val itemVerticalPadding: Dp = 4.dp
    val itemSpacing: Dp = 4.dp

    /** The first item lines up with the top bar, which floats this far below the status bar. */
    val verticalPadding: Dp = AppTopBarDefaults.topMargin
    val lineWidth: Dp = 1.dp
}
