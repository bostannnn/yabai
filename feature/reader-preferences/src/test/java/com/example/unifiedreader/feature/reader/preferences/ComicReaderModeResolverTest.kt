package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicReaderModeResolverTest {
    @Test
    fun `resolve comic reader mode prefers item override over series and library`() {
        val item = readerModeComicItem()
        val preferences = ComicReaderModePreferences(
            libraryModesById = mapOf(item.libraryId to ComicReaderMode.Paged),
            seriesModesByKey = mapOf(readerModeItemSeriesKey to ComicReaderMode.VerticalScroll),
        )

        val resolved = resolveComicReaderMode(
            item = item,
            itemOverride = ComicReaderMode.Paged,
            preferences = preferences,
        )

        assertEquals(ComicReaderMode.Paged, resolved)
    }

    @Test
    fun `resolve comic reader mode falls back to library when series override missing`() {
        val item = readerModeComicItem()
        val preferences = ComicReaderModePreferences(
            libraryModesById = mapOf(item.libraryId to ComicReaderMode.VerticalScroll),
        )

        val resolved = resolveComicReaderMode(
            item = item,
            itemOverride = null,
            preferences = preferences,
        )

        assertEquals(ComicReaderMode.VerticalScroll, resolved)
    }

    @Test
    fun `normalize series mode override clears override when it matches library`() {
        val item = readerModeComicItem()
        val preferences = ComicReaderModePreferences(
            libraryModesById = mapOf(item.libraryId to ComicReaderMode.VerticalScroll),
        )

        val normalized = normalizeComicSeriesModeOverride(
            item = item,
            selectedMode = ComicReaderMode.VerticalScroll,
            preferences = preferences,
        )

        assertNull(normalized)
    }

    @Test
    fun `normalize item mode override clears override when it matches inherited series mode`() {
        val item = readerModeComicItem()
        val preferences = ComicReaderModePreferences(
            libraryModesById = mapOf(item.libraryId to ComicReaderMode.Paged),
            seriesModesByKey = mapOf(readerModeItemSeriesKey to ComicReaderMode.VerticalScroll),
        )

        val normalized = normalizeComicItemModeOverride(
            item = item,
            selectedMode = ComicReaderMode.VerticalScroll,
            preferences = preferences,
        )

        assertNull(normalized)
    }

    @Test
    fun `normalize item mode override persists when it differs from inherited mode`() {
        val item = readerModeComicItem()
        val preferences = ComicReaderModePreferences(
            libraryModesById = mapOf(item.libraryId to ComicReaderMode.VerticalScroll),
        )

        val normalized = normalizeComicItemModeOverride(
            item = item,
            selectedMode = ComicReaderMode.Paged,
            preferences = preferences,
        )

        assertEquals(ComicReaderMode.Paged, normalized)
    }

    @Test
    fun `reconciliation clears redundant stored series override even when series items are not loaded`() {
        val preferences = ComicReaderModePreferences(
            libraryModesById = mapOf("kavita:library:webtoons" to ComicReaderMode.VerticalScroll),
            seriesModesByKey = mapOf(
                "kavita:library:webtoons::Tower of God" to ComicReaderMode.VerticalScroll,
            ),
        )

        val plan = buildComicReaderModeReconciliationPlan(
            preferences = preferences,
            items = emptyList(),
            itemOverridesByItemId = emptyMap(),
        )

        assertEquals(
            mapOf("kavita:library:webtoons::Tower of God" to null),
            plan.seriesOverrideUpdatesByKey,
        )
        assertTrue(plan.itemOverrideUpdatesByItemId.isEmpty())
    }
}

private const val readerModeItemSeriesKey = "kavita:library:webtoons::Tower of God"

private fun readerModeComicItem(): ReadableItem = ReadableItem(
    id = "kavita:item:tog-1",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:webtoons",
    remoteId = "tog-1",
    remoteFileType = "Tower of God",
    title = "Tower of God 001",
    format = FormatType.CBZ,
)
