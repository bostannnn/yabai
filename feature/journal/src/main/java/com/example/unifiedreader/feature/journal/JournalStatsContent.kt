package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The journal: Month or Year, the period as a title, then its sections in order (the four
 * figures, pages per day, books and comics, reading days, when you read, finished, needs rating,
 * ratings, most read). A section with nothing in it is left out, and a period with nothing at all
 * shows one empty state. Days, books and names open the journal's side sheet.
 */
@Composable
fun JournalStatsContent(
    snapshot: JournalSnapshot,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onDeleteItemJournal: (String) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onOpenInLibrary: ((ReadableItem) -> Unit)? = null,
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val now = rememberJournalNowEpochMillis()
    val firstJournalAt = remember(snapshot) { snapshot.firstJournalAtEpochMillis() }
    val lastReadAt = remember(snapshot) { snapshot.lastReadAtEpochMillis() }
    var selectedMonth by rememberSaveable { mutableLongStateOf(periodBounds(JournalPeriod.MONTH, now).first) }
    var selectedYear by rememberSaveable { mutableLongStateOf(periodBounds(JournalPeriod.YEAR, now).first) }
    var selectedScope by rememberSaveable { mutableStateOf(JournalStatsScope.MONTH) }
    var sheetPages by remember { mutableStateOf<List<JournalSheetPage>>(emptyList()) }
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
    val period = selectedScope.period
    val selected = if (period == JournalPeriod.MONTH) selectedMonth else selectedYear
    val overview = remember(snapshot, period, selected) {
        computePeriodOverview(snapshot = snapshot, period = period, referenceEpochMillis = selected)
    }
    val mostRead = remember(overview) { journalMostReadGroups(overview.topFacets, overview.mediaByItemId()) }
    fun select(epochMillis: Long) {
        val start = periodBounds(period, epochMillis).first
        if (period == JournalPeriod.MONTH) selectedMonth = start else selectedYear = start
    }
    val first = if (period == JournalPeriod.MONTH) firstMonth else firstYear
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(36.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp + contentTopInset, end = 20.dp, bottom = 40.dp),
    ) {
        section("period") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AppSegmentedRow(modifier = Modifier.width(240.dp)) {
                    JournalStatsScope.entries.forEach { scope ->
                        AppSegment(
                            selected = selectedScope == scope,
                            onClick = { selectedScope = scope },
                            label = { Text(text = scope.label) },
                        )
                    }
                }
                PeriodHeader(
                    title = if (period == JournalPeriod.MONTH) overview.monthLabel() else overview.yearLabel(),
                    canNavigatePrevious = selected > first,
                    canNavigateNext = selected < periodBounds(period, now).first,
                    onPrevious = { select(shiftPeriod(selected, period, -1)) },
                    onNext = { select(shiftPeriod(selected, period, 1)) },
                )
            }
        }
        if (overview.isEmpty) {
            section("empty") {
                EmptyPeriod(
                    overview = overview,
                    lastReadAt = lastReadAt,
                    onGoTo = ::select,
                )
            }
            return@LazyColumn
        }
        val periodName = overview.periodName()
        section("figures") { JournalFigures(overview = overview) }
        val hasReadingDays = overview.activityDays.any { it.heatValue > 0 }
        if (hasReadingDays) {
            section("pages") { JournalPagesChart(overview = overview) }
        }
        if (overview.stats.bookPagesRead > 0 || overview.stats.comicPagesRead > 0) {
            section("mix") { JournalBooksAndComics(overview = overview) }
        }
        if (hasReadingDays) {
            section("days") {
                val openDay = { day: JournalActivityDay -> sheetPages = listOf(JournalSheetPage.Day(day)) }
                if (period == JournalPeriod.MONTH) {
                    JournalMonthReadingDays(overview = overview, nowEpochMillis = now, onDaySelected = openDay)
                } else {
                    JournalYearReadingDays(overview = overview, nowEpochMillis = now, onDaySelected = openDay)
                }
            }
        }
        if (overview.averageReadingTimeBuckets.any { it.averageMillis > 0L }) {
            section("when") { JournalWhenYouRead(period = period, buckets = overview.averageReadingTimeBuckets) }
        }
        val openItem = { summary: JournalItemSummary -> sheetPages = listOf(JournalSheetPage.Item(summary.item.itemId)) }
        if (overview.finishedItems.isNotEmpty()) {
            section("finished") {
                FinishedItemsRow(
                    overview = overview,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpenItem = openItem,
                )
            }
        }
        if (finishedItemsNeedingRating(overview).isNotEmpty()) {
            section("needs-rating") {
                NeedsRatingItemsRow(
                    overview = overview,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpenItem = openItem,
                )
            }
        }
        if (overview.stats.ratingCountsByMediaType.values.any { counts -> counts.values.sum() > 0 }) {
            section("ratings") { JournalRatings(overview = overview, periodName = periodName) }
        }
        if (mostRead.isNotEmpty()) {
            section("most-read") {
                JournalMostRead(
                    groups = mostRead,
                    periodName = periodName,
                    onRowSelected = { group, row -> sheetPages = listOf(JournalSheetPage.Name(group, row)) },
                    onSeeAll = { group -> sheetPages = listOf(JournalSheetPage.AllNames(group)) },
                )
            }
        }
    }
    if (sheetPages.isNotEmpty()) {
        JournalSheet(
            pages = sheetPages,
            overview = overview,
            itemSummaries = snapshot.itemSummaries,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onOpenPage = { page -> sheetPages = sheetPages + page },
            onBack = { sheetPages = sheetPages.dropLast(1) },
            onSaveJournalEntry = onSaveJournalEntry,
            onRemoveItem = onDeleteItemJournal,
            onOpenInLibrary = onOpenInLibrary,
            onDismiss = { sheetPages = emptyList() },
        )
    }
}

/** One of the journal's sections, centred and no wider than a comfortable reading column. */
private fun LazyListScope.section(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        Box(modifier = Modifier.widthIn(max = JournalMaxWidth).fillMaxWidth()) { content() }
    }
}

/** Nothing read in the period: the kit's empty state, and a way to the latest period that has reading. */
@Composable
private fun EmptyPeriod(
    overview: JournalPeriodOverview,
    lastReadAt: Long?,
    onGoTo: (Long) -> Unit,
) {
    val periodName = overview.periodName()
    val target = lastReadAt?.takeUnless { at -> at in overview.periodStartEpochMillis until overview.periodEndEpochMillis }
    val targetName = target?.let { at ->
        SimpleDateFormat(if (overview.period == JournalPeriod.MONTH) "MMMM" else "yyyy", Locale.US).format(Date(at))
    }
    AppEmptyState(
        title = if (lastReadAt == null) "Nothing in your journal yet" else "No reading in $periodName",
        body = if (lastReadAt == null) "Books and comics you read show up here." else "What you read shows up here as you read it.",
        modifier = Modifier.padding(top = 40.dp),
        actions = if (target != null && targetName != null) {
            { AppButton(onClick = { onGoTo(target) }) { Text(text = "Go to $targetName") } }
        } else {
            null
        },
    )
}

@Composable
private fun PeriodHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(onClick = onPrevious, enabled = canNavigatePrevious) {
            Icon(imageVector = AppIcons.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        AppIconButton(onClick = onNext, enabled = canNavigateNext) {
            Icon(imageVector = AppIcons.KeyboardArrowRight, contentDescription = "Next period")
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
    val period: JournalPeriod,
) {
    MONTH("Month", JournalPeriod.MONTH),
    YEAR("Year", JournalPeriod.YEAR),
}

private val JournalMaxWidth = 960.dp
