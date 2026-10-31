package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.journal.JournalMediaType
import com.example.unifiedreader.journal.JournalReadingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishedListsTest {
    private val items = listOf(
        book("a", series = "Eclipse"),
        book("b", series = "eclipse "),
        book("c", series = "Neon"),
        book("d"),
    ).associateBy(ReadableItem::id)
    private val list = UserList(
        id = "list:order",
        name = "The Long Eclipse",
        description = null,
        kind = UserListKind.User,
        order = UserListOrder.Custom,
        hideFinished = false,
        createdAtEpochMillis = 5,
        updatedAtEpochMillis = 5,
        lastOpenedAtEpochMillis = 10,
        entries = listOf("a", "b", "c", "d").mapIndexed { index, id -> items.getValue(id).toUserListEntry(index, 1) },
    )
    private val finished = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true)

    @Test
    fun `a list is read to the end when the book just finished was its last unread one`() {
        val progress = mapOf("a" to finished, "b" to finished, "c" to finished)

        assertTrue(list.isReadToTheEnd("d", items, progress))
        assertFalse(list.isReadToTheEnd("c", items, progress - "b"))
        assertFalse(list.isReadToTheEnd("elsewhere", items, progress + ("d" to finished)))
    }

    @Test
    fun `the journal keeps its books, series, time read and first day read`() {
        val sessions = listOf(session("a", start = 100, millis = 60_000), session("c", start = 50, millis = 30_000), session("x", start = 1, millis = 9))

        val kept = list.toFinishedList(items, sessions, nowEpochMillis = 1_000)

        assertEquals(4, kept.bookCount)
        assertEquals(2, kept.seriesCount)
        assertEquals(90_000L, kept.readMillis)
        assertEquals(50L, kept.startedAtEpochMillis)
        assertEquals(listOf("a", "b", "c", "d"), kept.itemIds)
        assertTrue(listOf(kept).alreadyKeeps(list))
        assertFalse(listOf(kept.copy(bookCount = 3)).alreadyKeeps(list))
    }

    private fun session(itemId: String, start: Long, millis: Long) = JournalReadingSession(
        id = "s:$itemId",
        itemId = itemId,
        sourceType = SourceType.LOCAL,
        libraryId = "l",
        itemTitle = itemId,
        coverUrl = null,
        itemKind = ReadableItemKind.BOOK,
        format = FormatType.EPUB,
        mediaType = JournalMediaType.entries.first(),
        startedAtEpochMillis = start,
        endedAtEpochMillis = start + millis,
        durationMillis = millis,
        fromPercent = null,
        toPercent = null,
        fromPage = null,
        toPage = null,
        pageCount = null,
    )

    private fun book(id: String, series: String? = null) = ReadableItem(
        id = id,
        sourceType = SourceType.LOCAL,
        libraryId = "l",
        title = "Book $id",
        format = FormatType.EPUB,
        bookMetadata = series?.let { BookMetadata(seriesName = it) },
    )
}
