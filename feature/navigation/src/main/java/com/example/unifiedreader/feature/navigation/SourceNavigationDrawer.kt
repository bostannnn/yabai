package com.example.unifiedreader.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName

sealed interface SourceNavigationDestination {
    data class Source(
        val sourceType: SourceType,
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
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 320.dp),
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
                            NavigationDrawerItem(
                                label = { Text(text = sourceType.displayName()) },
                                selected = selectedDestination ==
                                    SourceNavigationDestination.Source(sourceType),
                                onClick = {
                                    onDestinationSelected(
                                        SourceNavigationDestination.Source(sourceType),
                                    )
                                },
                                icon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = sourceType.drawerIcon(),
                                        contentDescription = null,
                                    )
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    NavigationDrawerItem(
                        label = { Text(text = "Journal") },
                        selected = selectedDestination == SourceNavigationDestination.Journal,
                        onClick = { onDestinationSelected(SourceNavigationDestination.Journal) },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NavigationDrawerItem(
                        label = { Text(text = "Settings") },
                        selected = selectedDestination == SourceNavigationDestination.Settings,
                        onClick = { onDestinationSelected(SourceNavigationDestination.Settings) },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        content = content,
    )
}

private fun SourceType.drawerIcon(): ImageVector = when (this) {
    SourceType.KAVITA -> Icons.Default.CollectionsBookmark
    SourceType.STUMP -> Icons.AutoMirrored.Filled.MenuBook
    SourceType.GRIMMORY -> Icons.Default.Cloud
    SourceType.LOCAL -> Icons.Default.Folder
}
