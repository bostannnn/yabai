package com.example.unifiedreader

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmoryEpubProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RemoteSourceConnectivityStartupCoordinatorTest {
    @Test
    fun `startup probe marks configured source online`() = runTest {
        val healthCheck = FakeRemoteSourceHealthCheck(
            result = RemoteSourceHealthCheckResult.Online,
        )
        val policy = RemoteSourceConnectivityPolicy()
        val coordinator = RemoteSourceConnectivityStartupCoordinator(
            healthChecks = listOf(healthCheck),
            remoteSourceConnectivityPolicy = policy,
        )

        coordinator.checkConfiguredSources()

        assertEquals(RemoteSourceReachability.Reachable, policy.reachabilityFor(SourceType.KAVITA))
        assertEquals(1, healthCheck.callCount)
    }

    @Test
    fun `startup probe marks source offline for retryable network failure`() = runTest {
        val policy = RemoteSourceConnectivityPolicy()
        val coordinator = RemoteSourceConnectivityStartupCoordinator(
            healthChecks = listOf(
                FakeRemoteSourceHealthCheck(
                    failure = IOException("timeout"),
                ),
            ),
            remoteSourceConnectivityPolicy = policy,
        )

        coordinator.checkConfiguredSources()

        assertEquals(RemoteSourceReachability.Unavailable, policy.reachabilityFor(SourceType.KAVITA))
        assertNull(coordinator.sourceErrorsFlow.value[SourceType.KAVITA])
    }

    @Test
    fun `startup probe surfaces auth or protocol failure without classifying source online or offline`() = runTest {
        val failure = IllegalStateException("Unauthorized")
        val policy = RemoteSourceConnectivityPolicy()
        val coordinator = RemoteSourceConnectivityStartupCoordinator(
            healthChecks = listOf(
                FakeRemoteSourceHealthCheck(
                    failure = failure,
                ),
            ),
            remoteSourceConnectivityPolicy = policy,
        )

        coordinator.checkConfiguredSources()

        assertEquals(RemoteSourceReachability.Unknown, policy.reachabilityFor(SourceType.KAVITA))
        assertSame(failure, coordinator.sourceErrorsFlow.value[SourceType.KAVITA])
    }

    @Test
    fun `startup probe leaves source untracked when no session is configured`() = runTest {
        val policy = RemoteSourceConnectivityPolicy()
        val coordinator = RemoteSourceConnectivityStartupCoordinator(
            healthChecks = listOf(
                FakeRemoteSourceHealthCheck(
                    result = RemoteSourceHealthCheckResult.NotConfigured,
                ),
            ),
            remoteSourceConnectivityPolicy = policy,
        )

        coordinator.checkConfiguredSources()

        assertNull(coordinator.currentStates()[SourceType.KAVITA])
    }

    @Test
    fun `startup probe only runs once`() = runTest {
        val healthCheck = FakeRemoteSourceHealthCheck(
            result = RemoteSourceHealthCheckResult.Online,
        )
        val coordinator = RemoteSourceConnectivityStartupCoordinator(
            healthChecks = listOf(healthCheck),
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
        )

        coordinator.checkConfiguredSources()
        coordinator.checkConfiguredSources()

        assertEquals(1, healthCheck.callCount)
    }

    @Test
    fun `kavita startup health check accepts current access token before refreshing`() = runTest {
        val kavitaApi = RecordingStartupKavitaApi()
        val kavitaStore = FakeKavitaCredentialStore(
            initialSession = KavitaStoredSession(
                displayName = "Kavita",
                baseUrl = "https://kavita",
                username = "reader",
                accessToken = "current-access",
                refreshToken = "old-refresh",
                apiKey = "old-api-key",
            ),
        )

        KavitaRemoteSourceHealthCheck(
            kavitaApiFactory = KavitaApiFactory { kavitaApi },
            kavitaCredentialStore = kavitaStore,
        ).check()

        assertEquals(1, kavitaApi.getLibrariesCalls)
        assertEquals(0, kavitaApi.refreshSessionCalls)
        assertEquals("current-access", kavitaStore.sessionFlow.value?.accessToken)
    }

    @Test
    fun `kavita startup health check refreshes after rejected access token`() = runTest {
        val kavitaApi = RecordingStartupKavitaApi(
            unauthorizedLibraryTokens = setOf("old-access"),
        )
        val kavitaStore = FakeKavitaCredentialStore(
            initialSession = KavitaStoredSession(
                displayName = "Kavita",
                baseUrl = "https://kavita",
                username = "reader",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                apiKey = "old-api-key",
            ),
        )

        KavitaRemoteSourceHealthCheck(
            kavitaApiFactory = KavitaApiFactory { kavitaApi },
            kavitaCredentialStore = kavitaStore,
        ).check()

        assertEquals(1, kavitaApi.getLibrariesCalls)
        assertEquals(1, kavitaApi.refreshSessionCalls)
        assertEquals("new-access", kavitaStore.sessionFlow.value?.accessToken)
        assertEquals("new-refresh", kavitaStore.sessionFlow.value?.refreshToken)
    }

    @Test
    fun `non-kavita source startup health checks refresh sessions without fetching catalogs`() = runTest {
        val stumpApi = RecordingStartupStumpApi()
        val grimmoryApi = RecordingStartupGrimmoryApi()
        val stumpStore = FakeStumpCredentialStore(
            initialSession = StumpStoredSession(
                displayName = "Stump",
                baseUrl = "https://stump",
                username = "reader",
                accessToken = "old-access",
                refreshToken = "old-refresh",
            ),
        )
        val grimmoryStore = FakeGrimmoryCredentialStore(
            initialSession = GrimmoryStoredSession(
                displayName = "Grimmory",
                baseUrl = "https://grimmory",
                username = "reader",
                accessToken = "old-access",
                refreshToken = "old-refresh",
            ),
        )

        StumpRemoteSourceHealthCheck(
            stumpApiFactory = StumpApiFactory { stumpApi },
            stumpCredentialStore = stumpStore,
        ).check()
        GrimmoryRemoteSourceHealthCheck(
            grimmoryApiFactory = GrimmoryApiFactory { grimmoryApi },
            grimmoryCredentialStore = grimmoryStore,
        ).check()

        assertEquals(1, stumpApi.refreshSessionCalls)
        assertEquals(0, stumpApi.getLibrariesCalls)
        assertEquals("new-access", stumpStore.sessionFlow.value?.accessToken)
        assertEquals(1, grimmoryApi.refreshSessionCalls)
        assertEquals(0, grimmoryApi.getLibrariesCalls)
        assertEquals("new-access", grimmoryStore.sessionFlow.value?.accessToken)
    }
}

