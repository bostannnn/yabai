package com.example.unifiedreader

import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.GrimmoryUnauthorizedException

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
    private val credentialStore: SourceSessionStore<GrimmoryStoredSession>,
) {
    private val requestRunner = AccessTokenSessionRequestRunner<GrimmoryStoredSession, GrimmoryApi>(
        credentialStore = credentialStore,
        isUnauthorized = { throwable -> throwable is GrimmoryUnauthorizedException },
        refreshSession = { api: GrimmoryApi, session: GrimmoryStoredSession ->
            val refreshedSession = api.refreshSession(session.refreshToken)
            session.copy(
                accessToken = refreshedSession.accessToken,
                refreshToken = refreshedSession.refreshToken,
            )
        },
    )

    suspend fun <T> run(
        api: GrimmoryApi,
        session: GrimmoryStoredSession,
        block: suspend (accessToken: String) -> T,
    ): T = requestRunner.run(
        api = api,
        session = session,
    ) { activeSession ->
        block(activeSession.accessToken)
    }
}
