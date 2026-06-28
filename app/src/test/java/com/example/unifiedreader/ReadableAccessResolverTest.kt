package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadableAccessResolverTest {
    @Test
    fun `kavita books prepare a local file instead of opening an empty stream`() {
        val item = remoteItem(sourceType = SourceType.KAVITA, format = FormatType.PDF)
        val resolver = resolverFor(
            sourceType = SourceType.KAVITA,
            isReachable = true,
        )

        assertEquals(ReadableAccessDecision.PrepareRemoteFile, resolver.resolve(item))
    }

    @Test
    fun `grimmory comics prepare a local file instead of opening an empty comic source`() {
        val item = remoteItem(sourceType = SourceType.GRIMMORY, format = FormatType.CBR)
        val resolver = resolverFor(
            sourceType = SourceType.GRIMMORY,
            isReachable = true,
        )

        assertEquals(ReadableAccessDecision.PrepareRemoteFile, resolver.resolve(item))
    }

    @Test
    fun `remote comic page api formats still stream when online`() {
        val item = remoteItem(sourceType = SourceType.KAVITA, format = FormatType.CBZ)
        val resolver = resolverFor(
            sourceType = SourceType.KAVITA,
            isReachable = true,
        )

        assertEquals(ReadableAccessDecision.RemoteStream, resolver.resolve(item))
    }

    @Test
    fun `grimmory books still use remote read cache stream path when online`() {
        val item = remoteItem(sourceType = SourceType.GRIMMORY, format = FormatType.EPUB)
        val resolver = resolverFor(
            sourceType = SourceType.GRIMMORY,
            isReachable = true,
        )

        assertEquals(ReadableAccessDecision.RemoteStream, resolver.resolve(item))
    }

    @Test
    fun `offline downloaded remote file opens locally`() {
        val item = remoteItem(sourceType = SourceType.GRIMMORY, format = FormatType.CBZ)
        val resolver = resolverFor(
            sourceType = SourceType.GRIMMORY,
            isReachable = false,
            storedItemIds = setOf(item.id),
        )

        assertEquals(ReadableAccessDecision.DownloadedRemoteFile, resolver.resolve(item))
    }

    @Test
    fun `offline cached readable file opens locally where supported`() {
        val item = remoteItem(sourceType = SourceType.GRIMMORY, format = FormatType.EPUB)
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.GRIMMORY)
        }
        val resolver = ReadableAccessResolver(
            remoteItemLocalAvailability = RemoteItemLocalAvailability(
                remoteDownloadDataSources = emptyList(),
                remoteDocumentReadCache = cachedReadCacheFor(
                    item = item,
                    connectivityPolicy = connectivityPolicy,
                ),
            ),
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )

        assertEquals(ReadableAccessDecision.DownloadedRemoteFile, resolver.resolve(item))
    }

    @Test
    fun `missing remote file is blocked while source is offline`() {
        val item = remoteItem(sourceType = SourceType.KAVITA, format = FormatType.PDF)
        val resolver = resolverFor(
            sourceType = SourceType.KAVITA,
            isReachable = false,
        )

        assertEquals(ReadableAccessDecision.BlockedRemoteUnavailable(SourceType.KAVITA), resolver.resolve(item))
    }

    @Test
    fun `missing stored remote file is blocked while source probe is checking`() {
        val item = remoteItem(sourceType = SourceType.KAVITA, format = FormatType.PDF)
        val resolver = resolverFor(
            sourceType = SourceType.KAVITA,
            isChecking = true,
        )

        assertEquals(ReadableAccessDecision.BlockedRemoteUnavailable(SourceType.KAVITA), resolver.resolve(item))
    }

    @Test
    fun `non streamable remote file without download support fails through preparation path`() {
        val item = remoteItem(sourceType = SourceType.KAVITA, format = FormatType.PDF)
        val resolver = resolverFor(
            sourceType = SourceType.KAVITA,
            isReachable = true,
            includeDataSource = false,
        )

        assertEquals(ReadableAccessDecision.PrepareRemoteFile, resolver.resolve(item))
    }

    private fun resolverFor(
        sourceType: SourceType,
        isReachable: Boolean = true,
        isChecking: Boolean = false,
        storedItemIds: Set<String> = emptySet(),
        includeDataSource: Boolean = true,
    ): ReadableAccessResolver {
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            if (isChecking) {
                markChecking(sourceType)
            } else if (isReachable) {
                markReachable(sourceType)
            } else {
                markUnavailable(sourceType)
            }
        }
        return ReadableAccessResolver(
            remoteItemLocalAvailability = RemoteItemLocalAvailability(
                if (includeDataSource) {
                    listOf(TestDownloadDataSource(sourceType = sourceType, storedItemIds = storedItemIds))
                } else {
                    emptyList()
                },
            ),
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
    }
}

private fun cachedReadCacheFor(
    item: ReadableItem,
    connectivityPolicy: RemoteSourceConnectivityPolicy,
): RemoteDocumentReadCache {
    val context = ApplicationProvider.getApplicationContext<Application>()
    File(context.cacheDir, "grimmory-reader-documents").apply {
        deleteRecursively()
        mkdirs()
    }.resolve(item.grimmoryCacheFileName(item.format.name.lowercase()))
        .writeText("cached document")
    return RemoteDocumentReadCache(
        context = context,
        grimmoryApiFactory = GrimmoryApiFactory { error("Unexpected remote read for cached item.") },
        grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
        remoteSourceConnectivityPolicy = connectivityPolicy,
    )
}

private class TestDownloadDataSource(
    override val sourceType: SourceType,
    private val storedItemIds: Set<String>,
) : RemoteItemDownloadDataSource {
    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = Uri.parse("file:///tmp/${item.id}").takeIf { item.id in storedItemIds }

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = storedDocumentUri(item)

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = items.filterTo(mutableSetOf()) { item -> item.id in storedItemIds }
        .mapTo(mutableSetOf(), ReadableItem::id)

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}

private fun remoteItem(
    sourceType: SourceType,
    format: FormatType,
): ReadableItem = ReadableItem(
    id = sourceType.remoteItemId("item-1"),
    sourceType = sourceType,
    libraryId = sourceType.remoteLibraryId("library-1"),
    remoteId = "item-1",
    title = "Remote Item",
    format = format,
)
