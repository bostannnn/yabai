package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.journal.ItemJournalEntry

/**
 * The books whose reading history a library refresh loads: those read or finished on the server since the
 * last pull (a read time past the one their journal entry kept, which a pull sets from the same list) and
 * those the journal doesn't have yet. Null when nothing the journal shows changed, neither a book's reading
 * nor a rating, so the refresh skips the pull; an empty set when only a rating did. Sync now still loads
 * every book's history, which also catches a session Grimmory saved after the book's last read time.
 */
internal fun grimmoryJournalPullScope(
    entries: List<GrimmoryBookCatalogEntry>,
    storedEntries: List<ItemJournalEntry>,
): Set<String>? {
    val storedByItemId = storedEntries.associateBy(ItemJournalEntry::itemId)
    val readBookIds = entries
        .filter(GrimmoryBookCatalogEntry::shouldFetchReadingSessions)
        .filter { entry -> entry.wasReadSince(storedByItemId[entry.item.id]) }
        .mapTo(mutableSetOf()) { entry -> entry.item.id }
    val ratingChanged = entries.any { entry ->
        storedByItemId[entry.item.id]?.rating != entry.personalRating?.coerceIn(1, 10)
    }
    return readBookIds.takeIf { readBookIds.isNotEmpty() || ratingChanged }
}

private fun GrimmoryBookCatalogEntry.wasReadSince(
    stored: ItemJournalEntry?,
): Boolean {
    if (stored == null) return true
    val readAt = lastReadOrFinishedAtEpochMillis() ?: return false
    val keptAt = stored.remoteUpdatedAtEpochMillis ?: return true
    return readAt > keptAt
}
