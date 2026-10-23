package com.example.unifiedreader.feature.library

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppDialogDefaults
import com.example.unifiedreader.ui.components.AppDialogHost
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    // null opens the filter sheet at the top; a panel opens it at that section.
    onOpenFilterPanel: (LibraryFilterPanel?) -> Unit,
    onClearFilterPanel: (LibraryFilterPanel) -> Unit = {},
    onClearFilters: () -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
) {
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
                    onRemove = { onClearFilterPanel(option.panel) },
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        AppChip(
            selected = !filterState.isEmpty,
            enabled = filtersEnabled,
            onClick = { onOpenFilterPanel(null) },
            leadingIcon = {
                Icon(
                    imageVector = AppIcons.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(AppChipDefaults.iconSize),
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
    onRemove: (() -> Unit)? = null,
) {
    AppChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(
                text = if (showCount && count > 0) "$label ($count)" else label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = onRemove?.let { remove ->
            {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Remove $label filter",
                    modifier = Modifier
                        .size(AppChipDefaults.iconSize)
                        .clickable(onClick = remove),
                )
            }
        },
    )
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
            AppTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
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
                            AppDivider(modifier = Modifier.padding(vertical = 6.dp))
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

internal fun searchableFacetDisplayValues(
    values: List<String>,
    selectedValues: Set<String>,
    valueKey: (String) -> String,
    valueLabel: (String) -> String = { it },
    letterKey: (String) -> String = { it },
): List<String> = (values + selectedValues)
    .distinctBy(valueKey)
    // Keys are computed once per value, not once per comparison: the author key in particular
    // is string work, and a few thousand values sorted on every recomposition was a visible hitch.
    .map { value ->
        val label = valueLabel(value)
        SearchableFacetSortEntry(value = value, label = label, letterKey = letterKey(label))
    }
    .sortedWith(searchableFacetDisplayComparator)
    .map(SearchableFacetSortEntry::value)

private class SearchableFacetSortEntry(
    val value: String,
    val label: String,
    val letterKey: String,
)

private val searchableFacetDisplayComparator: Comparator<SearchableFacetSortEntry> =
    Comparator { left, right ->
        left.letterKey.compareTo(right.letterKey, ignoreCase = true).takeIf { it != 0 }
            ?: left.label.compareTo(right.label, ignoreCase = true).takeIf { it != 0 }
            ?: left.value.compareTo(right.value, ignoreCase = true)
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
    AppDialogHost(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppPanel(
            modifier = Modifier
                .padding(24.dp)
                .then(modifier),
            shape = AppDialogDefaults.shape,
            color = AppDialogDefaults.containerColor,
        ) {
            Column(
                modifier = Modifier
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
                        AppTextButton(onClick = onClearFilters) {
                            Text(text = "Clear")
                        }
                    }
                    AppTextButton(onClick = onDismiss) {
                        Text(text = "Done")
                    }
                }
            }
        }
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

