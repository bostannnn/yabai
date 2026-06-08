package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.authorFacetKey
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterComponentsTest {
    @Test
    fun `searchable facet display values can preserve duplicate labels with stable value keys`() {
        val titlesById = mapOf(
            "list-1" to "Hellboy",
            "list-2" to "Hellboy",
        )

        val displayValues = searchableFacetDisplayValues(
            values = listOf("list-1", "list-2"),
            selectedValues = emptySet(),
            valueKey = { id -> id },
        )

        assertEquals(listOf("list-1", "list-2"), displayValues)
        assertEquals(listOf("Hellboy", "Hellboy"), displayValues.map(titlesById::getValue))
    }

    @Test
    fun `searchable facet display values still support label based dedupe`() {
        val titlesById = mapOf(
            "list-1" to "Hellboy",
            "list-2" to "Hellboy",
        )

        val displayValues = searchableFacetDisplayValues(
            values = listOf("list-1", "list-2"),
            selectedValues = emptySet(),
            valueKey = { id -> titlesById.getValue(id).lowercase() },
        )

        assertEquals(listOf("list-1"), displayValues)
    }

    @Test
    fun `searchable facet display values sort selected values by display label`() {
        val titlesById = mapOf(
            "list-a" to "Alpha",
            "list-b" to "Beta",
            "list-z" to "Zulu",
        )

        val displayValues = searchableFacetDisplayValues(
            values = listOf("list-b", "list-z"),
            selectedValues = setOf("list-a"),
            valueKey = { id -> id },
            valueLabel = titlesById::getValue,
            letterKey = { it },
        )

        assertEquals(listOf("list-a", "list-b", "list-z"), displayValues)
    }

    @Test
    fun `searchable facet display values sort by letter key before label`() {
        val displayValues = searchableFacetDisplayValues(
            values = listOf("Alan Moore", "Brian Michael Bendis"),
            selectedValues = emptySet(),
            valueKey = { value -> value.lowercase() },
            letterKey = String::authorFacetKey,
        )

        assertEquals(listOf("Brian Michael Bendis", "Alan Moore"), displayValues)
    }
}
