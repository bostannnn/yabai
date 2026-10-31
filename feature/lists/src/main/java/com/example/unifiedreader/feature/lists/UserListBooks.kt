package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryItemListMembership

/** A list's book with what the library knows about it now: [item] is null once it has left the library. */
data class UserListBook(
    val entry: UserListEntry,
    val item: ReadableItem?,
    val progress: ItemProgressSummary?,
) {
    val isMissing: Boolean
        get() = item == null

    val isFinished: Boolean
        get() = progress?.isFinished == true
}

/** How long a started book may sit unopened before it counts as stalled. */
const val StalledAfterMillis: Long = 21L * 24 * 60 * 60 * 1000

/** The entries in the order the list shows them. */
fun UserList.sortedEntries(): List<UserListEntry> = when (order) {
    UserListOrder.Custom -> entries.sortedBy(UserListEntry::position)
    UserListOrder.DateAdded -> entries.sortedByDescending(UserListEntry::addedAtEpochMillis)
    UserListOrder.Title -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, UserListEntry::sortTitle))
}

/** The list's books in its order, each matched to the library's item and progress. */
fun UserList.books(
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): List<UserListBook> = sortedEntries().map { entry ->
    UserListBook(
        entry = entry,
        item = itemsById[entry.itemId],
        progress = progressByItemId[entry.itemId],
    )
}

/** The first book in the list's order that is still there and not finished: what Continue opens. */
fun List<UserListBook>.nextToRead(): UserListBook? = firstOrNull { book -> !book.isMissing && !book.isFinished }

/**
 * Stalled, worked out: books started (some progress, not finished) and not opened for
 * [stalledAfterMillis], every source, the most recently opened first.
 */
fun stalledList(
    items: Collection<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    nowEpochMillis: Long,
    stalledAfterMillis: Long = StalledAfterMillis,
): UserList {
    val cutoff = nowEpochMillis - stalledAfterMillis
    val stalled = items
        .asSequence()
        .filterNot { item -> item.isKavitaSeriesContainer() || item.isStumpSeriesContainer() }
        .mapNotNull { item -> progressByItemId[item.id]?.let { progress -> item to progress } }
        .filter { (_, progress) ->
            !progress.isFinished &&
                progress.fraction > 0f &&
                progress.lastReadAtEpochMillis in 1..cutoff
        }
        .distinctBy { (item, _) -> item.id }
        .sortedByDescending { (_, progress) -> progress.lastReadAtEpochMillis }
        .toList()
    return UserList(
        id = UserListIds.Stalled,
        name = "Stalled",
        description = "Books you started and haven't opened for three weeks.",
        kind = UserListKind.Stalled,
        order = UserListOrder.DateAdded,
        hideFinished = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = nowEpochMillis,
        lastOpenedAtEpochMillis = 0L,
        entries = stalled.mapIndexed { index, (item, progress) ->
            item.toUserListEntry(position = index, addedAtEpochMillis = progress.lastReadAtEpochMillis)
        },
    )
}

/** The reading order under way: the one opened from most recently that still has a book to read. */
data class UserListUnderWay(
    val list: UserList,
    val next: UserListBook,
    val position: Int,
    val count: Int,
    val finishedCount: Int,
)

fun readingOrderUnderWay(
    lists: List<UserList>,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): UserListUnderWay? = lists
    .asSequence()
    .filter { list -> list.isReadingOrder && list.isEditable && list.lastOpenedAtEpochMillis > 0L }
    .sortedByDescending(UserList::lastOpenedAtEpochMillis)
    .firstNotNullOfOrNull { list ->
        val books = list.books(itemsById, progressByItemId)
        val next = books.nextToRead() ?: return@firstNotNullOfOrNull null
        UserListUnderWay(
            list = list,
            next = next,
            position = books.indexOf(next) + 1,
            count = books.size,
            finishedCount = books.count(UserListBook::isFinished),
        )
    }

/** Lists on the Lists screen: Want to read, Stalled, then the user's, most recently opened or changed first. */
fun orderedForScreen(stored: List<UserList>, stalled: UserList): List<UserList> {
    val wantToRead = stored.filter { it.kind == UserListKind.WantToRead }
    val own = stored
        .filter { it.kind == UserListKind.User }
        .sortedByDescending { maxOf(it.lastOpenedAtEpochMillis, it.updatedAtEpochMillis) }
    return wantToRead + stalled + own
}

/** "9 books", "1 book", "No books yet". */
fun bookCountLabel(count: Int): String = when (count) {
    0 -> "No books yet"
    1 -> "1 book"
    else -> "$count books"
}

/** A list tile's line: "Your order · 6 of 14", "9 books", "Updates itself · 4", "5 books · by title". */
fun UserList.tileCaption(progressByItemId: Map<String, ItemProgressSummary>): String {
    val count = entries.size
    return when {
        kind == UserListKind.Stalled -> if (count == 0) "Nothing stalled" else "Updates itself · $count"
        kind == UserListKind.ServerShelf && count > 0 ->
            "${bookCountLabel(count)} · ${if (order == UserListOrder.Title) "by title" else "date added"}"
        isReadingOrder && count > 0 -> {
            val read = entries.count { entry -> progressByItemId[entry.itemId]?.isFinished == true }
            if (read > 0) "Your order · $read of $count" else "Your order · ${bookCountLabel(count)}"
        }
        else -> bookCountLabel(count)
    }
}

/**
 * The lists that hold [itemId], for its book page: Want to read, the user's lists, then the
 * servers' lists, never Stalled. In an order the line says where the book is.
 */
fun bookListMemberships(itemId: String, lists: List<UserList>): List<LibraryItemListMembership> = lists
    .filter { list -> list.kind != UserListKind.Stalled && list.contains(itemId) }
    .sortedBy { list -> if (list.kind == UserListKind.WantToRead) 0 else if (list.server == null) 1 else 2 }
    .map { list ->
        val count = list.entries.size
        val line = when {
            list.isReadingOrder -> {
                val number = list.sortedEntries().indexOfFirst { it.itemId == itemId } + 1
                "Number $number of $count · Read next follows this list"
            }
            list.kind == UserListKind.ServerShelf ->
                "${list.server?.sourceType?.displayName().orEmpty()} shelf · ${bookCountLabel(count)}"
            else -> bookCountLabel(count)
        }
        LibraryItemListMembership(listId = list.id, name = list.name, line = line)
    }

/** A list page's facts: "14 books · 6 read". */
fun List<UserListBook>.summaryLine(): String {
    val read = count(UserListBook::isFinished)
    return if (read == 0) bookCountLabel(size) else "${bookCountLabel(size)} · $read read"
}

private fun UserListEntry.sortTitle(): String = primaryText ?: title
