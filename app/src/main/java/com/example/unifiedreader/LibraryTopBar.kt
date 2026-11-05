package com.example.unifiedreader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.downloads.DownloadsButtonState
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarSearchField
import com.example.unifiedreader.feature.library.LibraryRefreshAction
import com.example.unifiedreader.feature.library.LibrarySelectionTopBar
import com.example.unifiedreader.feature.library.LibraryTopBarMoreMenu
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppStatusPill
import com.example.unifiedreader.ui.components.AppStatusTone
import com.example.unifiedreader.ui.components.AppTopBarCentredRow
import com.example.unifiedreader.ui.components.appBadgeDot
import com.example.unifiedreader.ui.components.appProgressRing
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The library's bar: the menu button (back inside a series or a Kavita list), the search field
 * centred on the bar, Downloads and "•••". The field is the door to the search panel, which opens
 * out of it. The field names what it searches ("Search Kavita",
 * "Search Neon Ronin") and carries the source's connection badge while it is not online. The
 * glass shows once the grid has scrolled under the bar; while books are selected the bar is the
 * selection bar.
 */
@Composable
fun LibraryTopBar(
    selectedSource: SourceType,
    sourceConnectivity: SourceConnectivityState,
    searchQuery: String,
    // The open series, Kavita list or collection: the field searches it and the bar offers back.
    contextTitle: String?,
    downloadSelectionState: LibraryDownloadSelectionTopBarState = LibraryDownloadSelectionTopBarState(),
    refreshState: LibraryRefreshTopBarState = LibraryRefreshTopBarState(),
    onSourceConnectivityClick: () -> Unit,
    // Null where the rail shows: the bar has no menu button then.
    onDrawerClick: (() -> Unit)?,
    onSearchQueryChanged: (String) -> Unit,
    // The field is the door to the search panel; it reports where it is, for the panel to open out of.
    onSearchFieldClick: () -> Unit = {},
    onSearchFieldBounds: (Rect) -> Unit = {},
    onRefreshClick: () -> Unit = {},
    downloadsButtonState: DownloadsButtonState,
    onDownloadsClick: () -> Unit,
    onDownloadSelectionModeChanged: (Boolean) -> Unit = {},
    onSelectAllDownloadItems: () -> Unit = {},
    onDownloadSelectedItems: () -> Unit = {},
    onAddSelectedToList: (() -> Unit)? = null,
    onMarkSelectedRead: ((Boolean) -> Unit)? = null,
    // Picking books for a list: its name, and Done.
    addingToList: String? = null,
    onDoneAdding: () -> Unit = {},
    onImportLocalFileClick: () -> Unit,
    onLeaveSeries: () -> Unit,
    // Whether the grid has scrolled under the bar.
    scrolled: Boolean = false,
    // The "•••" menu's long-press tip, until the first selection; only server sources select.
    showSelectionTip: Boolean = false,
) {
    val selecting = downloadSelectionState.isSelectionMode
    val focusManager = LocalFocusManager.current
    // Back clears a query before it leaves the screen.
    BackHandler(enabled = searchQuery.isNotBlank() && !selecting) {
        onSearchQueryChanged("")
        focusManager.clearFocus()
    }
    ThemedTopBarContainer(
        surfaceVisible = scrolled || selecting,
        containerColor = if (selecting) UnifiedReaderThemeTokens.colors.surfaceSelected else null,
    ) {
        // The selection bar fades in over the search bar and back, with the bar's color.
        AnimatedContent(
            targetState = selecting,
            transitionSpec = { fadeIn(AppMotion.effects()) togetherWith fadeOut(AppMotion.effects()) },
            contentAlignment = Alignment.Center,
            label = "libraryTopBarSelection",
        ) { showSelectionBar ->
            if (showSelectionBar) {
                LibrarySelectionTopBar(
                    selectedCount = downloadSelectionState.selectedCount,
                    selectableCount = downloadSelectionState.selectableCount,
                    onClose = { onDownloadSelectionModeChanged(false) },
                    onSelectAll = onSelectAllDownloadItems,
                    onDownload = onDownloadSelectedItems,
                    onAddToList = onAddSelectedToList,
                    onMarkRead = onMarkSelectedRead,
                    addingTo = addingToList,
                    onDoneAdding = onDoneAdding,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@AnimatedContent
            }
            // The field is centred on the bar, so it shares one centre with the page under it.
            AppTopBarCentredRow(
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    when {
                        contextTitle != null -> AppIconButton(onClick = onLeaveSeries) {
                            Icon(imageVector = AppIcons.ArrowBack, contentDescription = "Back to source library")
                        }
                        onDrawerClick != null -> DrawerMenuButton(onClick = onDrawerClick)
                    }
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloadsButton(state = downloadsButtonState, onClick = onDownloadsClick)
                        LibraryTopBarMoreMenu(
                            onImportFile = onImportLocalFileClick,
                            refresh = if (refreshState.isVisible) {
                                LibraryRefreshAction(
                                    enabled = refreshState.isEnabled,
                                    refreshing = refreshState.isRefreshing,
                                    onRefresh = onRefreshClick,
                                )
                            } else {
                                null
                            },
                            showSelectionTip = showSelectionTip && selectedSource != SourceType.LOCAL,
                        )
                    }
                },
            ) {
                LibraryBrowseTopBarSearchField(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    placeholder = "Search ${contextTitle ?: selectedSource.displayName()}",
                    trailingContent = if (searchQuery.isEmpty()) {
                        {
                            SourceConnectivityBadge(
                                sourceType = selectedSource,
                                state = sourceConnectivity,
                                onClick = onSourceConnectivityClick,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = onSearchFieldClick,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        onSearchFieldBounds(coordinates.boundsInWindow())
                    },
                )
            }
        }
    }
}

data class LibraryDownloadSelectionTopBarState(
    val isSelectionMode: Boolean = false,
    val selectedCount: Int = 0,
    val selectableCount: Int = 0,
)

data class LibraryRefreshTopBarState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false,
    val isRefreshing: Boolean = false,
)

