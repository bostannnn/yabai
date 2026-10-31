package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeReadingOrderTest {
    private val items = listOf(
        book("a", SourceType.KAVITA),
        book("b", SourceType.KAVITA),
        book("c", SourceType.GRIMMORY),
        book("d", SourceType.KAVITA),
        book("other", SourceType.KAVITA),
    ).associateBy(ReadableItem::id)
    private val order = UserList(
        id = "list:order",
        name = "The Long Eclipse",
        description = null,
        kind = UserListKind.User,
        order = UserListOrder.Custom,
        hideFinished = false,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = 10,
        entries = listOf("a", "b", "c", "d").mapIndexed { index, id ->
            items.getValue(id).toUserListEntry(position = index, addedAtEpochMillis = 1)
        },
    )

    @Test
    fun `the card follows the order when the book read last was finished and is in it`() {
        val progress = mapOf("a" to finished(at = 50))

        val home = homeReadingOrder(listOf(order), items, progress, SourceType.KAVITA)

        assertEquals("b", home?.underWay?.next?.item?.id)
        assertEquals("Next in The Long Eclipse · 2 of 4", home?.overline)
        assertEquals("Then Book c, Book d", home?.thenLine)
    }

    @Test
    fun `it doesn't when something else was read since, or the next book is another library's`() {
        assertNull(homeReadingOrder(listOf(order), items, mapOf("a" to finished(50), "other" to started(60)), SourceType.KAVITA))
        assertNull(homeReadingOrder(listOf(order), items, mapOf("a" to finished(50), "b" to finished(55)), SourceType.KAVITA))
        assertEquals(
            "c",
            homeReadingOrder(listOf(order), items, mapOf("a" to finished(50), "b" to finished(55)), SourceType.GRIMMORY)
                ?.underWay?.next?.item?.id,
        )
    }

    private fun finished(at: Long) = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true, lastReadAtEpochMillis = at)

    private fun started(at: Long) = ItemProgressSummary(label = "40%", fraction = 0.4f, lastReadAtEpochMillis = at)

    private fun book(id: String, source: SourceType) = ReadableItem(
        id = id,
        sourceType = source,
        libraryId = "lib",
        title = "Book $id",
        format = FormatType.EPUB,
        kind = ReadableItemKind.BOOK,
    )
}
