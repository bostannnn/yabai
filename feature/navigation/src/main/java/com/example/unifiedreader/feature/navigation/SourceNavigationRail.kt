package com.example.unifiedreader.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppMenuHeading
import com.example.unifiedreader.ui.components.AppNavigationRail
import com.example.unifiedreader.ui.components.AppNavigationRailDefaults
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
 * Kavita's Home, Collections and Reading lists open from a menu on its item.
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
            if (sourceType == SourceType.KAVITA) {
                KavitaRailItem(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                )
            } else {
                AppNavigationRailItem(
                    label = sourceType.displayName(),
                    icon = sourceType.navigationIcon(),
                    selected = selectedDestination == SourceNavigationDestination.Source(sourceType),
                    onClick = { onDestinationSelected(SourceNavigationDestination.Source(sourceType)) },
                )
            }
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

/** Kavita's item opens a menu of its sections beside the rail, the open one checked. */
@Composable
private fun KavitaRailItem(
    selectedDestination: SourceNavigationDestination,
    onDestinationSelected: (SourceNavigationDestination) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var itemHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box {
        AppNavigationRailItem(
            label = SourceType.KAVITA.displayName(),
            icon = SourceType.KAVITA.navigationIcon(),
            selected = selectedDestination.isKavita(),
            onClick = { menuOpen = true },
            modifier = Modifier.onSizeChanged { size -> itemHeightPx = size.height },
        )
        AppMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // Beside the rail, level with the item.
            offset = DpOffset(
                x = AppNavigationRailDefaults.itemWidth + KavitaMenuGap,
                y = -with(density) { itemHeightPx.toDp() },
            ),
        ) {
            AppMenuHeading(text = SourceType.KAVITA.displayName())
            KavitaLibrarySection.entries.forEach { section ->
                AppMenuChoiceItem(
                    text = section.displayName(),
                    selected = selectedDestination.isSelectedKavitaSection(section),
                    onClick = {
                        menuOpen = false
                        onDestinationSelected(SourceNavigationDestination.KavitaSection(section))
                    },
                )
            }
        }
    }
}

private fun SourceNavigationDestination.isKavita(): Boolean =
    this is SourceNavigationDestination.KavitaSection ||
        this == SourceNavigationDestination.Source(SourceType.KAVITA)

private val KavitaMenuGap = 8.dp
