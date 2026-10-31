package com.example.unifiedreader.feature.library.tags

import com.example.unifiedreader.core.database.entity.LibraryTagChoiceEntity
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryTagChoiceStoreTest {
    @Test
    fun `a stored choice reads back by source, case aside`() {
        val rows = listOf(
            LibraryTagSortChange(LibraryTagField.TAG, "Hollywood", LibraryTagChoice(LibraryTagKind.THEME)),
            LibraryTagSortChange(LibraryTagField.TAG, "Kosovo", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE)),
            LibraryTagSortChange(LibraryTagField.LOCATION, "Gotham City", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.MADE_UP)),
        ).map { it.toEntity(SourceType.GRIMMORY, chosenAtEpochMillis = 1L) }

        val sorting = rows.toLibraryTagSortings().getValue(SourceType.GRIMMORY)

        assertEquals("hollywood", rows.first().tagKey)
        assertEquals(LibraryTagKind.THEME, sorting.kindOf("HOLLYWOOD"))
        assertEquals(LibraryPlaceRegion.EUROPE, sorting.regionOfTag("kosovo"))
        assertEquals(LibraryPlaceRegion.MADE_UP, sorting.regionOfLocation("gotham city"))
    }

    @Test
    fun `only a place keeps a region, and a Location without one is Elsewhere`() {
        val time = LibraryTagSortChange(LibraryTagField.TAG, "1930s", LibraryTagChoice(LibraryTagKind.ERA, LibraryPlaceRegion.ASIA))
            .toEntity(SourceType.KAVITA, 1L)
        val location = LibraryTagSortChange(LibraryTagField.LOCATION, "Tokyo", LibraryTagChoice(LibraryTagKind.THEME))
            .toEntity(SourceType.KAVITA, 1L)

        assertNull(time.region)
        assertEquals(LibraryTagKind.PLACE.name, location.kind)
        assertEquals(LibraryPlaceRegion.ELSEWHERE.name, location.region)
    }

    @Test
    fun `rows naming something the app no longer knows are skipped`() {
        val rows = listOf(
            LibraryTagChoiceEntity("GONE", "TAG", "japan", "PLACE", "ASIA", 1L),
            LibraryTagChoiceEntity("KAVITA", "TAG", "japan", "SOMEWHERE", null, 1L),
            LibraryTagChoiceEntity("KAVITA", "TAG", "espionage", "THEME", null, 1L),
        )

        val sortings = rows.toLibraryTagSortings()

        assertEquals(setOf(SourceType.KAVITA), sortings.keys)
        assertEquals(setOf("espionage"), sortings.getValue(SourceType.KAVITA).tags.keys)
    }
}
