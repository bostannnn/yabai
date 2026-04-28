package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

internal sealed interface JournalDetailsTarget {
    data class Day(
        val details: JournalDayDetails,
    ) : JournalDetailsTarget

    data class Item(
        val summary: JournalItemSummary,
    ) : JournalDetailsTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalDetailsModal(
    target: JournalDetailsTarget,
    useDialog: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onDeleteItemJournal: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (useDialog) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                JournalDetailsContent(
                    target = target,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onSaveJournalEntry = onSaveJournalEntry,
                    onDeleteItemJournal = onDeleteItemJournal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            JournalDetailsContent(
                target = target,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onSaveJournalEntry = onSaveJournalEntry,
                onDeleteItemJournal = onDeleteItemJournal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun JournalDetailsContent(
    target: JournalDetailsTarget,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onDeleteItemJournal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summaries = target.finishedSummaries()
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            JournalDetailsHeader(target = target)
        }
        if (summaries.isEmpty()) {
            item {
                Text(
                    text = "No finished items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(summaries, key = { summary -> summary.item.itemId }) { summary ->
                JournalDetailsItemCard(
                    summary = summary,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onSaveJournalEntry = onSaveJournalEntry,
                    onDeleteItemJournal = { onDeleteItemJournal(summary.item.itemId) },
                )
            }
        }
    }
}

@Composable
private fun JournalDetailsHeader(
    target: JournalDetailsTarget,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = target.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        target.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JournalDetailsItemCard(
    summary: JournalItemSummary,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onDeleteItemJournal: () -> Unit,
) {
    val item = summary.item
    val entry = summary.entry
    val reviewState = rememberJournalReviewState(
        itemId = item.itemId,
        initialRating = entry?.rating,
        initialReviewText = entry?.reviewText.orEmpty(),
    )
    JournalItemReviewCard(
        item = item,
        reviewState = reviewState,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        activityText = summaryActivityText(summary).takeIf(String::isNotBlank),
        onRatingSelected = { rating ->
            onSaveJournalEntry(item, rating, reviewState.reviewText)
        },
    ) {
        TextButton(onClick = onDeleteItemJournal) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            Text(text = "Remove", modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = {
                onSaveJournalEntry(item, reviewState.selectedRating, reviewState.reviewText)
            },
        ) {
            Text(text = "Save review")
        }
    }
}

private fun JournalDetailsTarget.finishedSummaries(): List<JournalItemSummary> = when (this) {
    is JournalDetailsTarget.Day -> details.finishedItems
    is JournalDetailsTarget.Item -> listOf(summary)
}

private val JournalDetailsTarget.title: String
    get() = when (this) {
        is JournalDetailsTarget.Day -> details.dayTitle()
        is JournalDetailsTarget.Item -> summary.item.displayTitle
    }

private val JournalDetailsTarget.subtitle: String?
    get() = when (this) {
        is JournalDetailsTarget.Day -> details.daySummary()
        is JournalDetailsTarget.Item -> summaryActivityText(summary).takeIf(String::isNotBlank)
    }

private fun JournalDayDetails.dayTitle(): String =
    "${monthName(activityDay.month)} ${activityDay.dayOfMonth}, ${activityDay.year}"

private fun JournalDayDetails.daySummary(): String {
    val finishedCount = activityDay.finishedBooks + activityDay.finishedComics
    return listOf(
        "${activityDay.pagesRead} pages",
        "$finishedCount finished",
    ).joinToString(" · ")
}

private fun summaryActivityText(
    summary: JournalItemSummary,
): String = listOfNotNull(
    summary.finishedAtEpochMillis?.let { "Finished ${formatDateTime(it)}" },
    summary.pagesRead.takeIf { it > 0 }?.let { "$it pages read" },
).joinToString(" · ")

private fun monthName(
    month: Int,
): String = java.text.DateFormatSymbols.getInstance(java.util.Locale.US).months[month]
