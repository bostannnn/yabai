package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListCover
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppTextButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A list read to the end, as the journal keeps it; the list itself may be gone. [books] are the ones still in the library, in its order. */
data class JournalFinishedList(
    val id: String,
    val name: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val bookCount: Int,
    val seriesCount: Int,
    val readMillis: Long,
    val books: List<ReadableItem>,
)

/** Draws a book's cover filling the modifier it is given. */
typealias JournalListCover = @Composable (item: ReadableItem, modifier: Modifier) -> Unit

/** The period's finished lists: a row each, its mosaic, its name and what it took. */
@Composable
internal fun JournalFinishedListsPanel(
    lists: List<JournalFinishedList>,
    periodName: String,
    cover: JournalListCover,
    modifier: Modifier = Modifier,
) {
    if (lists.isEmpty()) return
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(
            title = "Lists finished",
            summary = if (lists.size == 1) "1 in $periodName" else "${lists.size} in $periodName",
        )
        Column(
            modifier = Modifier.padding(top = AppLayout.headingGap),
            verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        ) {
            lists.forEach { list ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(56.dp)) {
                        AppListCover(count = list.books.size.coerceAtMost(4)) { index, cellModifier -> cover(list.books[index], cellModifier) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = list.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = list.figuresLine(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = list.datesLine(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The card over Finished when a list is read to the end: its mosaic and name, when it was started
 * and finished, four figures, its covers, and where the journal keeps it. [onDeleteList] is null
 * once the list is gone.
 */
@Composable
fun JournalListFinishedDialog(
    list: JournalFinishedList,
    cover: JournalListCover,
    onDeleteList: (() -> Unit)?,
    onDone: () -> Unit,
) {
    val now = System.currentTimeMillis()
    AppDialogCard(
        onDismissRequest = onDone,
        title = list.name,
        subtitle = "List finished · ${list.datesLine(now)}",
        leading = {
            Box(modifier = Modifier.width(52.dp)) {
                AppListCover(count = list.books.size.coerceAtMost(4)) { index, cellModifier -> cover(list.books[index], cellModifier) }
            }
        },
        actions = {
            if (onDeleteList != null) {
                AppTextButton(onClick = onDeleteList) {
                    Text(text = "Delete the list", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            AppDialogConfirmButton(text = "Done", onClick = onDone)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.figures().forEach { (value, label) ->
                    AppPanel(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(AppLayout.headingGap)) {
                            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (list.books.isNotEmpty()) {
                // Room for fourteen covers, so a short list's covers stay cover-sized.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(FinishedListCoverSlots) { index ->
                        val book = list.books.getOrNull(index)
                        Box(modifier = Modifier.weight(1f).aspectRatio(2f / 3f)) {
                            if (book != null) cover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        }
                    }
                }
            }
            Text(
                text = "It goes in your journal for ${monthName(list.finishedAtEpochMillis)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val FinishedListCoverSlots = 14

/** "14 books · 4 series · 9 h 20 min read". */
internal fun JournalFinishedList.figuresLine(): String = listOfNotNull(
    if (bookCount == 1) "1 book" else "$bookCount books",
    seriesCount.takeIf { it > 0 }?.let { if (it == 1) "1 series" else "$it series" },
    readMillis.takeIf { it > 0 }?.let { "${formatJournalReadingTime(it)} read" },
).joinToString(" · ")

/** The card's four figures: books, series, time read and days, value over label. */
internal fun JournalFinishedList.figures(): List<Pair<String, String>> = listOf(
    bookCount.toString() to if (bookCount == 1) "book" else "books",
    seriesCount.toString() to "series",
    shortReadingTime(readMillis) to "read",
    days().toString() to if (days() == 1L) "day" else "days",
)

/** "Started 19 Oct · finished today". */
internal fun JournalFinishedList.datesLine(nowEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val started = Instant.ofEpochMilli(startedAtEpochMillis).atZone(zone).toLocalDate()
    val finished = Instant.ofEpochMilli(finishedAtEpochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    val day = DateTimeFormatter.ofPattern("d MMM")
    val finishedText = when (finished) {
        today -> "finished today"
        today.minusDays(1) -> "finished yesterday"
        else -> "finished ${finished.format(day)}"
    }
    return if (started == finished) finishedText.replaceFirstChar(Char::uppercase) else "Started ${started.format(day)} · $finishedText"
}

/** Days from the first day read to the last, both counted. */
internal fun JournalFinishedList.days(zone: ZoneId = ZoneId.systemDefault()): Long {
    val started = Instant.ofEpochMilli(startedAtEpochMillis).atZone(zone).toLocalDate()
    val finished = Instant.ofEpochMilli(finishedAtEpochMillis).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(started, finished).coerceAtLeast(0) + 1
}

/** "9 h 20", "45 min", "–": short enough for a figure tile. */
private fun shortReadingTime(millis: Long): String {
    val minutes = millis / 60_000L
    return when {
        minutes <= 0L -> "–"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
    }
}

private fun monthName(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM"))
