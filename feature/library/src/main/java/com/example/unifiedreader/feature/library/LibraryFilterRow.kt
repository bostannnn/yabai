package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppIcons

/**
 * The row above the grid: on a source's home the [heading] ("All books 1,474"), then the Filters
 * chip with the number of applied values, one chip per applied value naming it (its × removes
 * just that value), and the sort chip at the end. With a heading the chips sit at the end with
 * the sort chip; they scroll when they don't fit.
 */
@Composable
internal fun LibraryFilterRow(
    heading: LibraryGridHeading? = null,
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    sortMode: LibrarySortMode,
    gridCellSizeDp: Int,
    fadeReadItems: Boolean,
    showSortControl: Boolean,
    availableSortModes: List<LibrarySortMode> = LibrarySortMode.entries,
    lockedSortLabel: String? = null,
    // null opens the filter sheet at the top; a panel opens it at that section.
    onOpenFilterPanel: (LibraryFilterPanel?) -> Unit,
    // The filters are docked open beside the library: the Filters chip shows as selected.
    filtersOpen: Boolean = false,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appliedFilters = remember(filterPanels, filterState, libraryTitlesById) {
        appliedLibraryFilters(filterState, filterPanels, libraryTitlesById)
    }
    val filtersEnabled = remember(filterPanels, filterState, filterFacets, libraryTitlesById) {
        !filterState.isEmpty || filterPanels.any { panel ->
            panel.isEnabled(filterState = filterState, filterFacets = filterFacets, libraryTitlesById = libraryTitlesById)
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (heading != null) {
            AppSectionHeading(text = heading.title, count = heading.count, modifier = Modifier.padding(end = 12.dp))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (heading != null) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryFiltersChip(
                    appliedCount = appliedFilters.size,
                    enabled = filtersEnabled,
                    selected = filtersOpen,
                    onClick = { onOpenFilterPanel(null) },
                )
                appliedFilters.forEach { filter ->
                    LibraryAppliedFilterChip(
                        filter = filter,
                        onClick = { onOpenFilterPanel(filter.panel) },
                        onRemove = { onFilterStateChanged(filter.without) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        LibraryBrowseOptionsSelector(
            sortMode = sortMode,
            gridCellSizeDp = gridCellSizeDp,
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


/** "Filters", with a 20 dp accent badge counting the applied values. */
@Composable
fun LibraryFiltersChip(
    appliedCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    AppChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = if (appliedCount > 0) "Filters, $appliedCount applied" else "Filters"
        },
        leadingIcon = {
            Icon(
                imageVector = AppIcons.FilterList,
                contentDescription = null,
                modifier = Modifier.size(AppChipDefaults.iconSize),
            )
        },
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Filters", maxLines = 1)
                if (appliedCount > 0) {
                    AppBadge(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = appliedCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun LibraryAppliedFilterChip(
    filter: LibraryAppliedFilter,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    AppChip(
        selected = true,
        onClick = onClick,
        label = {
            Text(
                text = filter.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = LibraryAppliedFilterLabelMaxWidth),
            )
        },
        trailingIcon = {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "Remove ${filter.label}",
                modifier = Modifier
                    .size(AppChipDefaults.iconSize)
                    .clickable(onClick = onRemove),
            )
        },
    )
}

private val LibraryAppliedFilterLabelMaxWidth = 200.dp
