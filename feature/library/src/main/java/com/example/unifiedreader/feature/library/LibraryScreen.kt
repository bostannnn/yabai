package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import androidx.compose.foundation.layout.aspectRatio

@Composable
fun LibraryScreen(
    selectedSource: SourceType,
    libraries: List<Library>,
    items: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    libraryMessage: String? = null,
    onImportLocalFileClick: () -> Unit = {},
    onSourceSelected: (SourceType) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
) {
    var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.Detailed) }
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedSource.ordinal) {
            SourceType.entries.forEach { source ->
                Tab(
                    selected = source == selectedSource,
                    onClick = { onSourceSelected(source) },
                    text = { Text(text = source.displayName()) },
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = viewMode.gridCells(maxWidth = maxWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryHeader(
                        libraries = libraries,
                        selectedSource = selectedSource,
                        libraryMessage = libraryMessage,
                        viewMode = viewMode,
                        onViewModeChanged = { viewMode = it },
                        onImportLocalFileClick = onImportLocalFileClick,
                    )
                }

                if (items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyLibraryState(selectedSource = selectedSource)
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        when (viewMode) {
                            LibraryViewMode.Detailed -> DetailedItemCard(
                                item = item,
                                progress = progressByItemId[item.id],
                                onClick = { onItemSelected(item) },
                                onDeleteRequested = itemDeleteRequest(item) { deleteCandidate = it },
                            )
                            LibraryViewMode.Grid -> GridCoverCard(
                                item = item,
                                onClick = { onItemSelected(item) },
                                onDeleteRequested = itemDeleteRequest(item) { deleteCandidate = it },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(text = "Remove imported file?") },
            text = {
                Text(
                    text = "This removes ${item.libraryDisplayLabel()} from your library and clears local reading state.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteImportedItem(item)
                        deleteCandidate = null
                    },
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun EmptyLibraryState(
    selectedSource: SourceType,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "No items yet for ${selectedSource.displayName()}")
    }
}

@Composable
private fun LibraryHeader(
    libraries: List<Library>,
    selectedSource: SourceType,
    libraryMessage: String?,
    viewMode: LibraryViewMode,
    onViewModeChanged: (LibraryViewMode) -> Unit,
    onImportLocalFileClick: () -> Unit,
) {
    val count = libraries.count { it.sourceType == selectedSource }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = selectedSource.displayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$count local libraries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ViewModeToggle(
                    selectedMode = viewMode,
                    onViewModeChanged = onViewModeChanged,
                )
            }
            if (selectedSource == SourceType.KAVITA || selectedSource == SourceType.GRIMMORY) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    onClick = onImportLocalFileClick,
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import local file",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        libraryMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun itemDeleteRequest(
    item: ReadableItem,
    onDeleteCandidateSelected: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.isLocalImport) {
    { onDeleteCandidateSelected(item) }
} else {
    null
}

@Composable
private fun DetailedItemCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    onClick: () -> Unit,
    onDeleteRequested: (() -> Unit)?,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            val coverWidth = (maxWidth * 0.36f).coerceIn(72.dp, 104.dp)
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LibraryCover(
                        item = item,
                        modifier = Modifier
                            .width(coverWidth)
                            .aspectRatio(2f / 3f),
                    )
                    DetailedItemTextBlock(
                        item = item,
                        progress = progress,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (onDeleteRequested != null) 28.dp else 0.dp),
                    )
                }
                onDeleteRequested?.let {
                    ItemOverflowMenu(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onDeleteRequested = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCoverCard(
    item: ReadableItem,
    onClick: () -> Unit,
    onDeleteRequested: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        LibraryCover(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        onDeleteRequested?.let {
            ItemOverflowMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                onDeleteRequested = it,
            )
        }
    }
}

@Composable
private fun ItemOverflowMenu(
    modifier: Modifier = Modifier,
    onDeleteRequested: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More actions",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "Remove imported file") },
                onClick = {
                    expanded = false
                    onDeleteRequested()
                },
            )
        }
    }
}

@Composable
private fun LibraryCover(
    item: ReadableItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF425E6B),
                        Color(0xFF141A20),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (item.coverUrl != null) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = formatBadgeText(item.format),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ViewModeToggle(
    selectedMode: LibraryViewMode,
    onViewModeChanged: (LibraryViewMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryViewMode.entries.forEach { mode ->
            AssistChip(
                onClick = { onViewModeChanged(mode) },
                label = { Text(text = mode.label) },
                leadingIcon = if (mode == selectedMode) {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ProgressSummary(
    progress: ItemProgressSummary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.92f),
        )
    }
}

data class ItemProgressSummary(
    val label: String,
    val fraction: Float,
    val pageCount: Int? = null,
)

private enum class LibraryViewMode(
    val label: String,
) {
    Detailed("Detailed"),
    Grid("Grid");

    fun gridCells(maxWidth: androidx.compose.ui.unit.Dp): GridCells = when (this) {
        Detailed -> if (maxWidth < 700.dp) {
            GridCells.Fixed(1)
        } else {
            GridCells.Fixed(2)
        }
        Grid -> GridCells.Adaptive(minSize = 140.dp)
    }
}

@Composable
private fun DetailedItemTextBlock(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val primaryText = itemPrimaryText(item)
        val secondaryText = itemSecondaryText(item)
        val tertiaryText = itemTertiaryText(item)
        val pageCountText = itemPageCountText(item, progress)

        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = itemPrimaryMaxLines(item),
            overflow = TextOverflow.Ellipsis,
        )

        secondaryText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        tertiaryText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        pageCountText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        progress?.let {
            Box(modifier = Modifier.padding(top = 2.dp)) {
                ProgressSummary(progress = it)
            }
        }
    }
}

private fun itemPrimaryMaxLines(
    item: ReadableItem,
): Int = when (item.kind) {
    ReadableItemKind.BOOK -> 2
    ReadableItemKind.COMIC -> if (item.comicMetadata?.series.isNullOrBlank()) 2 else 1
}

private fun itemPrimaryText(
    item: ReadableItem,
): String = item.libraryPrimaryText()

private fun itemSecondaryText(
    item: ReadableItem,
): String? = item.librarySecondaryText()

private fun itemTertiaryText(
    item: ReadableItem,
): String? = when (item.kind) {
    ReadableItemKind.BOOK -> null
    ReadableItemKind.COMIC -> buildComicVolumeIssueLabel(item.comicMetadata)
}

private fun itemPageCountText(
    item: ReadableItem,
    progress: ItemProgressSummary?,
): String? {
    val pageCount = item.pageCount ?: progress?.pageCount
    return pageCount?.takeIf { it > 0 }?.let { count -> "$count pages" }
}

private fun buildComicVolumeIssueLabel(
    metadata: ComicMetadata?,
): String? {
    if (metadata == null) return null
    val parts = buildList {
        metadata.volume?.takeIf(String::isNotBlank)?.let { add("Vol. $it") }
        metadata.issueNumber?.takeIf(String::isNotBlank)?.let { add("Issue $it") }
    }
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(" • ")
}

private fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.GRIMMORY -> "Grimmory"
}

private fun formatBadgeText(formatType: FormatType): String = when (formatType) {
    FormatType.CBZ -> "CBZ"
    FormatType.CBR -> "CBR"
    FormatType.EPUB -> "EPUB"
    FormatType.PDF -> ""
}
