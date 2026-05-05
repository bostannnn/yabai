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
    fun `cached preparation result does not mark source reachable`() = runTest {
        val policy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
        val preparer = RemoteReaderContentPreparer(
            remoteDownloadDataSources = listOf(
                TestReaderPreparationDownloadDataSource(
                    result = RemoteItemDownloadResult(
                        uri = Uri.parse("file:///tmp/cached.pdf"),
                        downloadedFromNetwork = false,
                    ),
                ),
            ),
            remoteSourceConnectivityPolicy = policy,
        )

        val result = preparer.prepare(remoteItem())

        assertTrue(result is RemoteReaderContentPreparationResult.Ready)
        assertEquals(RemoteSourceReachability.Unavailable, policy.reachabilityFor(SourceType.KAVITA))
    }

    @Test
    fun `network preparation result marks source reachable`() = runTest {
        val policy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
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
}

private class TestReaderPreparationDownloadDataSource(
    private val result: RemoteItemDownloadResult?,
) : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun prepareDocumentForReading(
        item: ReadableItem,
    ): RemoteItemDownloadResult? = result

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = result?.uri

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
