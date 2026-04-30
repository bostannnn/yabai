package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun JournalStatsContent(
    snapshot: JournalSnapshot,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onDeleteItemJournal: (String) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberJournalNowEpochMillis()
    val firstJournalAt = remember(snapshot) { snapshot.firstJournalAtEpochMillis() }
    var selectedMonth by rememberSaveable { mutableLongStateOf(periodBounds(JournalPeriod.MONTH, now).first) }
    var selectedYear by rememberSaveable { mutableLongStateOf(periodBounds(JournalPeriod.YEAR, now).first) }
    var selectedScope by rememberSaveable { mutableStateOf(JournalStatsScope.MONTH) }
    var detailsTarget by remember { mutableStateOf<JournalDetailsTarget?>(null) }
    var facetRowsTarget by remember { mutableStateOf<JournalFacetSection?>(null) }
    val firstMonth = remember(firstJournalAt, now) {
        periodBounds(JournalPeriod.MONTH, firstJournalAt ?: now).first
    }
    val firstYear = remember(firstJournalAt, now) {
        periodBounds(JournalPeriod.YEAR, firstJournalAt ?: now).first
    }
    LaunchedEffect(firstMonth, firstYear) {
        if (selectedMonth < firstMonth) selectedMonth = firstMonth
        if (selectedYear < firstYear) selectedYear = firstYear
    }
    val monthOverview = remember(snapshot, selectedMonth) {
        computePeriodOverview(
            snapshot = snapshot,
            period = JournalPeriod.MONTH,
            referenceEpochMillis = selectedMonth,
        )
    }
    val yearOverview = remember(snapshot, selectedYear) {
        computePeriodOverview(
            snapshot = snapshot,
            period = JournalPeriod.YEAR,
            referenceEpochMillis = selectedYear,
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val useDialog = maxWidth >= 600.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                JournalStatsScopeSelector(
                    selectedScope = selectedScope,
                    onScopeSelected = { scope -> selectedScope = scope },
                )
            }
            item {
                when (selectedScope) {
                    JournalStatsScope.MONTH -> {
                        PeriodStatsPanel(
                            overview = monthOverview,
                            title = monthOverview.monthLabel(),
                            canNavigatePrevious = selectedMonth > firstMonth,
                            canNavigateNext = selectedMonth < periodBounds(JournalPeriod.MONTH, now).first,
                            onPrevious = { selectedMonth = shiftPeriod(selectedMonth, JournalPeriod.MONTH, -1) },
                            onNext = { selectedMonth = shiftPeriod(selectedMonth, JournalPeriod.MONTH, 1) },
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onOpenItem = { summary -> detailsTarget = JournalDetailsTarget.Item(summary) },
                            onOpenFacet = { section, row, summaries ->
                                detailsTarget = JournalDetailsTarget.Facet(section, row, summaries)
                            },
                            onShowAllFacetRows = { section ->
                                facetRowsTarget = section
                            },
                        ) {
                            MonthActivityCalendar(
                                overview = monthOverview,
                                nowEpochMillis = now,
                                onActivityDaySelected = { day ->
                                    detailsTarget = JournalDetailsTarget.Day(monthOverview.dayDetails(day))
                                },
                            )
                        }
                    }

                    JournalStatsScope.YEAR -> {
                        PeriodStatsPanel(
                            overview = yearOverview,
                            title = yearOverview.yearLabel(),
                            canNavigatePrevious = selectedYear > firstYear,
                            canNavigateNext = selectedYear < periodBounds(JournalPeriod.YEAR, now).first,
                            onPrevious = { selectedYear = shiftPeriod(selectedYear, JournalPeriod.YEAR, -1) },
                            onNext = { selectedYear = shiftPeriod(selectedYear, JournalPeriod.YEAR, 1) },
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onOpenItem = { summary -> detailsTarget = JournalDetailsTarget.Item(summary) },
                            onOpenFacet = { section, row, summaries ->
                                detailsTarget = JournalDetailsTarget.Facet(section, row, summaries)
                            },
                            onShowAllFacetRows = { section ->
                                facetRowsTarget = section
                            },
                        ) {
                            YearActivityCalendar(
                                overview = yearOverview,
                                nowEpochMillis = now,
                                onActivityDaySelected = { day ->
                                    detailsTarget = JournalDetailsTarget.Day(yearOverview.dayDetails(day))
                                },
                            )
                        }
                    }
                }
            }
        }
        detailsTarget?.let { target ->
            JournalDetailsModal(
                target = target,
                useDialog = useDialog,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onSaveJournalEntry = onSaveJournalEntry,
                onDeleteItemJournal = { itemId ->
                    detailsTarget = null
                    onDeleteItemJournal(itemId)
                },
                onDismiss = { detailsTarget = null },
            )
        }
        facetRowsTarget?.let { section ->
            JournalFacetRowsDialog(
                section = section,
                onRowSelected = { row ->
                    facetRowsTarget = null
                    val overview = when (selectedScope) {
                        JournalStatsScope.MONTH -> monthOverview
                        JournalStatsScope.YEAR -> yearOverview
                    }
                    detailsTarget = JournalDetailsTarget.Facet(section, row, overview.statsFacetSummaries(row))
                },
                onDismiss = { facetRowsTarget = null },
            )
        }
    }
}

