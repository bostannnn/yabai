package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.feature.reader.comics.ComicPageFitMode
import com.example.unifiedreader.feature.reader.comics.ComicReadingDirection
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import com.example.unifiedreader.feature.reader.comics.ComicsPageFitModeKey
import com.example.unifiedreader.feature.reader.comics.ComicsReadingDirectionKey
import com.example.unifiedreader.feature.reader.comics.ComicsSpreadModeKey
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderSessionEntityCodecTest {
    @Test
    fun `to snapshot prefers structured preferences json over legacy columns`() {
        val snapshot = ReaderSessionStateEntity(
            itemId = "comic-42",
            page = null,
            pageCount = 18,
            readingDirection = ComicReadingDirection.LTR.name,
            pageFitMode = ComicPageFitMode.FitPage.name,
            spreadMode = ComicSpreadMode.Auto.name,
            preferencesJson = JSONObject(
                mapOf(
                    ComicsReadingDirectionKey to ComicReadingDirection.RTL.name,
                    ComicsPageFitModeKey to ComicPageFitMode.FitWidth.name,
                    ComicsSpreadModeKey to ComicSpreadMode.Double.name,
                    "customTheme" to "sepia",
                ),
            ).toString(),
            updatedAtEpochMillis = 1234L,
        ).let(ReaderSessionEntityCodecTestAccess::toSnapshot)

        assertEquals("comic-42", snapshot.itemId)
        assertEquals(18, snapshot.pageCount)
        assertEquals(ComicReadingDirection.RTL.name, snapshot.preferences[ComicsReadingDirectionKey])
        assertEquals(ComicPageFitMode.FitWidth.name, snapshot.preferences[ComicsPageFitModeKey])
        assertEquals(ComicSpreadMode.Double.name, snapshot.preferences[ComicsSpreadModeKey])
        assertEquals("sepia", snapshot.preferences["customTheme"])
    }

    @Test
    fun `to snapshot falls back to legacy columns when preferences json is invalid`() {
        val snapshot = ReaderSessionStateEntity(
            itemId = "comic-legacy",
            page = null,
            pageCount = 9,
            readingDirection = ComicReadingDirection.RTL.name,
            pageFitMode = ComicPageFitMode.FitHeight.name,
            spreadMode = ComicSpreadMode.Single.name,
            preferencesJson = "{not-valid-json",
            updatedAtEpochMillis = 1234L,
        ).let(ReaderSessionEntityCodecTestAccess::toSnapshot)

        assertEquals(ComicReadingDirection.RTL.name, snapshot.preferences[ComicsReadingDirectionKey])
        assertEquals(ComicPageFitMode.FitHeight.name, snapshot.preferences[ComicsPageFitModeKey])
        assertEquals(ComicSpreadMode.Single.name, snapshot.preferences[ComicsSpreadModeKey])
    }

    @Test
    fun `to entity mirrors core reader preferences into legacy columns and json`() {
        val entity = ReaderSessionSnapshot(
            itemId = "comic-7",
            pageCount = 22,
            preferences = mapOf(
                ComicsReadingDirectionKey to ComicReadingDirection.RTL.name,
                ComicsPageFitModeKey to ComicPageFitMode.FitHeight.name,
                ComicsSpreadModeKey to ComicSpreadMode.Single.name,
                "customTheme" to "night",
            ),
        ).let(ReaderSessionEntityCodecTestAccess::toEntity)

        val preferencesJson = JSONObject(entity.preferencesJson)

        assertEquals("comic-7", entity.itemId)
        assertNull(entity.page)
        assertEquals(22, entity.pageCount)
        assertEquals(ComicReadingDirection.RTL.name, entity.readingDirection)
        assertEquals(ComicPageFitMode.FitHeight.name, entity.pageFitMode)
        assertEquals(ComicSpreadMode.Single.name, entity.spreadMode)
        assertEquals(ComicReadingDirection.RTL.name, preferencesJson.getString(ComicsReadingDirectionKey))
        assertEquals(ComicPageFitMode.FitHeight.name, preferencesJson.getString(ComicsPageFitModeKey))
        assertEquals(ComicSpreadMode.Single.name, preferencesJson.getString(ComicsSpreadModeKey))
        assertEquals("night", preferencesJson.getString("customTheme"))
    }
}
