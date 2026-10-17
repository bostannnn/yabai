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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.unifiedreader.feature.library.LibraryBrowseSearchModeTopBar
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppIconButton
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
    downloadBadgeCount: Int,
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
                    downloadBadgeCount = downloadBadgeCount,
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
    downloadBadgeCount: Int,
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
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search library",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LibraryToolbarActions(
                refreshState = refreshState,
                onRefreshClick = onRefreshClick,
                downloadBadgeCount = downloadBadgeCount,
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
    onDrawerClick: () -> Unit,
    onImportLocalFileClick: () -> Unit,
) {
    ThemedTopBarContainer {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AppSectionHeader(
                title = "Settings",
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
        AppIconButton(
            onClick = onDrawerClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation drawer",
            )
        }
        if (seriesTitle != null) {
            AppIconButton(
                onClick = onLeaveSeries,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            containerColor = colors.surfaceContainerHighest.copy(alpha = 0.9f),
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
            containerColor = colors.errorContainer.copy(alpha = 0.94f),
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
        shape = RoundedCornerShape(presentation.chipCornerRadius),
        color = style.containerColor,
        border = BorderStroke(1.dp, style.borderColor.copy(alpha = 0.7f)),
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
        AppIconButton(
            onClick = onDrawerClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation drawer",
            )
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

@Composable
private fun LibraryToolbarActions(
    refreshState: LibraryRefreshTopBarState = LibraryRefreshTopBarState(),
    onRefreshClick: () -> Unit = {},
    downloadBadgeCount: Int = 0,
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
                        imageVector = Icons.Default.Refresh,
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
            AppIconButton(onClick = handler) {
                BadgedBox(
                    badge = {
                        if (downloadBadgeCount > 0) {
                            Badge {
                                Text(text = downloadBadgeCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        onDownloadSelectionClick?.let { handler ->
            AppIconButton(
                enabled = downloadSelectionEnabled,
                onClick = handler,
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
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
                imageVector = Icons.Default.Add,
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
                imageVector = Icons.Default.Close,
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
                imageVector = Icons.Default.SelectAll,
                contentDescription = "Select all downloadable items",
            )
        }
        AppIconButton(
            enabled = state.selectedCount > 0,
            onClick = onDownloadSelected,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
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
