package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KAVITA_API_KEY_REJECTED_MESSAGE
import com.example.unifiedreader.data.kavita.KAVITA_REFRESH_TOKEN_REJECTED_MESSAGE
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.core.model.Library
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaAuthHelpersTest {
    @Test
    fun `stored session request runner retries with refreshed api key`() = runBlocking {
        val initialSession = KavitaStoredSession(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "stale-access-token",
            refreshToken = "refresh-token",
            apiKey = "stale-api-key",
        )
        val store = FakeStore(initialSession)
        val api = FakeApi(
            refreshedSession = KavitaSession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
                apiKey = "fresh-api-key",
            ),
        )
        val runner = KavitaStoredSessionRequestRunner(store)

        val result = runner.run(
            api = api,
            session = initialSession,
        ) { accessToken, apiKey ->
            api.pageRequests += accessToken to apiKey
            if (accessToken == "stale-access-token") {
                throw KavitaUnauthorizedException()
            }
            apiKey
        }

        assertEquals("fresh-api-key", result)
        assertEquals(
            listOf(
                "stale-access-token" to "stale-api-key",
                "fresh-access-token" to "fresh-api-key",
            ),
            api.pageRequests,
        )
        assertEquals("fresh-access-token", store.sessionFlow.value?.accessToken)
        assertEquals("fresh-api-key", store.sessionFlow.value?.apiKey)
    }

    @Test
    fun `stored session runner signs in with the api key when the refresh token is turned down`() = runBlocking {
        val initialSession = storedSession(accessToken = "expired-access-token")
        val store = FakeStore(initialSession)
        val api = FakeApi(
            refreshedSession = null,
            keySession = KavitaSession(
                accessToken = "key-access-token",
                refreshToken = "key-refresh-token",
                apiKey = "api-key",
            ),
        )
        val runner = KavitaStoredSessionRequestRunner(store)

        val result = runner.run(api = api, session = initialSession) { accessToken, _ ->
            if (accessToken == "expired-access-token") throw KavitaUnauthorizedException()
            accessToken
        }

        assertEquals("key-access-token", result)
        assertEquals(listOf("api-key"), api.apiKeyLogins)
        assertEquals("key-refresh-token", store.sessionFlow.value?.refreshToken)
    }

    @Test
    fun `stored session runner renews a token past half its life before the request`() = runBlocking {
        val now = 10_000_000_000L
        val agingToken = testJwt(
            issuedAtEpochSeconds = now / 1_000L - 2 * 86_400L,
            expiresAtEpochSeconds = now / 1_000L + 86_400L,
        )
        val initialSession = storedSession(accessToken = agingToken)
        val store = FakeStore(initialSession)
        val api = FakeApi(
            refreshedSession = KavitaSession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
                apiKey = null,
            ),
        )
        val runner = KavitaStoredSessionRequestRunner(store, nowEpochMillis = { now })

        val result = runner.run(api = api, session = initialSession) { accessToken, _ -> accessToken }

        assertEquals("fresh-access-token", result)
        assertEquals(1, api.refreshCount)
        assertEquals("fresh-access-token", store.sessionFlow.value?.accessToken)
        assertEquals("api-key", store.sessionFlow.value?.apiKey)
    }

    @Test
    fun `early renewal that fails keeps the token and waits before trying again`() = runBlocking {
        var now = 10_000_000_000L
        val agingToken = testJwt(
            issuedAtEpochSeconds = now / 1_000L - 2 * 86_400L,
            expiresAtEpochSeconds = now / 1_000L + 86_400L,
        )
        val initialSession = storedSession(accessToken = agingToken)
        val store = FakeStore(initialSession)
        val api = FakeApi(refreshedSession = null, refreshFailure = IllegalStateException("Server error"))
        val runner = KavitaStoredSessionRequestRunner(store, nowEpochMillis = { now })

        val first = runner.run(api = api, session = initialSession) { accessToken, _ -> accessToken }
        now += 10 * 60 * 1000L
        val second = runner.run(api = api, session = initialSession) { accessToken, _ -> accessToken }
        now += 60 * 60 * 1000L
        runner.run(api = api, session = initialSession) { accessToken, _ -> accessToken }

        assertEquals(agingToken, first)
        assertEquals(agingToken, second)
        assertEquals(2, api.refreshCount)
        assertEquals(agingToken, store.sessionFlow.value?.accessToken)
    }
}

private fun storedSession(
    accessToken: String,
): KavitaStoredSession = KavitaStoredSession(
    displayName = "Kavita",
    baseUrl = "https://kavita.example",
    username = "reader",
    accessToken = accessToken,
    refreshToken = "refresh-token",
    apiKey = "api-key",
)

private class FakeStore(
    initialSession: KavitaStoredSession?,
) : SourceSessionStore<KavitaStoredSession> {
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

private class FakeApi(
    // Null turns the refresh token down.
    private val refreshedSession: KavitaSession?,
    private val refreshFailure: Throwable? = null,
    private val keySession: KavitaSession? = null,
) : KavitaApi {
    val pageRequests = mutableListOf<Pair<String, String?>>()
    val apiKeyLogins = mutableListOf<String>()
    var refreshCount = 0

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession {
        refreshCount += 1
        refreshFailure?.let { throw it }
        return refreshedSession ?: throw KavitaUnauthorizedException(KAVITA_REFRESH_TOKEN_REJECTED_MESSAGE)
    }

    override suspend fun loginWithApiKey(
        apiKey: String,
    ): KavitaSession {
        apiKeyLogins += apiKey
        return keySession ?: throw KavitaUnauthorizedException(KAVITA_API_KEY_REJECTED_MESSAGE)
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = emptyList()

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
    ): KavitaChapterInfoDto = error("Not needed in test.")

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

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
}
