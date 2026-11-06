package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicReadingDirectionNormalizationTest {
    @Test
    fun `global reconciliation clears stale series and item overrides after parent changes`() {
        val libraryId = "kavita:library:comics"
        val item = comicItem(
            id = "akira-1",
            libraryId = libraryId,
            series = "Akira",
        )
        val preferences = ComicReadingDirectionPreferences(
            libraryDirectionsById = mapOf(libraryId to ComicReadingDirection.RTL),
            seriesDirectionsByKey = mapOf("$libraryId::Akira" to ComicReadingDirection.RTL),
        )

        val plan = buildComicReadingDirectionReconciliationPlan(
            preferences = preferences,
            items = listOf(item),
            itemOverridesByItemId = mapOf(item.id to ComicReadingDirection.RTL),
        )

        assertEquals(
            mapOf("$libraryId::Akira" to null),
            plan.seriesOverrideUpdatesByKey,
        )
        assertEquals(
            mapOf(item.id to null),
            plan.itemOverrideUpdatesByItemId,
        )
    }
}

private fun comicItem(
    id: String,
    sourceType: SourceType = SourceType.KAVITA,
    libraryId: String,
    series: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType,
    libraryId = libraryId,
    remoteId = id,
    remoteFileType = series,
    title = "$series #1",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(series = series),
)
