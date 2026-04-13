package com.example.unifiedreader

import android.os.Looper
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.DownloadEntryStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppViewModelRemoteDownloadsTest {
    @Test
    fun `download grimmory item marks it local and enables location filter`() {
        val remoteBook = sampleItem(
            id = "grimmory-epub",
            title = "Remote Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("grimmory-books"),
            remoteId = "grimmory-epub",
        )
        val grimmoryContentRepository = FakeGrimmoryContentRepository()
        val viewModel = createViewModel(
            sampleItems = listOf(remoteBook),
            grimmoryContentRepository = grimmoryContentRepository,
        )
        viewModel.onSourceSelected(SourceType.GRIMMORY)

        viewModel.onDownloadRemoteItem(remoteBook)
        waitForUiState {
            remoteBook.id in viewModel.uiState.downloadedRemoteItemIds
        }

        assertEquals(setOf(remoteBook.id), viewModel.uiState.downloadedRemoteItemIds)
        assertEquals(
            listOf(LibraryFileLocation.LOCAL),
            viewModel.uiState.selectedLibraryFilterFacets.fileLocations,
        )
        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(fileLocations = setOf(LibraryFileLocation.LOCAL)),
        )
        assertEquals(listOf(remoteBook), viewModel.uiState.visibleItems)
    }

    @Test
    fun `download grimmory failure is recorded in downloads snapshot`() {
        val remoteBook = sampleItem(
            id = "grimmory-epub",
            title = "Remote Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("grimmory-books"),
            remoteId = "grimmory-epub",
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteBook),
            grimmoryContentRepository = FakeGrimmoryContentRepository(
                downloadFailure = IllegalStateException("Disk full"),
            ),
        )
        viewModel.onSourceSelected(SourceType.GRIMMORY)

        viewModel.onDownloadRemoteItem(remoteBook)
        val expectedMessage = "Disk full"
        waitForUiState {
            viewModel.downloadsSnapshot.failedEntries.singleOrNull()?.errorMessage == expectedMessage
        }

        assertEquals(DownloadEntryStatus.Failed, viewModel.downloadsSnapshot.failedEntries.single().status)
        assertEquals(expectedMessage, viewModel.downloadsSnapshot.failedEntries.single().errorMessage)
    }

    @Test
    fun `remove failed download clears it from downloads snapshot`() {
        val remoteBook = sampleItem(
            id = "grimmory-epub",
            title = "Remote Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("grimmory-books"),
            remoteId = "grimmory-epub",
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteBook),
            grimmoryContentRepository = FakeGrimmoryContentRepository(
                downloadFailure = IllegalStateException("Disk full"),
            ),
        )
        viewModel.onSourceSelected(SourceType.GRIMMORY)

        viewModel.onDownloadRemoteItem(remoteBook)
        waitForUiState {
            viewModel.downloadsSnapshot.failedEntries.singleOrNull()?.errorMessage == "Disk full"
        }

        val failedEntryId = viewModel.downloadsSnapshot.failedEntries.single().id
        viewModel.removeDownload(failedEntryId)
        waitForUiState {
            viewModel.downloadsSnapshot.entries.isEmpty()
        }

        assertTrue(viewModel.downloadsSnapshot.failedEntries.isEmpty())
        assertTrue(viewModel.downloadsSnapshot.entries.isEmpty())
    }

    @Test
    fun `download grimmory success remains successful when progress sync fails`() {
        val remoteBook = sampleItem(
            id = "grimmory-epub",
            title = "Remote Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("grimmory-books"),
            remoteId = "grimmory-epub",
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteBook),
            grimmoryContentRepository = FakeGrimmoryContentRepository(),
            sourceProgressSyncs = listOf(FailingSourceProgressSync(sourceType = SourceType.GRIMMORY)),
        )
        viewModel.onSourceSelected(SourceType.GRIMMORY)

        viewModel.onDownloadRemoteItem(remoteBook)
        waitForUiState {
            remoteBook.id in viewModel.uiState.downloadedRemoteItemIds
        }

        assertEquals(setOf(remoteBook.id), viewModel.uiState.downloadedRemoteItemIds)
    }

    @Test
    fun `download kavita item marks it local and enables location filter`() {
        val remoteComic = sampleItem(
            id = "kavita-cbz",
            title = "Remote Comic",
            format = FormatType.CBZ,
        ).copy(
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("kavita-comics"),
            remoteId = "chapter-1",
        )
        val kavitaDownloadDataSource = FakeRemoteDownloadDataSource(SourceType.KAVITA)
        val viewModel = createViewModel(
            sampleItems = listOf(remoteComic),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                kavitaDownloadDataSource,
            ),
        )
        viewModel.onSourceSelected(SourceType.KAVITA)

        viewModel.onDownloadRemoteItem(remoteComic)
        waitForUiState {
            remoteComic.id in viewModel.uiState.downloadedRemoteItemIds
        }

        assertTrue(remoteComic.id in viewModel.uiState.downloadedRemoteItemIds)
        assertTrue(remoteComic.id in viewModel.uiState.locallyAvailableRemoteItemIds)
        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(fileLocations = setOf(LibraryFileLocation.LOCAL)),
        )
        assertEquals(1, viewModel.uiState.visibleItems.size)
        assertTrue(viewModel.uiState.visibleItems.single().isKavitaSeriesContainer())
    }

    @Test
    fun `download kavita series container downloads all child issues and marks series local`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val kavitaDownloadDataSource = FakeRemoteDownloadDataSource(SourceType.KAVITA)
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                kavitaDownloadDataSource,
            ),
        )
        val seriesItem = viewModel.uiState.visibleItems.single()

        viewModel.onDownloadRemoteItem(seriesItem)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            setOf(firstIssue.id, secondIssue.id),
            viewModel.uiState.downloadedRemoteItemIds,
        )
        assertEquals(
            setOf(firstIssue.id, secondIssue.id, seriesItem.id),
            viewModel.uiState.locallyAvailableRemoteItemIds,
        )
        assertTrue(seriesItem.id !in viewModel.uiState.remoteDownloadActionAvailability.downloadableItemIds)
        assertTrue(seriesItem.id in viewModel.uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds)

        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(fileLocations = setOf(LibraryFileLocation.LOCAL)),
        )

        assertEquals(1, viewModel.uiState.visibleItems.size)
        assertEquals(seriesItem.id, viewModel.uiState.visibleItems.single().id)
    }

    @Test
    fun `partially downloaded kavita series stays local but still exposes series download action`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                FakeRemoteDownloadDataSource(
                    sourceType = SourceType.KAVITA,
                    storedItemIds = mutableSetOf(firstIssue.id),
                ),
            ),
        )
        val seriesItem = viewModel.uiState.visibleItems.single()

        assertEquals(setOf(firstIssue.id), viewModel.uiState.downloadedRemoteItemIds)
        assertEquals(
            setOf(firstIssue.id, seriesItem.id),
            viewModel.uiState.locallyAvailableRemoteItemIds,
        )
        assertTrue(seriesItem.id in viewModel.uiState.remoteDownloadActionAvailability.downloadableItemIds)
        assertTrue(seriesItem.id in viewModel.uiState.remoteDownloadActionAvailability.deletableDownloadedItemIds)

        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(fileLocations = setOf(LibraryFileLocation.LOCAL)),
        )

        assertEquals(listOf(seriesItem), viewModel.uiState.visibleItems)
    }

    @Test
    fun `partially downloaded kavita series reports already local and failed downloads separately`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                FakeRemoteDownloadDataSource(
                    sourceType = SourceType.KAVITA,
                    storedItemIds = mutableSetOf(firstIssue.id),
                    failingItemIds = setOf(secondIssue.id),
                ),
            ),
        )
        waitForUiState {
            viewModel.uiState.visibleItems.size == 1
        }
        val seriesItem = viewModel.uiState.visibleItems.single()

        viewModel.onDownloadRemoteItem(seriesItem)
        shadowOf(Looper.getMainLooper()).idle()
        waitForUiState {
            viewModel.downloadsSnapshot.failedEntries.singleOrNull()?.status == DownloadEntryStatus.Failed
        }

        assertEquals(setOf(firstIssue.id), viewModel.uiState.downloadedRemoteItemIds)
        assertEquals(DownloadEntryStatus.Failed, viewModel.downloadsSnapshot.failedEntries.single().status)
        assertEquals(
            "Unable to download Akira: Issue 2.",
            viewModel.downloadsSnapshot.failedEntries.single().errorMessage,
        )
    }

    @Test
    fun `remove failed partial series download clears downloaded local state`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                FakeRemoteDownloadDataSource(
                    sourceType = SourceType.KAVITA,
                    failingItemIds = setOf(secondIssue.id),
                ),
            ),
        )
        val seriesItem = viewModel.uiState.visibleItems.single()

        viewModel.onDownloadRemoteItem(seriesItem)
        waitForUiState {
            viewModel.downloadsSnapshot.failedEntries.singleOrNull()?.errorMessage == "Unable to download Akira: Issue 2."
        }

        assertEquals(setOf(firstIssue.id), viewModel.uiState.downloadedRemoteItemIds)
        assertEquals(
            setOf(firstIssue.id, seriesItem.id),
            viewModel.uiState.locallyAvailableRemoteItemIds,
        )

        val failedEntryId = viewModel.downloadsSnapshot.failedEntries.single().id
        viewModel.removeDownload(failedEntryId)
        waitForUiState {
            viewModel.downloadsSnapshot.entries.isEmpty() &&
                viewModel.uiState.downloadedRemoteItemIds.isEmpty() &&
                viewModel.uiState.locallyAvailableRemoteItemIds.isEmpty()
        }

        assertTrue(viewModel.downloadsSnapshot.entries.isEmpty())
        assertTrue(viewModel.uiState.downloadedRemoteItemIds.isEmpty())
        assertTrue(viewModel.uiState.locallyAvailableRemoteItemIds.isEmpty())
    }

    @Test
    fun `series download tracks root and child ids and blocks duplicate child request`() = runBlocking {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val dataSource = BlockingRemoteDownloadDataSource(sourceType = SourceType.KAVITA)
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                dataSource,
            ),
        )
        val seriesItem = viewModel.uiState.visibleItems.single()

        viewModel.onDownloadRemoteItem(seriesItem)
        shadowOf(Looper.getMainLooper()).idle()
        dataSource.awaitFirstDownloadStarted()

        assertEquals(
            setOf(seriesItem.id, firstIssue.id, secondIssue.id),
            viewModel.uiState.downloadingRemoteItemIds,
        )

        viewModel.onDownloadRemoteItem(firstIssue)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(firstIssue.id), dataSource.startedItemIds())

        dataSource.cancelFirstDownload()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `cancelled download does not surface an error message`() = runBlocking {
        val remoteComic = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
        )
        val dataSource = BlockingRemoteDownloadDataSource(sourceType = SourceType.KAVITA)
        val viewModel = createViewModel(
            sampleItems = listOf(remoteComic),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                dataSource,
            ),
        )

        viewModel.onDownloadRemoteItem(remoteComic)
        shadowOf(Looper.getMainLooper()).idle()
        dataSource.awaitFirstDownloadStarted()

        dataSource.cancelFirstDownload()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(viewModel.uiState.downloadingRemoteItemIds.isEmpty())
        assertTrue(viewModel.downloadsSnapshot.failedEntries.isEmpty())
    }

    @Test
    fun `download stump item marks it local and delete removes downloaded state`() {
        val remoteComic = sampleItem(
            id = "stump-cbr",
            title = "Remote Comic",
            format = FormatType.CBR,
        ).copy(
            sourceType = SourceType.STUMP,
            libraryId = SourceType.STUMP.remoteLibraryId("stump-comics"),
            remoteId = "media-1",
        )
        val stumpDownloadDataSource = FakeRemoteDownloadDataSource(
            sourceType = SourceType.STUMP,
            storedItemIds = mutableSetOf(remoteComic.id),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteComic),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                stumpDownloadDataSource,
            ),
        )
        viewModel.onSourceSelected(SourceType.STUMP)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(remoteComic.id in viewModel.uiState.downloadedRemoteItemIds)

        viewModel.onDeleteRemoteLocalFile(remoteComic)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(viewModel.uiState.downloadedRemoteItemIds.isEmpty())
    }

    @Test
    fun `delete stump series container removes all child issue files`() {
        val firstIssue = sampleItem(
            id = "stump-issue-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            sourceType = SourceType.STUMP,
            libraryId = SourceType.STUMP.remoteLibraryId("stump-comics"),
            remoteId = "stump-issue-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "stump-issue-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            sourceType = SourceType.STUMP,
            libraryId = SourceType.STUMP.remoteLibraryId("stump-comics"),
            remoteId = "stump-issue-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val stumpDownloadDataSource = FakeRemoteDownloadDataSource(
            sourceType = SourceType.STUMP,
            storedItemIds = mutableSetOf(firstIssue.id, secondIssue.id),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            remoteDownloadDataSources = listOf(
                FakeGrimmoryContentRepository(),
                stumpDownloadDataSource,
            ),
        )
        viewModel.onSourceSelected(SourceType.STUMP)
        shadowOf(Looper.getMainLooper()).idle()
        val seriesItem = viewModel.uiState.visibleItems.single()

        assertTrue(seriesItem.isStumpSeriesContainer())
        assertEquals(
            setOf(firstIssue.id, secondIssue.id),
            viewModel.uiState.downloadedRemoteItemIds,
        )
        assertEquals(
            setOf(firstIssue.id, secondIssue.id, seriesItem.id),
            viewModel.uiState.locallyAvailableRemoteItemIds,
        )

        viewModel.onDeleteRemoteLocalFile(seriesItem)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(viewModel.uiState.downloadedRemoteItemIds.isEmpty())
        assertTrue(viewModel.uiState.locallyAvailableRemoteItemIds.isEmpty())
    }

    @Test
    fun `delete grimmory local file removes downloaded state`() {
        val remoteBook = sampleItem(
            id = "grimmory-epub",
            title = "Remote Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("grimmory-books"),
            remoteId = "grimmory-epub",
        )
        val grimmoryContentRepository = FakeGrimmoryContentRepository(
            storedItemIds = mutableSetOf(remoteBook.id),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteBook),
            grimmoryContentRepository = grimmoryContentRepository,
        )
        viewModel.onSourceSelected(SourceType.GRIMMORY)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(setOf(remoteBook.id), viewModel.uiState.downloadedRemoteItemIds)

        viewModel.onDeleteRemoteLocalFile(remoteBook)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(viewModel.uiState.downloadedRemoteItemIds.isEmpty())
        assertEquals(
            listOf(LibraryFileLocation.SERVER),
            viewModel.uiState.selectedLibraryFilterFacets.fileLocations,
        )
    }

    private fun waitForUiState(
        condition: () -> Boolean,
    ) {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10L)
            if (condition()) {
                return
            }
        }
        fail("Timed out waiting for AppViewModel state to settle.")
    }
}
