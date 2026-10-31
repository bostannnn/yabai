package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingListItemRef
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class AppReaderCompletionOrderTest {
    private val kavitaList = AppUiState(
        selectedSource = SourceType.KAVITA,
        kavitaReadingLists = AppKavitaReadingListState(
            selectedReadingListId = "kavita:reading-list:1",
            itemsByReadingListId = mapOf(
                "kavita:reading-list:1" to listOf(
                    ReadingListItemRef("kavita:reading-list:1", SourceType.KAVITA, "kavita:item:9", order = 0),
                ),
            ),
        ),
    )

    @Test
    fun `a book opened from the user's list follows that list`() {
        val state = kavitaList.copy(
            readerListContext = AppReaderListContext("list:a", "The Long Eclipse", listOf("kavita:item:1", "comic-5")),
        )

        assertEquals(listOf("kavita:item:1", "comic-5"), state.readerCompletionOrder("kavita:item:1"))
    }

    @Test
    fun `a book that isn't in the user's list falls back to the picked Kavita list`() {
        val state = kavitaList.copy(
            readerListContext = AppReaderListContext("list:a", "The Long Eclipse", listOf("kavita:item:1")),
        )

        assertEquals(listOf("kavita:item:9"), state.readerCompletionOrder("kavita:item:2"))
    }

    @Test
    fun `with no list the series decides`() {
        assertEquals(emptyList<String>(), AppUiState().readerCompletionOrder("kavita:item:2"))
    }

    @Test
    fun `a list's imported files join what the catalog tables found, once`() {
        val remote = item("kavita:item:1", SourceType.KAVITA)
        val local = item("comic-5", SourceType.LOCAL)
        val unrelated = item("comic-6", SourceType.LOCAL)

        val candidates = listCompletionCandidates(
            found = listOf(remote),
            fallbackItems = listOf(remote, local, unrelated),
            orderedItemIds = listOf("kavita:item:1", "comic-5"),
        )

        assertEquals(listOf("kavita:item:1", "comic-5"), candidates.map { it.id })
    }

    private fun item(id: String, source: SourceType) = ReadableItem(
        id = id,
        sourceType = source,
        libraryId = "lib",
        title = id,
        format = FormatType.CBZ,
    )
}
