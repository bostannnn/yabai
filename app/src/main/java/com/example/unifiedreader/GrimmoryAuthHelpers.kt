package com.example.unifiedreader

import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.GrimmoryUnauthorizedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class GrimmoryTokenRefreshResult<T>(
    val value: T,
    val accessToken: String,
    val refreshToken: String?,
    val refreshedSession: GrimmorySession?,
)

internal suspend fun <T> GrimmoryApi.runWithTokenRefresh(
    initialAccessToken: String,
    initialRefreshToken: String?,
    block: suspend (accessToken: String) -> T,
): GrimmoryTokenRefreshResult<T> = try {
    GrimmoryTokenRefreshResult(
        value = block(initialAccessToken),
        accessToken = initialAccessToken,
        refreshToken = initialRefreshToken,
        refreshedSession = null,
    )
} catch (exception: GrimmoryUnauthorizedException) {
    val refreshToken = initialRefreshToken?.takeIf(String::isNotBlank)
        ?: throw exception
    val refreshedSession = refreshSession(refreshToken)
    GrimmoryTokenRefreshResult(
        value = block(refreshedSession.accessToken),
        accessToken = refreshedSession.accessToken,
        refreshToken = refreshedSession.refreshToken,
        refreshedSession = refreshedSession,
    )
}

internal class GrimmoryStoredSessionRequestRunner(
    private val credentialStore: GrimmoryCredentialStore,
) {
    private val tokenRefreshMutex = Mutex()
    private var lastRefreshFailure: GrimmoryStoredSessionRefreshFailure? = null

    suspend fun <T> run(
        api: GrimmoryApi,
        session: GrimmoryStoredSession,
        block: suspend (accessToken: String) -> T,
    ): T = runCatching {
        block(session.accessToken)
    }.recoverCatching { throwable ->
        if (throwable !is GrimmoryUnauthorizedException) throw throwable
        val refreshedSession = refreshSessionAfterUnauthorized(
            api = api,
            session = session,
        )
        block(refreshedSession.accessToken)
    }.getOrThrow()

    private suspend fun refreshSessionAfterUnauthorized(
        api: GrimmoryApi,
        session: GrimmoryStoredSession,
    ): GrimmoryStoredSession = tokenRefreshMutex.withLock {
        val currentSession = credentialStore.sessionFlow.value
            ?.takeIf { storedSession -> storedSession.serverId == session.serverId }
            ?: session
        if (currentSession.accessToken != session.accessToken) {
            return@withLock currentSession
        }
        lastRefreshFailure?.takeIf { failure ->
            failure.serverId == currentSession.serverId &&
                failure.refreshToken == currentSession.refreshToken
        }?.let { failure ->
            throw failure.throwable
        }
        runCatching {
            api.refreshSession(currentSession.refreshToken)
        }.fold(
            onSuccess = { refreshedSession ->
                lastRefreshFailure = null
                currentSession.copy(
                    accessToken = refreshedSession.accessToken,
                    refreshToken = refreshedSession.refreshToken,
                ).also { storedSession ->
                    credentialStore.save(storedSession)
                }
            },
            onFailure = { throwable ->
                lastRefreshFailure = GrimmoryStoredSessionRefreshFailure(
                    serverId = currentSession.serverId,
                    refreshToken = currentSession.refreshToken,
                    throwable = throwable,
                )
                throw throwable
            },
        )
    }
}

private data class GrimmoryStoredSessionRefreshFailure(
    val serverId: String,
    val refreshToken: String,
    val throwable: Throwable,
)
