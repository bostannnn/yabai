package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.feature.library.supportsOfflineDownload

/** How many of a list's next books Keep the next 3 downloaded keeps on the device. */
const val KeepDownloadedCount: Int = 3

/**
 * The books [this] list keeps downloaded: its first unread books in its own order that a server
 * can download (a local file is already here; a whole series is left to its own Download).
 */
fun UserList.nextToKeepDownloaded(
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    count: Int = KeepDownloadedCount,
): List<ReadableItem> = books(itemsById, progressByItemId)
    .asSequence()
    .filterNot(UserListBook::isFinished)
    .mapNotNull(UserListBook::item)
    .filter { item -> item.supportsOfflineDownload() && !item.isKavitaSeriesContainer() && !item.isStumpSeriesContainer() }
    .take(count)
    .toList()

/**
 * What to queue now for every list that keeps its next books downloaded: the books not on the
 * device ([onDevice]) and not asked for already in this run of the app ([asked]), each once.
 */
fun booksToKeepDownloaded(
    lists: List<UserList>,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    onDevice: Set<String>,
    asked: Set<String>,
): List<ReadableItem> = lists
    .asSequence()
    .filter(UserList::keepNextDownloaded)
    .flatMap { list -> list.nextToKeepDownloaded(itemsById, progressByItemId) }
    .filter { item -> item.id !in onDevice && item.id !in asked }
    .distinctBy(ReadableItem::id)
    .toList()

/** The switch's line: "Neon Ronin #12 and Harbor Lights #7 are ready; The Long Eclipse #3 is on its way." */
fun keepDownloadedLine(next: List<ReadableItem>, onDevice: Set<String>, keeping: Boolean): String {
    if (!keeping) return "The next $KeepDownloadedCount unread books from your servers wait on this device, for reading offline."
    if (next.isEmpty()) return "Nothing to download: no unread book here comes from a server."
    val (ready, coming) = next.partition { item -> item.id in onDevice }
    val parts = listOfNotNull(
        ready.takeIf { it.isNotEmpty() }?.let { "${titles(it)} ${if (it.size == 1) "is" else "are"} ready" },
        coming.takeIf { it.isNotEmpty() }?.let { "${titles(it)} ${if (it.size == 1) "is on its way" else "are on their way"}" },
    )
    return parts.joinToString("; ") + "."
}

private fun titles(items: List<ReadableItem>): String {
    val names = items.map { it.libraryBookTitle() }
    return if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}
