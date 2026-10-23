package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppTextButton

/**
 * One long facet (Author, Series, ...) as a page inside the filter sheet: a search field, the
 * picks pinned under "Selected", then every other value under its letter with its count.
 */
@Composable
internal fun LibraryFilterFacetPage(
    panel: LibraryFilterPanel,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable(panel) { mutableStateOf("") }
    val groups = remember(panel, filterState, filterFacets, libraryTitlesById, query) {
        libraryFacetPageGroups(panel, filterState, filterFacets, libraryTitlesById, query)
    }
    val valueCount = remember(panel, filterState, filterFacets) {
        libraryFacetValueCount(panel, filterState, filterFacets)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppIconButton(onClick = onBack) {
                Icon(imageVector = AppIcons.ArrowBack, contentDescription = "Back to filters")
            }
            Text(
                text = panel.label,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (filterState.selectedValues(panel).isNotEmpty()) {
                AppTextButton(onClick = { onFilterStateChanged(filterState.clearing(panel)) }) {
                    Text(text = "Clear", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        AppSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search $valueCount ${panel.pluralLabel}",
            height = FacetSearchFieldHeight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            // A lazy list keeps its first visible item in place when items arrive above it. At the
            // top that item is this spacer, so a value picked there shows up under "Selected"
            // instead of landing above the screen.
            item(key = "top") { Spacer(modifier = Modifier.height(4.dp)) }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            groups.forEach { group ->
                item(key = "group:${group.title}") {
                    LibraryFilterSheetHeading(
                        text = group.title,
                        fontSize = 12.5.sp,
                        modifier = Modifier.animateItem(),
                    )
                }
                items(group.values, key = { value -> "value:${value.value}" }) { value ->
                    FacetValueRow(
                        value = value,
                        onToggle = { onFilterStateChanged(filterState.toggling(panel, value.value)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** A value on a facet page: its name, how many books it has, and a round check. */
@Composable
private fun FacetValueRow(
    value: LibraryFacetPageValue,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LibraryFilterSheetRowHeight)
                .toggleable(value = value.selected, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(start = 20.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = value.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            value.count?.let { count ->
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppRoundCheck(checked = value.selected)
        }
        LibraryFilterSheetHairline()
    }
}

private val FacetSearchFieldHeight = 42.dp
