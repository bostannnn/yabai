package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.buildLibraryTagFilterFacets
import com.example.unifiedreader.core.model.withTagFacets
import com.example.unifiedreader.ui.components.AppCheckbox
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [facets] with Set in, When, About and Mood counted again from [pool] with your tag [sorting]: the
 * library's own facets come without it, so every tag is in About and only Kavita's Locations are
 * places. Unchanged while there is no pool (a series, a list) or the sorting hasn't loaded.
 */
@Composable
internal fun rememberLibraryFilterFacetsWithTags(
    facets: LibraryFilterFacets,
    pool: List<ReadableItem>,
    filters: LibraryFilterState,
    sorting: LibraryTagSorting?,
    progressByItemId: Map<String, ItemProgressSummary>,
    pageCountByItemId: Map<String, Int>,
    locallyAvailableItemIds: Set<String>,
): LibraryFilterFacets {
    val tagged by produceState<LibraryFilterFacets?>(
        initialValue = null,
        pool,
        filters,
        sorting,
        progressByItemId,
        pageCountByItemId,
        locallyAvailableItemIds,
    ) {
        if (pool.isEmpty() || sorting == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            buildLibraryTagFilterFacets(
                items = pool,
                filters = filters,
                tagKindOf = sorting::kindOf,
                downloadedRemoteItemIds = locallyAvailableItemIds,
                readingStatusProvider = { item -> libraryFilterReadingStatus(progressByItemId[item.id]) },
                pageCountProvider = { item -> pageCountByItemId[item.id] },
            )
        }
    }
    val sections = tagged ?: return facets
    return remember(facets, sections) { facets.withTagFacets(sections) }
}

/**
 * Set in's page: its places as rows, most books first, a picked country with its cities under it.
 * [limit] places at most; with [onShowAll], "All N places" past them.
 */
@Composable
internal fun LibraryFilterPlacesSection(
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    limit: Int = PlaceRowLimit,
    onShowAll: (() -> Unit)? = null,
) {
    val rows = remember(state, facets, limit) { libraryFilterPlaceRows(state, facets, limit) }
    val allCount = remember(state, facets) { libraryFacetValueCount(LibraryFilterPanel.SetIn, state, facets) }
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            LibraryFilterValueRow(
                name = row.value,
                count = row.count,
                selected = row.selected,
                indent = row.city,
                onClick = { onFilterStateChanged(state.toggling(LibraryFilterPanel.SetIn, row.value)) },
            )
        }
        if (onShowAll != null && allCount > rows.size) {
            LibraryFilterShowAll(label = "All ${appCount(allCount)} places", onClick = onShowAll)
        }
    }
}

/**
 * When's page: one row per pile in time order, its books standing as spines in the title-cover fills,
 * then the named periods, which have no pile, under their own label.
 */
@Composable
internal fun LibraryFilterPilesSection(
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
) {
    val rows = remember(state, facets) { libraryFilterPileRows(state, facets) }
    val periods = remember(state, facets) { libraryFilterNamedPeriods(state, facets) }
    val fallback = MaterialTheme.colorScheme.outline
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            LibraryFilterValueRow(
                name = row.value,
                count = row.count,
                selected = row.selected,
                onClick = { onFilterStateChanged(state.toggling(LibraryFilterPanel.When, row.value)) },
                height = PileRowHeight,
                middle = {
                    Row(
                        modifier = Modifier.height(SpineAreaHeight),
                        horizontalArrangement = Arrangement.spacedBy(SpineGap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        repeat(row.spines) { index ->
                            val shade = (index + row.order).mod(SpineHeights.size)
                            Box(
                                modifier = Modifier
                                    .width(SpineWidth)
                                    .height(SpineHeights[shade])
                                    .clip(RoundedCornerShape(SpineCorner))
                                    .background(
                                        LibraryTitleCoverFills.getOrNull((index * 5 + row.order).mod(LibraryTitleCoverFills.size)) ?: fallback,
                                    ),
                            )
                        }
                    }
                },
            )
        }
        if (periods.isNotEmpty()) {
            AppSheetLabel(text = "Named periods")
            periods.forEach { period ->
                LibraryFilterValueRow(
                    name = period.value,
                    count = period.count,
                    selected = false,
                    onClick = { onFilterStateChanged(state.toggling(LibraryFilterPanel.When, period.value)) },
                )
            }
        }
    }
}

