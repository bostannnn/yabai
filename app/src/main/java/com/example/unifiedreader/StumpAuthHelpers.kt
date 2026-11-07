package com.example.unifiedreader

import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.data.stump.StumpUnauthorizedException

internal typealias StumpTokenRefreshResult<T> = TokenRefreshResult<T, StumpSession>

internal val StumpApi.tokenRenewal: TokenRenewal<StumpSession>
    get() = TokenRenewal(
        isUnauthorized = { throwable -> throwable is StumpUnauthorizedException },
        refreshSession = ::refreshSession,
        tokensOf = { session -> session.accessToken to session.refreshToken },
    )

internal suspend fun <T> StumpApi.runWithTokenRefresh(
    initialAccessToken: String,
    initialRefreshToken: String?,
    block: suspend (accessToken: String) -> T,
): StumpTokenRefreshResult<T> = tokenRenewal.run(initialAccessToken, initialRefreshToken, block)

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
