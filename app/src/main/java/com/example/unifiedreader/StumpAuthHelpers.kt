package com.example.unifiedreader

import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.data.stump.StumpUnauthorizedException

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
    private val requestRunner = AccessTokenSessionRequestRunner<StumpStoredSession, StumpApi>(
        credentialStore = credentialStore,
        isUnauthorized = { throwable -> throwable is StumpUnauthorizedException },
        refreshSession = { api: StumpApi, session: StumpStoredSession ->
            val refreshToken = session.refreshToken?.takeIf(String::isNotBlank)
                ?: throw StumpUnauthorizedException()
            val refreshedSession = api.refreshSession(refreshToken)
            session.copy(
                accessToken = refreshedSession.accessToken,
                refreshToken = refreshedSession.refreshToken,
            )
        },
    )

    suspend fun <T> run(
        api: StumpApi,
        session: StumpStoredSession,
        block: suspend (accessToken: String) -> T,
    ): T = requestRunner.run(
        api = api,
        session = session,
    ) { activeSession ->
        block(activeSession.accessToken)
    }
}
