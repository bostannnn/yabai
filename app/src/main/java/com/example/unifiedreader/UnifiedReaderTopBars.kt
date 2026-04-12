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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName

@Composable
fun LibraryTopBar(
    selectedSource: SourceType,
    searchQuery: String,
    seriesTitle: String?,
    onDrawerClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    downloadBadgeCount: Int,
    onDownloadsClick: () -> Unit,
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
                    LibraryTopBarTitle(
                        selectedSource = selectedSource,
                        seriesTitle = seriesTitle,
                        onDrawerClick = onDrawerClick,
                        onLeaveSeries = onLeaveSeries,
                        modifier = Modifier.weight(1f),
                    )
                    LibraryToolbarActions(
                        downloadBadgeCount = downloadBadgeCount,
                        onDownloadsClick = onDownloadsClick,
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
                    seriesTitle = seriesTitle,
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
                    downloadBadgeCount = downloadBadgeCount,
                    onDownloadsClick = onDownloadsClick,
                    onImportLocalFileClick = onImportLocalFileClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

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
private fun LibraryTopBarTitle(
    selectedSource: SourceType,
    seriesTitle: String?,
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
                    contentDescription = "Back to series list",
                )
            }
        }
        Text(
            text = seriesTitle ?: selectedSource.displayName(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

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
    downloadBadgeCount: Int = 0,
    onDownloadsClick: (() -> Unit)? = null,
    onImportLocalFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        IconButton(onClick = onImportLocalFileClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Import local file",
            )
        }
    }
}

private const val LibrarySearchFieldTag = "library-search-field"
