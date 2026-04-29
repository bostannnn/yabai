package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteServerAddressFailoverTest {
    @Test
    fun `run with server address failover retries backup after network failure`() = runTest {
        val attempts = mutableListOf<String>()

        val result = runWithServerAddressFailover(
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = null,
        ) { baseUrl ->
            attempts += baseUrl
            if (baseUrl == "https://primary.example.com") {
                throw IOException("primary unavailable")
            }
            "ok"
        }

        assertEquals(
            listOf("https://primary.example.com", "https://backup.example.com"),
            attempts,
        )
        assertEquals("ok", result.value)
        assertEquals("https://backup.example.com", result.activeBaseUrl)
    }

    @Test
    fun `run with server address failover tries primary before persisted active backup`() = runTest {
        val attempts = mutableListOf<String>()

        val result = runWithServerAddressFailover(
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = "https://backup.example.com",
        ) { baseUrl ->
            attempts += baseUrl
            "ok"
        }

        assertEquals(listOf("https://primary.example.com"), attempts)
        assertEquals("ok", result.value)
        assertEquals("https://primary.example.com", result.activeBaseUrl)
    }

    @Test
    fun `run with stored session address failover persists the working address`() = runTest {
        val initialSession = FailoverTestSession(
            displayName = "Server",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            username = "reader",
        )
        val store = FailoverTestSessionStore(initialSession)

        val result = runWithStoredSessionAddressFailover(
            sessionStore = store,
            session = initialSession,
            createApi = ::FailoverTestApi,
            withActiveBaseUrl = { session, activeBaseUrl ->
                session.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, _ ->
            if (api.baseUrl == "https://primary.example.com") {
                throw IOException("primary unavailable")
            }
            api.baseUrl
        }

        assertEquals("https://backup.example.com", result)
        assertEquals("https://backup.example.com", store.sessionFlow.value?.activeBaseUrl)
    }

    @Test
    fun `rewrite remote asset url uses active server for rotated host urls`() {
        val session = FailoverTestSession(
            displayName = "Server",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = "https://tailscale.example.ts.net",
            username = "reader",
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            "https://tailscale.example.ts.net/api/Image/chapter-cover?chapterId=21&apiKey=secret",
            rewriteRemoteAssetUrlForActiveServer(
                url = "https://tailscale-old.example.ts.net/api/Image/chapter-cover?chapterId=21&apiKey=secret",
                session = session,
            ),
        )
        assertEquals(
            "file:///tmp/cover.jpg",
            rewriteRemoteAssetUrlForActiveServer(
                url = "file:///tmp/cover.jpg",
                session = session,
            ),
        )
        assertNull(rewriteRemoteAssetUrlForActiveServer(url = null, session = session))
    }

    @Test
    fun `rewrite remote asset url preserves active server subpath`() {
        val session = FailoverTestSession(
            displayName = "Server",
            baseUrl = "https://primary.example.com/kavita",
            backupBaseUrl = "https://backup.example.com/kavita",
            activeBaseUrl = "https://tailscale.example.ts.net/kavita",
            username = "reader",
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            "https://tailscale.example.ts.net/kavita/api/Image/chapter-cover?chapterId=21&apiKey=secret",
            rewriteRemoteAssetUrlForActiveServer(
                url = "https://primary.example.com/kavita/api/Image/chapter-cover?chapterId=21&apiKey=secret",
                session = session,
            ),
        )
    }

    @Test
    fun `rewrite remote asset url swaps configured subpath when active server differs`() {
        val session = FailoverTestSession(
            displayName = "Server",
            baseUrl = "https://primary.example.com/kavita",
            backupBaseUrl = "https://backup.example.com/reader",
            activeBaseUrl = "https://tailscale.example.ts.net/reader",
            username = "reader",
            sourceType = SourceType.GRIMMORY,
        )

        assertEquals(
            "https://tailscale.example.ts.net/reader/api/v1/media/book/12/thumbnail",
            rewriteRemoteAssetUrlForActiveServer(
                url = "https://primary.example.com/kavita/api/v1/media/book/12/thumbnail",
                session = session,
            ),
        )
    }

    @Test
    fun `rewrite remote asset url leaves external absolute urls untouched`() {
        val session = FailoverTestSession(
            displayName = "Server",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = "https://tailscale.example.ts.net",
            username = "reader",
            sourceType = SourceType.GRIMMORY,
        )

        assertEquals(
            "https://cdn.example.com/cover.jpg",
            rewriteRemoteAssetUrlForActiveServer(
                url = "https://cdn.example.com/cover.jpg",
                session = session,
            ),
        )
    }
}

private data class FailoverTestApi(
    val baseUrl: String,
)

private data class FailoverTestSession(
    override val displayName: String,
    override val baseUrl: String,
    override val backupBaseUrl: String? = null,
    override val activeBaseUrl: String? = null,
    override val username: String,
    override val sourceType: SourceType = SourceType.KAVITA,
) : StoredSourceSession {
}

private class FailoverTestSessionStore(
    initialSession: FailoverTestSession? = null,
) : SourceSessionStore<FailoverTestSession> {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<FailoverTestSession?> = sessionState

    override suspend fun save(
        session: FailoverTestSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}
