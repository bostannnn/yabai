package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaFileDimensionDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KavitaPageSourceTest {
    @Test
    fun `load page image reuses cached bitmap for repeated requests`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val firstResult = pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        val secondResult = pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertTrue(firstResult is com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult.Success)
        assertTrue(secondResult is com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult.Success)
        assertEquals(1, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `kavita remote page source supports cbr comics`() = runTest {
        val item = sampleKavitaComic(
            pageCount = 2,
            format = FormatType.CBR,
        )
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)

        val pages = pageSource.getPages(item)
        val result = pageSource.loadPageImage(item, pages.first(), ComicPageLoadPriority.Visible)

        assertEquals(listOf("0", "1"), pages.map { page -> page.id })
        assertTrue(result is ComicPageLoadResult.Success)
        assertEquals(listOf(0), api.requestedPageIndexes)
    }

    @Test
    fun `successful kavita page load marks source reachable`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
        val pageSource = createPageSource(
            api = api,
            scope = this,
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertEquals(RemoteSourceReachability.Reachable, connectivityPolicy.reachabilityFor(SourceType.KAVITA))
    }

    @Test
    fun `failed kavita page load marks source unavailable`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi().apply {
            pageImageFailure = IOException("timeout")
        }
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val pageSource = createPageSource(
            api = api,
            scope = this,
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val result = pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertTrue(result is ComicPageLoadResult.Failure)
        assertEquals(RemoteSourceReachability.Unavailable, connectivityPolicy.reachabilityFor(SourceType.KAVITA))
    }

    @Test
    fun `prefetch around requests nearest surrounding pages for kavita comics`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
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
    fun `custom prefetch window requests a larger forward-biased range for kavita comics`() = runTest {
        val item = sampleKavitaComic(pageCount = 20)
        val api = RecordingKavitaPageApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val pageSource = createPageSource(api, scope)

        pageSource.prefetchAround(
            item = item,
            settledPageIndex = 5,
            pageCount = 20,
            window = ComicPagePrefetchWindow(forwardCount = 6, backwardCount = 2),
        )
        scope.advanceUntilIdle()

        assertEquals(8, api.requestedPageIndexes.size)
        assertTrue(api.requestedPageIndexes.containsAll(listOf(6, 7, 8, 9, 10, 11, 4, 3)))
    }

    @Test
    fun `page preview uses full page image instead of kavita thumbnail endpoint`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        val result = pageSource.loadPagePreview(item, page)

        assertTrue(result is ComicPagePreviewResult.Success)
        assertEquals(listOf(3), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `page metadata uses kavita chapter dimensions before full image bytes`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi().apply {
            chapterInfo = KavitaChapterInfoDto(
                pageDimensions = listOf(
                    KavitaFileDimensionDto(width = 800, height = 1200, pageNumber = 1),
                    KavitaFileDimensionDto(width = 1600, height = 1000, pageNumber = 2),
                ),
            )
        }
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "1", pageIndex = 1, contentDescription = "Page 2")

        val metadata = pageSource.loadPageMetadata(item, page)

        assertEquals(ComicPageMetadata(width = 1600, height = 1000), metadata)
        assertEquals(listOf(true), api.requestedChapterInfoIncludeDimensions)
        assertEquals(emptyList<Int>(), api.requestedPageIndexes)
    }

    @Test
    fun `wide kavita page preview uses full page image without consulting dimensions`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi().apply {
            chapterInfo = KavitaChapterInfoDto(
                pageDimensions = listOf(
                    KavitaFileDimensionDto(width = 800, height = 1200, pageNumber = 1),
                    KavitaFileDimensionDto(width = 2400, height = 1200, pageNumber = 2),
                ),
            )
        }
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "1", pageIndex = 1, contentDescription = "Page 2")

        val preview = pageSource.loadPagePreview(item, page)

        assertTrue(preview is ComicPagePreviewResult.Success)
        assertEquals(emptyList<Boolean>(), api.requestedChapterInfoIncludeDimensions)
        assertEquals(listOf(1), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `page metadata falls back to full image when kavita chapter dimensions fail`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi().apply {
            chapterInfoFailure = IllegalStateException("chapter info unavailable")
        }
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "2", pageIndex = 2, contentDescription = "Page 3")

        val metadata = pageSource.loadPageMetadata(item, page)

        assertEquals(ComicPageMetadata(width = 1, height = 1), metadata)
        assertEquals(listOf(true), api.requestedChapterInfoIncludeDimensions)
        assertEquals(listOf(2), api.requestedPageIndexes)
    }

    @Test
    fun `page preview ignores kavita chapter dimension failures`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi().apply {
            chapterInfoFailure = IllegalStateException("chapter info unavailable")
        }
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "2", pageIndex = 2, contentDescription = "Page 3")

        val preview = pageSource.loadPagePreview(item, page)

        assertTrue(preview is ComicPagePreviewResult.Success)
        assertEquals(emptyList<Boolean>(), api.requestedChapterInfoIncludeDimensions)
        assertEquals(listOf(2), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `concurrent kavita metadata requests share one chapter dimensions request`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi().apply {
            chapterInfo = KavitaChapterInfoDto(
                pageDimensions = listOf(
                    KavitaFileDimensionDto(width = 800, height = 1200, pageNumber = 1),
                    KavitaFileDimensionDto(width = 900, height = 1300, pageNumber = 2),
                    KavitaFileDimensionDto(width = 1000, height = 1400, pageNumber = 3),
                ),
            )
        }
        val pageSource = createPageSource(api, this)
        val pages = pageSource.getPages(item).take(3)

        val metadata = pages
            .map { page ->
                async {
                    pageSource.loadPageMetadata(item, page)
                }
            }
            .awaitAll()

        assertEquals(
            listOf(
                ComicPageMetadata(width = 800, height = 1200),
                ComicPageMetadata(width = 900, height = 1300),
                ComicPageMetadata(width = 1000, height = 1400),
            ),
            metadata,
        )
        assertEquals(listOf(true), api.requestedChapterInfoIncludeDimensions)
    }

    @Test
    fun `clearing kavita source cache clears chapter dimensions metadata`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi().apply {
            chapterInfo = KavitaChapterInfoDto(
                pageDimensions = listOf(
                    KavitaFileDimensionDto(width = 800, height = 1200, pageNumber = 1),
                ),
            )
        }
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "0", pageIndex = 0, contentDescription = "Page 1")

        val firstMetadata = pageSource.loadPageMetadata(item, page)
        api.chapterInfo = KavitaChapterInfoDto(
            pageDimensions = listOf(
                KavitaFileDimensionDto(width = 900, height = 1300, pageNumber = 1),
            ),
        )
        pageSource.clear()
        val secondMetadata = pageSource.loadPageMetadata(item, page)

        assertEquals(ComicPageMetadata(width = 800, height = 1200), firstMetadata)
        assertEquals(ComicPageMetadata(width = 900, height = 1300), secondMetadata)
        assertEquals(listOf(true, true), api.requestedChapterInfoIncludeDimensions)
    }

    @Test
    fun `kavita chapter dimensions metadata is keyed by server url`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val credentialStore = TestKavitaCredentialStore(
            KavitaStoredSession(
                displayName = "Kavita",
                baseUrl = "https://first-kavita.example",
                username = "reader",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                apiKey = "api-key",
            ),
        )
        val api = RecordingKavitaPageApi().apply {
            chapterInfo = KavitaChapterInfoDto(
                pageDimensions = listOf(
                    KavitaFileDimensionDto(width = 800, height = 1200, pageNumber = 1),
                ),
            )
        }
        val pageSource = createPageSource(
            api = api,
            scope = this,
            credentialStore = credentialStore,
        )
        val page = ComicPageSpec(id = "0", pageIndex = 0, contentDescription = "Page 1")

        val firstMetadata = pageSource.loadPageMetadata(item, page)
        api.chapterInfo = KavitaChapterInfoDto(
            pageDimensions = listOf(
                KavitaFileDimensionDto(width = 900, height = 1300, pageNumber = 1),
            ),
        )
        credentialStore.save(
            KavitaStoredSession(
                displayName = "Kavita",
                baseUrl = "https://second-kavita.example",
                username = "reader",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                apiKey = "api-key",
            ),
        )
        val secondMetadata = pageSource.loadPageMetadata(item, page)

        assertEquals(ComicPageMetadata(width = 800, height = 1200), firstMetadata)
        assertEquals(ComicPageMetadata(width = 900, height = 1300), secondMetadata)
        assertEquals(listOf(true, true), api.requestedChapterInfoIncludeDimensions)
    }

    @Test
    fun `page image preview warms authoritative page metadata`() = runTest {
        val item = sampleKavitaComic(pageCount = 4)
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "2", pageIndex = 2, contentDescription = "Page 3")

        pageSource.loadPagePreview(item, page)
        val previewMetadata = pageSource.loadPagePreviewMetadata(item, page)
        val metadata = pageSource.loadPageMetadata(item, page)

        assertEquals(null, previewMetadata)
        assertEquals(ComicPageMetadata(width = 1, height = 1), metadata)
        assertEquals(listOf(2), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `preview prefetch is disabled for kavita page image previews`() = runTest {
        val item = sampleKavitaComic(pageCount = 6)
        val api = RecordingKavitaPageApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val pageSource = createPageSource(api, scope)
        val pages = pageSource.getPages(item)

        pageSource.prefetchPagePreviews(item, pages)
        scope.advanceUntilIdle()

        assertEquals(emptyList<Int>(), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `preview prefetch priority is ignored for kavita page image previews`() = runTest {
        val item = sampleKavitaComic(pageCount = 6)
        val api = RecordingKavitaPageApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val pageSource = createPageSource(api, scope)
        val pages = pageSource.getPages(item)

        pageSource.prefetchPagePreviews(
            item = item,
            pages = pages,
            priorityPageIndex = 3,
        )
        scope.advanceUntilIdle()

        assertEquals(emptyList<Int>(), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `visible preview requests the matching full page image`() = runTest {
        val item = sampleKavitaComic(pageCount = 6)
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)
        val pages = pageSource.getPages(item)
        val visiblePreview = pageSource.loadPagePreview(item, pages[3])

        assertTrue(visiblePreview is ComicPagePreviewResult.Success)
        assertEquals(listOf(3), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
    }

    @Test
    fun `page image preview does not reuse persistent thumbnail bytes after source recreation`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val previewStoreDirectory = File(System.getProperty("java.io.tmpdir"), "kavita-preview-store-${System.nanoTime()}")
        val previewStore = PersistentComicPagePreviewStore(
            rootDirectory = previewStoreDirectory,
            ioDispatcher = coroutineContext[ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
        )
        val firstApi = RecordingKavitaPageApi()
        val firstSource = createPageSource(
            api = firstApi,
            scope = this,
            previewStore = previewStore,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        try {
            firstSource.loadPagePreview(item, page)

            val secondApi = RecordingKavitaPageApi()
            val secondSource = createPageSource(
                api = secondApi,
                scope = this,
                previewStore = previewStore,
            )
            val result = secondSource.loadPagePreview(item, page)

            assertTrue(result is ComicPagePreviewResult.Success)
            assertEquals(listOf(3), firstApi.requestedPageIndexes)
            assertEquals(emptyList<Int>(), firstApi.requestedThumbnailPageNumbers)
            assertEquals(listOf(3), secondApi.requestedPageIndexes)
            assertEquals(emptyList<Int>(), secondApi.requestedThumbnailPageNumbers)
        } finally {
            previewStoreDirectory.deleteRecursively()
        }
    }

    @Test
    fun `page preview version updates after page image preview bytes are cached`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")
        val nextVersion = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.observePagePreviewVersion(item, listOf(page), ComicPagePreviewOptions())
                .drop(1)
                .first()
        }

        val result = pageSource.loadPagePreview(item, page)

        assertTrue(result is ComicPagePreviewResult.Success)
        assertEquals(listOf(3), api.requestedPageIndexes)
        assertEquals(emptyList<Int>(), api.requestedThumbnailPageNumbers)
        assertEquals(1L, nextVersion.await())
    }

    @Test
    fun `clear invalidates cached kavita pages`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val pageSource = createPageSource(api, this)
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")

        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        pageSource.clear()
        pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)

        assertEquals(listOf(3, 3), api.requestedPageIndexes)
    }

    @Test
    fun `clear prevents in flight request from re populating cache`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
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
    fun `cancelled visible caller cancels page request and releases coordinator`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val readerWorkCoordinator = ReaderWorkCoordinator(openingGraceMillis = 0L)
        val pageSource = createPageSource(
            api = api,
            scope = scope,
            readerWorkCoordinator = readerWorkCoordinator,
        )
        val page = ComicPageSpec(id = "3", pageIndex = 3, contentDescription = "Page 4")
        api.beginBlockingRequests()

        val visibleLoad = async(UnconfinedTestDispatcher(testScheduler)) {
            pageSource.loadPageImage(item, page, ComicPageLoadPriority.Visible)
        }
        scope.runCurrent()
        api.awaitBlockedRequest()
        assertTrue(readerWorkCoordinator.hasVisiblePageLoads)

        visibleLoad.cancel()
        visibleLoad.join()

        assertFalse(readerWorkCoordinator.hasVisiblePageLoads)

        api.releaseBlockedRequests()
        scope.advanceUntilIdle()
        assertFalse(readerWorkCoordinator.hasVisiblePageLoads)
    }

    @Test
    fun `visible load interrupts in flight metadata page request`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
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
        runCurrent()
        api.releaseBlockedRequests()

        metadataLoad.await()
        imageLoad.await()

        assertEquals(2, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `visible load interrupts in flight prefetched page request`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
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

        assertEquals(2, api.requestedPageIndexes.count { it == 3 })
    }

    @Test
    fun `completed metadata request warms byte cache before later visible load`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
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
    fun `load aborts when kavita session is cleared before deferred byte fetch starts`() = runTest {
        val item = sampleKavitaComic(pageCount = 12)
        val api = RecordingKavitaPageApi()
        val fetchScope = TestScope(StandardTestDispatcher(testScheduler))
        val credentialStore = TestKavitaCredentialStore(
            KavitaStoredSession(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                apiKey = "api-key",
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
    api: RecordingKavitaPageApi,
    scope: TestScope,
    credentialStore: KavitaCredentialStore = TestKavitaCredentialStore(
        KavitaStoredSession(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            apiKey = "api-key",
        ),
    ),
    previewStore: PersistentComicPagePreviewStore? = null,
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
    readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
): KavitaPageSource = KavitaPageSource(
    kavitaApiFactory = KavitaApiFactory { _ -> api },
    kavitaCredentialStore = credentialStore,
    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    ioDispatcher = scope.coroutineContext[ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
    scope = scope,
    previewStore = previewStore,
    readerWorkCoordinator = readerWorkCoordinator,
)

private fun sampleKavitaComic(
    pageCount: Int,
    format: FormatType = FormatType.CBZ,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId("chapter-1"),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("library-1"),
    remoteId = "chapter-1",
    remoteFileId = null,
    remoteFileType = null,
    title = "Remote Comic",
    format = format,
    pageCount = pageCount,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = null,
    comicMetadata = null,
)

private class TestKavitaCredentialStore(
    initialSession: KavitaStoredSession?,
) : KavitaCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<KavitaStoredSession?> = sessionState

    override suspend fun save(
        session: KavitaStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

private class RecordingKavitaPageApi : KavitaApi {
    val requestedPageIndexes = mutableListOf<Int>()
    val requestedThumbnailPageNumbers = mutableListOf<Int>()
    val requestedChapterInfoIncludeDimensions = mutableListOf<Boolean>()
    var chapterInfo = KavitaChapterInfoDto()
    var chapterInfoFailure: Throwable? = null
    var pageImageFailure: Throwable? = null
    var blockRequests: Boolean = false
    private var blockedRequestCount = 0
    private var blockedRequestStarted = CompletableDeferred<Unit>()
    private var releaseBlockedRequests = CompletableDeferred<Unit>()

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ) = emptyList<com.example.unifiedreader.core.model.Library>()

    override suspend fun getSeries(
        accessToken: String,
        apiKey: String?,
    ): List<KavitaSeriesEntry> = emptyList()

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> = emptyList()

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto = error("Not needed in test.")

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto {
        requestedChapterInfoIncludeDimensions += includeDimensions
        chapterInfoFailure?.let { throw it }
        return chapterInfo
    }

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray {
        requestedPageIndexes += pageIndex
        pageImageFailure?.let { throw it }
        if (blockRequests) {
            blockedRequestCount += 1
            blockedRequestStarted.complete(Unit)
            releaseBlockedRequests.await()
            blockedRequestCount -= 1
        }
        return TEST_PNG_BYTES
    }

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray {
        requestedThumbnailPageNumbers += pageNumber
        if (blockRequests) {
            blockedRequestCount += 1
            blockedRequestStarted.complete(Unit)
            releaseBlockedRequests.await()
            blockedRequestCount -= 1
        }
        return TEST_PNG_BYTES
    }

    override suspend fun downloadCover(
        accessToken: String,
        coverUrl: String,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
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

private val TEST_PNG_BYTES = byteArrayOf(
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
