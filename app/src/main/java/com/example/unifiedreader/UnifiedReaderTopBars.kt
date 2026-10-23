package com.example.unifiedreader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.downloads.DownloadsButtonState
import com.example.unifiedreader.feature.library.LibraryBrowseSearchModeTopBar
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.appBadgeDot
import com.example.unifiedreader.ui.components.appProgressRing
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
fun LibraryTopBar(
    selectedSource: SourceType,
    sourceConnectivity: SourceConnectivityState,
    searchQuery: String,
    seriesTitle: String?,
    downloadSelectionState: LibraryDownloadSelectionTopBarState = LibraryDownloadSelectionTopBarState(),
    refreshState: LibraryRefreshTopBarState = LibraryRefreshTopBarState(),
    onSourceConnectivityClick: () -> Unit,
    onDrawerClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRefreshClick: () -> Unit = {},
    downloadsButtonState: DownloadsButtonState,
    onDownloadsClick: () -> Unit,
    onDownloadSelectionModeChanged: (Boolean) -> Unit = {},
    onSelectAllDownloadItems: () -> Unit = {},
    onDownloadSelectedItems: () -> Unit = {},
    onImportLocalFileClick: () -> Unit,
    onLeaveSeries: () -> Unit,
) {
    var searchModeOpen by remember(selectedSource) { mutableStateOf(searchQuery.isNotBlank()) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchModeOpen = true
        }
    }

    ThemedTopBarContainer {
        if (downloadSelectionState.isSelectionMode) {
            LibraryDownloadSelectionTopBar(
                state = downloadSelectionState,
                onCancel = { onDownloadSelectionModeChanged(false) },
                onSelectAll = onSelectAllDownloadItems,
                onDownloadSelected = onDownloadSelectedItems,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val compactToolbar = maxWidth < 760.dp
            if (searchModeOpen || searchQuery.isNotBlank()) {
                LibraryBrowseSearchModeTopBar(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    compactToolbar = compactToolbar,
                    onClose = {
                        searchModeOpen = false
                        if (searchQuery.isNotEmpty()) {
                            onSearchQueryChanged("")
                        }
                    },
                )
            } else {
                LibraryRestingTopBar(
                    selectedSource = selectedSource,
                    sourceConnectivity = sourceConnectivity,
                    seriesTitle = seriesTitle,
                    refreshState = refreshState,
                    downloadSelectionState = downloadSelectionState,
                    downloadsButtonState = downloadsButtonState,
                    onSourceConnectivityClick = onSourceConnectivityClick,
                    onDrawerClick = onDrawerClick,
                    onSearchClick = { searchModeOpen = true },
                    onRefreshClick = onRefreshClick,
                    onDownloadsClick = onDownloadsClick,
                    onDownloadSelectionModeChanged = onDownloadSelectionModeChanged,
                    onImportLocalFileClick = onImportLocalFileClick,
                    onLeaveSeries = onLeaveSeries,
                )
            }
        }
    }
}

