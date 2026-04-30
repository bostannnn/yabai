package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.authorFacetKey
import com.example.unifiedreader.core.model.displayName

@Composable
internal fun EmptyLibraryState(
    selectedSource: SourceType,
    filtersActive: Boolean,
    searchActive: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                searchActive && filtersActive -> "No items match this search and these filters"
                searchActive -> "No items match this search"
                filtersActive -> "No items match these filters"
                else -> "No items yet for ${selectedSource.displayName()}"
            },
        )
    }
}

@Composable
internal fun LibraryFilterSummary(
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    sortMode: LibrarySortMode,
    gridCellSizeDp: Int,
    gridSizeOptions: List<LibraryGridSizeOption>,
    fadeReadItems: Boolean,
    showSortControl: Boolean,
    onOpenFilterPanel: (LibraryFilterPanel) -> Unit,
    onClearFilters: () -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            filterPanels.forEach { panel ->
                LibraryFacetChip(
                    label = panel.label,
                    count = panel.selectedCount(filterState),
                    enabled = panel.isEnabled(
                        filterState = filterState,
                        filterFacets = filterFacets,
                        libraryTitlesById = libraryTitlesById,
                    ),
                    onClick = { onOpenFilterPanel(panel) },
                )
            }
            if (!filterState.isEmpty) {
                TextButton(onClick = onClearFilters) {
                    Text(text = "Clear")
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        LibraryBrowseOptionsSelector(
            sortMode = sortMode,
            gridCellSizeDp = gridCellSizeDp,
            gridSizeOptions = gridSizeOptions,
            fadeReadItems = fadeReadItems,
            showSortOptions = showSortControl,
            onSortModeChanged = onSortModeChanged,
            onGridCellSizeDpChanged = onGridCellSizeDpChanged,
            onFadeReadItemsChanged = onFadeReadItemsChanged,
        )
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
internal fun LibraryFilterPanelDialog(
    panel: LibraryFilterPanel,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    when (panel) {
        LibraryFilterPanel.Format -> CompactFacetDialog(
            title = "Format",
            hasActiveFilters = filterState.formats.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(formats = emptySet())) },
            onDismiss = onDismiss,
        ) {
            filterFacets.formats.forEach { format ->
                FilterToggleChip(
                    label = format.name,
                    selected = format in filterState.formats,
                    onClick = {
                        onFilterStateChanged(filterState.copy(formats = filterState.formats.toggle(format)))
                    },
                )
            }
        }

        LibraryFilterPanel.Library -> SearchableTextFacetDialog(
            title = "Libraries",
            values = filterFacets.libraries,
            selectedValues = filterState.libraries,
            valueLabel = { libraryId -> libraryTitlesById[libraryId] ?: libraryId },
            onToggleValue = { libraryId ->
                onFilterStateChanged(filterState.copy(libraries = filterState.libraries.toggle(libraryId)))
            },
            hasActiveFilters = filterState.libraries.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(libraries = emptySet())) },
            letterKey = { libraryId -> libraryTitlesById[libraryId] ?: libraryId },
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Location -> CompactFacetDialog(
            title = "Location",
            hasActiveFilters = filterState.fileLocations.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(fileLocations = emptySet())) },
            onDismiss = onDismiss,
        ) {
            filterFacets.fileLocations.forEach { location ->
                FilterToggleChip(
                    label = location.label,
                    selected = location in filterState.fileLocations,
                    onClick = {
                        onFilterStateChanged(
                            filterState.copy(fileLocations = filterState.fileLocations.toggle(location)),
                        )
                    },
                )
            }
        }

        LibraryFilterPanel.ReadingStatus -> CompactFacetDialog(
            title = "Reading status",
            hasActiveFilters = filterState.readingStatuses.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(readingStatuses = emptySet())) },
            onDismiss = onDismiss,
        ) {
            LibraryReadingStatus.entries.forEach { status ->
                FilterToggleChip(
                    label = status.label,
                    selected = status in filterState.readingStatuses,
                    onClick = {
                        onFilterStateChanged(
                            filterState.copy(readingStatuses = filterState.readingStatuses.toggle(status)),
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
                onFilterStateChanged(filterState.copy(authors = filterState.authors.toggle(author)))
            },
            hasActiveFilters = filterState.authors.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(authors = emptySet())) },
            letterKey = String::authorFacetKey,
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Series -> SearchableTextFacetDialog(
            title = "Series",
            values = filterFacets.series,
            selectedValues = filterState.series,
            onToggleValue = { series ->
                onFilterStateChanged(filterState.copy(series = filterState.series.toggle(series)))
            },
            hasActiveFilters = filterState.series.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(series = emptySet())) },
            letterKey = { it },
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Publisher -> SearchableTextFacetDialog(
            title = "Publishers",
            values = filterFacets.publishers,
            selectedValues = filterState.publishers,
            onToggleValue = { publisher ->
                onFilterStateChanged(filterState.copy(publishers = filterState.publishers.toggle(publisher)))
            },
            hasActiveFilters = filterState.publishers.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(publishers = emptySet())) },
            letterKey = { it },
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Genre -> SearchableTextFacetDialog(
            title = "Genres",
            values = filterFacets.genres,
            selectedValues = filterState.genres,
            onToggleValue = { genre ->
                onFilterStateChanged(filterState.copy(genres = filterState.genres.toggle(genre)))
            },
            hasActiveFilters = filterState.genres.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(genres = emptySet())) },
            letterKey = { it },
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Tag -> SearchableTextFacetDialog(
            title = "Tags",
            values = filterFacets.tags,
            selectedValues = filterState.tags,
            onToggleValue = { tag ->
                onFilterStateChanged(filterState.copy(tags = filterState.tags.toggle(tag)))
            },
            hasActiveFilters = filterState.tags.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(tags = emptySet())) },
            letterKey = { it },
            onDismiss = onDismiss,
        )

        LibraryFilterPanel.Language -> SearchableTextFacetDialog(
            title = "Languages",
            values = filterFacets.languages,
            selectedValues = filterState.languages,
            onToggleValue = { language ->
                onFilterStateChanged(filterState.copy(languages = filterState.languages.toggle(language)))
            },
            hasActiveFilters = filterState.languages.isNotEmpty(),
            onClearFilters = { onFilterStateChanged(filterState.copy(languages = emptySet())) },
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
    valueLabel: (String) -> String = { it },
    onToggleValue: (String) -> Unit,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    letterKey: (String) -> String,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    var selectedLetter by rememberSaveable(title) { mutableStateOf<String?>(null) }
    val displayValues = remember(values, selectedValues, valueLabel) {
        (values + selectedValues).distinctBy { value -> valueLabel(value).lowercase() }
    }
    val searchedValues = remember(displayValues, query, valueLabel) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            displayValues
        } else {
            displayValues.filter { value -> valueLabel(value).contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val availableLetters = remember(searchedValues, letterKey, valueLabel) {
        searchedValues
            .mapNotNull { value -> valueLabel(value).filterLetter(letterKey) }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val effectiveLetter = selectedLetter?.takeIf { it in availableLetters }
    val visibleValues = remember(searchedValues, effectiveLetter, letterKey, valueLabel) {
        effectiveLetter?.let { letter ->
            searchedValues.filter { value -> valueLabel(value).filterLetter(letterKey) == letter }
        } ?: searchedValues
    }
    val availableValues = remember(values) { values.toSet() }
    val viewportHeight = searchableFacetViewportHeight(visibleValues.size)
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(viewportHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (visibleValues.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
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
                            value = valueLabel(value),
                            selected = value in selectedValues,
                            available = value in availableValues,
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
                    modifier = Modifier
                        .height(viewportHeight),
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
internal fun AlphabetRail(
    letters: List<String>,
    selectedLetter: String?,
    onLetterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .width(28.dp)
            .heightIn(
                min = SearchableFacetViewportMinHeight,
                max = SearchableFacetViewportMaxHeight,
            ),
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
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    Text(
        text = label,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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
    available: Boolean,
    onClick: () -> Unit,
) {
    val textColor = when {
        selected || available -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
            enabled = available || selected,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun searchableFacetViewportHeight(
    visibleValueCount: Int,
): androidx.compose.ui.unit.Dp {
    val rowCount = when {
        visibleValueCount <= 0 -> 1
        else -> ((visibleValueCount + 1) / 2)
    }
        .coerceIn(SearchableFacetViewportMinRows, SearchableFacetViewportMaxRows)
    return (rowCount * SearchableFacetGridRowHeight.value).dp
}

private val SearchableFacetGridRowHeight = 48.dp
private const val SearchableFacetViewportMinRows = 6
private const val SearchableFacetViewportMaxRows = 8
private val SearchableFacetViewportMinHeight = SearchableFacetGridRowHeight * SearchableFacetViewportMinRows
private val SearchableFacetViewportMaxHeight = SearchableFacetGridRowHeight * SearchableFacetViewportMaxRows

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
