package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelfItemsBrowserEntriesTest {
    @Test
    fun `search filters shelf entries`() {
        val state = continueReadingState()

        assertEquals(
            listOf("metro"),
            state.visibleItemRemoteIds(
                searchQuery = "metro",
                sortMode = ShelfBrowserSortMode.ShelfOrder,
            ),
        )
    }

    @Test
    fun `shelf order sort preserves incoming shelf order`() {
        val state = continueReadingState()

        assertEquals(
            listOf("zulu", "alpha", "metro"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.ShelfOrder),
        )
    }

    @Test
    fun `recent activity sort uses latest shelf activity first`() {
        val state = continueReadingState()

        assertEquals(
            listOf("alpha", "metro", "zulu"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.RecentActivity),
        )
    }

    @Test
    fun `progress sort uses highest progress first`() {
        val state = continueReadingState()

        assertEquals(
            listOf("zulu", "metro", "alpha"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.Progress),
        )
    }

    @Test
    fun `title sort uses shelf entry title`() {
        val state = continueReadingState()

        assertEquals(
            listOf("alpha", "metro", "zulu"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.Title),
        )
    }

    @Test
    fun `series sort uses item series metadata`() {
        val state = continueReadingState()

        assertEquals(
            listOf("alpha", "zulu", "metro"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.Series),
        )
    }

    @Test
    fun `author sort uses item author metadata`() {
        val state = continueReadingState()

        assertEquals(
            listOf("alpha", "metro", "zulu"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.Author),
        )
    }

    @Test
    fun `date added sort uses newest item first`() {
        val state = continueReadingState()

        assertEquals(
            listOf("alpha", "metro", "zulu"),
            state.visibleItemRemoteIds(sortMode = ShelfBrowserSortMode.DateAdded),
        )
    }
}

private fun LibraryShelfBrowserState.visibleItemRemoteIds(
    searchQuery: String = "",
    sortMode: ShelfBrowserSortMode,
): List<String?> = visibleShelfBrowserEntries(
    nowEpochMillis = 1_000L,
    searchQuery = searchQuery,
    sortMode = sortMode,
).map { entry -> entry.item.remoteId }

private fun continueReadingState(): LibraryShelfBrowserState.ContinueReading = LibraryShelfBrowserState.ContinueReading(
    items = listOf(
        continueReadingItem(
            item = book(
                id = "zulu",
                title = "Zulu Sky",
                author = "Carol",
                series = "Beta Cycle",
                addedAtEpochMillis = 10L,
            ),
            fraction = 0.9f,
            lastReadAtEpochMillis = 100L,
        ),
        continueReadingItem(
            item = book(
                id = "alpha",
                title = "Alpha Road",
                author = "Alice",
                series = "Alpha Cycle",
                addedAtEpochMillis = 30L,
            ),
            fraction = 0.3f,
            lastReadAtEpochMillis = 300L,
        ),
        continueReadingItem(
            item = book(
                id = "metro",
                title = "Metro Gardens",
                author = "Bob",
                series = "Gamma Cycle",
                addedAtEpochMillis = 20L,
            ),
            fraction = 0.6f,
            lastReadAtEpochMillis = 200L,
        ),
    ),
)

private fun continueReadingItem(
    item: ReadableItem,
    fraction: Float,
    lastReadAtEpochMillis: Long,
): ContinueReadingShelfItem = ContinueReadingShelfItem(
    item = item,
    progress = ItemProgressSummary(
        label = "Resume",
        fraction = fraction,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
    ),
)

private fun book(
    id: String,
    title: String,
    author: String,
    series: String,
    addedAtEpochMillis: Long,
): ReadableItem = ReadableItem(
    id = "kavita:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library",
    remoteId = id,
    title = title,
    format = FormatType.EPUB,
    addedAtEpochMillis = addedAtEpochMillis,
    bookMetadata = BookMetadata(
        author = author,
        seriesName = series,
    ),
)
