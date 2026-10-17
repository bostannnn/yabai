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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppDialogHost
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern

internal sealed interface JournalDetailsTarget {
    data class Day(
        val details: JournalDayDetails,
    ) : JournalDetailsTarget

    data class Item(
        val summary: JournalItemSummary,
    ) : JournalDetailsTarget

    data class Facet(
        val section: JournalFacetSection,
        val row: JournalFacetRow,
        val summaries: List<JournalItemSummary>,
    ) : JournalDetailsTarget
}

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
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val sheetRecipe = UnifiedReaderThemeTokens.components.sheet
    val dialogShape = RoundedCornerShape(presentation.cardCornerRadius)
    if (useDialog) {
        AppDialogHost(onDismissRequest = onDismiss) {
            AppPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .unifiedReaderComponentTreatment(
                        recipe = sheetRecipe,
                        accentColor = semanticColors.readerAccent,
                        secondaryColor = semanticColors.syncAccent,
                        overlayColor = MaterialTheme.colorScheme.onSurface,
                        shape = dialogShape,
                    ),
                shape = dialogShape,
                color = semanticColors.surfaceBase,
                tonalElevation = 6.dp,
            ) {
                JournalDetailsContent(
                    target = target,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onSaveJournalEntry = onSaveJournalEntry,
                    onDeleteItemJournal = onDeleteItemJournal,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        AppSheet(
            onDismissRequest = onDismiss,
        ) {
            JournalDetailsContent(
                target = target,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onSaveJournalEntry = onSaveJournalEntry,
                onDeleteItemJournal = onDeleteItemJournal,
                onDismiss = onDismiss,
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summaries = target.summaries()
    val semanticColors = UnifiedReaderThemeTokens.colors
    val sheetRecipe = UnifiedReaderThemeTokens.components.sheet
    LazyColumn(
        modifier = modifier
            .unifiedReaderSurfacePattern(
                treatment = sheetRecipe.surfaceTreatment,
                accentColor = semanticColors.readerAccent,
                overlayColor = MaterialTheme.colorScheme.onSurface,
                intensity = sheetRecipe.patternIntensity,
            )
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            JournalDetailsHeader(target = target, onDismiss = onDismiss)
        }
        if (summaries.isEmpty()) {
            item {
                Text(
                    text = "No items.",
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
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
        AppIconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
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
        AppTextButton(onClick = onDeleteItemJournal) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            Text(text = "Remove", modifier = Modifier.padding(start = 8.dp))
        }
        AppButton(
            onClick = {
                onSaveJournalEntry(item, reviewState.selectedRating, reviewState.reviewText)
            },
        ) {
            Text(text = "Save review")
        }
    }
}

private fun JournalDetailsTarget.summaries(): List<JournalItemSummary> = when (this) {
    is JournalDetailsTarget.Day -> details.finishedItems
    is JournalDetailsTarget.Item -> listOf(summary)
    is JournalDetailsTarget.Facet -> summaries
}

private val JournalDetailsTarget.title: String
    get() = when (this) {
        is JournalDetailsTarget.Day -> details.dayTitle()
        is JournalDetailsTarget.Item -> summary.item.displayTitle
        is JournalDetailsTarget.Facet -> row.label
    }

private val JournalDetailsTarget.subtitle: String?
    get() = when (this) {
        is JournalDetailsTarget.Day -> details.daySummary()
        is JournalDetailsTarget.Item -> summaryActivityText(summary).takeIf(String::isNotBlank)
        is JournalDetailsTarget.Facet -> "${section.title} · ${row.count} items"
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
    summary.totalReadingTimeMillis.takeIf { it > 0L }?.let { "${formatDuration(it)} reading" },
    summary.sessionCount.takeIf { it > 0 }?.let { "$it sessions" },
    summary.pagesRead.takeIf { it > 0 }?.let { "$it pages read" },
).joinToString(" · ")

private fun formatDuration(
    millis: Long,
): String {
    val minutes = (millis / 60_000L).coerceAtLeast(1L)
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return if (hours > 0L) {
        "${hours}h ${remainingMinutes}m"
    } else {
        "${minutes}m"
    }
}

private fun monthName(
    month: Int,
): String = java.text.DateFormatSymbols.getInstance(java.util.Locale.US).months[month]
