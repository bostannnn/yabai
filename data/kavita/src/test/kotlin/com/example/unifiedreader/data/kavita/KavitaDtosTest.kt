package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaDtosTest {
    @Test
    fun `series detail maps archive chapters into Kavita comic items`() {
        val series = KavitaSeriesDto(
            id = 12,
            name = "Saga",
            libraryId = 5,
            format = 1,
            created = "2026-04-09T10:15:30Z",
            lastChapterAddedUtc = "2026-04-08T10:15:30Z",
            publishers = listOf(KavitaPersonDto(name = "Image")),
            genres = listOf(KavitaGenreTagDto(title = "Sci-Fi")),
            tags = listOf(KavitaTagDto(title = "Space Opera")),
            language = "English",
        ).toEntry(
            baseUrl = "https://kavita.example",
            apiKey = "secret",
        ) ?: error("Expected series entry.")

        val entries = KavitaSeriesDetailDto(
            volumes = listOf(
                KavitaVolumeDto(
                    id = 7,
                    name = "1",
                    chapters = listOf(
                        KavitaChapterDto(
                            id = 21,
                            range = "1",
                            title = "Opening Shot",
                            pages = 28,
                            pagesRead = 9,
                            lastReadingProgressUtc = "2026-04-07T06:00:00Z",
                            volumeId = 7,
                        ),
                    ),
                ),
            ),
        ).toCatalogEntries(
            series = series,
        )

        val entry = entries.single()
        assertEquals(SourceType.KAVITA.remoteItemId("21"), entry.item.id)
        assertEquals(SourceType.KAVITA.remoteLibraryId("5"), entry.item.libraryId)
        assertEquals("21", entry.item.remoteId)
        assertEquals("7", entry.item.remoteFileId)
        assertEquals("12", entry.item.remoteFileType)
        assertEquals(FormatType.CBZ, entry.item.format)
        assertEquals("Saga", entry.item.comicMetadata?.series)
        assertEquals("Opening Shot", entry.item.comicMetadata?.issueTitle)
        assertEquals("1", entry.item.comicMetadata?.volume)
        assertEquals("1", entry.item.comicMetadata?.issueNumber)
        assertEquals(listOf("Image"), entry.item.comicMetadata?.publishers)
        assertEquals(listOf("Sci-Fi"), entry.item.comicMetadata?.genres)
        assertEquals(listOf("Space Opera"), entry.item.comicMetadata?.tags)
        assertEquals(listOf("English"), entry.item.comicMetadata?.languages)
        assertEquals(1775729730000L, entry.item.addedAtEpochMillis)
        assertEquals(9, entry.progress?.page)
        assertFalse(entry.progress?.isFinished ?: true)
        assertFalse(entry.isExplicitUnreadReset)
        assertEquals(
            "https://kavita.example/api/Image/chapter-cover?chapterId=21&apiKey=secret",
            entry.item.coverUrl,
        )
    }

    @Test
    fun `series detail merges unique volume chapters and ignores duplicates`() {
        val series = KavitaSeriesDto(
            id = 12,
            name = "Saga",
            libraryId = 5,
            format = 1,
        ).toEntry(
            baseUrl = "https://kavita.example",
            apiKey = "secret",
        ) ?: error("Expected series entry.")

        val entries = KavitaSeriesDetailDto(
            chapters = listOf(
                KavitaChapterDto(
                    id = 21,
                    range = "1",
                    title = "Opening Shot",
                    pages = 28,
                    volumeId = 7,
                ),
            ),
            specials = listOf(
                KavitaChapterDto(
                    id = 21,
                    range = "1",
                    title = "Opening Shot",
                    pages = 28,
                    volumeId = 7,
                ),
            ),
            volumes = listOf(
                KavitaVolumeDto(
                    id = 7,
                    name = "1",
                    chapters = listOf(
                        KavitaChapterDto(
                            id = 21,
                            range = "1",
                            title = "Opening Shot",
                            pages = 28,
                            volumeId = 7,
                        ),
                        KavitaChapterDto(
                            id = 22,
                            range = "2",
                            title = "Second Shot",
                            pages = 30,
                            volumeId = 7,
                        ),
                    ),
                ),
            ),
        ).toCatalogEntries(
            series = series,
        )

        assertEquals(2, entries.size)
        assertEquals(listOf("21", "22"), entries.map { it.item.remoteId })
        assertEquals(listOf("1", "1"), entries.map { it.item.comicMetadata?.volume })
    }

    @Test
    fun `series detail dedupes repeated top level chapter ids`() {
        val series = KavitaSeriesDto(
            id = 12,
            name = "Saga",
            libraryId = 5,
            format = 1,
        ).toEntry(
            baseUrl = "https://kavita.example",
            apiKey = "secret",
        ) ?: error("Expected series entry.")

        val entries = KavitaSeriesDetailDto(
            chapters = listOf(
                KavitaChapterDto(
                    id = 21,
                    range = "1",
                    title = "Opening Shot",
                    pages = 28,
                ),
            ),
            storylineChapters = listOf(
                KavitaChapterDto(
                    id = 21,
                    range = "1",
                    title = "Opening Shot",
                    pages = 28,
                ),
            ),
        ).toCatalogEntries(
            series = series,
        )

        assertEquals(1, entries.size)
        assertEquals("21", entries.single().item.remoteId)
    }

    @Test
    fun `zero page progress is treated as unread`() {
        val progress = KavitaProgressDto(
            chapterId = 21,
            pageNum = 0,
            lastModifiedUtc = "2026-04-07T06:00:00Z",
        ).toReadingProgress(
            itemId = SourceType.KAVITA.remoteItemId("21"),
            pageCount = 30,
        )

        assertNull(progress)
    }

    @Test
    fun `finished chapter progress clamps to the last local page`() {
        val progress = KavitaProgressDto(
            chapterId = 21,
            pageNum = 30,
            lastModifiedUtc = "2026-04-07T06:00:00Z",
        ).toReadingProgress(
            itemId = SourceType.KAVITA.remoteItemId("21"),
            pageCount = 30,
        )

        assertNotNull(progress)
        assertEquals(29, progress?.page)
        assertTrue(progress?.isFinished ?: false)
        assertEquals(1.0, progress?.percent)
    }

    @Test
    fun `chapter info converts finished local progress into Kavita terminal page number`() {
        val item = com.example.unifiedreader.core.model.ReadableItem(
            id = SourceType.KAVITA.remoteItemId("21"),
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("5"),
            remoteId = "21",
            remoteFileId = "7",
            remoteFileType = "12",
            title = "Opening Shot",
            format = FormatType.CBZ,
            pageCount = 28,
        )

        val update = KavitaChapterInfoDto(
            chapterNumber = "1",
            volumeId = 7,
            seriesId = 12,
            libraryId = 5,
            pages = 28,
        ).toProgressUpdate(
            item = item,
            page = 27,
            isFinished = true,
            updatedAtEpochMillis = 1_775_046_600_000L,
        )

        assertNotNull(update)
        assertEquals(28, update?.pageNum)
        assertEquals(7, update?.volumeId)
        assertEquals(12, update?.seriesId)
        assertEquals(5, update?.libraryId)
    }

    @Test
    fun `invalid generated chapter titles fall back to formatted volume name`() {
        val title = preferredKavitaIssueTitle(
            explicitTitle = "Chapter -100000",
            volumeName = "2",
        )

        assertEquals("Volume 2", title)
    }

    @Test
    fun `range and number are ignored as display title inputs`() {
        val title = preferredKavitaIssueTitle(
            explicitTitle = null,
            volumeName = "2",
        )

        assertEquals("Volume 2", title)
    }

    @Test
    fun `negative or huge chapter tokens are rejected`() {
        assertFalse(isValidKavitaChapterToken("-100000"))
        assertFalse(isValidKavitaChapterToken("-1"))
        assertFalse(isValidKavitaChapterToken("100000"))
        assertTrue(isValidKavitaChapterToken("1099"))
        assertTrue(isValidKavitaChapterToken("Special 1"))
    }

    @Test
    fun `chapter info metadata title is used when series detail title is missing`() {
        val title = KavitaChapterInfoDto(
            chapterTitle = "Romance Dawn",
            title = "One Piece v02.cbz",
        ).preferredKavitaIssueTitleFallback(volumeName = null)

        assertEquals("Romance Dawn", title)
    }

    @Test
    fun `chapter info filename fallback strips archive extension`() {
        val title = KavitaChapterInfoDto(
            pageDimensions = listOf(
                KavitaFileDimensionDto(fileName = "One Piece Volume 02.cbz"),
            ),
        ).preferredKavitaIssueTitleFallback(volumeName = null)

        assertEquals("One Piece Volume 02", title)
    }

    @Test
    fun `page image names do not replace volume fallback`() {
        val title = KavitaChapterInfoDto(
            pageDimensions = listOf(
                KavitaFileDimensionDto(fileName = "001.jpg"),
            ),
        ).preferredKavitaIssueTitleFallback(volumeName = "2")

        assertEquals("Volume 2", title)
    }

    @Test
    fun `series detail resolves missing titles from chapter info`() = runBlocking {
        val overrides = KavitaSeriesDetailDto(
            chapters = listOf(
                KavitaChapterDto(
                    id = 21,
                    pages = 28,
                ),
            ),
        ).resolveSupplementalIssueTitles { chapterId ->
            assertEquals("21", chapterId)
            KavitaChapterInfoDto(
                title = "One Piece Volume 02.cbz",
            )
        }

        assertEquals(mapOf("21" to "One Piece Volume 02"), overrides)
    }
}
