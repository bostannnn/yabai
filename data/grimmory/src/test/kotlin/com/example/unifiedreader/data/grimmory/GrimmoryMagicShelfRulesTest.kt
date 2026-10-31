package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleGroup
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRuleTimeUnit
import com.example.unifiedreader.core.model.ListRules
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GrimmoryMagicShelfRulesTest {
    private val shelfIds = mapOf("Book club" to "3", "Holiday" to "8")
    private val shelfIdByName: (String) -> String? = shelfIds::get
    private val shelfNameById: (String) -> String? = { id -> shelfIds.entries.firstOrNull { it.value == id }?.key }

    private fun read(json: String): ListRules? = grimmoryFilterJsonToListRules(json, shelfNameById)

    @Test
    fun `every field Grimmory can hold comes back as it went`() {
        val rules = ListRules(
            matchAll = true,
            rules = listOf(
                ListRule(ListRuleField.Author, ListRuleOperator.AllOf, listOf("Tolstoy", "Chekhov")),
                ListRule(ListRuleField.Series, ListRuleOperator.NoneOf, listOf("Discworld")),
                ListRule(ListRuleField.Genre, ListRuleOperator.AnyOf, listOf("Classics", "Poetry")),
                ListRule(ListRuleField.Tag, ListRuleOperator.AllOf, listOf("Russia", "19th century")),
                ListRule(ListRuleField.Mood, ListRuleOperator.NoneOf, listOf("Dark")),
                ListRule(ListRuleField.Language, ListRuleOperator.AnyOf, listOf("ru", "en")),
                ListRule(ListRuleField.Publisher, ListRuleOperator.AnyOf, listOf("Penguin")),
                ListRule(ListRuleField.Pages, ListRuleOperator.Between, low = 100, high = 400),
                ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1850, high = 1900),
                ListRule(ListRuleField.Status, ListRuleOperator.AnyOf, listOf("NOT_STARTED", "FINISHED")),
                ListRule(ListRuleField.LastRead, ListRuleOperator.NotInLast, low = 2, unit = ListRuleTimeUnit.Months),
                ListRule(ListRuleField.Added, ListRuleOperator.InLast, low = 30, unit = ListRuleTimeUnit.Days),
                ListRule(ListRuleField.Format, ListRuleOperator.AnyOf, listOf("EPUB", "PDF")),
                ListRule(ListRuleField.Shelf, ListRuleOperator.AllOf, listOf("Book club", "Holiday")),
            ),
            groups = listOf(
                ListRuleGroup(
                    matchAll = false,
                    rules = listOf(
                        ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 500),
                        ListRule(ListRuleField.Pages, ListRuleOperator.Over, low = 50),
                        ListRule(ListRuleField.Published, ListRuleOperator.Under, high = 1900),
                        ListRule(ListRuleField.Published, ListRuleOperator.Over, low = 2020),
                        ListRule(ListRuleField.Status, ListRuleOperator.NoneOf, listOf("IN_PROGRESS")),
                        ListRule(ListRuleField.LastRead, ListRuleOperator.InLast, low = 3, unit = ListRuleTimeUnit.Weeks),
                        ListRule(ListRuleField.Format, ListRuleOperator.NoneOf, listOf("CBZ", "CBR")),
                        ListRule(ListRuleField.Shelf, ListRuleOperator.NoneOf, listOf("Holiday")),
                    ),
                ),
            ),
        )

        assertEquals(rules, read(rules.toGrimmoryFilterJson(shelfIdByName)))
    }

    @Test
    fun `tags, genres and a page limit read as Grimmory's own rules`() {
        val rules = ListRules(
            matchAll = true,
            rules = listOf(
                ListRule(ListRuleField.Tag, ListRuleOperator.AllOf, listOf("Russia", "19th century")),
                ListRule(ListRuleField.Genre, ListRuleOperator.AnyOf, listOf("Classics", "Poetry")),
                ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 500),
            ),
        )

        assertEquals(
            """{"type":"group","join":"and","rules":[""" +
                """{"field":"tags","operator":"includes_all","value":["Russia","19th century"]},""" +
                """{"field":"categories","operator":"includes_any","value":["Classics","Poetry"]},""" +
                """{"field":"pageCount","operator":"less_than","value":500}]}""",
            rules.toGrimmoryFilterJson(shelfIdByName),
        )
    }

    @Test
    fun `years, statuses, times and shelves are written as Grimmory says them`() {
        val rules = ListRules(
            matchAll = false,
            rules = listOf(
                ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1850, high = 1900),
                ListRule(ListRuleField.Status, ListRuleOperator.NoneOf, listOf("NOT_STARTED", "IN_PROGRESS")),
                ListRule(ListRuleField.Added, ListRuleOperator.InLast, low = 2, unit = ListRuleTimeUnit.Weeks),
                ListRule(ListRuleField.Shelf, ListRuleOperator.AnyOf, listOf("Book club")),
                ListRule(ListRuleField.Format, ListRuleOperator.AnyOf, listOf("CBZ")),
            ),
            groups = listOf(ListRuleGroup(matchAll = true)),
        )

        assertEquals(
            Json.parseToJsonElement(
                """{"type":"group","join":"or","rules":[
                  {"field":"publishedDate","operator":"in_between","valueStart":"1850-01-01","valueEnd":"1900-12-31"},
                  {"field":"readStatus","operator":"excludes_all","value":["UNREAD","UNSET","READING","RE_READING","PARTIALLY_READ","PAUSED"]},
                  {"field":"addedOn","operator":"within_last","value":2,"valueEnd":"weeks"},
                  {"field":"shelf","operator":"includes_any","value":[3]},
                  {"field":"fileType","operator":"includes_any","value":["cbz"]}]}""",
            ),
            Json.parseToJsonElement(rules.toGrimmoryFilterJson(shelfIdByName)),
        )
    }

    @Test
    fun `the web app's example reads as the app's rules`() {
        val json = """
            {"type":"group","join":"and","rules":[
             {"field":"readStatus","operator":"includes_any","value":["READING","RE_READING"]},
             {"field":"addedOn","operator":"within_last","value":30,"valueEnd":"days"},
             {"type":"group","join":"or","rules":[
               {"field":"tags","operator":"includes_all","value":["Space Opera","Military"]},
               {"field":"pageCount","operator":"in_between","value":"","valueStart":200,"valueEnd":400},
               {"field":"publishedDate","operator":"greater_than","value":"2020-12-31"}]}]}
        """.trimIndent()

        assertEquals(
            ListRules(
                matchAll = true,
                rules = listOf(
                    ListRule(ListRuleField.Status, ListRuleOperator.AnyOf, listOf("IN_PROGRESS")),
                    ListRule(ListRuleField.Added, ListRuleOperator.InLast, low = 30, unit = ListRuleTimeUnit.Days),
                ),
                groups = listOf(
                    ListRuleGroup(
                        matchAll = false,
                        rules = listOf(
                            ListRule(ListRuleField.Tag, ListRuleOperator.AllOf, listOf("Space Opera", "Military")),
                            ListRule(ListRuleField.Pages, ListRuleOperator.Between, low = 200, high = 400),
                            ListRule(ListRuleField.Published, ListRuleOperator.Over, low = 2020),
                        ),
                    ),
                ),
            ),
            read(json),
        )
    }

    @Test
    fun `other ways of saying the same rules are read too`() {
        val json = """
            {"name":"Mine","rules":[
             {"field":"genre","operator":"equals","value":"Poetry"},
             {"field":"seriesName","operator":"includes_all","value":["Discworld"]},
             {"field":"language","operator":"not_equals","value":"en"},
             {"field":"readStatus","operator":"equals","value":"UNSET"},
             {"field":"pageCount","operator":"less_than_equal_to","value":"300"},
             {"field":"pageCount","operator":"greater_than_equal_to","value":100.0},
             {"field":"publishedDate","operator":"in_between","valueStart":"1851-03-04","valueEnd":"1899-06-30"},
             {"field":"lastReadTime","operator":"older_than","value":"1","valueEnd":"years"},
             {"field":"addedOn","operator":"within_last","value":5},
             {"field":"shelf","operator":"includes_any","value":["8"]},
             {"field":"fileType","operator":"includes_all","value":["epub"]}]}
        """.trimIndent()

        assertEquals(
            ListRules(
                matchAll = false,
                rules = listOf(
                    ListRule(ListRuleField.Genre, ListRuleOperator.AnyOf, listOf("Poetry")),
                    ListRule(ListRuleField.Series, ListRuleOperator.AnyOf, listOf("Discworld")),
                    ListRule(ListRuleField.Language, ListRuleOperator.NoneOf, listOf("en")),
                    ListRule(ListRuleField.Status, ListRuleOperator.AnyOf, listOf("NOT_STARTED")),
                    ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = 301),
                    ListRule(ListRuleField.Pages, ListRuleOperator.Over, low = 99),
                    ListRule(ListRuleField.Published, ListRuleOperator.Between, low = 1851, high = 1899),
                    ListRule(ListRuleField.LastRead, ListRuleOperator.NotInLast, low = 12, unit = ListRuleTimeUnit.Months),
                    ListRule(ListRuleField.Added, ListRuleOperator.InLast, low = 5, unit = ListRuleTimeUnit.Days),
                    ListRule(ListRuleField.Shelf, ListRuleOperator.AnyOf, listOf("Holiday")),
                    ListRule(ListRuleField.Format, ListRuleOperator.AnyOf, listOf("EPUB")),
                ),
            ),
            read(json),
        )
    }

    @Test
    fun `rules the app can't say read as null`() {
        fun shelf(vararg rules: String) = """{"type":"group","join":"and","rules":[${rules.joinToString(",")}]}"""

        val unreadable = mapOf(
            "an unknown field" to shelf("""{"field":"narrator","operator":"includes_any","value":["Fry"]}"""),
            "a library" to shelf("""{"field":"library","operator":"includes_any","value":[1]}"""),
            "an unknown operator" to shelf("""{"field":"tags","operator":"contains","value":"war"}"""),
            "a group in a group" to shelf(
                """{"type":"group","join":"or","rules":[{"type":"group","join":"and","rules":[""" +
                    """{"field":"tags","operator":"includes_any","value":["Russia"]}]}]}""",
            ),
            "won't read" to shelf("""{"field":"readStatus","operator":"includes_any","value":["READING","WONT_READ"]}"""),
            "an unknown file type" to shelf("""{"field":"fileType","operator":"includes_any","value":["mobi"]}"""),
            "an unknown shelf" to shelf("""{"field":"shelf","operator":"includes_any","value":[99]}"""),
            "a date that isn't a year's edge" to shelf("""{"field":"publishedDate","operator":"less_than","value":"2020-06-15"}"""),
            "this period" to shelf("""{"field":"addedOn","operator":"this_period","value":"month"}"""),
            "equals a list on a one-value field" to shelf("""{"field":"seriesName","operator":"equals","value":["Discworld"]}"""),
            "an empty group under any" to """{"type":"group","join":"or","rules":[{"field":"tags","operator":"includes_any","value":["Russia"]},""" +
                """{"type":"group","join":"and","rules":[]}]}""",
            "not JSON" to "Russia",
        )

        unreadable.forEach { (case, json) -> assertNull(case, read(json)) }
    }

    @Test
    fun `an empty group under all changes nothing and is dropped`() {
        val rules = read(
            """{"type":"group","join":"and","rules":[{"field":"tags","operator":"includes_any","value":["Russia"]},""" +
                """{"type":"group","join":"or","rules":[]}]}""",
        )

        assertEquals(ListRules(matchAll = true, rules = listOf(ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("Russia")))), rules)
    }

    @Test
    fun `rules only this device knows can't be written`() {
        listOf(
            ListRule(ListRuleField.Downloaded, ListRuleOperator.Is) to "Downloaded",
            ListRule(ListRuleField.Source, ListRuleOperator.AnyOf, listOf("KAVITA")) to "Source: Kavita",
            ListRule(ListRuleField.Library, ListRuleOperator.AnyOf, listOf("1")) to "Library: 1",
        ).forEach { (rule, named) ->
            try {
                ListRules(groups = listOf(ListRuleGroup(rules = listOf(rule)))).toGrimmoryFilterJson(shelfIdByName)
                fail("Expected ${rule.field} to be refused.")
            } catch (exception: IllegalArgumentException) {
                assertTrue(exception.message.orEmpty(), exception.message.orEmpty().contains(named))
            }
        }
    }

    @Test
    fun `a shelf Grimmory has no id for can't be written`() {
        try {
            ListRules(rules = listOf(ListRule(ListRuleField.Shelf, ListRuleOperator.AnyOf, listOf("Gone"))))
                .toGrimmoryFilterJson(shelfIdByName)
            fail("Expected the missing shelf to be refused.")
        } catch (exception: IllegalArgumentException) {
            assertTrue(exception.message.orEmpty(), exception.message.orEmpty().contains("Gone"))
        }
    }
}