/**
 * A server's connection inside the search field: "Offline" (a tap retries) or "Checking". Online
 * is the normal case and shows nothing; Local has no connection.
 */
@Composable
private fun SourceConnectivityBadge(
    sourceType: SourceType,
    state: SourceConnectivityState,
    onClick: () -> Unit,
) {
    if (sourceType == SourceType.LOCAL) return
    val offline = when (state) {
        SourceConnectivityState.Offline -> true
        SourceConnectivityState.Checking -> false
        SourceConnectivityState.Online,
        SourceConnectivityState.Unknown -> return
    }
    // Offline is a state, not an error: a dot and a word like Checking (design system, AppStatusPill).
    AppStatusPill(
        label = if (offline) "Offline" else "Checking",
        tone = AppStatusTone.Neutral,
        modifier = Modifier
            .padding(end = 8.dp)
            .then(if (offline) Modifier.clickable(onClickLabel = "Retry connection", onClick = onClick) else Modifier)
            .semantics {
                contentDescription = if (offline) {
                    "${sourceType.displayName()} connection is offline. Downloaded and cached content only. Retry connection."
                } else {
                    "${sourceType.displayName()} connection is checking"
                }
            },
    )
}

@Composable
private fun DownloadsButton(
    state: DownloadsButtonState,
    onClick: () -> Unit,
) {
    val downloading = state.activeCount > 0
    AppIconButton(
        onClick = onClick,
        modifier = if (downloading) Modifier.appProgressRing(state.progress) else Modifier,
    ) {
        Icon(
            imageVector = AppIcons.Download,
            contentDescription = when {
                downloading -> "Downloads, ${state.activeCount} downloading"
                state.hasFailed -> "Downloads, a download failed"
                else -> "Downloads"
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.appBadgeDot(
                visible = !downloading && state.hasFailed,
                color = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

