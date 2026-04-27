package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var selectedRating by rememberSaveable(item.itemId, entry?.rating) {
        mutableStateOf(entry?.rating)
    }
    var reviewText by rememberSaveable(item.itemId, entry?.reviewText) {
        mutableStateOf(entry?.reviewText.orEmpty())
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                JournalCoverArt(
                    item = item,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(0.68f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.displaySubtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${item.sourceType.displayName()} · ${item.format.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    summaryActivityText(summary).takeIf(String::isNotBlank)?.let { activity ->
                        Text(
                            text = activity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            JournalRatingEditor(
                selectedRating = selectedRating,
                onRatingSelected = { rating ->
                    selectedRating = rating
                    onSaveJournalEntry(item, rating, reviewText)
                },
            )
            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text(text = "Private review") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDeleteItemJournal) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Text(text = "Remove", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = {
                        onSaveJournalEntry(item, selectedRating, reviewText)
                    },
                ) {
                    Text(text = "Save review")
                }
            }
        }
    }
}

@Composable
private fun JournalRatingEditor(
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = selectedRating?.let { "$it / 5" } ?: "No rating",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..5).forEach { rating ->
                val isSelected = selectedRating != null && rating <= selectedRating
                IconButton(
                    onClick = { onRatingSelected(rating) },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "$rating star rating",
                        modifier = Modifier.size(30.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
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
