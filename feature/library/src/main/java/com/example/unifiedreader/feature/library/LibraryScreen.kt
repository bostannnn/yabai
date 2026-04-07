package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    selectedSource: SourceType,
    items: List<ReadableItem>,
    viewMode: LibraryViewMode,
    progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    libraryMessage: String? = null,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
) {
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = viewMode.gridCells(maxWidth = maxWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            libraryMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryStatusMessage(message = message)
                }
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
private fun LibraryStatusMessage(
    message: String,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    val coverShape = RoundedCornerShape(4.dp)
    val coverBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = coverShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            )
            .clip(coverShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF425E6B),
                        Color(0xFF141A20),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = coverBorderColor,
                shape = coverShape,
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.22f),
                            0.04f to Color.White.copy(alpha = 0.08f),
                            0.11f to Color.Transparent,
                            0.88f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.12f),
                            0.20f to Color.Transparent,
                            0.82f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun ProgressSummary(
    progress: ItemProgressSummary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = progress.detailLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    val currentPage: Int? = null,
    val pageCount: Int? = null,
) {
    val percentLabel: String
        get() = "${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"

    val pageProgressLabel: String?
        get() = pageCount?.takeIf { it > 0 }?.let { count ->
            val page = currentPage
                ?: (fraction.coerceIn(0f, 1f) * count)
                    .roundToInt()
                    .coerceIn(1, count)
            "${page.coerceIn(1, count)}/$count"
        }

    val detailLabel: String
        get() = listOfNotNull(percentLabel, pageProgressLabel).joinToString(" · ")
}

enum class LibraryViewMode {
    Detailed,
    Grid;

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
    SourceType.LOCAL -> "Local"
}

private fun formatBadgeText(formatType: FormatType): String = when (formatType) {
    FormatType.CBZ -> "CBZ"
    FormatType.CBR -> "CBR"
    FormatType.EPUB -> "EPUB"
    FormatType.PDF -> ""
}
