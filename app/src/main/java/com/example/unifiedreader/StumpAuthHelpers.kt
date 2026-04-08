package com.example.unifiedreader

import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.data.stump.StumpUnauthorizedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class StumpTokenRefreshResult<T>(
    val value: T,
    val accessToken: String,
    val refreshToken: String?,
    val refreshedSession: StumpSession?,
)

internal suspend fun <T> StumpApi.runWithTokenRefresh(
    initialAccessToken: String,
    initialRefreshToken: String?,
    block: suspend (accessToken: String) -> T,
): StumpTokenRefreshResult<T> = try {
    StumpTokenRefreshResult(
        value = block(initialAccessToken),
        accessToken = initialAccessToken,
        refreshToken = initialRefreshToken,
        refreshedSession = null,
    )
} catch (exception: StumpUnauthorizedException) {
    val refreshToken = initialRefreshToken?.takeIf(String::isNotBlank)
        ?: throw exception
    val refreshedSession = refreshSession(refreshToken)
    StumpTokenRefreshResult(
        value = block(refreshedSession.accessToken),
        accessToken = refreshedSession.accessToken,
        refreshToken = refreshedSession.refreshToken,
        refreshedSession = refreshedSession,
    )
}

internal class StumpStoredSessionRequestRunner(
    private val credentialStore: SourceSessionStore<StumpStoredSession>,
) {
    private val tokenRefreshMutex = Mutex()

    suspend fun <T> run(
        api: StumpApi,
        session: StumpStoredSession,
        block: suspend (accessToken: String) -> T,
    ): T = runCatching {
        block(session.accessToken)
    }.recoverCatching { throwable ->
        if (throwable !is StumpUnauthorizedException) throw throwable
        val refreshedSession = refreshSessionAfterUnauthorized(
            api = api,
            session = session,
        )
        block(refreshedSession.accessToken)
    }.getOrThrow()

    private suspend fun refreshSessionAfterUnauthorized(
        api: StumpApi,
        session: StumpStoredSession,
    ): StumpStoredSession = tokenRefreshMutex.withLock {
        val currentSession = credentialStore.sessionFlow.value ?: session
        if (currentSession.accessToken != session.accessToken) {
            return@withLock currentSession
        }
        val refreshToken = currentSession.refreshToken?.takeIf(String::isNotBlank)
            ?: throw StumpUnauthorizedException()
        val refreshedSession = api.refreshSession(refreshToken)
        val storedSession = currentSession.copy(
            accessToken = refreshedSession.accessToken,
            refreshToken = refreshedSession.refreshToken,
        )
        credentialStore.save(storedSession)
        storedSession
    }
}
