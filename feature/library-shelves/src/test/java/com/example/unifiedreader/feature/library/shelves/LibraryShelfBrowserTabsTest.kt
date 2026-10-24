package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryShelfBrowserTabsTest {

    @Test
    fun `just started books stay out of In progress`() {
        val readInto = comic("read-into")
        val firstPage = comic("first-page")
        val opened = comic("opened")
        val state = LibraryShelvesStateComputer(LibrarySeriesContinuationResolver()).compute(
            LibraryShelvesInput(
                selectedSource = SourceType.LOCAL,
                items = listOf(readInto, firstPage, opened),
                progressByItemId = mapOf(
                    readInto.id to ItemProgressSummary(label = "", fraction = 0.5f, currentPage = 4, pageCount = 8, lastReadAtEpochMillis = 100L),
                    firstPage.id to ItemProgressSummary(label = "", fraction = 0.125f, currentPage = 1, pageCount = 8, lastReadAtEpochMillis = 300L),
                    opened.id to ItemProgressSummary(label = "", fraction = 0f, lastReadAtEpochMillis = 200L),
                ),
            ),
        )

        assertEquals(listOf(readInto.id), state.continueReading.map { it.item.id })
    }

    @Test
    fun `See all's tabs are the shelves with books, and the open one even when empty`() {
        val shelves = LibraryShelvesState(
            continueReading = listOf(ContinueReadingShelfItem(comic("a"), progress(0.5f))),
            recentlyFinished = listOf(RecentlyFinishedShelfItem(comic("b"), ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true, lastReadAtEpochMillis = 1L))),
        )

        assertEquals(
            listOf(LibraryShelfBrowserKind.ContinueReading to 1, LibraryShelfBrowserKind.RecentlyFinished to 1),
            shelves.browserTabs(selected = LibraryShelfBrowserKind.ContinueReading),
        )
        assertEquals(
            listOf(
                LibraryShelfBrowserKind.ContinueReading to 1,
                LibraryShelfBrowserKind.NextInSeries to 0,
                LibraryShelfBrowserKind.RecentlyFinished to 1,
            ),
            shelves.browserTabs(selected = LibraryShelfBrowserKind.NextInSeries),
        )
        assertTrue(LibraryShelvesState().browserState(LibraryShelfBrowserKind.ContinueReading).books.isEmpty())
    }

    @Test
    fun `See all's tiles say what the home strip says`() {
        val issue = comic("2")
        val entry = LibraryShelfBrowserState.ContinueReading(
            listOf(ContinueReadingShelfItem(issue, ItemProgressSummary(label = "Resume on page 3 of 8", fraction = 0.375f, currentPage = 3, pageCount = 8))),
        ).browserEntries(nowEpochMillis = 0L).single()

        assertEquals("Star Courier", entry.title)
        assertEquals(continueReadingSupportingText(issue), entry.supportingText)
        assertEquals("38%", entry.trailingText)
        assertEquals(0.375f, entry.progressFraction)
    }

    @Test
    fun `each shelf keeps its own sort and layout across a save`() {
        val state = LibraryShelfBrowserViewState()
        state.setSortMode(LibraryShelfBrowserKind.ContinueReading, ShelfBrowserSortMode.Title)
        state.setShowsList(LibraryShelfBrowserKind.RecentlyFinished, true)

        val saved = with(LibraryShelfBrowserViewState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }
        val restored = LibraryShelfBrowserViewState.Saver.restore(saved!!)!!

        assertEquals(ShelfBrowserSortMode.Title, restored.sortMode(LibraryShelfBrowserKind.ContinueReading))
        assertEquals(ShelfBrowserSortMode.ShelfOrder, restored.sortMode(LibraryShelfBrowserKind.RecentlyFinished))
        assertTrue(restored.showsList(LibraryShelfBrowserKind.RecentlyFinished))
        assertEquals(false, restored.showsList(LibraryShelfBrowserKind.ContinueReading))
    }
}

private fun comic(id: String): ReadableItem = ReadableItem(
    id = "local:$id",
    sourceType = SourceType.LOCAL,
    libraryId = "local:library",
    remoteId = id,
    title = "Star Courier $id",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(series = "Star Courier", issueNumber = id),
)

private fun progress(fraction: Float): ItemProgressSummary = ItemProgressSummary(label = "", fraction = fraction)
