package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.core.model.buildStumpSeriesContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryItemCardsTest {
    @Test
    fun `series browse title prefers issue number and issue title`() {
        val item = ReadableItem(
            id = "kavita:item:chapter-1",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "chapter-1",
            title = "Conan the Barbarian (2024)",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Conan the Barbarian (2024)",
                issueTitle = "Bound in Black Stone",
                issueNumber = "1",
            ),
        )

        assertEquals("Issue 1 - Bound in Black Stone", itemSeriesBrowsePrimaryText(item))
    }

    @Test
    fun `series browse title falls back to issue number without repeating series`() {
        val item = ReadableItem(
            id = "kavita:item:chapter-1",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "chapter-1",
            title = "Conan the Barbarian (2024)",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Conan the Barbarian (2024)",
                issueTitle = "Conan the Barbarian (2024)",
                issueNumber = "1",
            ),
        )

        assertEquals("Issue 1", itemSeriesBrowsePrimaryText(item))
    }

    @Test
    fun `a Kavita volume reads Volume 1, not Issue v1 - Chapter v1`() {
        val item = ReadableItem(
            id = "kavita:item:chapter-9",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:k-manga",
            remoteId = "chapter-9",
            title = "Chapter v1",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(series = "DEAD Tube", issueTitle = "Chapter v1", issueNumber = "v1"),
        )

        assertEquals("Volume 1", itemSeriesBrowsePrimaryText(item))
        assertEquals("DEAD Tube - Volume 1", itemMixedIssueBrowsePrimaryText(item))
        assertEquals("Vol. 1", buildComicVolumeIssueLabel(item.comicMetadata))
    }

    @Test
    fun `mixed issue title includes series issue number and issue title`() {
        val item = ReadableItem(
            id = "kavita:item:chapter-2",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "chapter-2",
            title = "Dark Knights of Steel 002",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Dark Knights of Steel",
                issueTitle = "Distant Thunder",
                issueNumber = "2",
            ),
        )

        assertEquals("Dark Knights of Steel - Issue 2 - Distant Thunder", itemMixedIssueBrowsePrimaryText(item))
    }

    @Test
    fun `mixed issue title avoids repeating series from issue title`() {
        val item = ReadableItem(
            id = "kavita:item:chapter-3",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "chapter-3",
            title = "Dark Knights of Steel",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Dark Knights of Steel",
                issueTitle = "Dark Knights of Steel - Issue 3 - The Gathering Storm",
                issueNumber = "3",
            ),
        )

        assertEquals("Dark Knights of Steel - Issue 3 - The Gathering Storm", itemMixedIssueBrowsePrimaryText(item))
    }

    @Test
    fun `remote cover prefetch includes every remote source`() {
        assertTrue(shouldPrefetchRemoteCover(kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")))
        assertTrue(shouldPrefetchRemoteCover(stumpIssue(id = "issue-1", title = "Akira 001", seriesKey = "series-7")))
        assertTrue(
            shouldPrefetchRemoteCover(
                ReadableItem(
                    id = "grimmory:item:book-1",
                    sourceType = SourceType.GRIMMORY,
                    libraryId = "grimmory:library:books",
                    remoteId = "book-1",
                    title = "Grimmory Book",
                    format = FormatType.EPUB,
                    coverUrl = "https://grimmory.example/api/v1/media/book/book-1/thumbnail",
                ),
            ),
        )
        assertFalse(
            shouldPrefetchRemoteCover(
                ReadableItem(
                    id = "local:item:book-1",
                    sourceType = SourceType.LOCAL,
                    libraryId = "local-imports",
                    title = "Local Book",
                    format = FormatType.EPUB,
                ),
            ),
        )
    }

    @Test
    fun `kavita series container exposes download action`() {
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(
                kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7"),
            ),
        )
        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem),
            allItems = listOf(kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")),
            downloadedItemIds = emptySet(),
            downloadingItemIds = emptySet(),
        )
        val actions = libraryBookFileActions(
            item = seriesItem,
            actionContext = LibraryItemActionContext(downloadableItemIds = actionAvailability.downloadableItemIds),
        )

        assertEquals(listOf(LibraryBookFileAction.Download), actions)
    }

    @Test
    fun `stump series container exposes delete downloaded action when stored`() {
        val seriesItem = buildStumpSeriesContainer(
            seriesKey = "stump:library:stump-comics::series-7",
            issues = listOf(
                stumpIssue(id = "issue-1", title = "Akira 001", seriesKey = "series-7"),
            ),
        )
        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem),
            allItems = listOf(stumpIssue(id = "issue-1", title = "Akira 001", seriesKey = "series-7")),
            downloadedItemIds = setOf("stump:item:issue-1"),
            downloadingItemIds = emptySet(),
        )
        val actions = libraryBookFileActions(
            item = seriesItem,
            actionContext = LibraryItemActionContext(
                deletableDownloadedItemIds = actionAvailability.deletableDownloadedItemIds,
            ),
        )

        assertEquals(listOf(LibraryBookFileAction.RemoveDownload), actions)
    }

    @Test
    fun `stump series container keeps series cover identity`() {
        val seriesCoverUrl = "https://stump.example/api/v2/series/series-7/thumbnail?updated=2"
        val seriesItem = buildStumpSeriesContainer(
            seriesKey = "stump:library:stump-comics::series-7",
            issues = listOf(
                stumpIssue(
                    id = "issue-1",
                    title = "Akira 001",
                    seriesKey = "series-7",
                    seriesCoverUrl = seriesCoverUrl,
                ),
            ),
        )

        assertEquals(seriesCoverUrl, seriesItem.coverUrl)
    }

    @Test
    fun `series container exposes issue count`() {
        val firstIssue = kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")
        val secondIssue = kavitaIssue(id = "chapter-2", title = "Akira 002", seriesKey = "series-7")

        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        assertEquals(2, seriesItem.issueCount)
    }

    @Test
    fun `partially downloaded series keeps both download and delete actions`() {
        val firstIssue = kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")
        val secondIssue = kavitaIssue(id = "chapter-2", title = "Akira 002", seriesKey = "series-7")
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem),
            allItems = listOf(firstIssue, secondIssue),
            downloadedItemIds = setOf(firstIssue.id),
            downloadingItemIds = emptySet(),
        )

        assertTrue(seriesItem.id in actionAvailability.downloadableItemIds)
        assertTrue(seriesItem.id in actionAvailability.deletableDownloadedItemIds)
        assertEquals(
            listOf(LibraryBookFileAction.Download, LibraryBookFileAction.RemoveDownload),
            libraryBookFileActions(
                item = seriesItem,
                actionContext = LibraryItemActionContext(
                    downloadableItemIds = actionAvailability.downloadableItemIds,
                    deletableDownloadedItemIds = actionAvailability.deletableDownloadedItemIds,
                ),
            ),
        )
    }

    @Test
    fun `series container download marks child issues as actively downloading`() {
        val firstIssue = kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")
        val secondIssue = kavitaIssue(id = "chapter-2", title = "Akira 002", seriesKey = "series-7")
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem, firstIssue, secondIssue),
            allItems = listOf(firstIssue, secondIssue),
            downloadedItemIds = emptySet(),
            downloadingItemIds = setOf(seriesItem.id),
        )

        assertTrue(seriesItem.id in actionAvailability.activeDownloadingItemIds)
        assertTrue(firstIssue.id in actionAvailability.activeDownloadingItemIds)
        assertTrue(secondIssue.id in actionAvailability.activeDownloadingItemIds)
        assertTrue(firstIssue.id !in actionAvailability.downloadableItemIds)
        assertTrue(secondIssue.id !in actionAvailability.downloadableItemIds)
    }

    @Test
    fun `series containers do not expose read status toggle`() {
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")),
        )

        assertNull(libraryBookReadAction(item = seriesItem, progress = null))
    }
}

private fun kavitaIssue(
    id: String,
    title: String,
    seriesKey: String,
): ReadableItem = ReadableItem(
    id = "kavita:item:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:k-comics",
    remoteId = id,
    remoteFileType = seriesKey,
    title = title,
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Akira",
        issueTitle = title,
    ),
)

private fun stumpIssue(
    id: String,
    title: String,
    seriesKey: String,
    seriesCoverUrl: String? = null,
): ReadableItem = ReadableItem(
    id = "stump:item:$id",
    sourceType = SourceType.STUMP,
    libraryId = "stump:library:stump-comics",
    remoteId = id,
    remoteFileType = seriesKey,
    title = title,
    format = FormatType.CBZ,
    seriesCoverUrl = seriesCoverUrl,
    comicMetadata = ComicMetadata(
        series = "Akira",
        issueTitle = title,
    ),
)
