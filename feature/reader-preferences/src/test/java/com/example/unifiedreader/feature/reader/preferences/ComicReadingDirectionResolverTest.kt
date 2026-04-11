package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicReadingDirectionResolverTest {
    @Test
    fun `resolve comic reading direction prefers item override over series and library`() {
        val item = comicItem()
        val preferences = ComicReadingDirectionPreferences(
            libraryDirectionsById = mapOf(item.libraryId to ComicReadingDirection.LTR),
            seriesDirectionsByKey = mapOf(itemSeriesKey to ComicReadingDirection.RTL),
        )

        val resolved = resolveComicReadingDirection(
            item = item,
            itemOverride = ComicReadingDirection.LTR,
            preferences = preferences,
        )

        assertEquals(ComicReadingDirection.LTR, resolved)
    }

    @Test
    fun `resolve comic reading direction falls back to library when series override missing`() {
        val item = comicItem()
        val preferences = ComicReadingDirectionPreferences(
            libraryDirectionsById = mapOf(item.libraryId to ComicReadingDirection.RTL),
        )

        val resolved = resolveComicReadingDirection(
            item = item,
            itemOverride = null,
            preferences = preferences,
        )

        assertEquals(ComicReadingDirection.RTL, resolved)
    }

    @Test
    fun `normalize series override clears override when it matches library`() {
        val item = comicItem()
        val preferences = ComicReadingDirectionPreferences(
            libraryDirectionsById = mapOf(item.libraryId to ComicReadingDirection.RTL),
        )

        val normalized = normalizeComicSeriesDirectionOverride(
            item = item,
            selectedDirection = ComicReadingDirection.RTL,
            preferences = preferences,
        )

        assertNull(normalized)
    }

    @Test
    fun `normalize item override clears override when it matches inherited series direction`() {
        val item = comicItem()
        val preferences = ComicReadingDirectionPreferences(
            libraryDirectionsById = mapOf(item.libraryId to ComicReadingDirection.LTR),
            seriesDirectionsByKey = mapOf(itemSeriesKey to ComicReadingDirection.RTL),
        )

        val normalized = normalizeComicItemDirectionOverride(
            item = item,
            selectedDirection = ComicReadingDirection.RTL,
            preferences = preferences,
        )

        assertNull(normalized)
    }

    @Test
    fun `normalize item override persists when it differs from inherited direction`() {
        val item = comicItem()
        val preferences = ComicReadingDirectionPreferences(
            libraryDirectionsById = mapOf(item.libraryId to ComicReadingDirection.RTL),
        )

        val normalized = normalizeComicItemDirectionOverride(
            item = item,
            selectedDirection = ComicReadingDirection.LTR,
            preferences = preferences,
        )

        assertEquals(ComicReadingDirection.LTR, normalized)
    }
}

private const val itemSeriesKey = "kavita:library:comics::Akira"

private fun comicItem(): ReadableItem = ReadableItem(
    id = "kavita:item:akira-1",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:comics",
    remoteId = "akira-1",
    remoteFileType = "Akira",
    title = "Akira 001",
    format = FormatType.CBZ,
)
