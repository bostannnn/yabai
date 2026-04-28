package com.example.unifiedreader.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class JournalReadingEventType {
    STARTED,
    CONTINUED,
    FINISHED,
}

enum class JournalPeriod {
    MONTH,
    YEAR,
}

data class JournalReadingEvent(
    val id: String,
    val itemId: String,
    val sourceType: SourceType,
    val libraryId: String,
    val itemTitle: String,
    val coverUrl: String?,
    val itemKind: ReadableItemKind,
    val format: FormatType,
    val eventType: JournalReadingEventType,
    val fromPercent: Double?,
    val toPercent: Double?,
    val fromPage: Int?,
    val toPage: Int?,
    val pageCount: Int?,
    val occurredAtEpochMillis: Long,
)

data class ItemJournalEntry(
    val itemId: String,
    val sourceType: SourceType,
    val libraryId: String,
    val itemTitle: String,
    val coverUrl: String?,
    val itemKind: ReadableItemKind,
    val format: FormatType,
    val rating: Int?,
    val reviewText: String,
    val reviewedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
)

data class JournalItemRef(
    val itemId: String,
    val sourceType: SourceType,
    val libraryId: String,
    val itemTitle: String,
    val itemPrimaryText: String? = null,
    val itemSecondaryText: String? = null,
    val coverUrl: String?,
    val itemKind: ReadableItemKind,
    val format: FormatType,
) {
    val displayTitle: String
        get() = itemPrimaryText?.takeIf(String::isNotBlank) ?: itemTitle

    val displaySubtitle: String?
        get() = itemSecondaryText?.takeIf(String::isNotBlank)
            ?.takeUnless { it == displayTitle }
}

data class JournalStats(
    val booksFinished: Int = 0,
    val comicsFinished: Int = 0,
    val pagesRead: Int = 0,
    val activeDays: Int = 0,
    val sourceCounts: Map<SourceType, Int> = emptyMap(),
    val formatCounts: Map<FormatType, Int> = emptyMap(),
    val ratingCounts: Map<Int, Int> = emptyMap(),
)

data class JournalActivityDay(
    val localDayKey: Int,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val pagesRead: Int,
    val eventCount: Int,
    val finishedBooks: Int,
    val finishedComics: Int,
)

data class JournalPeriodOverview(
    val period: JournalPeriod,
    val periodStartEpochMillis: Long,
    val periodEndEpochMillis: Long,
    val stats: JournalStats,
    val activityDays: List<JournalActivityDay>,
    val finishedItems: List<JournalItemSummary>,
)

data class JournalItemSummary(
    val item: JournalItemRef,
    val events: List<JournalReadingEvent>,
    val entry: ItemJournalEntry?,
) {
    val startedAtEpochMillis: Long?
        get() = events
            .filter { event -> event.eventType == JournalReadingEventType.STARTED }
            .minOfOrNull(JournalReadingEvent::occurredAtEpochMillis)
            ?: events.minOfOrNull(JournalReadingEvent::occurredAtEpochMillis)

    val finishedAtEpochMillis: Long?
        get() = events
            .filter { event -> event.eventType == JournalReadingEventType.FINISHED }
            .maxOfOrNull(JournalReadingEvent::occurredAtEpochMillis)

    val lastActivityAtEpochMillis: Long
        get() = listOfNotNull(
            events.maxOfOrNull(JournalReadingEvent::occurredAtEpochMillis),
            entry?.updatedAtEpochMillis,
        ).maxOrNull() ?: 0L

    val continuedDatesEpochMillis: List<Long>
        get() = events
            .filter { event -> event.eventType == JournalReadingEventType.CONTINUED }
            .map(JournalReadingEvent::occurredAtEpochMillis)
            .distinctBy(::localDateKey)
            .sorted()

    val isFinished: Boolean
        get() = finishedAtEpochMillis != null

    val pagesRead: Int
        get() = events.sumOf(JournalReadingEvent::positivePageDelta)

    val latestProgressLabel: String
        get() = events.maxByOrNull(JournalReadingEvent::occurredAtEpochMillis)?.progressLabel().orEmpty()
}

data class JournalSnapshot(
    val events: List<JournalReadingEvent> = emptyList(),
    val entries: List<ItemJournalEntry> = emptyList(),
    val itemSummaries: List<JournalItemSummary> = emptyList(),
    val monthStats: JournalStats = JournalStats(),
    val yearStats: JournalStats = JournalStats(),
) {
    val entryByItemId: Map<String, ItemJournalEntry> by lazy {
        entries.associateBy(ItemJournalEntry::itemId)
    }
}

data class JournalProgressInput(
    val item: ReadableItem,
    val page: Int?,
    val percent: Double?,
    val pageCount: Int?,
    val isFinished: Boolean,
    val occurredAtEpochMillis: Long,
)

interface JournalEventRecorder {
    suspend fun recordReaderProgress(input: JournalProgressInput)
}

interface JournalRepository : JournalEventRecorder {
    fun observeSnapshot(): Flow<JournalSnapshot>

    suspend fun saveJournalEntry(
        item: ReadableItem,
        rating: Int?,
        reviewText: String,
    )

    suspend fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    )

    suspend fun deleteItemJournal(itemId: String)
}

fun periodBounds(
    period: JournalPeriod,
    nowEpochMillis: Long,
): Pair<Long, Long> {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = nowEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (period == JournalPeriod.YEAR) {
            set(Calendar.MONTH, Calendar.JANUARY)
        }
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val start = calendar.timeInMillis
    calendar.add(
        if (period == JournalPeriod.MONTH) Calendar.MONTH else Calendar.YEAR,
        1,
    )
    return start to calendar.timeInMillis
}

fun ReadableItem.toJournalEntry(
    rating: Int?,
    reviewText: String,
    reviewedAtEpochMillis: Long?,
    updatedAtEpochMillis: Long,
): ItemJournalEntry = ItemJournalEntry(
    itemId = id,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = title,
    coverUrl = coverUrl,
    itemKind = kind,
    format = format,
    rating = rating?.coerceIn(1, 5),
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun JournalItemRef.toJournalEntry(
    rating: Int?,
    reviewText: String,
    reviewedAtEpochMillis: Long?,
    updatedAtEpochMillis: Long,
): ItemJournalEntry = ItemJournalEntry(
    itemId = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind,
    format = format,
    rating = rating?.coerceIn(1, 5),
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun JournalReadingEvent.toItemRef(): JournalItemRef = JournalItemRef(
    itemId = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind,
    format = format,
)

fun ItemJournalEntry.toItemRef(): JournalItemRef = JournalItemRef(
    itemId = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind,
    format = format,
)

private fun JournalReadingEvent.positivePageDelta(): Int {
    val from = fromPage ?: fromPercent?.let { percent -> pageCount?.let { (percent * it).toInt() } } ?: 0
    val to = toPage ?: toPercent?.let { percent -> pageCount?.let { (percent * it).toInt() } } ?: return 0
    return (to - from).coerceAtLeast(0)
}

private fun JournalReadingEvent.progressLabel(): String {
    val pageText = toPage?.let { page -> pageCount?.let { "$page / $it" } ?: page.toString() }
    val percentText = toPercent?.let { percent -> "${(percent * 100).toInt()}%" }
    return pageText ?: percentText.orEmpty()
}

private fun localDateKey(epochMillis: Long): Int {
    val calendar = Calendar.getInstance(Locale.US).apply { timeInMillis = epochMillis }
    return calendar.get(Calendar.YEAR) * 10_000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)
}
