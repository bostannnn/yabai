package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleGroup
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartListEditorPartsTest {
    private val context = listRuleContext(emptyMap(), emptySet(), nowEpochMillis = 0)
    private val pool = listOf(
        book("a", pages = 120, year = 1890, tags = listOf("Russia")),
        book("b", pages = 640, year = 1869, tags = listOf("Russia", "War")),
        book("c", pages = 90, year = 1925, tags = listOf("France")),
    )
    private val russia = ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("Russia"))
    private val short = ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 200)
    private val old = ListRule(ListRuleField.Published, ListRuleOperator.Under, high = 1800)

    @Test
    fun `a rule reads in plain words after its field`() {
        assertEquals("Fantasy or Horror", ListRule(ListRuleField.Genre, ListRuleOperator.AnyOf, listOf("Fantasy", "Horror")).phrase())
        assertEquals("Russia and War", ListRule(ListRuleField.Tag, ListRuleOperator.AllOf, listOf("Russia", "War")).phrase())
        assertEquals("Not Comics", ListRule(ListRuleField.Genre, ListRuleOperator.NoneOf, listOf("Comics")).phrase())
        assertEquals("Under 200 pages", short.phrase())
        assertEquals("Before 1800", old.phrase())
        assertEquals("1850–1900", ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1850, high = 1900).phrase())
        // Many values: the first three and how many more.
        assertEquals(
            "a, b, c or 3 more",
            ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("a", "b", "c", "d", "e", "f")).phrase(),
        )
    }

    @Test
    fun `each rule counts the books it picks on its own, groups included`() {
        val rules = ListRules(rules = listOf(russia), groups = listOf(ListRuleGroup(matchAll = false, rules = listOf(short))))

        assertEquals(mapOf(russia to 2, short to 2), smartListRuleCounts(rules, pool, context))
    }

    @Test
    fun `the rule that leaves nothing is named, with what would match without it`() {
        val rules = ListRules(matchAll = true, rules = listOf(russia, short, old))

        assertEquals(old to 1, smartListEmptiedBy(rules, pool, context))
    }

    @Test
    fun `no culprit while something matches, for any-of rules, or for a single rule`() {
        assertNull(smartListEmptiedBy(ListRules(matchAll = true, rules = listOf(russia, short)), pool, context))
        assertNull(smartListEmptiedBy(ListRules(matchAll = false, rules = listOf(old, ListRule(ListRuleField.Published, ListRuleOperator.Under, high = 1700))), pool, context))
        assertNull(smartListEmptiedBy(ListRules(matchAll = true, rules = listOf(old)), pool, context))
    }

    private fun book(id: String, pages: Int, year: Int, tags: List<String>) = ReadableItem(
        id = id,
        sourceType = SourceType.LOCAL,
        libraryId = "lib",
        title = id,
        format = FormatType.EPUB,
        pageCount = pages,
        publishedYear = year,
        bookMetadata = BookMetadata(tags = tags),
    )
}
