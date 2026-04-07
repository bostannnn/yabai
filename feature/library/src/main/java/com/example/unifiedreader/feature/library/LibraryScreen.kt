package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.authorFacetKey
import com.example.unifiedreader.core.model.libraryAlphabetKey
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
    filterState: LibraryFilterState = LibraryFilterState(),
    filterFacets: LibraryFilterFacets = LibraryFilterFacets(),
    sortMode: LibrarySortMode = LibrarySortMode.DATE_ADDED,
    onFilterStateChanged: (LibraryFilterState) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSortModeChanged: (LibrarySortMode) -> Unit = {},
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
) {
    var deleteCandidate by remember { mutableStateOf<ReadableItem?>(null) }
    var openFilterPanel by rememberSaveable { mutableStateOf<LibraryFilterPanel?>(null) }
    var selectedSortLetter by rememberSaveable(sortMode) { mutableStateOf<String?>(null) }
    val alphabetRailVisible = sortMode.usesAlphabetRail && items.isNotEmpty()
    val sortLetters = remember(items, sortMode) {
        if (!sortMode.usesAlphabetRail) {
            emptyList()
        } else {
            items.mapNotNull { item -> item.sortFilterLetter(sortMode) }
                .distinct()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
    }
    val effectiveSortLetter = selectedSortLetter?.takeIf { it in sortLetters }
    val visibleItems = remember(items, sortMode, effectiveSortLetter) {
        effectiveSortLetter?.let { letter ->
            items.filter { item -> item.sortFilterLetter(sortMode) == letter }
        } ?: items
    }

    LaunchedEffect(selectedSortLetter, effectiveSortLetter) {
        if (selectedSortLetter != effectiveSortLetter) {
            selectedSortLetter = effectiveSortLetter
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = viewMode.gridCells(maxWidth = maxWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = if (alphabetRailVisible) 56.dp else 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            libraryMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryStatusMessage(message = message)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFilterSummary(
                    filterState = filterState,
                    filterFacets = filterFacets,
                    sortMode = sortMode,
                    onOpenFilterPanel = { openFilterPanel = it },
                    onClearFilters = onClearFilters,
                    onSortModeChanged = {
                        selectedSortLetter = null
                        onSortModeChanged(it)
                    },
                )
            }

            if (visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibraryState(
                        selectedSource = selectedSource,
                        filtersActive = !filterState.isEmpty,
                    )
                }
            } else {
                items(visibleItems, key = { it.id }) { item ->
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
        if (alphabetRailVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                AlphabetRail(
                    letters = sortLetters,
                    selectedLetter = effectiveSortLetter,
                    onLetterSelected = { letter ->
                        selectedSortLetter = if (effectiveSortLetter == letter) null else letter
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
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

    openFilterPanel?.let { panel ->
        LibraryFilterPanelDialog(
            panel = panel,
            filterState = filterState,
            filterFacets = filterFacets,
            onFilterStateChanged = onFilterStateChanged,
            onDismiss = { openFilterPanel = null },
        )
    }
}

@Composable
private fun EmptyLibraryState(
    selectedSource: SourceType,
    filtersActive: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (filtersActive) {
                "No items match these filters"
            } else {
                "No items yet for ${selectedSource.displayName()}"
            },
        )
    }
}

@Composable
private fun LibraryFilterSummary(
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    sortMode: LibrarySortMode,
    onOpenFilterPanel: (LibraryFilterPanel) -> Unit,
    onClearFilters: () -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryFacetChip(
                label = "Format",
                count = filterState.formats.size,
                enabled = filterFacets.formats.isNotEmpty() || filterState.formats.isNotEmpty(),
                onClick = { onOpenFilterPanel(LibraryFilterPanel.Format) },
            )
            LibraryFacetChip(
                label = "Author",
                count = filterState.authors.size,
                enabled = filterFacets.authors.isNotEmpty() || filterState.authors.isNotEmpty(),
                onClick = { onOpenFilterPanel(LibraryFilterPanel.Author) },
            )
            LibraryFacetChip(
                label = "Series",
                count = filterState.series.size,
                enabled = filterFacets.series.isNotEmpty() || filterState.series.isNotEmpty(),
                onClick = { onOpenFilterPanel(LibraryFilterPanel.Series) },
            )
            if (!filterState.isEmpty) {
                TextButton(onClick = onClearFilters) {
                    Text(text = "Clear")
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        LibrarySortSelector(
            sortMode = sortMode,
            onSortModeChanged = onSortModeChanged,
        )
    }
}

@Composable
private fun LibrarySortSelector(
    sortMode: LibrarySortMode,
    onSortModeChanged: (LibrarySortMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = sortMode != LibrarySortMode.DATE_ADDED,
            onClick = { expanded = true },
            label = {
                Text(
                    text = "Sort: ${sortMode.label}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibrarySortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.label,
                            fontWeight = if (mode == sortMode) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortModeChanged(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryFacetChip(
    label: String,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = count > 0,
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(
                text = if (count > 0) "$label ($count)" else label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun LibraryFilterPanelDialog(
    panel: LibraryFilterPanel,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    when (panel) {
        LibraryFilterPanel.Format -> CompactFacetDialog(
            title = "Format",
            hasActiveFilters = filterState.formats.isNotEmpty(),
            onClearFilters = {
                onFilterStateChanged(filterState.copy(formats = emptySet()))
            },
            onDismiss = onDismiss,
        ) {
            filterFacets.formats.forEach { format ->
                FilterToggleChip(
                    label = format.name,
                    selected = format in filterState.formats,
                    onClick = {
                        onFilterStateChanged(
                            filterState.copy(
                                formats = filterState.formats.toggle(format),
                            ),
                        )
                    },
                )
            }
        }

        LibraryFilterPanel.Author -> SearchableTextFacetDialog(
            title = "Authors",
            values = filterFacets.authors,
            selectedValues = filterState.authors,
            onToggleValue = { author ->
                onFilterStateChanged(
                    filterState.copy(authors = filterState.authors.toggle(author)),
                )
            },
            hasActiveFilters = filterState.authors.isNotEmpty(),
            onClearFilters = {
                onFilterStateChanged(filterState.copy(authors = emptySet()))
            },
            letterKey = String::authorFacetKey,
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Series -> SearchableTextFacetDialog(
            title = "Series",
            values = filterFacets.series,
            selectedValues = filterState.series,
            onToggleValue = { series ->
                onFilterStateChanged(
                    filterState.copy(series = filterState.series.toggle(series)),
                )
            },
            hasActiveFilters = filterState.series.isNotEmpty(),
            onClearFilters = {
                onFilterStateChanged(filterState.copy(series = emptySet()))
            },
            letterKey = { it },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CompactFacetDialog(
    title: String,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilterDialogScaffold(
        title = title,
        hasActiveFilters = hasActiveFilters,
        onClearFilters = onClearFilters,
        onDismiss = onDismiss,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SearchableTextFacetDialog(
    title: String,
    values: List<String>,
    selectedValues: Set<String>,
    onToggleValue: (String) -> Unit,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    letterKey: (String) -> String,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    var selectedLetter by rememberSaveable(title) { mutableStateOf<String?>(null) }
    val searchedValues = remember(values, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            values
        } else {
            values.filter { it.contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val availableLetters = remember(searchedValues, letterKey) {
        searchedValues
            .mapNotNull { it.filterLetter(letterKey) }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val effectiveLetter = selectedLetter?.takeIf { it in availableLetters }
    val visibleValues = remember(searchedValues, effectiveLetter, letterKey) {
        effectiveLetter?.let { letter ->
            searchedValues.filter { it.filterLetter(letterKey) == letter }
        } ?: searchedValues
    }
    FilterDialogScaffold(
        title = title,
        hasActiveFilters = hasActiveFilters,
        onClearFilters = onClearFilters,
        onDismiss = onDismiss,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    selectedLetter = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = "Search $title") },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (visibleValues.isEmpty()) {
                        item {
                            Text(
                                text = "No matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(visibleValues, key = { it }) { value ->
                        TextFacetRow(
                            value = value,
                            selected = value in selectedValues,
                            onClick = { onToggleValue(value) },
                        )
                    }
                }
                AlphabetRail(
                    letters = availableLetters,
                    selectedLetter = effectiveLetter,
                    onLetterSelected = { letter ->
                        selectedLetter = if (effectiveLetter == letter) null else letter
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterDialogScaffold(
    title: String,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .then(modifier),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasActiveFilters) {
                        TextButton(onClick = onClearFilters) {
                            Text(text = "Clear")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = "Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetRail(
    letters: List<String>,
    selectedLetter: String?,
    onLetterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .width(28.dp)
            .heightIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "all") {
            AlphabetRailItem(
                label = "All",
                selected = selectedLetter == null,
                onClick = { onLetterSelected(null) },
            )
        }
        items(letters, key = { it }) { letter ->
            AlphabetRailItem(
                label = letter,
                selected = selectedLetter == letter,
                onClick = { onLetterSelected(letter) },
            )
        }
    }
}

@Composable
private fun AlphabetRailItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        maxLines = 1,
    )
}

@Composable
private fun TextFacetRow(
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private enum class LibraryFilterPanel {
    Format,
    Author,
    Series,
}

@Composable
private fun FilterToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
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
    val lastReadAtEpochMillis: Long = 0L,
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

private val LibrarySortMode.label: String
    get() = when (this) {
        LibrarySortMode.DATE_ADDED -> "Date added"
        LibrarySortMode.LAST_READ -> "Last read"
        LibrarySortMode.AUTHOR -> "Author"
        LibrarySortMode.TITLE -> "Title"
        LibrarySortMode.SERIES -> "Series"
    }

private val LibrarySortMode.usesAlphabetRail: Boolean
    get() = when (this) {
        LibrarySortMode.AUTHOR,
        LibrarySortMode.TITLE,
        LibrarySortMode.SERIES -> true
        LibrarySortMode.DATE_ADDED,
        LibrarySortMode.LAST_READ -> false
    }

private fun ReadableItem.sortFilterLetter(
    sortMode: LibrarySortMode,
): String? = libraryAlphabetKey(sortMode)?.filterLetter { it }

private fun formatBadgeText(formatType: FormatType): String = when (formatType) {
    FormatType.CBZ -> "CBZ"
    FormatType.CBR -> "CBR"
    FormatType.EPUB -> "EPUB"
    FormatType.PDF -> ""
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

private fun String.filterLetter(
    letterKey: (String) -> String,
): String? {
    val first = letterKey(this).trim().firstOrNull() ?: return null
    return if (first.isLetterOrDigit()) {
        first.uppercaseChar().toString()
    } else {
        "#"
    }
}