@Composable
private fun JournalStatsScopeSelector(
    selectedScope: JournalStatsScope,
    onScopeSelected: (JournalStatsScope) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        JournalStatsScope.entries.forEachIndexed { index, scope ->
            SegmentedButton(
                selected = selectedScope == scope,
                onClick = { onScopeSelected(scope) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = JournalStatsScope.entries.size,
                ),
                label = { Text(text = scope.label) },
            )
        }
    }
}

@Composable
private fun PeriodStatsPanel(
    overview: JournalPeriodOverview,
    title: String,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onOpenFacet: (JournalFacetSection, JournalFacetRow, List<JournalItemSummary>) -> Unit,
    onShowAllFacetRows: (JournalFacetSection) -> Unit,
    activityContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PeriodHeader(
            title = title,
            onPrevious = onPrevious,
            onNext = onNext,
            canNavigatePrevious = canNavigatePrevious,
            canNavigateNext = canNavigateNext,
        )
        JournalStatsMetricGrid(overview = overview)
        activityContent()
        ReadingPacePanels(overview = overview)
        AverageReadingTimeChart(
            title = if (overview.period == JournalPeriod.MONTH) {
                "Average time spent reading by hour"
            } else {
                "Average time spent reading by month"
            },
            buckets = overview.averageReadingTimeBuckets,
        )
        FinishedItemsRow(
            overview = overview,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onOpenItem = onOpenItem,
        )
        NeedsRatingItemsRow(
            overview = overview,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onOpenItem = onOpenItem,
        )
        RatingBreakdownSection(
            bookRatings = overview.stats.ratingCountsByMediaType[JournalMediaType.BOOK].orEmpty(),
            comicRatings = overview.stats.ratingCountsByMediaType[JournalMediaType.COMIC].orEmpty(),
        )
        TopFacetSections(
            sections = overview.topFacets,
            onFacetSelected = { section, row ->
                onOpenFacet(section, row, overview.statsFacetSummaries(row))
            },
            onSeeAllSelected = onShowAllFacetRows,
        )
    }
}

private fun JournalPeriodOverview.statsFacetSummaries(
    row: JournalFacetRow,
): List<JournalItemSummary> {
    val itemIds = row.itemIds.toSet()
    return finishedItems
        .filter { summary -> summary.item.itemId in itemIds }
}

@Composable
private fun PeriodHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious, enabled = canNavigatePrevious) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = onNext,
            enabled = canNavigateNext,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

private fun shiftPeriod(
    epochMillis: Long,
    period: JournalPeriod,
    amount: Int,
): Long {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = epochMillis
    }
    calendar.add(
        if (period == JournalPeriod.MONTH) Calendar.MONTH else Calendar.YEAR,
        amount,
    )
    return periodBounds(period, calendar.timeInMillis).first
}

internal fun JournalPeriodOverview.monthLabel(): String =
    SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(periodStartEpochMillis))

internal fun JournalPeriodOverview.yearLabel(): String =
    SimpleDateFormat("yyyy", Locale.US).format(Date(periodStartEpochMillis))

private enum class JournalStatsScope(
    val label: String,
) {
    MONTH("Month"),
    YEAR("Year"),
}
