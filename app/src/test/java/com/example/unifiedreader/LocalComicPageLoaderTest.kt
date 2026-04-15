package com.example.unifiedreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalComicPageLoaderTest {
    @Test
    fun `metadata warmup shares in flight archive read with visible load`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val codec = RecordingComicArchiveCodec()
        val loader = LocalComicPageLoader(
            context = context,
            comicArchiveCodecs = listOf(codec),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
        )
        val item = sampleLocalComic(pageCount = 12)
        val record = sampleLocalRecord(pageCount = 12)
        val entryName = "3.png"
        codec.beginBlockingReads()

        val metadataLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            loader.loadPageMetadata(item, record, entryName)
        }
        codec.awaitBlockedRead()
        val imageLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            loader.loadPageImage(item, record, entryName, ComicPageLoadPriority.Visible)
        }
        runCurrent()
        codec.releaseBlockedReads()

        metadataLoad.await()
        imageLoad.await()

        assertEquals(1, codec.requestedEntryNames.count { it == entryName })
    }

    @Test
    fun `visible load reuses in flight prefetched archive read`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val codec = RecordingComicArchiveCodec()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val loader = LocalComicPageLoader(
            context = context,
            comicArchiveCodecs = listOf(codec),
            scope = scope,
        )
        val item = sampleLocalComic(pageCount = 12)
        val record = sampleLocalRecord(pageCount = 12)
        val entryName = "3.png"
        codec.beginBlockingReads()

        loader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = 2,
            pageCount = 12,
            window = ComicPagePrefetchWindow(
                forwardCount = 1,
                backwardCount = 0,
            ),
        )
        scope.runCurrent()
        codec.awaitBlockedRead()

        val visibleLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            loader.loadPageImage(item, record, entryName, ComicPageLoadPriority.Visible)
        }
        runCurrent()
        codec.releaseBlockedReads()
        visibleLoad.await()
        scope.advanceUntilIdle()

        assertEquals(1, codec.requestedEntryNames.count { it == entryName })
    }
}

private fun sampleLocalComic(
    pageCount: Int,
): ReadableItem = ReadableItem(
    id = "comic-local-1",
    sourceType = SourceType.LOCAL,
    libraryId = "local-imports",
    isLocalImport = true,
    title = "Local Comic",
    format = FormatType.CBZ,
    pageCount = pageCount,
    description = "Imported local CBZ with $pageCount pages.",
    coverUrl = null,
)

private fun sampleLocalRecord(
    pageCount: Int,
): LocalDocumentRecord = LocalDocumentRecord(
    id = "comic-local-1",
    uri = "content://example/comic.cbz",
    displayName = "comic.cbz",
    title = "Local Comic",
    format = FormatType.CBZ,
    pageCount = pageCount,
    pageEntryNames = List(pageCount) { index -> "$index.png" },
)

private class RecordingComicArchiveCodec : ComicArchiveCodec {
    override val format: FormatType = FormatType.CBZ

    val requestedEntryNames = mutableListOf<String>()

    private var blockReads = false
    private var blockedReadStarted = CompletableDeferred<Unit>()
    private var releaseBlockedReads = CompletableDeferred<Unit>()

    override fun matchesFileName(fileName: String): Boolean = true

    override suspend fun listEntries(
        context: Context,
        uri: android.net.Uri,
    ): List<String> = emptyList()

    override suspend fun readEntryBytes(
        context: Context,
        uri: android.net.Uri,
        entryName: String,
    ): ByteArray? {
        requestedEntryNames += entryName
        if (blockReads) {
            blockedReadStarted.complete(Unit)
            releaseBlockedReads.await()
        }
        return LOCAL_TEST_PNG_BYTES
    }

    fun beginBlockingReads() {
        blockReads = true
        blockedReadStarted = CompletableDeferred()
        releaseBlockedReads = CompletableDeferred()
    }

    suspend fun awaitBlockedRead() {
        blockedReadStarted.await()
    }

    fun releaseBlockedReads() {
        blockReads = false
        releaseBlockedReads.complete(Unit)
    }
}

private val LOCAL_TEST_PNG_BYTES = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
    0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
    0x54, 0x78, 0x9C.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(),
    0xF0.toByte(), 0x1F, 0x00, 0x05, 0x00, 0x01, 0xFF.toByte(), 0x89.toByte(),
    0x99.toByte(), 0x3D, 0x1D, 0x00, 0x00, 0x00, 0x00, 0x49,
    0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
)
