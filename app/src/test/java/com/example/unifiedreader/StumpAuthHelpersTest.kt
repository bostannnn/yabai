package com.example.unifiedreader

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.data.stump.StumpUnauthorizedException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StumpAuthHelpersTest {
    @Test
    fun `stored session request runner retries with refreshed token`() = runBlocking {
        val initialSession = StumpStoredSession(
            displayName = "Stump",
            baseUrl = "https://stump.example",
            username = "reader",
            accessToken = "stale-access-token",
            refreshToken = "refresh-token",
        )
        val store = FakeStumpStore(initialSession)
        val api = FakeStumpApi(
            refreshedSession = StumpSession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            ),
        )
        val runner = StumpStoredSessionRequestRunner(store)

        val result = runner.run(
            api = api,
            session = initialSession,
        ) { accessToken ->
            api.pageRequests += accessToken
            if (accessToken == "stale-access-token") {
                throw StumpUnauthorizedException()
            }
            accessToken
        }

        assertEquals("fresh-access-token", result)
        assertEquals(
            listOf(
                "stale-access-token",
                "fresh-access-token",
            ),
            api.pageRequests,
        )
        assertEquals("fresh-access-token", store.sessionFlow.value?.accessToken)
        assertEquals("fresh-refresh-token", store.sessionFlow.value?.refreshToken)
    }
}

private class FakeStumpStore(
    initialSession: StumpStoredSession?,
) : SourceSessionStore<StumpStoredSession> {
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

private class FakeStumpApi(
    private val refreshedSession: StumpSession,
) : StumpApi {
    val pageRequests = mutableListOf<String>()

    override suspend fun login(
        username: String,
        password: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession = refreshedSession

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = emptyList()

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
    ): ByteArray = error("Not needed in test.")

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
}
