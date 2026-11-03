package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.toListRules
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListLibraryModesTest {
    private fun issue(id: String, number: String, series: String = "series-7") = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:1",
        remoteId = id,
        remoteFileType = series,
        title = "Akira $number",
        format = FormatType.CBZ,
        comicMetadata = ComicMetadata(series = "Akira", issueNumber = number),
    )

    @Test
    fun `a picked series goes into a list as its issues, in issue order, each once`() {
        val second = issue("issue-2", "2")
        val first = issue("issue-1", "1")
        val other = issue("other-1", "1", series = "series-9")
        val series = ReadableItem(
            id = kavitaSeriesContainerId(requireNotNull(first.kavitaSeriesGroupKey())),
            sourceType = SourceType.KAVITA,
            libraryId = first.libraryId,
            title = "Akira",
            format = FormatType.CBZ,
        )
        val book = ReadableItem(id = "grimmory:item:3", sourceType = SourceType.GRIMMORY, libraryId = "g", title = "Book", format = FormatType.EPUB)

        val books = listOf(book, series, first).withSeriesIssues(catalog = listOf(second, other, first, series))

        assertEquals(listOf("grimmory:item:3", "issue-1", "issue-2"), books.map(ReadableItem::id))
    }

    @Test
    fun `picks without a series stay as they are`() {
        val first = issue("issue-1", "1")
        assertEquals(listOf(first), listOf(first).withSeriesIssues(catalog = emptyList()))
    }

    @Test
    fun `a magic shelf keeps the filters' rules without Source`() {
        val rules = LibraryFilterState(readingStatuses = setOf(LibraryReadingStatus.IN_PROGRESS)).toListRules(SourceType.GRIMMORY)

        val shelf = rules.forMagicShelf()

        assertEquals(listOf(ListRuleField.Status), shelf.rules.map { it.field })
        assertEquals(true, shelf.fitsMagicShelf)
    }

    @Test
    fun `Done adds new picks and takes out the list's books that were unpicked`() {
        val kept = issue("issue-1", "1")
        val dropped = issue("issue-2", "2")
        val added = issue("issue-3", "3")
        val notShown = issue("issue-4", "4")

        val change = addingChange(
            picked = listOf(kept, added),
            shown = listOf(kept, dropped, added),
            inList = setOf("issue-1", "issue-2", "issue-4"),
            catalog = listOf(kept, dropped, added, notShown),
        )

        assertEquals(listOf("issue-3"), change.added.map(ReadableItem::id))
        assertEquals(listOf("issue-2"), change.removed.map(ReadableItem::id))
    }
}
