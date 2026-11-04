package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageHistogram
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.ui.components.AppCheckRow
import com.example.unifiedreader.ui.components.AppCoverStack
import com.example.unifiedreader.ui.components.AppCoverStackDefaults
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppLetterGrid
import com.example.unifiedreader.ui.components.AppLetterGridDefaults
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTile
import com.example.unifiedreader.ui.components.AppTileGrid
import com.example.unifiedreader.ui.components.AppTileGridDefaults
import com.example.unifiedreader.ui.components.AppTimeline
import com.example.unifiedreader.ui.components.AppTimelineEntry
import com.example.unifiedreader.ui.components.appCount

/**
 * What Series' cover stacks need from the library: a series' first books (front cover first), how many of
 * them are read, and the library's cover. Null where no covers can be drawn (a list's own Filters): Series
 * is then a row naming its top values.
 */
class LibraryFilterSeriesCovers(
    val booksOf: (series: String) -> List<ReadableItem>,
    val readCountOf: (series: String) -> Int,
    val cover: @Composable (item: ReadableItem, modifier: Modifier) -> Unit,
)

/**
 * Series' covers from the books in [pool]: each series' books oldest first, how many of them are finished
 * by [progressByItemId], and the library's cover. Null for an empty pool.
 */
internal fun libraryFilterSeriesCovers(
    pool: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): LibraryFilterSeriesCovers? {
    if (pool.isEmpty()) return null
    val bySeries: Map<String, List<ReadableItem>> = pool
        .mapNotNull { item -> item.facetMetadata().series.firstOrNull()?.let { series -> series.lowercase() to item } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, books) -> books.sortedWith(compareBy<ReadableItem> { it.publishedYear ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }) }
    return LibraryFilterSeriesCovers(
        booksOf = { series -> bySeries[series.lowercase()].orEmpty() },
        readCountOf = { series -> bySeries[series.lowercase()].orEmpty().count { book -> progressByItemId[book.id]?.isComplete == true } },
        cover = { item, modifier ->
            ResolvedLibraryCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                showReadBadge = false,
                modifier = modifier,
            )
        },
    )
}

/** Library, Reading status or Mood: every value as a tile with its count; past six libraries, five and "N more". */
@Composable
internal fun LibraryFilterTilesSection(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onShowAll: () -> Unit,
) {
    val content = remember(panel, state, facets, libraryTitlesById) {
        libraryFilterTiles(panel, state, facets, libraryTitlesById, AppTileGridDefaults.maxTiles)
    }
    val byKey = remember(content) { content.tiles.associateBy(LibraryFilterChipOption::key) }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSheetLabel(text = panel.label)
        AppTileGrid(
            tiles = content.tiles.map { option ->
                AppTile(key = option.key, label = option.label, supporting = option.count?.let(::appCount), selected = option.selected)
            },
            onTileClick = { tile -> byKey[tile.key]?.let { option -> onFilterStateChanged(option.toggled) } },
            more = content.moreCount.takeIf { it > 0 }?.let { count ->
                AppTile(key = "more", label = "$count more", supporting = content.moreNames.joinToString(", ") + "…")
            },
            onMoreClick = onShowAll,
            modifier = Modifier.padding(horizontal = AppLayout.inset),
        )
    }
}

/** Language, Format or Location: the values as check rows with their counts; "All N" past a language's five. */
@Composable
internal fun LibraryFilterChecksSection(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onShowAll: () -> Unit,
) {
    val options = remember(panel, state, facets, libraryTitlesById) {
        libraryFilterSectionOptions(panel, state, facets, libraryTitlesById, panel.topChipLimit)
    }
    val allCount = remember(panel, state, facets) { libraryFacetValueCount(panel, state, facets) }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSheetLabel(text = panel.label)
        Column(modifier = Modifier.padding(horizontal = AppLayout.inset)) {
            options.forEach { option ->
                AppCheckRow(
                    label = option.label,
                    checked = option.selected,
                    count = option.count,
                    onClick = { onFilterStateChanged(option.toggled) },
                )
            }
        }
        if (panel.opensAsPage && allCount > options.size) {
            LibraryFilterShowAllButton(label = "All ${appCount(allCount)} ${panel.pluralLabel}", onClick = onShowAll)
        }
    }
}

