package com.example.unifiedreader.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.appGlass
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

sealed interface SourceNavigationDestination {
    data class Source(
        val sourceType: SourceType,
    ) : SourceNavigationDestination

    data class KavitaSection(
        val section: KavitaLibrarySection,
    ) : SourceNavigationDestination

    data object Settings : SourceNavigationDestination
    data object Journal : SourceNavigationDestination
}

@Composable
fun SourceNavigationDrawer(
    drawerState: DrawerState,
    sourceOrder: List<SourceType>,
    selectedDestination: SourceNavigationDestination,
    onDestinationSelected: (SourceNavigationDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val presentation = UnifiedReaderThemeTokens.presentation
    val drawerShape = RoundedCornerShape(presentation.cardCornerRadius)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // A floating glass panel, inset from the screen edges and the system bars.
            ModalDrawerSheet(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                    )
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .widthIn(max = 320.dp)
                    .shadow(elevation = 8.dp, shape = drawerShape, clip = false)
                    .appGlass(shape = drawerShape),
                drawerShape = drawerShape,
                drawerContainerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Text(
                            text = "Sources",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        sourceOrder.forEach { sourceType ->
                            if (sourceType == SourceType.KAVITA) {
                                KavitaNavigationGroup(
                                    selectedDestination = selectedDestination,
                                    onDestinationSelected = onDestinationSelected,
                                )
                            } else {
                                SourceNavigationItem(
                                    sourceType = sourceType,
                                    selected = selectedDestination ==
                                        SourceNavigationDestination.Source(sourceType),
                                    onDestinationSelected = onDestinationSelected,
                                )
                            }
                        }
                    }
                    AppDivider(modifier = Modifier.padding(vertical = 12.dp))
                    NavigationDrawerItem(
                        label = { Text(text = "Journal") },
                        selected = selectedDestination == SourceNavigationDestination.Journal,
                        onClick = { onDestinationSelected(SourceNavigationDestination.Journal) },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = AppIcons.History,
                                contentDescription = null,
                            )
                        },
                        colors = sourceNavigationItemColors(),
                        shape = sourceNavigationItemShape(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NavigationDrawerItem(
                        label = { Text(text = "Settings") },
                        selected = selectedDestination == SourceNavigationDestination.Settings,
                        onClick = { onDestinationSelected(SourceNavigationDestination.Settings) },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = AppIcons.Settings,
                                contentDescription = null,
                            )
                        },
                        colors = sourceNavigationItemColors(),
                        shape = sourceNavigationItemShape(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
private fun KavitaNavigationGroup(
    selectedDestination: SourceNavigationDestination,
    onDestinationSelected: (SourceNavigationDestination) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = SourceType.KAVITA.navigationIcon(),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = SourceType.KAVITA.displayName(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    KavitaLibrarySection.entries.forEach { section ->
        NavigationDrawerItem(
            label = { Text(text = section.displayName()) },
            selected = selectedDestination.isSelectedKavitaSection(section),
            onClick = {
                onDestinationSelected(
                    SourceNavigationDestination.KavitaSection(section),
                )
            },
            icon = {
                Icon(
                    imageVector = section.navigationIcon(),
                    contentDescription = null,
                )
            },
            colors = sourceNavigationItemColors(),
            shape = sourceNavigationItemShape(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
        )
    }
}

@Composable
private fun SourceNavigationItem(
    sourceType: SourceType,
    selected: Boolean,
    onDestinationSelected: (SourceNavigationDestination) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(text = sourceType.displayName()) },
        selected = selected,
        onClick = {
            onDestinationSelected(
                SourceNavigationDestination.Source(sourceType),
            )
        },
        icon = {
            Icon(
                imageVector = sourceType.navigationIcon(),
                contentDescription = null,
            )
        },
        colors = sourceNavigationItemColors(),
        shape = sourceNavigationItemShape(),
    )
}

@Composable
private fun sourceNavigationItemColors() = NavigationDrawerItemDefaults.colors(
    // The selected destination is a solid accent pill, as in the approved mock.
    selectedContainerColor = UnifiedReaderThemeTokens.colors.readerAccent,
    selectedIconColor = UnifiedReaderThemeTokens.colors.onReaderAccent,
    selectedTextColor = UnifiedReaderThemeTokens.colors.onReaderAccent,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun sourceNavigationItemShape() = RoundedCornerShape(
    UnifiedReaderThemeTokens.presentation.controlCornerRadius,
)

private fun SourceNavigationDestination.isSelectedKavitaSection(
    section: KavitaLibrarySection,
): Boolean = this == SourceNavigationDestination.KavitaSection(section) ||
    (this == SourceNavigationDestination.Source(SourceType.KAVITA) && section == KavitaLibrarySection.Home)

@Composable
fun SourceType.navigationIcon(): ImageVector = when (this) {
    SourceType.KAVITA -> AppIcons.CollectionsBookmark
    SourceType.STUMP -> AppIcons.MenuBook
    SourceType.GRIMMORY -> AppIcons.Cloud
    SourceType.LOCAL -> AppIcons.Folder
}

@Composable
private fun KavitaLibrarySection.navigationIcon(): ImageVector = when (this) {
    KavitaLibrarySection.Home -> AppIcons.Home
    KavitaLibrarySection.Collections -> AppIcons.CollectionsBookmark
    KavitaLibrarySection.ReadingLists -> AppIcons.Checklist
}
