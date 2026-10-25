package com.example.unifiedreader.feature.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppNavigationRail
import com.example.unifiedreader.ui.components.AppNavigationRailItem
import com.example.unifiedreader.ui.components.AppWindowHeight
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowHeight
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/**
 * Expanded windows navigate from the rail instead of the drawer; a short one (a phone on its
 * side) keeps the drawer, since six destinations would not fit down its side.
 */
@Composable
fun sourceNavigationUsesRail(): Boolean =
    currentAppWindowWidth() == AppWindowWidth.Expanded && currentAppWindowHeight() != AppWindowHeight.Compact

/**
 * The drawer's destinations as a rail: the sources in the drawer order (the connected servers
 * and Local), "Add" while a server is not connected, then Journal and Settings at the bottom.
 */
@Composable
fun SourceNavigationRail(
    sourceOrder: List<SourceType>,
    selectedDestination: SourceNavigationDestination,
    onDestinationSelected: (SourceNavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** Something waiting in Settings, such as the version of an update: a dot on its icon. */
    settingsBadge: String? = null,
    /** "Add" after the sources, while a server is not connected. */
    showAddSource: Boolean = false,
) {
    AppNavigationRail(
        modifier = modifier,
        footer = {
            AppNavigationRailItem(
                label = "Journal",
                icon = AppIcons.History,
                selected = selectedDestination == SourceNavigationDestination.Journal,
                onClick = { onDestinationSelected(SourceNavigationDestination.Journal) },
            )
            AppNavigationRailItem(
                label = "Settings",
                icon = AppIcons.Settings,
                selected = selectedDestination == SourceNavigationDestination.Settings,
                onClick = { onDestinationSelected(SourceNavigationDestination.Settings) },
                badged = settingsBadge != null,
                badgeDescription = settingsBadge?.let { version -> "Update $version available" },
            )
        },
    ) {
        sourceOrder.forEach { sourceType ->
            AppNavigationRailItem(
                label = sourceType.displayName(),
                icon = sourceType.navigationIcon(),
                selected = selectedDestination == SourceNavigationDestination.Source(sourceType),
                onClick = { onDestinationSelected(SourceNavigationDestination.Source(sourceType)) },
            )
        }
        if (showAddSource) {
            AppNavigationRailItem(
                label = "Add",
                icon = AppIcons.Add,
                selected = false,
                onClick = { onDestinationSelected(SourceNavigationDestination.AddSource) },
            )
        }
    }
}
