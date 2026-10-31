package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.journal.JournalReadingSession

/** A list read to the end, as kept for the journal: it stays when the list is deleted. [itemIds] are its books in order. */
data class FinishedList(
    val id: String,
    val listId: String,
    val name: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val bookCount: Int,
    val seriesCount: Int,
    val readMillis: Long,
    val itemIds: List<String>,
)

/**
 * Whether finishing [justFinishedItemId] reads [this] list to the end: it was in the list and every
 * other book there is finished (or has left the library).
 */
fun UserList.isReadToTheEnd(
    justFinishedItemId: String,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): Boolean = contains(justFinishedItemId) &&
    books(itemsById, progressByItemId).all { book -> book.entry.itemId == justFinishedItemId || book.isFinished || book.isMissing }

/**
 * What the journal keeps of [this] list read to the end at [nowEpochMillis]: its books, how many
 * series they come from, the time spent reading them ([sessions]) and the day the first of them was
 * read (else the day the list was made).
 */
fun UserList.toFinishedList(
    itemsById: Map<String, ReadableItem>,
    sessions: List<JournalReadingSession>,
    nowEpochMillis: Long,
): FinishedList {
    val ids = sortedEntries().map(UserListEntry::itemId)
    val idSet = ids.toHashSet()
    val ownSessions = sessions.filter { session -> session.itemId in idSet }
    val series = ids.mapNotNull { id ->
        val item = itemsById[id] ?: return@mapNotNull null
        (item.comicMetadata?.series ?: item.bookMetadata?.seriesName)?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
    }.toSet()
    return FinishedList(
        id = id,
        listId = id,
        name = name,
        startedAtEpochMillis = ownSessions.minOfOrNull(JournalReadingSession::startedAtEpochMillis)
            ?.coerceAtMost(nowEpochMillis)
            ?: createdAtEpochMillis.takeIf { it > 0L }
            ?: nowEpochMillis,
        finishedAtEpochMillis = nowEpochMillis,
        bookCount = ids.size,
        seriesCount = series.size,
        readMillis = ownSessions.sumOf(JournalReadingSession::durationMillis),
        itemIds = ids,
    )
}

/** Whether the journal already keeps [list] as finished as it is now (same books), so its card isn't shown again. */
fun List<FinishedList>.alreadyKeeps(list: UserList): Boolean =
    any { finished -> finished.listId == list.id && finished.bookCount == list.entries.size }
