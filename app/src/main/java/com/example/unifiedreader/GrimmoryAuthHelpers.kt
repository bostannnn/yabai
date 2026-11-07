package com.example.unifiedreader

import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.GrimmoryUnauthorizedException

internal typealias GrimmoryTokenRefreshResult<T> = TokenRefreshResult<T, GrimmorySession>

internal val GrimmoryApi.tokenRenewal: TokenRenewal<GrimmorySession>
    get() = TokenRenewal(
        isUnauthorized = { throwable -> throwable is GrimmoryUnauthorizedException },
        refreshSession = ::refreshSession,
        tokensOf = { session -> session.accessToken to session.refreshToken },
    )

internal suspend fun <T> GrimmoryApi.runWithTokenRefresh(
    initialAccessToken: String,
    initialRefreshToken: String?,
    block: suspend (accessToken: String) -> T,
): GrimmoryTokenRefreshResult<T> = tokenRenewal.run(initialAccessToken, initialRefreshToken, block)

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
