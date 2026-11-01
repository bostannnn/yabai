package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSeriesTest {
    // Two issues of a comic series (100), one EPUB of a book series (200), as the Kavita catalog holds them.
    private val issue1 = kavita(chapterId = "1", seriesId = "100", series = "Saga", format = FormatType.CBZ)
    private val issue2 = kavita(chapterId = "2", seriesId = "100", series = "Saga", format = FormatType.CBZ)
    private val novel = kavita(chapterId = "3", seriesId = "200", series = "Dune", format = FormatType.EPUB)
    private val items = listOf(issue1, issue2, novel)

    private val collection = ServerShelf(
        id = "kavita:collection:2",
        sourceType = SourceType.KAVITA,
        remote = RemoteShelf(remoteId = "2", name = "Eisner winners"),
        order = UserListOrder.Title,
        hideFinished = false,
        itemIds = listOf(series("100"), series("200"), series("999")),
        kind = UserListKind.ServerCollection,
    )

    @Test
    fun `a collection's series are found as the library shows them, and one the library doesn't hold stays out`() {
        val itemsById = listItemsById(items, listOf(collection))

        val comic = itemsById.getValue(series("100"))
        assertTrue(comic.isKavitaSeriesContainer())
        assertEquals("Saga", comic.title)
        assertEquals(2, comic.issueCount)
        assertEquals(novel, itemsById[series("200")])
        assertEquals(null, itemsById[series("999")])
        // The catalog's own items are still there.
        assertEquals(issue1, itemsById[issue1.id])
    }

    @Test
    fun `a collection as a list keeps its series ids, so taking one off names the series`() {
        val list = collection.toUserList(listItemsById(items, listOf(collection)))

        assertEquals(listOf(series("100"), series("200")), list.entries.map(UserListEntry::itemId))
        assertEquals("Collection · 2 series", list.tileCaption(emptyMap()))
        val progress = mapOf(novel.id to ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true))
        val books = list.books(listItemsById(items, listOf(collection)), progress)
        assertEquals(listOf("Dune", "Saga"), books.map { it.item?.title })
        assertTrue(books.first { it.item == novel }.isFinished)
    }

    @Test
    fun `without a collection the lookup is the catalog by id`() {
        val shelf = collection.copy(kind = UserListKind.ServerReadingList, itemIds = listOf(series("100")))

        assertEquals(items.associateBy(ReadableItem::id), listItemsById(items, listOf(shelf)))
    }

    private fun series(seriesId: String) = "kavita:series:kavita:library:1::$seriesId"

    private fun kavita(chapterId: String, seriesId: String, series: String, format: FormatType) = ReadableItem(
        id = "kavita:item:$chapterId",
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:1",
        remoteId = chapterId,
        remoteFileType = seriesId,
        title = series,
        format = format,
        comicMetadata = ComicMetadata(series = series),
    )
}