/** Library, Language, Genre, About or Mood: the values with most books as chips, then "All N" for the rest. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFilterTopChipsSection(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onShowAll: () -> Unit,
) {
    val chips = remember(panel, state, facets, libraryTitlesById) {
        libraryFilterTopChips(panel, state, facets, panel.topChipLimit, libraryTitlesById)
    }
    val allCount = remember(panel, state, facets) { libraryFacetValueCount(panel, state, facets) }
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
            verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
        ) {
            chips.forEach { option ->
                AppChip(
                    selected = option.selected,
                    onClick = { onFilterStateChanged(option.toggled) },
                    count = option.count,
                    label = { Text(text = option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        if (allCount > chips.size) {
            LibraryFilterShowAll(label = "All ${appCount(allCount)} ${panel.pluralLabel}", onClick = onShowAll)
        }
    }
}

/** Find's matches, grouped under their sections; a tap picks or drops the value. */
@Composable
internal fun LibraryFilterFindResults(
    groups: List<LibraryFilterFindGroup>,
    state: LibraryFilterState,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
) {
    if (groups.isEmpty()) {
        Text(
            text = "Nothing in the filters matches.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppLayout.inset, vertical = AppLayout.headingGap),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        groups.forEach { group ->
            AppSheetLabel(text = group.panel.label)
            group.matches.forEach { match ->
                LibraryFilterFindRow(
                    match = match,
                    onClick = { onFilterStateChanged(state.toggling(match.panel, match.value)) },
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterFindRow(match: LibraryFilterFindMatch, onClick: () -> Unit) {
    val label = remember(match) {
        buildAnnotatedString {
            append(match.label.substring(0, match.matchStart))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.label.substring(match.matchStart, match.matchEnd)) }
            append(match.label.substring(match.matchEnd))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = FindRowStart, end = AppLayout.inset),
        horizontalArrangement = Arrangement.spacedBy(FindRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCheckbox(checked = match.selected, onCheckedChange = { onClick() })
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        match.count?.let { count ->
            Text(text = appCount(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A value row of the filter sheet (a place, a pile): its name, an optional [middle] (a pile's spines),
 * the count, and, picked, a `primary-container` fill with a check.
 */
@Composable
private fun LibraryFilterValueRow(
    name: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    indent: Boolean = false,
    height: androidx.compose.ui.unit.Dp = if (indent) CityRowHeight else ValueRowHeight,
    middle: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ValueRowOuterInset)
            .height(height)
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = if (indent) ValueRowFillInset + CityIndent else ValueRowFillInset, end = ValueRowFillInset),
        horizontalArrangement = Arrangement.spacedBy(ValueRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (indent && !selected) colors.onSurfaceVariant else content,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (middle == null) Modifier.weight(1f) else Modifier.width(PileNameWidth),
        )
        if (middle != null) {
            Box(modifier = Modifier.weight(1f)) { middle() }
        }
        count?.let {
            Text(
                text = appCount(it),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) content else colors.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(imageVector = AppIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(CheckIconSize))
        }
    }
}

/** "All 53 places": opens the section's full list, with its search and letters. */
@Composable
private fun LibraryFilterShowAll(label: String, onClick: () -> Unit) {
    AppTextButton(onClick = onClick, modifier = Modifier.padding(start = ShowAllStart)) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

// The fill of a picked row reaches 8 past the sheet's inset, so its text stays on the inset line.
private val ValueRowFillInset = 8.dp
private val ValueRowOuterInset = AppLayout.inset - ValueRowFillInset
private val ValueRowHeight = 40.dp
private val CityRowHeight = 34.dp
private val CityIndent = 16.dp
private val ValueRowGap = 12.dp
private val PileRowHeight = 30.dp
private val PileNameWidth = 100.dp
private val SpineAreaHeight = 18.dp
private val SpineWidth = 4.dp
private val SpineGap = 1.dp
private val SpineCorner = 1.dp
private val SpineHeights = listOf(14.dp, 16.dp, 18.dp)
private val CheckIconSize = 18.dp
private val FindRowStart = 8.dp
private val FindRowGap = 4.dp
private val ShowAllStart = 8.dp