@Composable
private fun LibraryRestingTopBar(
    selectedSource: SourceType,
    sourceConnectivity: SourceConnectivityState,
    seriesTitle: String?,
    refreshState: LibraryRefreshTopBarState,
    downloadSelectionState: LibraryDownloadSelectionTopBarState,
    downloadsButtonState: DownloadsButtonState,
    onSourceConnectivityClick: () -> Unit,
    onDrawerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onDownloadSelectionModeChanged: (Boolean) -> Unit,
    onImportLocalFileClick: () -> Unit,
    onLeaveSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryTopBarTitle(
            selectedSource = selectedSource,
            sourceConnectivity = sourceConnectivity,
            seriesTitle = seriesTitle,
            onSourceConnectivityClick = onSourceConnectivityClick,
            onDrawerClick = onDrawerClick,
            onLeaveSeries = onLeaveSeries,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = "Search library",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LibraryToolbarActions(
                refreshState = refreshState,
                onRefreshClick = onRefreshClick,
                downloadsButtonState = downloadsButtonState,
                onDownloadsClick = onDownloadsClick,
                downloadSelectionEnabled = downloadSelectionState.selectableCount > 0,
                onDownloadSelectionClick = { onDownloadSelectionModeChanged(true) },
                onImportLocalFileClick = onImportLocalFileClick,
            )
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

@Composable
fun SettingsTopBar(
    title: String,
    onDrawerClick: () -> Unit,
    onBack: (() -> Unit)?,
) {
    ThemedTopBarContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                AppIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back to settings",
                    )
                }
            } else {
                DrawerMenuButton(onClick = onDrawerClick)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
fun JournalTopBar(
    onDrawerClick: () -> Unit,
    onImportLocalFileClick: () -> Unit,
) {
    ThemedTopBarContainer {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AppSectionHeader(
                title = "Journal",
                onDrawerClick = onDrawerClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 280.dp),
            )
            LibraryToolbarActions(
                onImportLocalFileClick = onImportLocalFileClick,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ThemedTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    LibraryBrowseTopBarContainer(
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun LibraryTopBarTitle(
    selectedSource: SourceType,
    sourceConnectivity: SourceConnectivityState,
    seriesTitle: String?,
    onSourceConnectivityClick: () -> Unit,
    onDrawerClick: () -> Unit,
    onLeaveSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerMenuButton(onClick = onDrawerClick)
        if (seriesTitle != null) {
            AppIconButton(
                onClick = onLeaveSeries,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = "Back to source library",
                )
            }
        }
        Text(
            text = seriesTitle ?: selectedSource.displayName(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        SourceConnectivityIndicator(
            sourceType = selectedSource,
            state = sourceConnectivity,
            onClick = onSourceConnectivityClick,
        )
    }
}

@Composable
private fun SourceConnectivityIndicator(
    sourceType: SourceType,
    state: SourceConnectivityState,
    onClick: () -> Unit,
) {
    if (sourceType == SourceType.LOCAL) return
    val colors = MaterialTheme.colorScheme
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val style = when (state) {
        SourceConnectivityState.Unknown -> return
        SourceConnectivityState.Checking -> SourceConnectivityIndicatorStyle(
            label = "Checking",
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurfaceVariant,
            borderColor = colors.outlineVariant,
        )
        SourceConnectivityState.Online -> SourceConnectivityIndicatorStyle(
            label = "Online",
            containerColor = semanticColors.syncAccentContainer,
            contentColor = semanticColors.onSyncAccentContainer,
            borderColor = semanticColors.syncAccent,
        )
        SourceConnectivityState.Offline -> SourceConnectivityIndicatorStyle(
            label = "Offline",
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            borderColor = colors.error,
        )
    }
    Spacer(modifier = Modifier.size(2.dp))
    val clickModifier = if (state == SourceConnectivityState.Offline) {
        Modifier.clickable(onClickLabel = "Retry connection", onClick = onClick)
    } else {
        Modifier
    }
    AppBadge(
        modifier = clickModifier.semantics {
            contentDescription = when (state) {
                SourceConnectivityState.Checking -> "${sourceType.displayName()} connection is checking"
                SourceConnectivityState.Online -> "${sourceType.displayName()} connection is online"
                SourceConnectivityState.Offline ->
                    "${sourceType.displayName()} connection is offline. Downloaded and cached content only. Retry connection."
                SourceConnectivityState.Unknown -> ""
            }
        },
        color = style.containerColor,
        border = BorderStroke(1.dp, style.borderColor),

    ) {
        Text(
            text = style.label,
            color = style.contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private data class SourceConnectivityIndicatorStyle(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
)

@Composable
private fun AppSectionHeader(
    title: String,
    onDrawerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerMenuButton(onClick = onDrawerClick)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun LibraryToolbarActions(
    refreshState: LibraryRefreshTopBarState = LibraryRefreshTopBarState(),
    onRefreshClick: () -> Unit = {},
    downloadsButtonState: DownloadsButtonState = DownloadsButtonState(),
    onDownloadsClick: (() -> Unit)? = null,
    downloadSelectionEnabled: Boolean = false,
    onDownloadSelectionClick: (() -> Unit)? = null,
    onImportLocalFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (refreshState.isVisible) {
            val isRefreshEnabled = refreshState.isEnabled && !refreshState.isRefreshing
            AppIconButton(
                enabled = isRefreshEnabled,
                onClick = onRefreshClick,
                modifier = Modifier.semantics {
                    contentDescription = if (refreshState.isRefreshing) {
                        "Kavita fast sync in progress"
                    } else {
                        "Fast sync Kavita library"
                    }
                },
            ) {
                if (refreshState.isRefreshing) {
                    AppCircularProgress(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = null,
                        tint = if (isRefreshEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
        onDownloadsClick?.let { handler ->
            val downloading = downloadsButtonState.activeCount > 0
            AppIconButton(
                onClick = handler,
                modifier = if (downloading) Modifier.appProgressRing(downloadsButtonState.progress) else Modifier,
            ) {
                Icon(
                    imageVector = AppIcons.Download,
                    contentDescription = when {
                        downloading -> "Downloads, ${downloadsButtonState.activeCount} downloading"
                        downloadsButtonState.hasFailed -> "Downloads, a download failed"
                        else -> "Downloads"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.appBadgeDot(
                        visible = !downloading && downloadsButtonState.hasFailed,
                        color = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
        onDownloadSelectionClick?.let { handler ->
            AppIconButton(
                enabled = downloadSelectionEnabled,
                onClick = handler,
            ) {
                Icon(
                    imageVector = AppIcons.Checklist,
                    contentDescription = "Select items to download",
                    tint = if (downloadSelectionEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
        AppIconButton(onClick = onImportLocalFileClick) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = "Import local file",
            )
        }
    }
}

@Composable
private fun LibraryDownloadSelectionTopBar(
    state: LibraryDownloadSelectionTopBarState,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            onClick = onCancel,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "Cancel selection",
            )
        }
        Text(
            text = selectedDownloadsLabel(state.selectedCount),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        AppIconButton(
            enabled = state.selectedCount < state.selectableCount,
            onClick = onSelectAll,
        ) {
            Icon(
                imageVector = AppIcons.SelectAll,
                contentDescription = "Select all downloadable items",
            )
        }
        AppIconButton(
            enabled = state.selectedCount > 0,
            onClick = onDownloadSelected,
        ) {
            Icon(
                imageVector = AppIcons.Download,
                contentDescription = "Download selected items",
            )
        }
    }
}

private fun selectedDownloadsLabel(selectedCount: Int): String =
    if (selectedCount == 1) {
        "1 selected"
    } else {
        "$selectedCount selected"
    }

/** True while something waits in the drawer (an app update), to dot the menu button. */
val LocalDrawerButtonDot = staticCompositionLocalOf { false }

@Composable
private fun DrawerMenuButton(onClick: () -> Unit) {
    val hasDot = LocalDrawerButtonDot.current
    AppIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = AppIcons.Menu,
            contentDescription = if (hasDot) "Open navigation drawer, update available" else "Open navigation drawer",
            modifier = Modifier.appBadgeDot(visible = hasDot, color = MaterialTheme.colorScheme.primary),
        )
    }
}
