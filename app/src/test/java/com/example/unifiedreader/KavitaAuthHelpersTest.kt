package com.example.unifiedreader

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
}

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
    private val refreshedSession: KavitaSession,
) : KavitaApi {
    val pageRequests = mutableListOf<Pair<String, String?>>()

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession = refreshedSession

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
