package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private val sons = book(
        id = "sons",
        tags = listOf("Russia", "19th century", "Family"),
        genres = listOf("Classics"),
        pages = 300,
        year = 1862,
        added = now - 3 * day,
    )
    private val ship = book(id = "ship", tags = listOf("Space"), genres = listOf("Science fiction"), pages = 700, year = 1990, added = now - 90 * day)
    private val yozh = book(id = "yozh", tags = listOf("Ёжики"), genres = listOf("Poetry"), pages = null, year = null, added = null)

    private val context = ListRuleContext(
        nowEpochMillis = now,
        readingStatus = { item -> if (item.id == "ship") LibraryReadingStatus.FINISHED else LibraryReadingStatus.NOT_STARTED },
        lastReadAtEpochMillis = { item -> if (item.id == "ship") now - 30 * day else null },
        isDownloaded = { item -> item.id == "sons" },
    )

    @Test
    fun `names match any, all or none of the values, case and ё aside`() {
        assertTrue(rule(ListRuleField.Tag, ListRuleOperator.AllOf, "russia", "19TH CENTURY").matches(sons, context))
        assertFalse(rule(ListRuleField.Tag, ListRuleOperator.AllOf, "Russia", "Space").matches(sons, context))
        assertTrue(rule(ListRuleField.Genre, ListRuleOperator.AnyOf, "Poetry", "Classics").matches(sons, context))
        assertTrue(rule(ListRuleField.Tag, ListRuleOperator.AnyOf, "ежики").matches(yozh, context))
        assertTrue(rule(ListRuleField.Genre, ListRuleOperator.NoneOf, "Poetry").matches(ship, context))
        assertFalse(rule(ListRuleField.Genre, ListRuleOperator.NoneOf, "Poetry").matches(yozh, context))
    }

    @Test
    fun `numbers, years and times leave out books that don't have them`() {
        assertTrue(ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 500).matches(sons, context))
        assertFalse(ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 500).matches(yozh, context))
        assertTrue(ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1850, high = 1900).matches(sons, context))
        assertFalse(ListRule(ListRuleField.Published, ListRuleOperator.Over, low = 1990).matches(ship, context))
        val addedLately = ListRule(ListRuleField.Added, ListRuleOperator.InLast, low = 1, unit = ListRuleTimeUnit.Weeks)
        assertTrue(addedLately.matches(sons, context))
        assertFalse(addedLately.matches(ship, context))
        assertFalse(addedLately.matches(yozh, context))
        val readLongAgo = ListRule(ListRuleField.LastRead, ListRuleOperator.NotInLast, low = 3, unit = ListRuleTimeUnit.Weeks)
        assertTrue(readLongAgo.matches(ship, context))
        assertFalse(readLongAgo.matches(sons, context))
    }

    @Test
    fun `your reading and downloads come from the device`() {
        assertTrue(rule(ListRuleField.Status, ListRuleOperator.AnyOf, LibraryReadingStatus.FINISHED.name).matches(ship, context))
        assertTrue(ListRule(ListRuleField.Downloaded, ListRuleOperator.Is).matches(sons, context))
        assertTrue(ListRule(ListRuleField.Downloaded, ListRuleOperator.IsNot).matches(ship, context))
        assertTrue(rule(ListRuleField.Source, ListRuleOperator.AnyOf, SourceType.GRIMMORY.name).matches(ship, context))
    }

    @Test
    fun `a list's rules join by all or any, and a group counts as one rule`() {
        val classicsOrShort = ListRules(
            matchAll = true,
            rules = listOf(rule(ListRuleField.Tag, ListRuleOperator.AnyOf, "Russia")),
            groups = listOf(
                ListRuleGroup(
                    matchAll = false,
                    rules = listOf(
                        rule(ListRuleField.Genre, ListRuleOperator.AnyOf, "Poetry"),
                        ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 400),
                    ),
                ),
            ),
        )
        assertTrue(classicsOrShort.matches(sons, context))
        assertFalse(classicsOrShort.matches(ship, context))
        assertTrue(classicsOrShort.copy(matchAll = false).matches(yozh, context))
    }

    @Test
    fun `rules come back from their text as they were saved`() {
        val rules = ListRules(
            matchAll = false,
            rules = listOf(
                rule(ListRuleField.Tag, ListRuleOperator.AllOf, "Russia", "a,b|c=d\ne"),
                ListRule(ListRuleField.Added, ListRuleOperator.InLast, low = 30, unit = ListRuleTimeUnit.Days),
            ),
            groups = listOf(ListRuleGroup(matchAll = true, rules = listOf(ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1850, high = 1900)))),
        )

        assertEquals(rules, decodeListRules(rules.encode()))
        assertNull(decodeListRules("source=KAVITA\nformats="))
    }

    @Test
    fun `the library's filters become rules, its source first`() {
        val rules = LibraryFilterState(
            formats = setOf(FormatType.CBZ),
            readingStatuses = setOf(LibraryReadingStatus.NOT_STARTED),
            fileLocations = setOf(LibraryFileLocation.LOCAL),
            pages = LibraryPageRange(max = 60),
            genres = setOf("Science fiction"),
        ).toListRules(SourceType.KAVITA)

        assertEquals(
            listOf(
                rule(ListRuleField.Source, ListRuleOperator.AnyOf, "KAVITA"),
                rule(ListRuleField.Format, ListRuleOperator.AnyOf, "CBZ"),
                ListRule(ListRuleField.Downloaded, ListRuleOperator.Is),
                rule(ListRuleField.Status, ListRuleOperator.AnyOf, "NOT_STARTED"),
                ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 61),
                rule(ListRuleField.Genre, ListRuleOperator.AnyOf, "Science fiction"),
            ),
            rules.rules,
        )
        assertFalse(rules.fitsMagicShelf)
    }

    @Test
    fun `rules read as pills and summaries`() {
        assertEquals("Tag: Russia and 19th century", rule(ListRuleField.Tag, ListRuleOperator.AllOf, "Russia", "19th century").pill())
        assertEquals("Genre: Classics or Poetry", rule(ListRuleField.Genre, ListRuleOperator.AnyOf, "Classics", "Poetry").pill())
        assertEquals("Pages under 500", ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 500).pill())
        assertEquals("Published 1850–1900", ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1850, high = 1900).pill())
        assertEquals("Status: Reading", rule(ListRuleField.Status, ListRuleOperator.AnyOf, "IN_PROGRESS").pill())
        assertEquals("is all of: Russia, 19th century", rule(ListRuleField.Tag, ListRuleOperator.AllOf, "Russia", "19th century").summary())
        assertEquals("not in the last 3 weeks", ListRule(ListRuleField.LastRead, ListRuleOperator.NotInLast, low = 3, unit = ListRuleTimeUnit.Weeks).summary())
    }

    private fun rule(field: ListRuleField, operator: ListRuleOperator, vararg values: String) =
        ListRule(field, operator, values.toList())

    private fun book(id: String, tags: List<String>, genres: List<String>, pages: Int?, year: Int?, added: Long?) = ReadableItem(
        id = id,
        sourceType = if (id == "ship") SourceType.GRIMMORY else SourceType.LOCAL,
        libraryId = "lib",
        title = id,
        format = FormatType.EPUB,
        pageCount = pages,
        publishedYear = year,
        addedAtEpochMillis = added,
        bookMetadata = BookMetadata(tags = tags, genres = genres),
    )
}
