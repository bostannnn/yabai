package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.authorFacetKey
import com.example.unifiedreader.ui.components.AppCheckbox
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * Every library filter in one side sheet (a bottom sheet on phones). One section per visible
 * panel; opening a section shows its options, with a search field for long facets.
 */
@Composable
internal fun LibraryFilterSheet(
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    initialPanel: LibraryFilterPanel?,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedPanel by remember { mutableStateOf(initialPanel ?: filterPanels.firstOrNull()) }
    var query by remember(expandedPanel) { mutableStateOf("") }
    val activeCount = filterPanels.sumOf { panel -> panel.selectedCount(filterState) }

    AppSideSheet(onDismissRequest = onDismiss) {
        FilterSheetHeader(
            activeCount = activeCount,
            onClearFilters = onClearFilters,
            onDismiss = onDismiss,
        )
        AppDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            filterPanels.forEach { panel ->
                val section = facetSectionFor(panel, filterState, filterFacets, libraryTitlesById, onFilterStateChanged)
                val enabled = panel.isEnabled(filterState, filterFacets, libraryTitlesById)
                val expanded = expandedPanel == panel && enabled
                item(key = "header:${panel.name}") {
                    FilterSectionHeader(
                        label = panel.label,
                        selectedCount = panel.selectedCount(filterState),
                        enabled = enabled,
                        expanded = expanded,
                        onToggle = { expandedPanel = if (expanded) null else panel },
                        onClear = { onFilterStateChanged(filterState.clearing(panel)) },
                    )
                }
                if (!expanded) return@forEach
                when (section) {
                    is FacetSection.Chips -> item(key = "chips:${panel.name}") {
                        FacetChipRow(section = section)
                    }
                    is FacetSection.Searchable -> {
                        val displayValues = searchableFacetDisplayValues(
                            values = section.values,
                            selectedValues = section.selectedValues,
                            valueKey = { value -> section.valueLabel(value).lowercase() },
                            valueLabel = section.valueLabel,
                            letterKey = section.letterKey,
                        )
                        if (displayValues.size > SearchFieldThreshold) {
                            item(key = "search:${panel.name}") {
                                AppTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    singleLine = true,
                                    shape = libraryAccentTextFieldShape(),
                                    placeholder = { Text(text = "Search ${panel.label.lowercase()}") },
                                )
                            }
                        }
                        val trimmed = query.trim()
                        val shown = if (trimmed.isEmpty()) {
                            displayValues
                        } else {
                            displayValues.filter { section.valueLabel(it).contains(trimmed, ignoreCase = true) }
                        }
                        if (shown.isEmpty()) {
                            item(key = "empty:${panel.name}") {
                                Text(
                                    text = "No matches",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        }
                        items(shown, key = { "${panel.name}:$it" }) { value ->
                            TextFacetRow(
                                value = section.valueLabel(value),
                                selected = value in section.selectedValues,
                                available = value in section.availableValues,
                                onClick = { section.toggle(value) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterSheetHeader(
    activeCount: Int,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = when (activeCount) {
                    0 -> "Nothing applied"
                    1 -> "1 filter applied"
                    else -> "$activeCount filters applied"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (activeCount > 0) {
            AppTextButton(onClick = onClearFilters) {
                Text(text = "Clear all")
            }
        }
        AppIconButton(onClick = onDismiss) {
            Icon(imageVector = AppIcons.Close, contentDescription = "Close filters")
        }
    }
}

@Composable
private fun FilterSectionHeader(
    label: String,
    selectedCount: Int,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selectedCount > 0) {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.readerAccent,
            )
            AppIconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Clear $label",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Icon(
            imageVector = AppIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.rotate(if (expanded) 90f else 0f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetChipRow(
    section: FacetSection.Chips,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        section.options.forEach { option ->
            FilterToggleChip(
                label = option.label,
                selected = option.selected,
                onClick = option.toggle,
            )
        }
    }
}

@Composable
internal fun FilterToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppChip(
        selected = selected,
        onClick = onClick,
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

@Composable
internal fun TextFacetRow(
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
            .padding(start = 12.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCheckbox(
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

private sealed interface FacetSection {
    data class Chips(val options: List<ChipOption>) : FacetSection

    data class Searchable(
        val values: List<String>,
        val selectedValues: Set<String>,
        val valueLabel: (String) -> String = { it },
        val letterKey: (String) -> String = { it },
        val toggle: (String) -> Unit,
    ) : FacetSection {
        val availableValues: Set<String> = values.toSet()
    }
}

private data class ChipOption(
    val label: String,
    val selected: Boolean,
    val toggle: () -> Unit,
)

private fun facetSectionFor(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onChanged: (LibraryFilterState) -> Unit,
): FacetSection = when (panel) {
    LibraryFilterPanel.Format -> FacetSection.Chips(
        facets.formats.map { format ->
            ChipOption(format.name, format in state.formats) {
                onChanged(state.copy(formats = state.formats.toggle(format)))
            }
        },
    )
    LibraryFilterPanel.Location -> FacetSection.Chips(
        facets.fileLocations.map { location ->
            ChipOption(location.label, location in state.fileLocations) {
                onChanged(state.copy(fileLocations = state.fileLocations.toggle(location)))
            }
        },
    )
    LibraryFilterPanel.ReadingStatus -> FacetSection.Chips(
        LibraryReadingStatus.entries.map { status ->
            ChipOption(status.label, status in state.readingStatuses) {
                onChanged(state.copy(readingStatuses = state.readingStatuses.toggle(status)))
            }
        },
    )
    LibraryFilterPanel.Library -> FacetSection.Searchable(
        values = facets.libraries,
        selectedValues = state.libraries,
        valueLabel = { libraryId -> libraryTitlesById[libraryId] ?: libraryId },
        letterKey = { libraryId -> libraryTitlesById[libraryId] ?: libraryId },
        toggle = { id -> onChanged(state.copy(libraries = state.libraries.toggle(id))) },
    )
    LibraryFilterPanel.Author -> FacetSection.Searchable(
        values = facets.authors,
        selectedValues = state.authors,
        letterKey = String::authorFacetKey,
        toggle = { value -> onChanged(state.copy(authors = state.authors.toggle(value))) },
    )
    LibraryFilterPanel.Series -> FacetSection.Searchable(
        values = facets.series,
        selectedValues = state.series,
        toggle = { value -> onChanged(state.copy(series = state.series.toggle(value))) },
    )
    LibraryFilterPanel.Publisher -> FacetSection.Searchable(
        values = facets.publishers,
        selectedValues = state.publishers,
        toggle = { value -> onChanged(state.copy(publishers = state.publishers.toggle(value))) },
    )
    LibraryFilterPanel.Genre -> FacetSection.Searchable(
        values = facets.genres,
        selectedValues = state.genres,
        toggle = { value -> onChanged(state.copy(genres = state.genres.toggle(value))) },
    )
    LibraryFilterPanel.Tag -> FacetSection.Searchable(
        values = facets.tags,
        selectedValues = state.tags,
        toggle = { value -> onChanged(state.copy(tags = state.tags.toggle(value))) },
    )
    LibraryFilterPanel.Language -> FacetSection.Searchable(
        values = facets.languages,
        selectedValues = state.languages,
        toggle = { value -> onChanged(state.copy(languages = state.languages.toggle(value))) },
    )
}

/** The state with one panel's selection removed, for the × on an applied-filter chip. */
internal fun LibraryFilterState.clearing(panel: LibraryFilterPanel): LibraryFilterState = when (panel) {
    LibraryFilterPanel.Format -> copy(formats = emptySet())
    LibraryFilterPanel.Library -> copy(libraries = emptySet())
    LibraryFilterPanel.Location -> copy(fileLocations = emptySet())
    LibraryFilterPanel.ReadingStatus -> copy(readingStatuses = emptySet())
    LibraryFilterPanel.Author -> copy(authors = emptySet())
    LibraryFilterPanel.Series -> copy(series = emptySet())
    LibraryFilterPanel.Publisher -> copy(publishers = emptySet())
    LibraryFilterPanel.Genre -> copy(genres = emptySet())
    LibraryFilterPanel.Tag -> copy(tags = emptySet())
    LibraryFilterPanel.Language -> copy(languages = emptySet())
}

private const val SearchFieldThreshold = 8
