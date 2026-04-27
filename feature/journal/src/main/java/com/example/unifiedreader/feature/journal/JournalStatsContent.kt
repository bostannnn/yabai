package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
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
    var selectedDayDetails by remember { mutableStateOf<JournalDayDetails?>(null) }
    var detailTarget by remember { mutableStateOf<JournalItemSummary?>(null) }
    var editTarget by remember { mutableStateOf<JournalItemRef?>(null) }
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

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            PeriodStatsPanel(
                overview = monthOverview,
                title = monthOverview.monthLabel(),
                canNavigatePrevious = selectedMonth > firstMonth,
                canNavigateNext = selectedMonth < periodBounds(JournalPeriod.MONTH, now).first,
                onPrevious = { selectedMonth = shiftPeriod(selectedMonth, JournalPeriod.MONTH, -1) },
                onNext = { selectedMonth = shiftPeriod(selectedMonth, JournalPeriod.MONTH, 1) },
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onOpenItem = { summary -> detailTarget = summary },
            ) {
                MonthActivityCalendar(
                    overview = monthOverview,
                    nowEpochMillis = now,
                    onActivityDaySelected = { day ->
                        selectedDayDetails = monthOverview.dayDetails(day)
                    },
                )
            }
        }
        item {
            PeriodStatsPanel(
                overview = yearOverview,
                title = yearOverview.yearLabel(),
                canNavigatePrevious = selectedYear > firstYear,
                canNavigateNext = selectedYear < periodBounds(JournalPeriod.YEAR, now).first,
                onPrevious = { selectedYear = shiftPeriod(selectedYear, JournalPeriod.YEAR, -1) },
                onNext = { selectedYear = shiftPeriod(selectedYear, JournalPeriod.YEAR, 1) },
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onOpenItem = { summary -> detailTarget = summary },
            ) {
                YearActivityCalendar(
                    overview = yearOverview,
                    nowEpochMillis = now,
                    onActivityDaySelected = { day ->
                        selectedDayDetails = yearOverview.dayDetails(day)
                    },
                )
            }
        }
    }
    selectedDayDetails?.let { details ->
        JournalDayDetailsSheet(
            details = details,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onOpenItem = { summary ->
                selectedDayDetails = null
                detailTarget = summary
            },
            onDismiss = { selectedDayDetails = null },
        )
    }
    detailTarget?.let { summary ->
        JournalItemDetailSheet(
            summary = summary,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onDismiss = { detailTarget = null },
            onReview = {
                detailTarget = null
                editTarget = summary.item
            },
            onDeleteItemJournal = {
                onDeleteItemJournal(summary.item.itemId)
                detailTarget = null
            },
        )
    }
    editTarget?.let { item ->
        JournalEntryEditorSheet(
            item = item,
            existingEntry = snapshot.entryByItemId[item.itemId],
            onDismiss = { editTarget = null },
            onSave = { rating, review ->
                onSaveJournalEntry(item, rating, review)
                editTarget = null
            },
        )
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
    activityContent: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PeriodHeader(
                title = title,
                onPrevious = onPrevious,
                onNext = onNext,
                canNavigatePrevious = canNavigatePrevious,
                canNavigateNext = canNavigateNext,
            )
            JournalStatsMetricGrid(stats = overview.stats)
            activityContent()
            FinishedItemsRow(
                overview = overview,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onOpenItem = onOpenItem,
            )
            RatingBreakdown(values = overview.stats.ratingCounts)
        }
    }
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

private fun JournalPeriodOverview.monthLabel(): String =
    SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(periodStartEpochMillis))

private fun JournalPeriodOverview.yearLabel(): String =
    SimpleDateFormat("yyyy", Locale.US).format(Date(periodStartEpochMillis))
