package com.example.unifiedreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryViewMode

@Composable
fun LibraryTopBar(
    selectedSource: SourceType,
    libraryViewMode: LibraryViewMode,
    searchQuery: String,
    seriesTitle: String?,
    onSourceSelected: (SourceType) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    downloadBadgeCount: Int,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImportLocalFileClick: () -> Unit,
    onLeaveSeries: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
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
                    if (seriesTitle != null) {
                        SeriesContextHeader(
                            title = seriesTitle,
                            onBack = onLeaveSeries,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SourceTabs(
                            selectedSource = selectedSource,
                            topLevel = TopLevelDestination.Library,
                            onSourceSelected = onSourceSelected,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LibraryToolbarActions(
                        libraryViewMode = libraryViewMode,
                        topLevel = TopLevelDestination.Library,
                        onLibraryViewModeChanged = onLibraryViewModeChanged,
                        downloadBadgeCount = downloadBadgeCount,
                        onDownloadsClick = onDownloadsClick,
                        onSettingsClick = onSettingsClick,
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
                if (seriesTitle != null) {
                    SeriesContextHeader(
                        title = seriesTitle,
                        onBack = onLeaveSeries,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .widthIn(max = 320.dp),
                    )
                } else {
                    SourceTabs(
                        selectedSource = selectedSource,
                        topLevel = TopLevelDestination.Library,
                        onSourceSelected = onSourceSelected,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .widthIn(max = 260.dp),
                    )
                }
                LibrarySearchField(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 280.dp, max = 340.dp),
                )
                LibraryToolbarActions(
                    libraryViewMode = libraryViewMode,
                    topLevel = TopLevelDestination.Library,
                    onLibraryViewModeChanged = onLibraryViewModeChanged,
                    downloadBadgeCount = downloadBadgeCount,
                    onDownloadsClick = onDownloadsClick,
                    onSettingsClick = onSettingsClick,
                    onImportLocalFileClick = onImportLocalFileClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
fun SettingsTopBar(
    selectedSource: SourceType,
    libraryViewMode: LibraryViewMode,
    onSourceSelected: (SourceType) -> Unit,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onSettingsClick: () -> Unit,
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
            SourceTabs(
                selectedSource = selectedSource,
                topLevel = TopLevelDestination.Settings,
                onSourceSelected = onSourceSelected,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 260.dp),
            )
            LibraryToolbarActions(
                libraryViewMode = libraryViewMode,
                topLevel = TopLevelDestination.Settings,
                onLibraryViewModeChanged = onLibraryViewModeChanged,
                onSettingsClick = onSettingsClick,
                onImportLocalFileClick = onImportLocalFileClick,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun SeriesContextHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to series list",
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
private fun SourceTabs(
    selectedSource: SourceType,
    topLevel: TopLevelDestination,
    onSourceSelected: (SourceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedSource.ordinal,
        modifier = modifier,
        edgePadding = 0.dp,
    ) {
        SourceType.entries.forEach { source ->
            Tab(
                selected = topLevel == TopLevelDestination.Library && source == selectedSource,
                onClick = { onSourceSelected(source) },
                text = { Text(text = source.displayName()) },
            )
        }
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .height(40.dp)
            .testTag(LibrarySearchFieldTag),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceVariant.copy(alpha = 0.18f),
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
                        tint = colors.onSurfaceVariant,
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
    libraryViewMode: LibraryViewMode,
    topLevel: TopLevelDestination,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    downloadBadgeCount: Int = 0,
    onDownloadsClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onImportLocalFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewModeButton(
            mode = LibraryViewMode.Detailed,
            selectedMode = libraryViewMode,
            onLibraryViewModeChanged = onLibraryViewModeChanged,
        )
        ViewModeButton(
            mode = LibraryViewMode.Grid,
            selectedMode = libraryViewMode,
            onLibraryViewModeChanged = onLibraryViewModeChanged,
        )
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
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = if (topLevel == TopLevelDestination.Settings) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
private fun ViewModeButton(
    mode: LibraryViewMode,
    selectedMode: LibraryViewMode,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
) {
    val isSelected = mode == selectedMode
    IconButton(onClick = { onLibraryViewModeChanged(mode) }) {
        Icon(
            imageVector = when (mode) {
                LibraryViewMode.Detailed -> Icons.AutoMirrored.Filled.ViewList
                LibraryViewMode.Grid -> Icons.Default.GridView
            },
            contentDescription = when (mode) {
                LibraryViewMode.Detailed -> "Detailed view"
                LibraryViewMode.Grid -> "Grid view"
            },
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.STUMP -> "Stump"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> "Local"
}

private const val LibrarySearchFieldTag = "library-search-field"
