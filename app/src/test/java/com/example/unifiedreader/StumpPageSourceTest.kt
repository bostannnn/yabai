package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StumpPageSourceTest {
    @Test
    fun `load page image reuses cached bitmap for repeated requests`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val firstResult = pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        val secondResult = pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertTrue(firstResult is ComicPageLoadResult.Success)
        assertTrue(secondResult is ComicPageLoadResult.Success)
        assertEquals(1, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `successful stump page load marks source reachable`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.STUMP)
        }
        val pageSource = createPageSource(
            api = api,
            scope = this,
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertEquals(RemoteSourceReachability.Reachable, connectivityPolicy.reachabilityFor(SourceType.STUMP))
    }

    @Test
    fun `failed stump page load marks source unavailable`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi().apply {
            pageImageFailure = IOException("timeout")
        }
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.STUMP)
        }
        val pageSource = createPageSource(
            api = api,
            scope = this,
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val result = pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertTrue(result is ComicPageLoadResult.Failure)
        assertEquals(RemoteSourceReachability.Unavailable, connectivityPolicy.reachabilityFor(SourceType.STUMP))
    }

    @Test
    fun `prefetch around requests nearest surrounding pages for stump comics`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val pageSource = createPageSource(api, scope)

        pageSource.prefetchAround(
            item = item,
            settledPageIndex = 5,
            pageCount = 12,
        )
        scope.advanceUntilIdle()

        assertEquals(listOf(6, 7, 8, 9, 4, 3, 2, 1), api.requestedPageIndexes)
    }

    @Test
    fun `clear invalidates cached stump pages`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        pageSource.clear()
        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertEquals(listOf(3, 3), api.requestedPageIndexes)
    }

    @Test
    fun `clear prevents in flight request from re populating cache`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")
        api.beginBlockingRequests()

        val inFlightLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        }
        api.awaitBlockedRequest()
        pageSource.clear()
        api.releaseBlockedRequests()
        inFlightLoad.await()
        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertEquals(listOf(3, 3), api.requestedPageIndexes)
    }

    @Test
    fun `metadata warmup shares in flight page request with visible load`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")
        api.beginBlockingRequests()

        val metadataLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageMetadata(item, page)
        }
        api.awaitBlockedRequest()
        val imageLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        }
        api.releaseBlockedRequests()

        metadataLoad.await()
        imageLoad.await()

        assertEquals(1, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `visible load reuses in flight prefetched page request`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val pageSource = createPageSource(api, scope)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")
        api.beginBlockingRequests()

        pageSource.prefetchAround(
            item = item,
            settledPageIndex = 2,
            pageCount = 12,
        )
        scope.runCurrent()
        api.awaitBlockedRequest()

        val visibleLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        }
        runCurrent()
        api.releaseBlockedRequests()
        visibleLoad.await()
        scope.advanceUntilIdle()

        assertEquals(1, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `completed metadata request warms byte cache before later visible load`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val fetchScope = TestScope(StandardTestDispatcher(testScheduler))
        val pageSource = createPageSource(api, fetchScope)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")
        api.beginBlockingRequests()

        val metadataLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageMetadata(item, page)
        }
        fetchScope.runCurrent()
        api.awaitBlockedRequest()
        api.releaseBlockedRequests()
        fetchScope.runCurrent()

        val imageLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        }
        runCurrent()

        metadataLoad.await()
        imageLoad.await()

        assertEquals(1, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `page preview does not start full page request for stump without cached bytes`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val result = pageSource.loadPagePreview(item, page)

        assertTrue(result is ComicPagePreviewResult.Unavailable)
        assertEquals(emptyList<Int>(), api.requestedPageIndexes)
    }

    @Test
    fun `load aborts when stump session is cleared before deferred byte fetch starts`() = runTest {
        val item = sampleStumpComic(pageCount = 12)
        val api = RecordingStumpPageApi()
        val fetchScope = TestScope(StandardTestDispatcher(testScheduler))
        val credentialStore = TestStumpCredentialStore(
            StumpStoredSession(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "access-token",
                refreshToken = "refresh-token",
            ),
        )
        val pageSource = createPageSource(
            api = api,
            scope = fetchScope,
            credentialStore = credentialStore,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val imageLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        }
        credentialStore.clear()
        fetchScope.runCurrent()

        val result = imageLoad.await()

        assertTrue(result is ComicPageLoadResult.Failure)
        assertEquals(emptyList<Int>(), api.requestedPageIndexes)
    }
}

