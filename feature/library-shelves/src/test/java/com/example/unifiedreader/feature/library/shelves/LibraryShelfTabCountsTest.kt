package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelfTabCountsTest {
    @Test
    fun `In progress counts the whole shelf, as the header and See all do`() {
        val state = LibraryShelvesState(continueReading = listOf(reading("a"), reading("b"), reading("c")))

        assertEquals(
            listOf(LibraryShelfTab.InProgress to 3),
            libraryShelfTabCounts(state, readingCardItemId = "a"),
        )
    }

    @Test
    fun `In progress hides when the reading card holds its only book`() {
        val state = LibraryShelvesState(continueReading = listOf(reading("a")))

        assertEquals(emptyList<Pair<LibraryShelfTab, Int>>(), libraryShelfTabCounts(state, readingCardItemId = "a"))
    }

    private fun reading(id: String) = ContinueReadingShelfItem(
        item = ReadableItem(id = id, sourceType = SourceType.LOCAL, libraryId = "local", title = id, format = FormatType.EPUB),
        progress = ItemProgressSummary(label = "", fraction = 0.4f),
    )
}