private class FakeRemoteSourceHealthCheck(
    override val sourceType: SourceType = SourceType.KAVITA,
    private val result: RemoteSourceHealthCheckResult = RemoteSourceHealthCheckResult.Online,
    private val failure: Throwable? = null,
) : RemoteSourceHealthCheck {
    var callCount = 0
        private set

    override suspend fun check(): RemoteSourceHealthCheckResult {
        callCount += 1
        failure?.let { throw it }
        return result
    }
}

private class RecordingStartupKavitaApi(
    private val unauthorizedLibraryTokens: Set<String> = emptySet(),
) : KavitaApi {
    var refreshSessionCalls = 0
    var getLibrariesCalls = 0

    override suspend fun login(username: String, password: String): KavitaSession = error("Not used")

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession {
        refreshSessionCalls += 1
        return KavitaSession(
            accessToken = "new-access",
            refreshToken = "new-refresh",
            apiKey = "new-api-key",
        )
    }

    override suspend fun getLibraries(accessToken: String): List<Library> {
        getLibrariesCalls += 1
        if (accessToken in unauthorizedLibraryTokens) {
            throw KavitaUnauthorizedException()
        }
        return emptyList()
    }

    override suspend fun getSeries(accessToken: String, apiKey: String?): List<KavitaSeriesEntry> = error("Not used")
    override suspend fun getSeriesDetail(accessToken: String, series: KavitaSeriesEntry): List<KavitaChapterCatalogEntry> = error("Not used")
    override suspend fun getProgress(accessToken: String, chapterId: String): KavitaProgressDto = error("Not used")
    override suspend fun getChapterInfo(accessToken: String, chapterId: String, includeDimensions: Boolean): KavitaChapterInfoDto = error("Not used")
    override suspend fun updateProgress(accessToken: String, progress: KavitaProgressUpdate) = error("Not used")
    override suspend fun downloadPageImage(accessToken: String, chapterId: String, pageIndex: Int, apiKey: String?): ByteArray = error("Not used")
    override suspend fun downloadPageThumbnail(accessToken: String, chapterId: String, pageNumber: Int, apiKey: String?): ByteArray = error("Not used")
    override suspend fun downloadCover(accessToken: String, coverUrl: String, apiKey: String?): ByteArray = error("Not used")
    override suspend fun downloadChapter(accessToken: String, chapterId: String, apiKey: String?, destination: File) = error("Not used")
}