/** Author or Publisher: the label with what is picked, then the alphabet; a letter opens the page there. */
@Composable
internal fun LibraryFilterLettersSection(
    panel: LibraryFilterPanel,
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    libraryTitlesById: Map<String, String>,
    onOpenAtLetter: (String?) -> Unit,
) {
    val letters = remember(panel, state, facets, libraryTitlesById) { libraryFilterLetters(panel, state, facets, libraryTitlesById) }
    val summary = remember(panel, state, libraryTitlesById) { libraryFacetSelectionSummary(panel, state, libraryTitlesById) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = { onOpenAtLetter(null) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSheetLabel(text = panel.label, value = summary ?: "Any", modifier = Modifier.weight(1f))
        }
        AppLetterGrid(
            alphabets = listOf(AppLetterGridDefaults.cyrillic, AppLetterGridDefaults.latin),
            available = letters,
            onLetter = { letter -> onOpenAtLetter(letter) },
            letterLabel = { letter -> "${panel.pluralLabel.replaceFirstChar(Char::uppercase)} at $letter" },
            modifier = Modifier.padding(horizontal = AppLayout.inset),
        )
    }
}

/** Series: the series with most books as fanned cover stacks in a row that scrolls; "All N" opens the page. */
@Composable
internal fun LibraryFilterStacksSection(
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    covers: LibraryFilterSeriesCovers,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    onShowAll: () -> Unit,
) {
    val panel = LibraryFilterPanel.Series
    val series = remember(state, facets) { libraryFilterTopChips(panel, state, facets, StackLimit) }
    val allCount = remember(state, facets) { libraryFacetValueCount(panel, state, facets) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppSheetLabel(text = panel.label, modifier = Modifier.weight(1f))
            if (allCount > series.size) {
                AppTextButton(onClick = onShowAll, modifier = Modifier.padding(end = ShowAllEnd)) {
                    Text(text = "All ${appCount(allCount)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(AppCoverStackDefaults.rowGap),
        ) {
            series.forEach { option ->
                val books = remember(option.key, covers) { covers.booksOf(option.label).take(AppCoverStackDefaults.maxCovers) }
                val total = option.count
                val read = remember(option.key, covers) { covers.readCountOf(option.label) }
                AppCoverStack(
                    name = option.label,
                    line = libraryFilterSeriesLine(total, read),
                    coverCount = books.size.coerceAtLeast(1),
                    selected = option.selected,
                    onClick = { onFilterStateChanged(option.toggled) },
                    cover = { index, modifier ->
                        books.getOrNull(index)?.let { book -> covers.cover(book, modifier) }
                    },
                )
            }
        }
    }
}

/** "41 · 12 read", "9 · all read", or the count alone before any is read. */
internal fun libraryFilterSeriesLine(total: Int?, read: Int): String? {
    if (total == null) return null
    return when {
        read <= 0 -> appCount(total)
        read >= total -> "${appCount(total)} · all read"
        else -> "${appCount(total)} · ${appCount(read)} read"
    }
}

/** Set in: the places with most books as check rows with share bars; a picked country's cities under it. */
@Composable
internal fun LibraryFilterPlacesChecksSection(
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    limit: Int = PlaceRowLimit,
    onShowAll: (() -> Unit)? = null,
    showLabel: Boolean = true,
) {
    val rows = remember(state, facets, limit) { libraryFilterPlaceRows(state, facets, limit) }
    val biggest = remember(rows) { rows.maxOfOrNull { it.count ?: 0 }?.takeIf { it > 0 } }
    val allCount = remember(state, facets) { libraryFacetValueCount(LibraryFilterPanel.SetIn, state, facets) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showLabel) AppSheetLabel(text = LibraryFilterPanel.SetIn.label)
        Column(modifier = Modifier.padding(horizontal = AppLayout.inset)) {
            rows.forEach { row ->
                AppCheckRow(
                    label = row.value,
                    checked = row.selected,
                    count = row.count,
                    share = biggest?.let { max -> (row.count ?: 0).toFloat() / max },
                    indent = row.city,
                    onClick = { onFilterStateChanged(state.toggling(LibraryFilterPanel.SetIn, row.value)) },
                )
            }
        }
        if (onShowAll != null && allCount > rows.size) {
            LibraryFilterShowAllButton(label = "All ${appCount(allCount)} places", onClick = onShowAll)
        }
    }
}

/** When: the piles in time order on one line, the named periods where their years fall. */
@Composable
internal fun LibraryFilterTimelineSection(
    state: LibraryFilterState,
    facets: LibraryFilterFacets,
    onFilterStateChanged: (LibraryFilterState) -> Unit,
    showLabel: Boolean = true,
) {
    val rows = remember(state, facets) { libraryFilterTimelineRows(state, facets) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showLabel) AppSheetLabel(text = LibraryFilterPanel.When.label)
        AppTimeline(
            entries = rows.map { row ->
                AppTimelineEntry(key = row.value, label = row.value, count = row.count, selected = row.selected, period = row.period)
            },
            onToggle = { entry -> onFilterStateChanged(state.toggling(LibraryFilterPanel.When, entry.key)) },
            modifier = Modifier.padding(horizontal = AppLayout.inset),
        )
    }
}

