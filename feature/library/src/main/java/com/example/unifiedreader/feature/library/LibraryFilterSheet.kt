package com.example.unifiedreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.IntOffset
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppCheckbox
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppScreenTransition
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetSectionEnd
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton

/**
 * Filters opened on a list's behalf: [title] in the header ("New smart list", the list's name),
 * and at the foot [cancelLabel] and [primaryLabel] ("Save as list", "Save changes") instead of
 * Save as list and Show.
 */
data class LibraryFilterListMode(
    val title: String,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val onCancel: () -> Unit,
    val cancelLabel: String = "Cancel",
    val primaryEnabled: Boolean = true,
    // How many books the list held before these changes: the line then says "12 match now · 8 before".
    val countBefore: Int? = null,
)

/**
 * Every library filter in one side sheet (a bottom sheet on phones), group by group under their
 * headings. Short choices stay open as chips with counts; Library, Language, Genre, About and Mood show
 * their values with most books; the long facets (Set in and When among them) are rows naming their top
 * values, each opening its own page inside the sheet; Find searches them all. Filters apply as they are tapped; "Show N books" closes the sheet. [shownCount] is what
 * the grid shows now, of [totalCount] in this view.
 */
@Composable
fun LibraryFilterSheet(
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
    // Save as list, beside Show: keeps these filters as a smart list. Null where it isn't offered.
    onSaveAsList: (() -> Unit)? = null,
    // The panel opened on a list's behalf: its title and its own two buttons instead of Save as list.
    listMode: LibraryFilterListMode? = null,
    // Covers for Series' stacks; without them Series is a row naming its top values.
    seriesCovers: LibraryFilterSeriesCovers? = null,
) {
    AppSideSheet(onDismissRequest = onDismiss) {
        val close = { dismiss() }
        LibraryFilterContent(
            docked = false,
            filterPanels = filterPanels,
            filterState = filterState,
            filterFacets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            shownCount = shownCount,
            totalCount = totalCount,
            initialPanel = initialPanel,
            openRequest = 0,
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onClose = close,
            onSaveAsList = onSaveAsList,
            listMode = listMode,
            seriesCovers = seriesCovers,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The filters docked beside the library on an Expanded window: the sheet's content in a panel that
 * stays open while the books change behind every tap, so it has no Show button, only Save as list.
 * [openRequest] changes each time an applied value's chip asks for its section ([initialPanel]).
 */
@Composable
internal fun LibraryFilterDockedPanel(
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    shownCount: Int,
    totalCount: Int?,
    initialPanel: LibraryFilterPanel?,
    openRequest: Int,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onHide: () -> Unit,
    onSaveAsList: (() -> Unit)?,
    modifier: Modifier = Modifier,
    listMode: LibraryFilterListMode? = null,
    seriesCovers: LibraryFilterSeriesCovers? = null,
) {
    AppPanel(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(DockedPanelBorder, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LibraryFilterContent(
            docked = true,
            filterPanels = filterPanels,
            filterState = filterState,
            filterFacets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            shownCount = shownCount,
            totalCount = totalCount,
            initialPanel = initialPanel,
            openRequest = openRequest,
            onFilterStateChanged = onFilterStateChanged,
            onClearFilters = onClearFilters,
            onClose = onHide,
            onSaveAsList = onSaveAsList,
            listMode = listMode,
            seriesCovers = seriesCovers,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LibraryFilterContent(
    docked: Boolean,
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    shownCount: Int,
    totalCount: Int?,
    initialPanel: LibraryFilterPanel?,
    openRequest: Int,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onClose: () -> Unit,
    onSaveAsList: (() -> Unit)?,
    listMode: LibraryFilterListMode?,
    modifier: Modifier = Modifier,
    seriesCovers: LibraryFilterSeriesCovers? = null,
) {
    // The facet page on screen; null is the overview.
    var page by rememberSaveable {
        mutableStateOf(initialPanel?.takeIf { panel -> panel.opensAsPage && panel in filterPanels })
    }
    // The letter a letter grid opened the page at; null opens it at the top.
    var pageLetter by rememberSaveable { mutableStateOf<String?>(null) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    // A docked panel stays open: an applied value's chip opens its section again.
    LaunchedEffect(openRequest) {
        if (openRequest != 0) {
            page = initialPanel?.takeIf { panel -> panel.opensAsPage && panel in filterPanels }
            pageLetter = null
        }
    }
    // Kept out here so the overview comes back where it was left.
    val overviewListState = rememberLazyListState()
    Column(modifier = modifier.fillMaxWidth()) {
        AppScreenHost(
            screen = page,
            modifier = Modifier.weight(1f),
            back = page?.let { AppScreenBack(target = null, onBack = { page = null }) },
            transitionFor = { _, to -> if (to != null) AppScreenTransition.Forward else AppScreenTransition.Backward },
        ) { shown ->
            if (shown == null) {
                LibraryFilterOverview(
                    docked = docked,
                    filterPanels = filterPanels,
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                    shownCount = shownCount,
                    totalCount = totalCount,
                    findQuery = findQuery,
                    onFindQueryChanged = { query -> findQuery = query },
                    listState = overviewListState,
                    onFilterStateChanged = onFilterStateChanged,
                    onClearFilters = onClearFilters,
                    onOpenPage = { panel, letter ->
                        pageLetter = letter
                        page = panel
                    },
                    onClose = onClose,
                    title = listMode?.title ?: "Filters",
                    countBefore = listMode?.countBefore,
                    seriesCovers = seriesCovers,
                )
            } else {
                LibraryFilterFacetPage(
                    panel = shown,
                    filterState = filterState,
                    filterFacets = filterFacets,
                    libraryTitlesById = libraryTitlesById,
                    onFilterStateChanged = onFilterStateChanged,
                    onBack = { page = null },
                    initialLetter = pageLetter,
                )
            }
        }
        if (listMode != null) {
            // On a list's behalf the panel ends with its own two buttons, docked or not.
            Row(
                modifier = Modifier.fillMaxWidth().padding(AppLayout.inset),
                horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(onClick = listMode.onCancel, modifier = Modifier.height(LibraryFilterSheetButtonHeight)) {
                    Text(text = listMode.cancelLabel, fontWeight = FontWeight.SemiBold)
                }
                AppButton(
                    onClick = listMode.onPrimary,
                    enabled = listMode.primaryEnabled,
                    modifier = Modifier.weight(1f).height(LibraryFilterSheetButtonHeight),
                ) {
                    Text(text = listMode.primaryLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            return@Column
        }
        if (docked) {
            if (onSaveAsList != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(AppLayout.inset)) {
                    AppTonalButton(
                        onClick = onSaveAsList,
                        modifier = Modifier.fillMaxWidth().height(LibraryFilterSheetButtonHeight),
                    ) {
                        Text(text = "Save as list", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppLayout.inset, vertical = FooterVerticalPadding)
                .animateContentSize(AppMotion.spatial()),
            verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
        ) {
            if (onSaveAsList != null) {
                Text(
                    text = "Save as list keeps these filters, so the list changes as your library does.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                if (onSaveAsList != null) {
                    AppTonalButton(
                        onClick = {
                            onSaveAsList()
                            onClose()
                        },
                        modifier = Modifier.weight(1f).height(LibraryFilterSheetButtonHeight),
                    ) {
                        Text(text = "Save as list", fontWeight = FontWeight.SemiBold)
                    }
                }
                AppButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(LibraryFilterSheetButtonHeight),
                ) {
                    Text(text = libraryFilterShowButtonLabel(shownCount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterOverview(
    docked: Boolean,
    filterPanels: List<LibraryFilterPanel>,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    shownCount: Int,
    totalCount: Int?,
    findQuery: String,
    onFindQueryChanged: (String) -> Unit,
    listState: LazyListState,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onClearFilters: () -> Unit,
    onOpenPage: (LibraryFilterPanel, String?) -> Unit,
    onClose: () -> Unit,
    title: String,
    countBefore: Int? = null,
    seriesCovers: LibraryFilterSeriesCovers? = null,
) {
    val anyApplied = filterPanels.any { panel -> panel.selectedCount(filterState) > 0 }
    // The sections in panel order, each while it has something to offer (Pages while it has lengths,
    // Library while there is more than one).
    val sections = remember(filterPanels, filterState, filterFacets, libraryTitlesById) {
        filterPanels.filter { panel ->
            val available = when (panel) {
                LibraryFilterPanel.ReadingStatus,
                LibraryFilterPanel.Format,
                LibraryFilterPanel.Location -> libraryFilterChipOptions(panel, filterState, filterFacets).isNotEmpty()
                else -> panel.isEnabled(filterState, filterFacets, libraryTitlesById)
            }
            available && panel.offersChoice(filterState, filterFacets)
        }
    }
    val groupHeadings = remember(sections) { libraryFilterGroupHeadings(sections) }
    val findGroups = remember(findQuery, filterPanels, filterState, filterFacets, libraryTitlesById) {
        libraryFilterFindGroups(findQuery, filterPanels, filterState, filterFacets, libraryTitlesById)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AppPanelHeader(
            title = title,
            subtitle = countBefore?.let { before -> "${appCount(shownCount)} match now · ${appCount(before)} before" }
                ?: libraryFilterShowingLabel(shownCount, totalCount),
            onClose = onClose,
            closeContentDescription = if (docked) "Hide filters" else "Close filters",
        ) {
            AnimatedVisibility(
                visible = anyApplied,
                enter = fadeIn(AppMotion.effects()),
                exit = fadeOut(AppMotion.effects()),
            ) {
                AppTextButton(onClick = onClearFilters) {
                    Text(text = "Clear all", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        AppSearchField(
            query = findQuery,
            onQueryChange = onFindQueryChanged,
            placeholder = "Find a place, tag, mood or author",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppLayout.inset),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = OverviewBottomPadding),
        ) {
            if (findQuery.isNotBlank()) {
                item(key = "find") {
                    LibraryFilterFindResults(groups = findGroups, state = filterState, onFilterStateChanged = onFilterStateChanged)
                }
                return@LazyColumn
            }
            sections.forEachIndexed { index, panel ->
                item(key = "section:${panel.name}") {
                    // A pick that adds or drops sections moves the ones below instead of cutting.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = AppMotion.effects(),
                                placementSpec = AppMotion.spatial(IntOffset.VisibilityThreshold),
                                fadeOutSpec = AppMotion.effects(),
                            ),
                    ) {
                        if (index > 0) AppSheetDivider()
                        groupHeadings[index]?.let { heading ->
                            AppGroupLabel(
                                text = heading,
                                modifier = Modifier
                                    .padding(horizontal = AppLayout.inset)
                                    .padding(top = AppLayout.gridGap),
                            )
                        }
                        LibraryFilterOverviewSection(
                            panel = panel,
                            filterState = filterState,
                            filterFacets = filterFacets,
                            libraryTitlesById = libraryTitlesById,
                            seriesCovers = seriesCovers,
                            onFilterStateChanged = onFilterStateChanged,
                            onOpenPage = onOpenPage,
                        )
                        AppSheetSectionEnd()
                    }
                }
            }
            if (sections.isNotEmpty()) {
                item(key = "end") { AppSheetDivider() }
            }
        }
    }
}

/** One section of the overview, in the form its kind of choice takes. */
@Composable
private fun LibraryFilterOverviewSection(
    panel: LibraryFilterPanel,
    filterState: LibraryFilterState,
    filterFacets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    seriesCovers: LibraryFilterSeriesCovers?,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onOpenPage: (LibraryFilterPanel, String?) -> Unit,
) {
    when (panel.sectionStyle) {
        LibraryFilterSectionStyle.Tiles -> LibraryFilterTilesSection(
            panel = panel,
            state = filterState,
            facets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            onFilterStateChanged = onFilterStateChanged,
            onShowAll = { onOpenPage(panel, null) },
        )
        LibraryFilterSectionStyle.Checks -> LibraryFilterChecksSection(
            panel = panel,
            state = filterState,
            facets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            onFilterStateChanged = onFilterStateChanged,
            onShowAll = { onOpenPage(panel, null) },
        )
        LibraryFilterSectionStyle.TopChips -> {
            AppSheetLabel(text = panel.label)
            LibraryFilterTopChipsSection(
                panel = panel,
                state = filterState,
                facets = filterFacets,
                libraryTitlesById = libraryTitlesById,
                onFilterStateChanged = onFilterStateChanged,
                onShowAll = { onOpenPage(panel, null) },
            )
        }
        LibraryFilterSectionStyle.Letters -> LibraryFilterLettersSection(
            panel = panel,
            state = filterState,
            facets = filterFacets,
            libraryTitlesById = libraryTitlesById,
            onOpenAtLetter = { letter -> onOpenPage(panel, letter) },
        )
        LibraryFilterSectionStyle.Stacks -> if (seriesCovers != null) {
            LibraryFilterStacksSection(
                state = filterState,
                facets = filterFacets,
                covers = seriesCovers,
                onFilterStateChanged = onFilterStateChanged,
                onShowAll = { onOpenPage(panel, null) },
            )
        } else {
            LibraryFilterFacetRow(
                label = panel.label,
                summary = libraryFacetSelectionSummary(panel, filterState, libraryTitlesById),
                topValues = libraryFilterTopValuesLine(panel, filterFacets, libraryTitlesById),
                enabled = true,
                onClick = { onOpenPage(panel, null) },
            )
        }
        LibraryFilterSectionStyle.Places -> LibraryFilterPlacesChecksSection(
            state = filterState,
            facets = filterFacets,
            onFilterStateChanged = onFilterStateChanged,
            onShowAll = { onOpenPage(panel, null) },
        )
        LibraryFilterSectionStyle.Timeline -> LibraryFilterTimelineSection(
            state = filterState,
            facets = filterFacets,
            onFilterStateChanged = onFilterStateChanged,
        )
        LibraryFilterSectionStyle.Length -> LibraryFilterLengthSection(
            range = filterState.pages,
            histogram = filterFacets.pages,
            onRangeChanged = { range -> onFilterStateChanged(filterState.copy(pages = range)) },
        )
    }
}

/**
 * A long facet in the overview: its name with its values with most books under it ([topValues]),
 * what is picked ("Any" when nothing), and a chevron.
 */
@Composable
private fun LibraryFilterFacetRow(
    label: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    topValues: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LibraryFilterSheetRowHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = AppLayout.inset, end = 14.dp)
            .alpha(if (enabled) 1f else DisabledFacetRowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = FacetRowVerticalPadding)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            if (topValues != null) {
                Text(
                    text = topValues,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = when {
                !enabled -> "None"
                summary == null -> "Any"
                else -> summary
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = if (summary != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = FacetSummaryMaxWidth),
        )
        Icon(
            imageVector = AppIcons.NavigateNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
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
    LibraryFilterPanel.Pages -> copy(pages = null)
    LibraryFilterPanel.Author -> copy(authors = emptySet())
    LibraryFilterPanel.Series -> copy(series = emptySet())
    LibraryFilterPanel.Publisher -> copy(publishers = emptySet())
    LibraryFilterPanel.Genre -> copy(genres = emptySet())
    LibraryFilterPanel.Tag -> copy(tags = emptySet())
    LibraryFilterPanel.Language -> copy(languages = emptySet())
    LibraryFilterPanel.SetIn -> copy(places = emptySet())
    LibraryFilterPanel.When -> copy(times = emptySet())
    LibraryFilterPanel.Mood -> copy(moods = emptySet())
}

internal val LibraryFilterSheetRowHeight = 52.dp
private val LibraryFilterSheetButtonHeight = 46.dp
private val DockedPanelBorder = 1.dp
private val FooterVerticalPadding = 12.dp
private val OverviewBottomPadding = 8.dp
private val FacetRowVerticalPadding = 6.dp
private val FacetSummaryMaxWidth = 168.dp
private const val DisabledFacetRowAlpha = 0.38f
