package com.example.unifiedreader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
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
            if (compactToolbar) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    LibrarySearchField(
                        query = searchQuery,
                        onQueryChanged = onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LibraryTopBarTitle(
                        selectedSource = selectedSource,
                        sourceConnectivity = sourceConnectivity,
                        seriesTitle = seriesTitle,
                        onSourceConnectivityClick = onSourceConnectivityClick,
                        onDrawerClick = onDrawerClick,
                        onLeaveSeries = onLeaveSeries,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .widthIn(max = 320.dp),
                    )
                    LibrarySearchField(
                        query = searchQuery,
                        onQueryChanged = onSearchQueryChanged,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(min = 280.dp, max = 340.dp),
                    )
                    LibraryToolbarActions(
                        refreshState = refreshState,
                        onRefreshClick = onRefreshClick,
                        downloadBadgeCount = downloadBadgeCount,
                        onDownloadsClick = onDownloadsClick,
                        downloadSelectionEnabled = downloadSelectionState.selectableCount > 0,
                        onDownloadSelectionClick = { onDownloadSelectionModeChanged(true) },
                        onImportLocalFileClick = onImportLocalFileClick,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
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

@Composable
fun SettingsTopBar(
    onDrawerClick: () -> Unit,
    onImportLocalFileClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
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
        IconButton(
            onClick = onDrawerClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation drawer",
            )
        }
        if (seriesTitle != null) {
            IconButton(
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
    Surface(
        modifier = clickModifier,
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
        IconButton(
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
        )
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val active = query.isNotEmpty()
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .height(presentation.searchHeight)
            .testTag(LibrarySearchFieldTag),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(semanticColors.sourceAccent),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(presentation.controlCornerRadius),
                color = if (active) {
                    colors.surfaceContainerHigh.copy(alpha = 0.92f)
                } else {
                    colors.surfaceContainerHigh.copy(alpha = 0.52f)
                },
                border = BorderStroke(
                    1.dp,
                    if (active) {
                        semanticColors.sourceAccent.copy(alpha = 0.82f)
                    } else {
                        colors.outlineVariant.copy(alpha = 0.36f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (active) semanticColors.sourceAccent else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    )
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
            IconButton(
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
                    CircularProgressIndicator(
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
            IconButton(onClick = handler) {
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
            IconButton(
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
        IconButton(onClick = onImportLocalFileClick) {
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
        IconButton(
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
        IconButton(
            enabled = state.selectedCount < state.selectableCount,
            onClick = onSelectAll,
        ) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = "Select all downloadable items",
            )
        }
        IconButton(
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

private const val LibrarySearchFieldTag = "library-search-field"
