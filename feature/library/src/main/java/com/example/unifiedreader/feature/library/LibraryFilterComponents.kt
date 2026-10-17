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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern
import kotlinx.coroutines.launch

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
    availableSortModes: List<LibrarySortMode> = LibrarySortMode.entries,
    lockedSortLabel: String? = null,
    readingListScopeState: LibraryReadingListScopeState = LibraryReadingListScopeState(),
    onOpenReadingListScope: () -> Unit = {},
    onOpenFilterPanel: (LibraryFilterPanel) -> Unit,
    onClearFilters: () -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
) {
    var filtersMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val filterOptions = remember(filterPanels, filterState, filterFacets, libraryTitlesById) {
        filterPanels.map { panel ->
            LibraryFilterPanelSummary(
                panel = panel,
                selectedCount = panel.selectedCount(filterState),
                enabled = panel.isEnabled(
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                ),
            )
        }
    }
    val activeFilterOptions = filterOptions.filter { option -> option.selectedCount > 0 }
    val filtersEnabled = filterOptions.any { option -> option.enabled } || !filterState.isEmpty
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
            if (readingListScopeState.shouldShow) {
                val selectedOption = readingListScopeState.selectedOption
                LibraryFacetChip(
                    label = selectedOption?.chipLabel() ?: "Kavita lists",
                    count = 0,
                    selected = selectedOption != null,
                    showCount = false,
                    enabled = readingListScopeState.shouldShow,
                    onClick = onOpenReadingListScope,
                )
            }
            activeFilterOptions.forEach { option ->
                LibraryFacetChip(
                    label = option.panel.label,
                    count = option.selectedCount,
                    enabled = option.enabled,
                    onClick = { onOpenFilterPanel(option.panel) },
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            FilterChip(
                selected = !filterState.isEmpty,
                enabled = filtersEnabled,
                onClick = { filtersMenuExpanded = true },
                modifier = Modifier.libraryAccentChipTreatment(
                    selected = !filterState.isEmpty,
                ),
                colors = libraryAccentFilterChipColors(),
                shape = libraryAccentFilterChipShape(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(
                        text = "Filters",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            DropdownMenu(
                expanded = filtersMenuExpanded,
                onDismissRequest = { filtersMenuExpanded = false },
            ) {
                if (!filterState.isEmpty) {
                    DropdownMenuItem(
                        text = { Text(text = "Clear filters") },
                        onClick = {
                            filtersMenuExpanded = false
                            onClearFilters()
                        },
                    )
                    HorizontalDivider()
                }
                filterOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (option.selectedCount > 0) {
                                    "${option.panel.label} (${option.selectedCount})"
                                } else {
                                    option.panel.label
                                },
                            )
                        },
                        enabled = option.enabled,
                        onClick = {
                            filtersMenuExpanded = false
                            onOpenFilterPanel(option.panel)
                        },
                    )
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
            availableSortModes = availableSortModes,
            lockedSortLabel = lockedSortLabel,
            onSortModeChanged = onSortModeChanged,
            onGridCellSizeDpChanged = onGridCellSizeDpChanged,
            onFadeReadItemsChanged = onFadeReadItemsChanged,
        )
    }
}

private data class LibraryFilterPanelSummary(
    val panel: LibraryFilterPanel,
    val selectedCount: Int,
    val enabled: Boolean,
)

@Composable
private fun LibraryFacetChip(
    label: String,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = count > 0,
    showCount: Boolean = true,
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.libraryAccentChipTreatment(selected = selected),
        colors = libraryAccentFilterChipColors(),
        shape = libraryAccentFilterChipShape(),
        label = {
            Text(
                text = if (showCount && count > 0) "$label ($count)" else label,
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
internal fun LibraryReadingListScopeDialog(
    state: LibraryReadingListScopeState,
    onReadingListSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.options.isEmpty() && (state.isLoadingOptions || state.errorMessage != null)) {
        FilterDialogScaffold(
            title = "Kavita lists",
            hasActiveFilters = state.selectedOptionId != null,
            onClearFilters = {
                onReadingListSelected(null)
                onDismiss()
            },
            onDismiss = onDismiss,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = state.errorMessage ?: "Loading Kavita lists...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    SearchableKavitaScopeDialog(
        state = state,
        onReadingListSelected = onReadingListSelected,
        onDismiss = onDismiss,
    )
}

@Composable
private fun SearchableKavitaScopeDialog(
    state: LibraryReadingListScopeState,
    onReadingListSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable("kavita-lists") { mutableStateOf("") }
    val filteredOptions = remember(state.options, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            state.options
        } else {
            state.options.filter { option ->
                option.scopeLabel().contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val optionsByKind = remember(filteredOptions) {
        filteredOptions.groupBy(LibraryReadingListScopeOption::kind)
    }
    val sectionKinds = listOf(
        LibraryReadingListScopeOptionKind.Collection,
        LibraryReadingListScopeOptionKind.ReadingList,
    ).filter { kind -> optionsByKind[kind].orEmpty().isNotEmpty() }
    val viewportHeight = searchableFacetViewportHeight(filteredOptions.size)
    FilterDialogScaffold(
        title = "Kavita lists",
        hasActiveFilters = state.selectedOptionId != null,
        onClearFilters = {
            onReadingListSelected(null)
            onDismiss()
        },
        onDismiss = onDismiss,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .libraryAccentTextFieldTreatment(selected = query.isNotEmpty()),
                singleLine = true,
                shape = libraryAccentTextFieldShape(),
                label = { Text(text = "Search Kavita lists") },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (filteredOptions.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "No matches",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                sectionKinds.forEachIndexed { sectionIndex, kind ->
                    if (sectionIndex > 0) {
                        item(key = "divider-${kind.name}") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                    item(key = "header-${kind.name}") {
                        Text(
                            text = kind.sectionTitle(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    items(
                        items = optionsByKind[kind].orEmpty(),
                        key = LibraryReadingListScopeOption::id,
                    ) { option ->
                        TextFacetRow(
                            value = option.scopeLabel(),
                            selected = option.id == state.selectedOptionId,
                            available = true,
                            onClick = {
                                onReadingListSelected(
                                    if (state.selectedOptionId == option.id) {
                                        null
                                    } else {
                                        option.id
                                    },
                                )
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun LibraryReadingListScopeOption.scopeLabel(): String =
    if (itemCount > 0) {
        "$title ($itemCount)"
    } else {
        title
    }

private fun LibraryReadingListScopeOption.chipLabel(): String = when (kind) {
    LibraryReadingListScopeOptionKind.Collection -> "Collection: $title"
    LibraryReadingListScopeOptionKind.ReadingList -> "Reading list: $title"
}

private fun LibraryReadingListScopeOptionKind.sectionTitle(): String = when (this) {
    LibraryReadingListScopeOptionKind.Collection -> "Collections"
    LibraryReadingListScopeOptionKind.ReadingList -> "Reading lists"
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
    valueKey: (String) -> String = { value -> valueLabel(value).lowercase() },
    supportingMessage: String? = null,
    onToggleValue: (String) -> Unit,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    letterKey: (String) -> String,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val gridState = remember(title) { LazyGridState() }
    val displayValues = remember(values, selectedValues, valueKey, valueLabel, letterKey) {
        searchableFacetDisplayValues(
            values = values,
            selectedValues = selectedValues,
            valueKey = valueKey,
            valueLabel = valueLabel,
            letterKey = letterKey,
        )
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
        alphabetNavigationSections(
            searchedValues.mapNotNull { value -> valueLabel(value).filterLetter(letterKey) },
        )
    }
    val valueAlphabetSections = remember(searchedValues, letterKey, valueLabel) {
        searchedValues.map { value -> valueLabel(value).filterLetter(letterKey) }
    }
    val alphabetSectionStarts = remember(valueAlphabetSections) {
        alphabetSectionStarts(valueAlphabetSections)
    }
    val activeLetter by remember(gridState, valueAlphabetSections) {
        derivedStateOf {
            activeAlphabetSection(
                sectionKeys = valueAlphabetSections,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                leadingContentItemCount = 0,
            )
        }
    }
    val availableValues = remember(values) { values.toSet() }
    val viewportHeight = searchableFacetViewportHeight(searchedValues.size)
    LaunchedEffect(query) {
        gridState.scrollToItem(0)
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
            supportingMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .libraryAccentTextFieldTreatment(selected = query.isNotEmpty()),
                singleLine = true,
                shape = libraryAccentTextFieldShape(),
                label = { Text(text = "Search $title") },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    state = gridState,
                    modifier = Modifier
                        .weight(1f)
                        .height(viewportHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (searchedValues.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "No matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(searchedValues, key = { it }) { value ->
                        TextFacetRow(
                            value = valueLabel(value),
                            selected = value in selectedValues,
                            available = value in availableValues,
                            onClick = { onToggleValue(value) },
                        )
                    }
                }
                if (availableLetters.isNotEmpty()) {
                    AlphabetNavigationRail(
                        sections = availableLetters,
                        activeSection = activeLetter,
                        onSectionSelected = { section ->
                            alphabetSectionStarts[section]?.let { valueIndex ->
                                coroutineScope.launch {
                                    gridState.scrollToItem(valueIndex)
                                }
                            }
                        },
                        modifier = Modifier.height(viewportHeight),
                    )
                }
            }
        }
    }
}

internal fun searchableFacetDisplayValues(
    values: List<String>,
    selectedValues: Set<String>,
    valueKey: (String) -> String,
    valueLabel: (String) -> String = { it },
    letterKey: (String) -> String = { it },
): List<String> = (values + selectedValues)
    .distinctBy(valueKey)
    .sortedWith(searchableFacetDisplayComparator(valueLabel, letterKey))

private fun searchableFacetDisplayComparator(
    valueLabel: (String) -> String,
    letterKey: (String) -> String,
): Comparator<String> = Comparator { left, right ->
    letterKey(valueLabel(left)).compareTo(letterKey(valueLabel(right)), ignoreCase = true)
        .takeIf { it != 0 }
        ?: valueLabel(left).compareTo(valueLabel(right), ignoreCase = true).takeIf { it != 0 }
        ?: left.compareTo(right, ignoreCase = true)
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
    val presentation = UnifiedReaderThemeTokens.presentation
    val semanticColors = UnifiedReaderThemeTokens.colors
    val sheetRecipe = UnifiedReaderThemeTokens.components.sheet
    val sheetShape = RoundedCornerShape(presentation.cardCornerRadius)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .then(modifier)
                .unifiedReaderComponentTreatment(
                    recipe = sheetRecipe,
                    accentColor = semanticColors.readerAccent,
                    secondaryColor = semanticColors.syncAccent,
                    overlayColor = MaterialTheme.colorScheme.onSurface,
                    shape = sheetShape,
                ),
            shape = sheetShape,
            color = semanticColors.surfaceBase,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .unifiedReaderSurfacePattern(
                        treatment = sheetRecipe.surfaceTreatment,
                        accentColor = semanticColors.readerAccent,
                        overlayColor = MaterialTheme.colorScheme.onSurface,
                        intensity = sheetRecipe.patternIntensity,
                    )
                    .padding(
                        horizontal = presentation.shelfCardHorizontalPadding + 10.dp,
                        vertical = presentation.shelfCardVerticalPadding + 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing + 6.dp),
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
        modifier = Modifier.libraryAccentChipTreatment(selected = selected),
        colors = libraryAccentFilterChipColors(),
        shape = libraryAccentFilterChipShape(),
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
