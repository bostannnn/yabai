package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ListDownloadsTest {
    private val items = listOf(
        book("done"),
        book("local", SourceType.LOCAL),
        book("a"),
        book("b"),
        book("c"),
        book("d"),
    ).associateBy(ReadableItem::id)
    private val progress = mapOf("done" to ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true))

    @Test
    fun `a list keeps its first three unread server books, in its order`() {
        val list = list("done", "local", "gone", "a", "b", "c", "d")

        assertEquals(listOf("a", "b", "c"), list.nextToKeepDownloaded(items, progress).map(ReadableItem::id))
    }

    @Test
    fun `only lists that keep books ask, for what isn't here or asked for yet, each once`() {
        val keeping = list("a", "b", "c").copy(keepNextDownloaded = true)
        val alsoKeeping = list("b", "d").copy(id = "list:other", keepNextDownloaded = true)
        val notKeeping = list("d").copy(id = "list:quiet")

        val queue = booksToKeepDownloaded(
            lists = listOf(keeping, alsoKeeping, notKeeping),
            itemsById = items,
            progressByItemId = progress,
            onDevice = setOf("a"),
            asked = setOf("c"),
        )

        assertEquals(listOf("b", "d"), queue.map(ReadableItem::id))
    }

    @Test
    fun `the line says what is ready and what is on its way`() {
        val next = listOf(items.getValue("a"), items.getValue("b"), items.getValue("c"))

        assertEquals("Book a is ready; Book b and Book c are on their way.", keepDownloadedLine(next, setOf("a"), keeping = true))
        assertEquals("Book a, Book b and Book c are ready.", keepDownloadedLine(next, setOf("a", "b", "c"), keeping = true))
    }

    private fun list(vararg ids: String) = UserList(
        id = "list:a",
        name = "List",
        description = null,
        kind = UserListKind.User,
        order = UserListOrder.Custom,
        hideFinished = false,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = 0,
        entries = ids.mapIndexed { index, id ->
            UserListEntry(
                itemId = id,
                position = index,
                addedAtEpochMillis = 1,
                sourceType = SourceType.GRIMMORY,
                libraryId = "g",
                title = id,
                primaryText = null,
                secondaryText = null,
                coverUrl = null,
                kind = com.example.unifiedreader.core.model.ReadableItemKind.BOOK,
                format = FormatType.EPUB,
            )
        },
    )

    private fun book(id: String, source: SourceType = SourceType.GRIMMORY) =
        ReadableItem(id = id, sourceType = source, libraryId = "g", title = "Book $id", format = FormatType.EPUB)
}
