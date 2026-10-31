package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserListBooksTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun `books follow the list's order and a book that left the library stays, missing`() {
        val list = list(entries = listOf(entry("a", 1, added = 3), entry("gone", 0, added = 1), entry("b", 2, added = 2)))

        val books = list.books(itemsById = items("a", "b"), progressByItemId = emptyMap())

        assertEquals(listOf("gone", "a", "b"), books.map { it.entry.itemId })
        assertTrue(books.first().isMissing)
        assertEquals(listOf("a", "b", "gone"), list.copy(order = UserListOrder.DateAdded).sortedEntries().map { it.itemId })
    }

    @Test
    fun `next to read skips finished and missing books`() {
        val list = list(entries = listOf(entry("done", 0), entry("gone", 1), entry("next", 2), entry("later", 3)))

        val next = list.books(items("done", "next", "later"), mapOf("done" to finished())).nextToRead()

        assertEquals("next", next?.entry?.itemId)
    }

    @Test
    fun `stalled holds started books left for three weeks, newest first, and nothing finished`() {
        val items = listOf(item("old"), item("recent"), item("older"), item("done"), item("unstarted"))
        val progress = mapOf(
            "old" to started(lastRead = now - 30 * day),
            "older" to started(lastRead = now - 60 * day),
            "recent" to started(lastRead = now - 2 * day),
            "done" to finished().copy(lastReadAtEpochMillis = now - 40 * day),
            "unstarted" to ItemProgressSummary(label = "", fraction = 0f, lastReadAtEpochMillis = now - 40 * day),
        )

        val stalled = stalledList(items, progress, nowEpochMillis = now)

        assertEquals(listOf("old", "older"), stalled.sortedEntries().map { it.itemId })
        assertEquals("Updates itself · 2", stalled.tileCaption(progress))
    }

    @Test
    fun `the reading order under way is the most recently opened one with a book left`() {
        val finishedOrder = list(id = "list:done", entries = listOf(entry("a", 0)), opened = 9)
        val underWay = list(id = "list:on", entries = listOf(entry("a", 0), entry("b", 1), entry("c", 2)), opened = 5)
        val collection = list(id = "list:c", entries = listOf(entry("c", 0)), opened = 10).copy(order = UserListOrder.Title)

        val result = readingOrderUnderWay(
            lists = listOf(finishedOrder, underWay, collection),
            itemsById = items("a", "b", "c"),
            progressByItemId = mapOf("a" to finished()),
        )

        assertEquals("list:on", result?.list?.id)
        assertEquals("b", result?.next?.entry?.itemId)
        assertEquals(2, result?.position)
        assertEquals(3, result?.count)
    }

    @Test
    fun `no reading order is under way until a book is opened from one`() {
        val result = readingOrderUnderWay(listOf(list(entries = listOf(entry("a", 0)))), items("a"), emptyMap())

        assertNull(result)
    }

    @Test
    fun `tile captions say the order and how far you are`() {
        val order = list(entries = listOf(entry("a", 0), entry("b", 1)))

        assertEquals("Your order · 2 books", order.tileCaption(emptyMap()))
        assertEquals("Your order · 1 of 2", order.tileCaption(mapOf("a" to finished())))
        assertEquals("1 book", order.copy(order = UserListOrder.DateAdded, entries = order.entries.take(1)).tileCaption(emptyMap()))
    }

    @Test
    fun `the screen puts Want to read and Stalled first, then the lists you used last`() {
        val want = list(id = UserListIds.WantToRead).copy(kind = UserListKind.WantToRead)
        val older = list(id = "list:older").copy(updatedAtEpochMillis = 1)
        val newer = list(id = "list:newer", opened = 50)
        val stalled = stalledList(emptyList(), emptyMap(), now)

        val ordered = orderedForScreen(listOf(older, want, newer), stalled)

        assertEquals(listOf(UserListIds.WantToRead, UserListIds.Stalled, "list:newer", "list:older"), ordered.map { it.id })
    }

    private fun list(
        id: String = "list:a",
        entries: List<UserListEntry> = emptyList(),
        opened: Long = 0L,
    ) = UserList(
        id = id,
        name = "List",
        description = null,
        kind = UserListKind.User,
        order = UserListOrder.Custom,
        hideFinished = false,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = opened,
        entries = entries,
    )

    private fun entry(id: String, position: Int, added: Long = 1) =
        item(id).toUserListEntry(position = position, addedAtEpochMillis = added)

    private fun item(id: String) = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:1",
        title = "Book $id",
        format = FormatType.CBZ,
    )

    private fun items(vararg ids: String) = ids.associateWith(::item)

    private fun finished() = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true, lastReadAtEpochMillis = 1)

    private fun started(lastRead: Long) = ItemProgressSummary(label = "40%", fraction = 0.4f, lastReadAtEpochMillis = lastRead)
}
