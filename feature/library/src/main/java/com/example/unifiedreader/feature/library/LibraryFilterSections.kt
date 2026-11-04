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

/** Genre or About: the values with most books as chips, then "All N" for the rest. */
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
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.label.substring(match.matchStart, match.matchEnd)) }
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

/** "All 53 places": opens the section's full list, with its search and letters. */
@Composable
private fun LibraryFilterShowAll(label: String, onClick: () -> Unit) {
    AppTextButton(onClick = onClick, modifier = Modifier.padding(start = ShowAllStart)) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

private val FindRowStart = 8.dp
private val FindRowGap = 4.dp
private val ShowAllStart = 8.dp
