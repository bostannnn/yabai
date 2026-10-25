package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The journal: the period between its arrows and Month / Year, then the period in panels.
 * Month: the summary, what you read, ratings, reading days, pages per day, when you read. Year:
 * the summary, reading days, time and pages by month, ratings. Then both: finished, needs rating
 * and most read. Two columns where the window is wide, one on phones; a panel with nothing in it
 * is left out, and a period with nothing at all shows one empty state. Days, books and names open
 * the journal's side sheet.
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
    // Whether anything has scrolled under the bar (it then needs its glass).
    onScrolledChanged: (Boolean) -> Unit = {},
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
    val loading = remember(snapshot) { snapshot.booksLoading }
    val summary = remember(overview, now, loading) { overview.summary(now, loading) }
    val mostRead = remember(overview) {
        if (overview.showsMostRead) journalMostReadGroups(overview.topFacets, overview.mediaByItemId()) else emptyList()
    }
    fun select(epochMillis: Long) {
        val start = periodBounds(period, epochMillis).first
        if (period == JournalPeriod.MONTH) selectedMonth = start else selectedYear = start
    }
    val first = if (period == JournalPeriod.MONTH) firstMonth else firstYear
    val openItem = { item: JournalItemSummary -> sheetPages = listOf(JournalSheetPage.Item(item.item.itemId)) }
    val openDay = { day: JournalActivityDay -> sheetPages = listOf(JournalSheetPage.Day(day)) }

    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val latestOnScrolledChanged by rememberUpdatedState(onScrolledChanged)
    LaunchedEffect(scrolled) { latestOnScrolledChanged(scrolled) }

    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sidePadding = appGutter()
        val wide = (maxWidth - sidePadding * 2).coerceAtMost(JournalMaxWidth) >= JournalTwoColumnMinWidth
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                start = sidePadding,
                top = contentTopInset + AppLayout.contentTop,
                end = sidePadding,
                bottom = AppLayout.contentBottom,
            ),
        ) {
            section("header") {
                JournalHeader(
                    title = if (period == JournalPeriod.MONTH) overview.monthLabel() else overview.yearLabel(),
                    scope = selectedScope,
                    onScopeSelected = { scope -> selectedScope = scope },
                    canNavigatePrevious = selected > first,
                    canNavigateNext = selected < periodBounds(period, now).first,
                    onPrevious = { select(shiftPeriod(selected, period, -1)) },
                    onNext = { select(shiftPeriod(selected, period, 1)) },
                    compact = compact,
                )
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
            section("summary") { JournalSummaryPanel(overview = overview, summary = summary) }
            val readingDays: @Composable () -> Unit = {
                if (period == JournalPeriod.MONTH) {
                    JournalMonthReadingDaysPanel(overview = overview, nowEpochMillis = now, onDaySelected = openDay)
                } else {
                    JournalYearReadingDaysPanel(overview = overview, nowEpochMillis = now, onDaySelected = openDay)
                }
            }
            val ratings: @Composable () -> Unit = { JournalRatingsPanel(overview = overview) }
            val pages: @Composable () -> Unit = { JournalPagesPanel(overview = overview) }
            if (period == JournalPeriod.MONTH) {
                val whatYouRead: @Composable () -> Unit = {
                    JournalWhatYouReadPanel(
                        overview = overview,
                        loading = loading,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onOpenItem = openItem,
                    )
                }
                val whenYouRead: @Composable () -> Unit = { JournalWhenYouReadPanel(buckets = overview.averageReadingTimeBuckets) }
                columns(
                    key = "month",
                    wide = wide,
                    start = listOf(whatYouRead, ratings),
                    end = listOf(readingDays, pages, whenYouRead),
                )
            } else {
                val timeByMonth: @Composable () -> Unit = { JournalTimeByMonthPanel(buckets = overview.averageReadingTimeBuckets) }
                columns(
                    key = "year",
                    wide = wide,
                    start = listOf(readingDays),
                    end = listOf(timeByMonth, pages, ratings),
                )
            }
            // Only rows with something in them, so an empty one leaves no gap.
            if (overview.finishedItems.isNotEmpty()) {
                section("finished") {
                    JournalFinishedPanel(
                        overview = overview,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onOpenItem = openItem,
                        onSeeAll = { sheetPages = listOf(JournalSheetPage.Finished(waitingOnly = false)) },
                    )
                }
            }
            if (finishedItemsNeedingRating(overview).isNotEmpty()) {
                section("needs-rating") {
                    JournalNeedsRatingPanel(
                        overview = overview,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onOpenItem = openItem,
                        onSeeAll = { sheetPages = listOf(JournalSheetPage.Finished(waitingOnly = true)) },
                    )
                }
            }
            if (mostRead.isNotEmpty()) {
                section("most-read") {
                    JournalMostReadPanel(
                        groups = mostRead,
                        finishedCount = overview.finishedItems.size,
                        onRowSelected = { group, row -> sheetPages = listOf(JournalSheetPage.Name(group, row)) },
                        onSeeAll = { group -> sheetPages = listOf(JournalSheetPage.AllNames(group)) },
                    )
                }
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

/** One of the journal's rows, centred and no wider than the journal's column. */
private fun LazyListScope.section(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        Box(modifier = Modifier.widthIn(max = JournalMaxWidth).fillMaxWidth()) { content() }
    }
}

/**
 * Panels in two columns, seven twelfths and five, where the journal is wide; one after another
 * where it isn't. A panel with nothing to show draws nothing and leaves no gap.
 */
private fun LazyListScope.columns(
    key: String,
    wide: Boolean,
    start: List<@Composable () -> Unit>,
    end: List<@Composable () -> Unit>,
) {
    if (wide) {
        section(key) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(7f), verticalArrangement = Arrangement.spacedBy(16.dp)) { start.forEach { it() } }
                Column(modifier = Modifier.weight(5f), verticalArrangement = Arrangement.spacedBy(16.dp)) { end.forEach { it() } }
            }
        }
    } else {
        // One item, so a panel that folds away doesn't leave its gap in the list.
        section(key) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { (start + end).forEach { it() } }
        }
    }
}

