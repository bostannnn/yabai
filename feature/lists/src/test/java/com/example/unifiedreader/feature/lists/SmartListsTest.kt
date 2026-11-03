package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleGroup
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRules
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
    fun `a list saved from filters picks what they did, from that library only, newest first`() {
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
            // A list saved from the filters before rules: read back as rules, the library's source among them.
            smart = decodeSmartListSpec(
                SmartListFilter(
                    SourceType.KAVITA,
                    LibraryFilterState(pages = LibraryPageRange(max = 60), readingStatuses = setOf(LibraryReadingStatus.NOT_STARTED)),
                ).encode(),
            ),
        )
        val progress = mapOf("short-read" to ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true))

        val resolved = listOf(smart).withSmartBooks(items, progress, onDevice = emptySet()).single()

        assertEquals(listOf("short-new", "short-old"), resolved.sortedEntries().map(UserListEntry::itemId))
        assertEquals("Updates itself · 2", resolved.tileCaption(progress))
    }

    @Test
    fun `rules pick from every source, in the list's order, as many as it keeps`() {
        val items = listOf(
            tagged("kavita-old", SourceType.KAVITA, year = 1850, tags = listOf("Russia")),
            tagged("grimmory-new", SourceType.GRIMMORY, year = 1990, tags = listOf("russia", "Space")),
            tagged("local-mid", SourceType.LOCAL, year = 1920, tags = listOf("Russia")),
            tagged("elsewhere", SourceType.GRIMMORY, year = 1900, tags = listOf("France")),
        )
        val smart = UserList(
            id = "list:smart",
            name = "Russia",
            description = null,
            kind = UserListKind.Smart,
            order = UserListOrder.Published,
            hideFinished = false,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
            lastOpenedAtEpochMillis = 0,
            entries = emptyList(),
            smart = SmartListSpec(
                rules = ListRules(rules = listOf(ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("Russia")))),
                limit = 2,
            ),
        )

        val resolved = listOf(smart).withSmartBooks(items, emptyMap(), onDevice = emptySet()).single()

        assertEquals(listOf("kavita-old", "local-mid"), resolved.sortedEntries().map(UserListEntry::itemId))
    }

    @Test
    fun `a smart list's text keeps its rules and how many`() {
        val spec = SmartListSpec(
            rules = ListRules(
                matchAll = false,
                rules = listOf(ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 500)),
                groups = listOf(ListRuleGroup(matchAll = true, rules = listOf(ListRule(ListRuleField.Tag, ListRuleOperator.AllOf, listOf("Russia", "19th century"))))),
            ),
            limit = 25,
        )

        assertEquals(spec, decodeSmartListSpec(spec.encode()))
        assertNull(decodeSmartListSpec("not a list"))
    }

    @Test
    fun `a smart list saved from Filters keeps its picks beside its rules`() {
        val picks = SmartListFilter(
            sourceType = SourceType.GRIMMORY,
            filters = LibraryFilterState(
                readingStatuses = setOf(LibraryReadingStatus.NOT_STARTED),
                genres = setOf("Fantasy", "Horror"),
                places = setOf("Russia"),
                times = setOf("1800s"),
                moods = setOf("Dark"),
                pages = LibraryPageRange(min = 100, max = 400, includeUnknown = false),
            ),
        )
        val spec = SmartListSpec(
            rules = ListRules(rules = listOf(ListRule(ListRuleField.Genre, ListRuleOperator.AnyOf, listOf("Fantasy", "Horror")))),
            filter = picks,
        )

        val decoded = decodeSmartListSpec(spec.encode())

        assertEquals(spec, decoded)
        // The rules alone still read for an app that knows nothing of the picks.
        assertEquals(spec.rules, com.example.unifiedreader.core.model.decodeListRules(spec.encode()))
    }

    private fun tagged(id: String, source: SourceType, year: Int, tags: List<String>) = ReadableItem(
        id = id,
        sourceType = source,
        libraryId = "lib",
        title = id,
        format = FormatType.EPUB,
        publishedYear = year,
        bookMetadata = BookMetadata(tags = tags),
    )

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
