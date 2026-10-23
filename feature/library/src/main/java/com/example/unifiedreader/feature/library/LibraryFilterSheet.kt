package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppCheckbox
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppScreenTransition
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton

/**
 * Every library filter in one side sheet (a bottom sheet on phones). Reading status, format and
 * location stay open as chips with counts; the long facets are rows showing what is picked, each
 * opening its own page inside the sheet. Filters apply as they are tapped; "Show N books" closes
 * the sheet. [shownCount] is what the grid shows now, of [totalCount] in this view.
 */
@Composable
internal fun LibraryFilterSheet(
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    shownCount: Int,
    totalCount: Int?,
    initialPanel: LibraryFilterPanel?,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The facet page on screen; null is the overview.
    var page by rememberSaveable {
        mutableStateOf(initialPanel?.takeIf { panel -> panel.opensAsPage && panel in filterPanels })
    }
    // Kept out here so the overview comes back where it was left.
    val overviewListState = rememberLazyListState()
    AppSideSheet(onDismissRequest = onDismiss, width = LibraryFilterSheetWidth) {
        AppScreenHost(
            screen = page,
            modifier = Modifier.weight(1f),
            back = page?.let { AppScreenBack(target = null, onBack = { page = null }) },
            transitionFor = { _, to -> if (to != null) AppScreenTransition.Forward else AppScreenTransition.Backward },
        ) { shown ->
            if (shown == null) {
                LibraryFilterOverview(
                    filterPanels = filterPanels,
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                    shownCount = shownCount,
                    totalCount = totalCount,
                    listState = overviewListState,
                    onFilterStateChanged = onFilterStateChanged,
                    onClearFilters = onClearFilters,
                    onOpenPage = { panel -> page = panel },
                    onClose = { dismiss() },
                )
            } else {
                LibraryFilterFacetPage(
                    panel = shown,
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                    onFilterStateChanged = onFilterStateChanged,
                    onBack = { page = null },
                )
            }
        }
        AppButton(
            onClick = { dismiss() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .height(LibraryFilterSheetButtonHeight),
        ) {
            Text(text = libraryFilterShowButtonLabel(shownCount), fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterOverview(
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    shownCount: Int,
    totalCount: Int?,
    listState: LazyListState,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onOpenPage: (LibraryFilterPanel) -> Unit,
    onClose: () -> Unit,
) {
    val anyApplied = filterPanels.any { panel -> panel.selectedCount(filterState) > 0 }
    val chipGroups = remember(filterPanels, filterState, filterFacets) {
        filterPanels
            .filterNot(LibraryFilterPanel::opensAsPage)
            .map { panel -> panel to libraryFilterChipOptions(panel, filterState, filterFacets) }
            .filter { (_, options) -> options.isNotEmpty() }
    }
    val pagePanels = filterPanels.filter(LibraryFilterPanel::opensAsPage)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = libraryFilterShowingLabel(shownCount, totalCount),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (anyApplied) {
                AppTextButton(onClick = onClearFilters) {
                    Text(text = "Clear all", fontWeight = FontWeight.SemiBold)
                }
            }
            AppIconButton(onClick = onClose) {
                Icon(imageVector = AppIcons.Close, contentDescription = "Close filters")
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            chipGroups.forEachIndexed { index, (panel, options) ->
                item(key = "chips:${panel.name}") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (index > 0) LibraryFilterSheetHairline()
                        LibraryFilterSheetHeading(text = panel.label)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            options.forEach { option ->
                                AppChip(
                                    selected = option.selected,
                                    onClick = { onFilterStateChanged(option.toggled) },
                                    count = option.count,
                                    label = {
                                        Text(text = option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            pagePanels.forEachIndexed { index, panel ->
                item(key = "page:${panel.name}") {
                    if (index > 0 || chipGroups.isNotEmpty()) LibraryFilterSheetHairline()
                    LibraryFilterFacetRow(
                        label = panel.label,
                        summary = libraryFacetSelectionSummary(panel, filterState, libraryTitlesById),
                        enabled = panel.isEnabled(filterState, filterFacets, libraryTitlesById),
                        onClick = { onOpenPage(panel) },
                    )
                }
            }
            if (pagePanels.isNotEmpty()) {
                item(key = "page-end") { LibraryFilterSheetHairline() }
            }
        }
    }
}

/** A long facet in the overview: its name, what is picked ("Any" when nothing), and a chevron. */
@Composable
private fun LibraryFilterFacetRow(
    label: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LibraryFilterSheetRowHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 20.dp, end = 14.dp)
            .alpha(if (enabled) 1f else DisabledFacetRowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = when {
                !enabled -> "None"
                summary == null -> "Any"
                else -> summary
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
            color = if (summary != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = AppIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}

/** A group title inside the sheet: "Reading status", or a letter on a facet page. */
@Composable
internal fun LibraryFilterSheetHeading(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
    )
}

@Composable
internal fun LibraryFilterSheetHairline() {
    AppDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
internal fun TextFacetRow(
    value: String,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = when {
        selected || available -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
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

/** The state with one panel's selection removed: a facet page's Clear. */
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

private val LibraryFilterSheetWidth = 420.dp
internal val LibraryFilterSheetRowHeight = 52.dp
private val LibraryFilterSheetButtonHeight = 46.dp
private const val DisabledFacetRowAlpha = 0.38f