/**
 * The period's name between its arrows, Month / Year at the end; on phones the arrows follow the
 * name and Month / Year takes its own line. No "Journal" above it: the rail or the drawer says it.
 */
@Composable
private fun JournalHeader(
    title: String,
    scope: JournalStatsScope,
    onScopeSelected: (JournalStatsScope) -> Unit,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean,
) {
    val segmented = @Composable { segmentModifier: Modifier ->
        AppSegmentedRow(modifier = segmentModifier) {
            JournalStatsScope.entries.forEach { entry ->
                AppSegment(
                    selected = scope == entry,
                    onClick = { onScopeSelected(entry) },
                    label = { Text(text = entry.label) },
                )
            }
        }
    }
    val previous = @Composable {
        AppIconButton(onClick = onPrevious, enabled = canNavigatePrevious) {
            Icon(imageVector = AppIcons.KeyboardArrowLeft, contentDescription = "Previous period")
        }
    }
    val next = @Composable {
        AppIconButton(onClick = onNext, enabled = canNavigateNext) {
            Icon(imageVector = AppIcons.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
    val periodTitle = @Composable { titleModifier: Modifier ->
        Text(
            text = title,
            modifier = titleModifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (compact) 26.sp else 30.sp,
                lineHeight = if (compact) 32.sp else 36.sp,
                letterSpacing = (-0.01).em,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                periodTitle(Modifier.weight(1f))
                Row(modifier = Modifier.offset(x = 8.dp)) {
                    previous()
                    next()
                }
            }
            segmented(Modifier.fillMaxWidth())
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The arrow before the name sits out in the margin, so its glyph lines up with the panels.
            Row(
                modifier = Modifier.weight(1f).offset(x = (-12).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                previous()
                periodTitle(Modifier)
                next()
            }
            segmented(Modifier.width(240.dp))
        }
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

private val JournalMaxWidth = 1140.dp
private val JournalTwoColumnMinWidth = 880.dp
