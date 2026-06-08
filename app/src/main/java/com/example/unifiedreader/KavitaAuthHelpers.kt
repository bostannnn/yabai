package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KAVITA_REFRESH_TOKEN_MISSING_MESSAGE
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException

internal data class KavitaTokenRefreshResult<T>(
    val value: T,
    val accessToken: String,
    val refreshToken: String?,
    val refreshedSession: KavitaSession?,
)

internal suspend fun <T> KavitaApi.runWithTokenRefresh(
    initialAccessToken: String,
    initialRefreshToken: String?,
    initialApiKey: String?,
    block: suspend (accessToken: String, apiKey: String?) -> T,
): KavitaTokenRefreshResult<T> = try {
    KavitaTokenRefreshResult(
        value = block(initialAccessToken, initialApiKey),
        accessToken = initialAccessToken,
        refreshToken = initialRefreshToken,
        refreshedSession = null,
    )
} catch (_: KavitaUnauthorizedException) {
    val refreshToken = initialRefreshToken?.takeIf(String::isNotBlank)
        ?: throw KavitaUnauthorizedException(KAVITA_REFRESH_TOKEN_MISSING_MESSAGE)
    val refreshedSession = refreshSession(
        refreshToken = refreshToken,
        accessToken = initialAccessToken,
        apiKey = initialApiKey,
    )
    KavitaTokenRefreshResult(
        value = block(refreshedSession.accessToken, refreshedSession.apiKey),
        accessToken = refreshedSession.accessToken,
        refreshToken = refreshedSession.refreshToken,
        refreshedSession = refreshedSession,
    )
}

internal class KavitaStoredSessionRequestRunner(
    private val credentialStore: SourceSessionStore<KavitaStoredSession>,
) {
    private val requestRunner = AccessTokenSessionRequestRunner<KavitaStoredSession, KavitaApi>(
        credentialStore = credentialStore,
        isUnauthorized = { throwable -> throwable is KavitaUnauthorizedException },
        refreshSession = { api: KavitaApi, session: KavitaStoredSession ->
            val refreshToken = session.refreshToken?.takeIf(String::isNotBlank)
                ?: throw KavitaUnauthorizedException(KAVITA_REFRESH_TOKEN_MISSING_MESSAGE)
            val refreshedSession = api.refreshSession(
                refreshToken = refreshToken,
                accessToken = session.accessToken,
                apiKey = session.apiKey,
            )
            session.copy(
                accessToken = refreshedSession.accessToken,
                refreshToken = refreshedSession.refreshToken,
                apiKey = refreshedSession.apiKey ?: session.apiKey,
            )
        },
    )

    suspend fun <T> run(
        api: KavitaApi,
        session: KavitaStoredSession,
        block: suspend (accessToken: String, apiKey: String?) -> T,
    ): T = requestRunner.run(
        api = api,
        session = session,
    ) { activeSession ->
        block(activeSession.accessToken, activeSession.apiKey)
    }
}
