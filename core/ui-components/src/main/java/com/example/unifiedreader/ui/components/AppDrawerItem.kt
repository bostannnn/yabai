package com.example.unifiedreader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * One destination in the navigation drawer. The selected destination is a solid accent pill
 * on the control radius; unselected rows are muted.
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
            selectedContainerColor = semanticColors.readerAccent,
            selectedIconColor = semanticColors.onReaderAccent,
            selectedTextColor = semanticColors.onReaderAccent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
