package com.example.unifiedreader.feature.library.browse

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowseViewTest {
    private val sorting = LibraryTagSorting().with(
        listOf(
            tag("Russia", LibraryTagKind.PLACE, LibraryPlaceRegion.RUSSIA_AND_AROUND),
            tag("Moscow", LibraryTagKind.PLACE, LibraryPlaceRegion.RUSSIA_AND_AROUND),
            tag("France", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            tag("19th century", LibraryTagKind.ERA),
            tag("1960s", LibraryTagKind.ERA),
        ),
    )
    private val items = listOf(
        book("1", "Russia", "19th century", genre = "Classics"),
        book("2", "Moscow", "19th century", genre = "Classics"),
        book("3", "France", "19th century", genre = "Poetry"),
        book("4", "Moscow", "1960s", genre = "Classics"),
    )
    private val rows = listOf(LibraryBrowseRow.SET_IN, LibraryBrowseRow.WHEN, LibraryBrowseRow.GENRE, LibraryBrowseRow.READING)

    @Test
    fun `the shapes count among the books shown once something is picked`() {
        val picked = listOf(LibraryBrowseChip(LibraryBrowseRow.WHEN, "1800s"))

        val view = libraryBrowseView(items, picked, rows, sorting, readingStatus = { null })

        assertEquals(listOf("Russia" to 2, "France" to 1), view.countries.map { it.name to it.count })
        assertEquals(listOf("Moscow"), view.countries.first().cities.map { it.value })
        assertEquals(listOf("1800s" to 3), view.timeline.map { it.slot.label to it.count })
        assertEquals(listOf("1", "2", "3"), view.timeline.single().books.map(ReadableItem::id))
        assertEquals(listOf("1", "2"), view.genreBooks["classics"]?.map(ReadableItem::id))
    }

    @Test
    fun `a picked country brings its cities' books`() {
        val picked = listOf(LibraryBrowseChip(LibraryBrowseRow.SET_IN, "Russia"))

        val view = libraryBrowseView(items, picked, rows, sorting, readingStatus = { null })

        assertEquals(listOf("1", "2", "4"), view.result.items.map(ReadableItem::id))
        assertTrue(view.countries.single().isOpen(picked))
        // Russia and Moscow.
        assertEquals(2, view.countries.placeCount())
    }

    @Test
    fun `your reading lists the books in progress, last read first`() {
        val status = mapOf(
            "1" to LibraryReadingStatus.IN_PROGRESS,
            "2" to LibraryReadingStatus.IN_PROGRESS,
            "3" to LibraryReadingStatus.FINISHED,
            "4" to LibraryReadingStatus.NOT_STARTED,
        )
        val lastRead = mapOf("1" to 10L, "2" to 20L)

        val view = libraryBrowseView(
            items = items,
            picked = emptyList(),
            rows = rows,
            sorting = sorting,
            readingStatus = { item -> status[item.id] },
            lastReadAt = { item -> lastRead[item.id] ?: 0L },
        )

        assertEquals(listOf("2", "1"), view.inProgress.map(ReadableItem::id))
        assertEquals(listOf("3"), view.statusBooks["Finished"]?.map(ReadableItem::id))
        assertEquals(listOf("4"), view.statusBooks["Not started"]?.map(ReadableItem::id))
    }

    private fun tag(name: String, kind: LibraryTagKind, region: LibraryPlaceRegion? = null) =
        LibraryTagSortChange(LibraryTagField.TAG, name, LibraryTagChoice(kind, region))

    private fun book(id: String, vararg tags: String, genre: String): ReadableItem = ReadableItem(
        id = id,
        sourceType = SourceType.GRIMMORY,
        libraryId = "1",
        title = "Book $id",
        format = FormatType.EPUB,
        bookMetadata = BookMetadata(tags = tags.toList(), genres = listOf(genre)),
    )
}
