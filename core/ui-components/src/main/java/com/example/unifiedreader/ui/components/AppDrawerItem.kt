package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * One destination in the navigation drawer. The selected destination sits on the selected
 * container, as a selected settings row or chip does; unselected rows are muted.
 */
@Composable
fun AppDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: NavigationDrawerItemColors = AppDrawerItemDefaults.colors(),
) {
    NavigationDrawerItem(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        badge = badge,
        shape = shape,
        colors = colors,
    )
}

object AppDrawerItemDefaults {
    @Composable
    fun colors(): NavigationDrawerItemColors {
        val semanticColors = UnifiedReaderThemeTokens.colors
        return NavigationDrawerItemDefaults.colors(
            selectedContainerColor = semanticColors.surfaceSelected,
            selectedIconColor = semanticColors.onSurfaceSelected,
            selectedTextColor = semanticColors.onSurfaceSelected,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A short accent pill at the end of a drawer row, such as the version of a waiting update. On
 * the [selected] row it swaps to the row's text colour so it still stands out.
 */
@Composable
fun AppDrawerBadge(
    text: String,
    selected: Boolean = false,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    AppBadge(
        shape = CircleShape,
        color = if (selected) semanticColors.onSurfaceSelected else MaterialTheme.colorScheme.primary,
        contentColor = if (selected) semanticColors.surfaceSelected else MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**
 * A small dot at the top end of an icon, for something waiting behind the button (an update
 * behind the drawer's menu button). Nothing is drawn while [visible] is false.
 */
fun Modifier.appBadgeDot(
    visible: Boolean,
    color: Color,
): Modifier = if (!visible) {
    this
} else {
    drawWithContent {
        drawContent()
        val radius = 4.5.dp.toPx()
        drawCircle(color = color, radius = radius, center = Offset(size.width - radius / 2, radius / 2))
    }
}
