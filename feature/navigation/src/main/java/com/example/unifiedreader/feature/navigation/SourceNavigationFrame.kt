package com.example.unifiedreader.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.SourceType

/**
 * The app's navigation around [content]: the rail beside it where [sourceNavigationUsesRail],
 * the drawer over it everywhere else. The content keeps its place in the tree either way, so a
 * window that crosses the breakpoint keeps its screens' state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceNavigationFrame(
    drawerState: DrawerState,
    sourceOrder: List<SourceType>,
    selectedDestination: SourceNavigationDestination,
    onDestinationSelected: (SourceNavigationDestination) -> Unit,
    /** Something waiting in Settings, such as the version of a waiting update. */
    settingsBadge: String? = null,
    /** "Add a server", while a server is not connected. */
    showAddSource: Boolean = false,
    content: @Composable () -> Unit,
) {
    val railShown = sourceNavigationUsesRail()
    // A drawer left open closes when the window grows a rail.
    LaunchedEffect(railShown) {
        if (railShown && drawerState.currentValue != DrawerValue.Closed) drawerState.snapTo(DrawerValue.Closed)
    }
    SourceNavigationDrawer(
        drawerState = drawerState,
        sourceOrder = sourceOrder,
        selectedDestination = selectedDestination,
        onDestinationSelected = onDestinationSelected,
        settingsBadge = settingsBadge,
        showAddSource = showAddSource,
        gesturesEnabled = !railShown,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (railShown) {
                SourceNavigationRail(
                    sourceOrder = sourceOrder,
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                    settingsBadge = settingsBadge,
                    showAddSource = showAddSource,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The rail already keeps clear of the start edge's cutout and bars.
                    .then(
                        if (railShown) {
                            Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
            }
        }
    }
}
