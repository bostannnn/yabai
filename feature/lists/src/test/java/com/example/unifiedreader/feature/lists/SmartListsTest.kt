package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartListsTest {
    @Test
    fun `a smart list's filters come back as they were saved`() {
        val filter = SmartListFilter(
            sourceType = SourceType.KAVITA,
            filters = LibraryFilterState(
                formats = setOf(FormatType.CBZ),
                readingStatuses = setOf(LibraryReadingStatus.NOT_STARTED),
                pages = LibraryPageRange(max = 60, includeUnknown = true),
                genres = setOf("Science fiction", "a,b=c\nd"),
                libraries = setOf("kavita:library:2"),
            ),
        )

        assertEquals(filter, decodeSmartListFilter(filter.encode()))
        assertNull(decodeSmartListFilter("not a filter"))
    }

    @Test
    fun `its books are what the filters pick out of its library now, newest first`() {
        val items = listOf(
            book("short-new", SourceType.KAVITA, pages = 40, added = 30),
            book("short-old", SourceType.KAVITA, pages = 50, added = 10),
            book("long", SourceType.KAVITA, pages = 400, added = 20),
            book("short-read", SourceType.KAVITA, pages = 30, added = 40),
            book("elsewhere", SourceType.GRIMMORY, pages = 20, added = 50),
        )
        val smart = UserList(
            id = "list:smart",
            name = "Short reads",
            description = null,
            kind = UserListKind.Smart,
            order = UserListOrder.DateAdded,
            hideFinished = false,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
            lastOpenedAtEpochMillis = 0,
            entries = emptyList(),
            smartFilter = SmartListFilter(
                SourceType.KAVITA,
                LibraryFilterState(pages = LibraryPageRange(max = 60), readingStatuses = setOf(LibraryReadingStatus.NOT_STARTED)),
            ),
        )
        val progress = mapOf("short-read" to ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true))

        val resolved = listOf(smart).withSmartBooks(items, progress, onDevice = emptySet()).single()

        assertEquals(listOf("short-new", "short-old"), resolved.sortedEntries().map(UserListEntry::itemId))
        assertEquals("Updates itself · 2", resolved.tileCaption(progress))
    }

    private fun book(id: String, source: SourceType, pages: Int, added: Long) = ReadableItem(
        id = id,
        sourceType = source,
        libraryId = "lib",
        title = id,
        format = FormatType.CBZ,
        pageCount = pages,
        addedAtEpochMillis = added,
    )
}
