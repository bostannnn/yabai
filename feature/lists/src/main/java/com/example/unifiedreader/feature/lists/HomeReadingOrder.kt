package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.libraryBookTitle

/** What a library home's card shows when it follows a reading order: the order and the two books after its next one. */
data class HomeReadingOrder(
    val underWay: UserListUnderWay,
    val then: List<ReadableItem>,
) {
    val overline: String
        get() = "Next in ${underWay.list.name} · ${underWay.position} of ${underWay.count}"

    val thenLine: String?
        get() = then.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Then ", separator = ", ") { it.libraryBookTitle() }
}

/**
 * The reading order a library home's card follows: the one under way, when the book read last was
 * finished and is in it, and its next book is from [sourceType] (this library). Null: the card
 * shows the book read last, as it always has.
 */
fun homeReadingOrder(
    lists: List<UserList>,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    sourceType: SourceType,
): HomeReadingOrder? {
    val underWay = readingOrderUnderWay(lists, itemsById, progressByItemId) ?: return null
    val (lastReadId, lastRead) = progressByItemId.entries
        .maxByOrNull { (_, progress) -> progress.lastReadAtEpochMillis }
        ?.toPair() ?: return null
    if (!lastRead.isFinished || !underWay.list.contains(lastReadId)) return null
    val next = underWay.next.item ?: return null
    if (next.sourceType != sourceType) return null
    val after = underWay.list.books(itemsById, progressByItemId)
        .drop(underWay.position)
        .filter { book -> !book.isFinished && !book.isMissing }
        .mapNotNull(UserListBook::item)
        .take(2)
    return HomeReadingOrder(underWay = underWay, then = after)
}
