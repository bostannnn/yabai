package com.example.unifiedreader.feature.library

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
}
