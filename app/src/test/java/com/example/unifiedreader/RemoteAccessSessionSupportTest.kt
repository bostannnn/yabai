package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteAccessSessionSupportTest {
    @Test
    fun `runner retries with refreshed session and persists it`() = runTest {
        val initialSession = TestAccessTokenSession(
            displayName = "Server",
            baseUrl = "https://example.com",
            username = "reader",
            accessToken = "stale-access-token",
            refreshToken = "refresh-token",
        )
        val store = TestAccessTokenSessionStore(initialSession)
        val refreshCalls = mutableListOf<String>()
        val runner = AccessTokenSessionRequestRunner<TestAccessTokenSession, Unit>(
            credentialStore = store,
            isUnauthorized = { throwable -> throwable is TestUnauthorizedException },
            refreshSession = { _, session ->
                refreshCalls += session.accessToken
                session.copy(
                    accessToken = "fresh-access-token",
                    refreshToken = "fresh-refresh-token",
                )
            },
        )
        val attemptedTokens = mutableListOf<String>()

        val result = runner.run(
            api = Unit,
            session = initialSession,
        ) { session ->
            attemptedTokens += session.accessToken
            if (session.accessToken == "stale-access-token") {
                throw TestUnauthorizedException()
            }
            session.accessToken
        }

        assertEquals("fresh-access-token", result)
        assertEquals(listOf("stale-access-token"), refreshCalls)
        assertEquals(
            listOf("stale-access-token", "fresh-access-token"),
            attemptedTokens,
        )
        assertEquals("fresh-access-token", store.sessionFlow.value?.accessToken)
        assertEquals("fresh-refresh-token", store.sessionFlow.value?.refreshToken)
    }

    @Test
    fun `runner refreshes only once for concurrent unauthorized requests`() = runTest {
        val initialSession = TestAccessTokenSession(
            displayName = "Server",
            baseUrl = "https://example.com",
            username = "reader",
            accessToken = "stale-access-token",
            refreshToken = "refresh-token",
        )
        val store = TestAccessTokenSessionStore(initialSession)
        val refreshCalls = AtomicInteger(0)
        val staleAttemptsReached = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val staleAttemptCount = AtomicInteger(0)
        val runner = AccessTokenSessionRequestRunner<TestAccessTokenSession, Unit>(
            credentialStore = store,
            isUnauthorized = { throwable -> throwable is TestUnauthorizedException },
            refreshSession = { _, session ->
                refreshCalls.incrementAndGet()
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                session.copy(
                    accessToken = "fresh-access-token",
                    refreshToken = "fresh-refresh-token",
                )
            },
        )

        val requestOne = async {
            runner.run(
                api = Unit,
                session = initialSession,
            ) { session ->
                if (session.accessToken == "stale-access-token") {
                    if (staleAttemptCount.incrementAndGet() == 2) {
                        staleAttemptsReached.complete(Unit)
                    }
                    staleAttemptsReached.await()
                    throw TestUnauthorizedException()
                }
                session.accessToken
            }
        }
        val requestTwo = async {
            runner.run(
                api = Unit,
                session = initialSession,
            ) { session ->
                if (session.accessToken == "stale-access-token") {
                    if (staleAttemptCount.incrementAndGet() == 2) {
                        staleAttemptsReached.complete(Unit)
                    }
                    staleAttemptsReached.await()
                    throw TestUnauthorizedException()
                }
                session.accessToken
            }
        }

        staleAttemptsReached.await()
        refreshStarted.await()
        releaseRefresh.complete(Unit)

        assertEquals("fresh-access-token", requestOne.await())
        assertEquals("fresh-access-token", requestTwo.await())
        assertEquals(1, refreshCalls.get())
        assertEquals("fresh-access-token", store.sessionFlow.value?.accessToken)
    }
}

private data class TestAccessTokenSession(
    override val displayName: String,
    override val baseUrl: String,
    override val username: String,
    override val accessToken: String,
    override val refreshToken: String?,
) : AccessTokenStoredSession {
    override val sourceType: SourceType = SourceType.STUMP
}

private class TestAccessTokenSessionStore(
    initialSession: TestAccessTokenSession?,
) : SourceSessionStore<TestAccessTokenSession> {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<TestAccessTokenSession?> = sessionState

    override suspend fun save(
        session: TestAccessTokenSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

private class TestUnauthorizedException : RuntimeException("Unauthorized")