private class RecordingStartupStumpApi : StumpApi {
    var refreshSessionCalls = 0
    var getLibrariesCalls = 0

    override suspend fun login(username: String, password: String): StumpSession = error("Not used")

    override suspend fun refreshSession(refreshToken: String): StumpSession {
        refreshSessionCalls += 1
        return StumpSession(
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )
    }

    override suspend fun getLibraries(accessToken: String): List<Library> {
        getLibrariesCalls += 1
        return emptyList()
    }

    override suspend fun getSeries(accessToken: String, libraryId: String): List<StumpSeriesEntry> = error("Not used")
    override suspend fun getSeriesDetail(accessToken: String, series: StumpSeriesEntry): List<StumpMediaCatalogEntry> = error("Not used")
    override suspend fun getProgress(accessToken: String, mediaId: String, itemId: String, pageCount: Int?): ReadingProgress? = error("Not used")
    override suspend fun updatePagedProgress(accessToken: String, mediaId: String, pageIndex: Int) = error("Not used")
    override suspend fun markMediaComplete(accessToken: String, mediaId: String, isComplete: Boolean, pageIndex: Int?) = error("Not used")
    override suspend fun downloadPageImage(accessToken: String, mediaId: String, pageIndex: Int): ByteArray = error("Not used")
    override suspend fun downloadThumbnail(accessToken: String, mediaId: String): ByteArray = error("Not used")
    override suspend fun downloadSeriesThumbnail(accessToken: String, seriesId: String): ByteArray = error("Not used")
    override suspend fun downloadUrl(accessToken: String, url: String): ByteArray = error("Not used")
    override suspend fun downloadMediaFile(accessToken: String, mediaId: String, destination: File) = error("Not used")
}

private class RecordingStartupGrimmoryApi : GrimmoryApi {
    var refreshSessionCalls = 0
    var getLibrariesCalls = 0

    override suspend fun login(username: String, password: String): GrimmorySession = error("Not used")

    override suspend fun refreshSession(refreshToken: String): GrimmorySession {
        refreshSessionCalls += 1
        return GrimmorySession(
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )
    }

    override suspend fun getLibraries(accessToken: String): List<Library> {
        getLibrariesCalls += 1
        return emptyList()
    }

    override suspend fun getBooks(accessToken: String, libraryId: String): List<ReadableItem> = error("Not used")
    override suspend fun getBookEntries(accessToken: String, libraryId: String): List<GrimmoryBookCatalogEntry> = error("Not used")
    override suspend fun getAllBookEntries(accessToken: String): List<GrimmoryBookCatalogEntry> = error("Not used")
    override suspend fun getBookEntry(accessToken: String, bookId: String): GrimmoryBookCatalogEntry? = error("Not used")
    override fun bookContentUrl(bookId: String, bookType: String?): String = error("Not used")
    override fun bookCoverUrl(bookId: String): String = error("Not used")
    override suspend fun updateEpubProgress(accessToken: String, progress: GrimmoryEpubProgressUpdate) = error("Not used")
    override suspend fun updateProgress(accessToken: String, progress: GrimmoryProgressUpdate) = error("Not used")
    override suspend fun downloadBookContent(accessToken: String, bookId: String, destination: File, bookType: String?) = error("Not used")
    override suspend fun downloadBookCover(accessToken: String, bookId: String, coverUrl: String?): ByteArray = error("Not used")
}
