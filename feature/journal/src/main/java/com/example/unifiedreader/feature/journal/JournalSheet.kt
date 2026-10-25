package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppScreenBackHandler
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.LocalAppScreenBackGesture

/** What the journal's side sheet shows. Pages stack: Back returns to the one before, × closes. */
internal sealed interface JournalSheetPage {
    data class Day(val day: JournalActivityDay) : JournalSheetPage

    data class Item(val itemId: String) : JournalSheetPage

    /** The finished books carrying one name under Most read. */
    data class Name(val group: JournalMostReadGroup, val row: JournalFacetRow) : JournalSheetPage

    /** Every name of one kind, from its See all. */
    data class AllNames(val group: JournalMostReadGroup) : JournalSheetPage

    /** The period's finished books, or only the ones waiting for a rating. */
    data class Finished(val waitingOnly: Boolean) : JournalSheetPage
}

/**
 * The journal's side sheet (a bottom sheet on phones), like the library's book page: a day's
 * books, a name's books, a kind's names, or one book's journal with its rating, review and
 * reading sessions. A rating saves when it is tapped and a review when its field is left.
 */
@Composable
internal fun JournalSheet(
    pages: List<JournalSheetPage>,
    overview: JournalPeriodOverview,
    itemSummaries: List<JournalItemSummary>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenPage: (JournalSheetPage) -> Unit,
    onBack: () -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onOpenInLibrary: ((ReadableItem) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val page = pages.lastOrNull() ?: return
    AppSideSheet(onDismissRequest = onDismiss) {
        // Plays the sheet's exit before it closes.
        val dismiss = { dismiss() }
        // Back steps to the page before; the journal under the sheet stays put.
        CompositionLocalProvider(LocalAppScreenBackGesture provides null) {
            AppScreenBackHandler(enabled = pages.size > 1) { onBack() }
        }
        // Back and Close where every sheet has them; each page's title is its first content.
        AppPanelHeader(
            title = null,
            onBack = if (pages.size > 1) onBack else null,
            onClose = { dismiss() },
        )
        // Each page starts at its top.
        val listState = remember(page) { LazyListState() }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = AppLayout.inset, end = AppLayout.inset, bottom = 28.dp),
        ) {
            when (page) {
                is JournalSheetPage.Day -> dayPage(
                    day = page.day,
                    entries = overview.dayEntries(page.day),
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpenItem = { itemId -> onOpenPage(JournalSheetPage.Item(itemId)) },
                )
                is JournalSheetPage.Name -> namePage(
                    page = page,
                    summaries = overview.facetSummaries(page.row),
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpenItem = { itemId -> onOpenPage(JournalSheetPage.Item(itemId)) },
                )
                is JournalSheetPage.AllNames -> allNamesPage(
                    group = page.group,
                    onOpenName = { row -> onOpenPage(JournalSheetPage.Name(page.group, row)) },
                )
                is JournalSheetPage.Finished -> finishedPage(
                    title = if (page.waitingOnly) "Waiting for a rating" else "Finished in ${overview.periodName()}",
                    summaries = if (page.waitingOnly) finishedItemsNeedingRating(overview) else overview.finishedItems,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpenItem = { itemId -> onOpenPage(JournalSheetPage.Item(itemId)) },
                )
                is JournalSheetPage.Item -> item(key = "item:${page.itemId}") {
                    val summary = itemSummaries.firstOrNull { it.item.itemId == page.itemId }
                    if (summary == null) {
                        // Removed while open: step back, or close when it was the first page.
                        LaunchedEffect(page.itemId) { if (pages.size > 1) onBack() else onDismiss() }
                    } else {
                        JournalItemPage(
                            summary = summary,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onSaveJournalEntry = onSaveJournalEntry,
                            onOpenInLibrary = onOpenInLibrary?.let { open ->
                                summary.catalogItem?.let { catalogItem -> { open(catalogItem); dismiss() } }
                            },
                            onRemove = {
                                dismiss()
                                onRemoveItem(summary.item.itemId)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.pageTitle(title: String, summary: String?) {
    item(key = "title") {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp), fontWeight = FontWeight.Bold)
            summary?.takeIf(String::isNotBlank)?.let { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.dayPage(
    day: JournalActivityDay,
    entries: List<JournalDayEntry>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    pageTitle(title = day.longLabel(), summary = journalDaySummary(day, entries))
    items(entries, key = { entry -> entry.summary.item.itemId }) { entry ->
        JournalSheetBookRow(
            summary = entry.summary,
            line = entry.line(),
            showRate = entry.finishedAtEpochMillis != null,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onClick = { onOpenItem(entry.summary.item.itemId) },
        )
    }
}

private fun LazyListScope.namePage(
    page: JournalSheetPage.Name,
    summaries: List<JournalItemSummary>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val count = page.group.rows.firstOrNull { it.row.label == page.row.label }?.countLabel
    pageTitle(title = page.row.label, summary = listOfNotNull(page.group.title, count).joinToString(" · "))
    items(summaries, key = { summary -> summary.item.itemId }) { summary ->
        JournalSheetBookRow(
            summary = summary,
            line = summary.finishedAtEpochMillis?.let { "Finished ${formatShortDate(it)}" }.orEmpty(),
            showRate = true,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onClick = { onOpenItem(summary.item.itemId) },
        )
    }
}

private fun LazyListScope.finishedPage(
    title: String,
    summaries: List<JournalItemSummary>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    pageTitle(title = title, summary = "${summaries.size}, newest first")
    items(summaries, key = { summary -> summary.item.itemId }) { summary ->
        JournalSheetBookRow(
            summary = summary,
            line = summary.finishedAtEpochMillis?.let { "Finished ${formatShortDate(it)}" }.orEmpty(),
            showRate = true,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            onClick = { onOpenItem(summary.item.itemId) },
        )
    }
}

private fun LazyListScope.allNamesPage(
    group: JournalMostReadGroup,
    onOpenName: (JournalFacetRow) -> Unit,
) {
    val noun = group.title.lowercase().let { plural -> if (group.rows.size == 1) plural.removeSuffix("s") else plural }
    pageTitle(title = group.title, summary = "${group.rows.size} $noun")
    val maxCount = group.rows.maxOfOrNull { it.row.count }?.coerceAtLeast(1) ?: 1
    items(group.rows, key = { row -> row.row.label }) { row ->
        JournalMostReadRowItem(row = row, maxCount = maxCount, onClick = { onOpenName(row.row) })
    }
}

/** A book in a sheet's list: its cover, title, one line, and its stars or Rate. */
@Composable
private fun JournalSheetBookRow(
    summary: JournalItemSummary,
    line: String,
    showRate: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onClick: () -> Unit,
) {
    AppBookRow(
        title = summary.item.displayTitle,
        onClick = onClick,
        cover = {
            JournalCoverArt(
                item = summary.item,
                catalogItem = summary.catalogItem,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                modifier = Modifier
                    .width(AppBookRowDefaults.coverWidth)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
        supporting = {
            if (line.isNotBlank()) AppBookRowLine(text = line)
        },
    ) {
        val rating = summary.entry?.rating
        when {
            rating != null -> JournalStars(rating = rating)
            showRate -> Text(
                text = "Rate",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Read-only stars for a rating out of 10. */
@Composable
internal fun JournalStars(
    rating: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        (0 until 5).forEach { index ->
            val filled = (rating - index * 2).coerceIn(0, 2)
            Icon(
                imageVector = when (filled) {
                    2 -> AppIcons.Star
                    1 -> AppIcons.StarHalf
                    else -> AppIcons.StarBorder
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (filled > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/** One book's journal: what it is, the rating and review, the reading sessions, and removal. */
@Composable
private fun JournalItemPage(
    summary: JournalItemSummary,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onOpenInLibrary: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val item = summary.item
    val entry = summary.entry
    val reviewState = rememberJournalReviewState(
        itemId = item.itemId,
        initialRating = entry?.rating,
        initialReviewText = entry?.reviewText.orEmpty(),
    )
    val latestSave by rememberUpdatedState {
        if (reviewState.reviewText.trim() != entry?.reviewText.orEmpty().trim()) {
            onSaveJournalEntry(item, reviewState.selectedRating, reviewState.reviewText.trim())
        }
    }
    // A review still being typed is kept when the page or the sheet goes away.
    DisposableEffect(item.itemId) { onDispose { latestSave() } }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            JournalCoverArt(
                item = item,
                catalogItem = summary.catalogItem,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                imageVariant = LibraryCoverImageVariant.Grid,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                item.displaySubtitle?.let { subtitle ->
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                itemActivityLine(summary).takeIf(String::isNotBlank)?.let { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onOpenInLibrary?.let { open ->
                    AppTonalButton(onClick = open, modifier = Modifier.padding(top = 8.dp).height(42.dp)) {
                        Icon(imageVector = AppIcons.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = "Open in library", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemPageLabel("Your rating")
            JournalRatingEditor(
                selectedRating = reviewState.selectedRating,
                onRatingSelected = { rating ->
                    reviewState.selectedRating = rating
                    onSaveJournalEntry(item, rating, reviewState.reviewText.trim())
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemPageLabel("Review")
            AppTextField(
                value = reviewState.reviewText,
                onValueChange = { reviewState.reviewText = it },
                placeholder = { Text(text = "Only you can see this") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus -> if (!focus.hasFocus) latestSave() },
            )
        }
        val sessions = summary.sessions.sortedByDescending(JournalReadingSession::startedAtEpochMillis)
        if (sessions.isNotEmpty()) {
            Column {
                ItemPageLabel("Sessions")
                Spacer(modifier = Modifier.height(4.dp))
                sessions.take(MaxSessionsShown).forEach { session ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = "${formatSessionDay(session.startedAtEpochMillis)} · ${formatJournalTime(session.startedAtEpochMillis)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            text = listOfNotNull(
                                session.positivePageDelta().takeIf { it > 0 }?.counted("page"),
                                session.durationMillis.takeIf { it > 0L }?.let(::formatJournalMinutes),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (sessions.size > MaxSessionsShown) {
                    Text(
                        text = "and ${sessions.size - MaxSessionsShown} earlier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box {
            AppTextButton(
                onClick = onRemove,
                colors = AppButtonDefaults.destructiveTextColors(),
            ) {
                Text(text = "Remove from journal", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ItemPageLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Finished 17 Oct · 3 sessions · 1 h 1 min". */
private fun itemActivityLine(summary: JournalItemSummary): String = listOfNotNull(
    summary.finishedAtEpochMillis?.let { "Finished ${formatShortDate(it)}" },
    summary.sessionCount.takeIf { it > 0 }?.counted("session"),
    summary.totalReadingTimeMillis.takeIf { it > 0L }?.let(::formatJournalMinutes),
).joinToString(" · ")

private fun formatSessionDay(epochMillis: Long): String =
    java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.US).format(java.util.Date(epochMillis))

private const val MaxSessionsShown = 20