private fun createPageSource(
    api: RecordingStumpPageApi,
    scope: TestScope,
    credentialStore: StumpCredentialStore = TestStumpCredentialStore(
        StumpStoredSession(
            displayName = "Stump",
            baseUrl = "https://stump.example",
            username = "reader",
            accessToken = "access-token",
            refreshToken = "refresh-token",
        ),
    ),
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
): StumpPageSource = StumpPageSource(
    stumpApiFactory = StumpApiFactory { _ -> api },
    stumpCredentialStore = credentialStore,
    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    ioDispatcher = scope.coroutineContext[ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
    scope = scope,
)

private fun sampleStumpComic(
    pageCount: Int,
): ReadableItem = ReadableItem(
    id = SourceType.STUMP.remoteItemId("media-1"),
    sourceType = SourceType.STUMP,
    libraryId = SourceType.STUMP.remoteLibraryId("library-1"),
    remoteId = "media-1",
    remoteFileId = null,
    remoteFileType = null,
    title = "Remote Comic",
    format = FormatType.CBZ,
    pageCount = pageCount,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = null,
    comicMetadata = null,
)

private class TestStumpCredentialStore(
    initialSession: StumpStoredSession?,
) : StumpCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<StumpStoredSession?> = sessionState

    override suspend fun save(
        session: StumpStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

private class RecordingStumpPageApi : StumpApi {
    val requestedPageIndexes = mutableListOf<Int>()
    var pageImageFailure: Throwable? = null
    var blockRequests: Boolean = false
    private var blockedRequestStarted = CompletableDeferred<Unit>()
    private var releaseBlockedRequests = CompletableDeferred<Unit>()

    override suspend fun login(
        username: String,
        password: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ) = emptyList<com.example.unifiedreader.core.model.Library>()

    override suspend fun getSeries(
        accessToken: String,
        libraryId: String,
    ): List<StumpSeriesEntry> = emptyList()

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: StumpSeriesEntry,
    ): List<StumpMediaCatalogEntry> = emptyList()

    override suspend fun getProgress(
        accessToken: String,
        mediaId: String,
        itemId: String,
        pageCount: Int?,
    ) = error("Not needed in test.")

    override suspend fun updatePagedProgress(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ) = Unit

    override suspend fun markMediaComplete(
        accessToken: String,
        mediaId: String,
        isComplete: Boolean,
        pageIndex: Int?,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ): ByteArray {
        requestedPageIndexes += pageIndex
        pageImageFailure?.let { throw it }
        if (blockRequests) {
            blockedRequestStarted.complete(Unit)
            releaseBlockedRequests.await()
        }
        return TEST_STUMP_PNG_BYTES
    }

    override suspend fun downloadThumbnail(
        accessToken: String,
        mediaId: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadSeriesThumbnail(
        accessToken: String,
        seriesId: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadUrl(
        accessToken: String,
        url: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadMediaFile(
        accessToken: String,
        mediaId: String,
        destination: File,
    ) = error("Not needed in test.")

    fun beginBlockingRequests() {
        blockRequests = true
        blockedRequestStarted = CompletableDeferred()
        releaseBlockedRequests = CompletableDeferred()
    }

    suspend fun awaitBlockedRequest() {
        blockedRequestStarted.await()
    }

    fun releaseBlockedRequests() {
        blockRequests = false
        releaseBlockedRequests.complete(Unit)
    }
}

private val TEST_STUMP_PNG_BYTES = byteArrayOf(
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
