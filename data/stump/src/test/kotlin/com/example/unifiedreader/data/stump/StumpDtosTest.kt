package com.example.unifiedreader.data.stump

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class StumpDtosTest {
    @Test
    fun `series detail maps metadata facets onto media entries`() {
        val seriesCoverUrl = "https://stump.example/api/v2/series/series-1/thumbnail"
        val series = StumpSeriesEntry(
            id = "series-1",
            libraryId = "library-1",
            title = "Saga",
            coverUrl = seriesCoverUrl,
        )

        val entry = StumpSeriesDetailDto(
            metadata = StumpSeriesMetadataDto(
                publisher = "Image",
                genres = listOf("Sci-Fi"),
            ),
            tags = listOf(StumpTagDto(name = "Space Opera")),
            media = listOf(
                StumpMediaNodeDto(
                    id = "media-1",
                    extension = "cbz",
                    metadata = StumpMediaMetadataDto(
                        title = "Issue 1",
                        publisher = "Image",
                        genres = listOf("Sci-Fi"),
                        language = "English",
                    ),
                    tags = listOf(StumpTagDto(name = "Space Opera")),
                ),
            ),
        ).toCatalogEntries(
            baseUrl = "https://stump.example",
            series = series,
        ).single()

        assertEquals(listOf("Image"), entry.item.comicMetadata?.publishers)
        assertEquals(listOf("Sci-Fi"), entry.item.comicMetadata?.genres)
        assertEquals(listOf("Space Opera"), entry.item.comicMetadata?.tags)
        assertEquals(listOf("English"), entry.item.comicMetadata?.languages)
        assertEquals(seriesCoverUrl, entry.item.seriesCoverUrl)
    }

    @Test
    fun `active read progress keeps fractional stump percentages`() {
        val progress = StumpMediaProgressDto(
            readProgress = StumpActiveReadingSessionDto(
                page = 3,
                percentageCompleted = JsonPrimitive(0.75),
                updatedAt = "2026-04-01T12:00:00Z",
            ),
        ).toReadingProgress(
            itemId = "stump:item-1",
            pageCount = 10,
        )

        requireNotNull(progress)
        assertEquals(2, progress.page)
        assertEquals(0.75, progress.percent ?: 0.0, 0.0001)
        assertFalse(progress.isFinished)
    }

    @Test
    fun `active read progress accepts whole-number stump percentages`() {
        val progress = StumpMediaProgressDto(
            readProgress = StumpActiveReadingSessionDto(
                page = 3,
                percentageCompleted = JsonPrimitive(75),
                updatedAt = "2026-04-01T12:00:00Z",
            ),
        ).toReadingProgress(
            itemId = "stump:item-1",
            pageCount = 10,
        )

        requireNotNull(progress)
        assertEquals(0.75, progress.percent ?: 0.0, 0.0001)
    }

    @Test
    fun `active read progress treats ambiguous whole-number one as one percent before final page`() {
        val progress = StumpMediaProgressDto(
            readProgress = StumpActiveReadingSessionDto(
                page = 3,
                percentageCompleted = JsonPrimitive(1),
                updatedAt = "2026-04-01T12:00:00Z",
            ),
        ).toReadingProgress(
            itemId = "stump:item-1",
            pageCount = 10,
        )

        requireNotNull(progress)
        assertEquals(0.01, progress.percent ?: 0.0, 0.0001)
        assertFalse(progress.isFinished)
    }

    @Test
    fun `finished read history maps to finished progress`() {
        val progress = StumpMediaProgressDto(
            readHistory = listOf(
                StumpFinishedReadingSessionDto(completedAt = "2026-04-02T12:00:00Z"),
            ),
        ).toReadingProgress(
            itemId = "stump:item-1",
            pageCount = 10,
        )

        requireNotNull(progress)
        assertEquals(9, progress.page)
        assertEquals(1.0, progress.percent ?: 0.0, 0.0001)
        assertTrue(progress.isFinished)
        assertEquals(1775131200000L, progress.updatedAtEpochMillis)
    }

    @Test
    fun `newer finished read history wins over stale active progress`() {
        val progress = StumpMediaProgressDto(
            readProgress = StumpActiveReadingSessionDto(
                page = 3,
                percentageCompleted = JsonPrimitive(0.3),
                updatedAt = "2026-04-01T12:00:00Z",
            ),
            readHistory = listOf(
                StumpFinishedReadingSessionDto(completedAt = "2026-04-02T12:00:00Z"),
            ),
        ).toReadingProgress(
            itemId = "stump:item-1",
            pageCount = 10,
        )

        requireNotNull(progress)
        assertEquals(9, progress.page)
        assertEquals(1.0, progress.percent ?: 0.0, 0.0001)
        assertTrue(progress.isFinished)
    }

    @Test
    fun `newer active progress wins over old finished read history`() {
        val progress = StumpMediaProgressDto(
            readProgress = StumpActiveReadingSessionDto(
                page = 3,
                percentageCompleted = JsonPrimitive(0.3),
                updatedAt = "2026-04-02T12:00:00Z",
            ),
            readHistory = listOf(
                StumpFinishedReadingSessionDto(completedAt = "2026-04-01T12:00:00Z"),
            ),
        ).toReadingProgress(
            itemId = "stump:item-1",
            pageCount = 10,
        )

        requireNotNull(progress)
        assertEquals(2, progress.page)
        assertEquals(0.3, progress.percent ?: 0.0, 0.0001)
        assertFalse(progress.isFinished)
    }

    @Test
    fun `missing stump progress marks catalog entry as explicit unread reset`() {
        val series = StumpSeriesEntry(
            id = "series-1",
            libraryId = "library-1",
            title = "Saga",
            coverUrl = null,
        )

        val entry = StumpSeriesDetailDto(
            media = listOf(
                StumpMediaNodeDto(
                    id = "media-1",
                    extension = "cbz",
                    metadata = StumpMediaMetadataDto(
                        title = "Issue 1",
                    ),
                ),
            ),
        ).toCatalogEntries(
            baseUrl = "https://stump.example",
            series = series,
        ).single()

        assertNull(entry.progress)
        assertTrue(entry.isExplicitUnreadReset)
    }
}