/**
 * Length: four tiles (Short, Medium, Long, Very long) that pick a span of pages, then "Exact pages", which
 * opens the range slider; it is open by itself while the picked span is none of the four.
 */
@Composable
internal fun LibraryFilterLengthSection(
    range: LibraryPageRange?,
    histogram: LibraryPageHistogram,
    onRangeChanged: (LibraryPageRange?) -> Unit,
) {
    val picked = remember(range) { libraryLengthBuckets(range) }
    var exact by rememberSaveable { mutableStateOf(false) }
    val showSlider = exact || picked == null
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSheetLabel(text = "Length")
        AppTileGrid(
            tiles = LibraryLengthBucket.entries.map { bucket ->
                AppTile(
                    key = bucket.name,
                    label = bucket.label,
                    supporting = "${bucket.pagesLabel} · ${appCount(bucket.count(histogram))}",
                    selected = picked?.contains(bucket) == true,
                )
            },
            onTileClick = { tile ->
                val bucket = LibraryLengthBucket.valueOf(tile.key)
                onRangeChanged(libraryLengthRangeToggling(range, bucket))
            },
            modifier = Modifier.padding(horizontal = AppLayout.inset),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = { exact = !showSlider })
                .padding(horizontal = AppLayout.inset)
                .height(ExactRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Exact pages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(ExactIconGap))
            Icon(
                imageVector = AppIcons.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ExactIconSize).rotate(if (showSlider) -90f else 90f),
            )
        }
        if (showSlider) {
            LibraryPagesFilterSection(range = range, histogram = histogram, onRangeChanged = onRangeChanged)
        }
    }
}

/** "All 53 places": opens the section's full list, with its search and letters. */
@Composable
private fun LibraryFilterShowAllButton(label: String, onClick: () -> Unit) {
    AppTextButton(onClick = onClick, modifier = Modifier.padding(start = ShowAllStart)) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

private const val StackLimit = 8
private val ShowAllStart = 8.dp
private val ShowAllEnd = 8.dp
private val ExactRowHeight = 44.dp
private val ExactIconGap = 4.dp
private val ExactIconSize = 20.dp
