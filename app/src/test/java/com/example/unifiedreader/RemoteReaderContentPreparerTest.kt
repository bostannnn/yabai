package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteReaderContentPreparerTest {
    @Test
    fun `stored document preparation does not mark source reachable while offline`() = runTest {
        val policy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
        val dataSource = TestReaderPreparationDownloadDataSource(
            storedUri = Uri.parse("file:///tmp/cached.pdf"),
        )
        val preparer = RemoteReaderContentPreparer(
            remoteDownloadDataSources = listOf(dataSource),
            remoteSourceConnectivityPolicy = policy,
        )

        val result = preparer.prepare(remoteItem())

        assertTrue(result is RemoteReaderContentPreparationResult.Ready)
        assertEquals(0, dataSource.prepareDocumentForReadingCalls)
        assertEquals(RemoteSourceReachability.Unavailable, policy.reachabilityFor(SourceType.KAVITA))
    }

    @Test
    fun `network preparation result marks source reachable`() = runTest {
        val policy = RemoteSourceConnectivityPolicy()
        val preparer = RemoteReaderContentPreparer(
            remoteDownloadDataSources = listOf(
                TestReaderPreparationDownloadDataSource(
                    result = RemoteItemDownloadResult(
                        uri = Uri.parse("file:///tmp/downloaded.pdf"),
                        downloadedFromNetwork = true,
                    ),
                ),
            ),
            remoteSourceConnectivityPolicy = policy,
        )

        val result = preparer.prepare(remoteItem())

        assertTrue(result is RemoteReaderContentPreparationResult.Ready)
        assertEquals(RemoteSourceReachability.Reachable, policy.reachabilityFor(SourceType.KAVITA))
    }

    @Test
    fun `missing document preparation is blocked while source is offline`() = runTest {
        val policy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
        val dataSource = TestReaderPreparationDownloadDataSource(
            result = RemoteItemDownloadResult(
                uri = Uri.parse("file:///tmp/downloaded.pdf"),
                downloadedFromNetwork = true,
            ),
        )
        val preparer = RemoteReaderContentPreparer(
            remoteDownloadDataSources = listOf(dataSource),
            remoteSourceConnectivityPolicy = policy,
        )

        val result = preparer.prepare(remoteItem())

        assertTrue(result is RemoteReaderContentPreparationResult.Failure)
        assertTrue((result as RemoteReaderContentPreparationResult.Failure).failure.throwable is RemoteSourceUnavailableException)
        assertEquals(0, dataSource.prepareDocumentForReadingCalls)
        assertEquals(RemoteSourceReachability.Unavailable, policy.reachabilityFor(SourceType.KAVITA))
    }
}

private class TestReaderPreparationDownloadDataSource(
    private val result: RemoteItemDownloadResult? = null,
    private val storedUri: Uri? = null,
) : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    var prepareDocumentForReadingCalls: Int = 0
        private set

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = storedUri

    override suspend fun prepareDocumentForReading(
        item: ReadableItem,
    ): RemoteItemDownloadResult? {
        prepareDocumentForReadingCalls += 1
        return result
    }

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = storedUri ?: result?.uri

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = emptySet()

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}

private fun remoteItem(): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId("item-1"),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("library-1"),
    remoteId = "item-1",
    title = "Remote PDF",
    format = FormatType.PDF,
)
