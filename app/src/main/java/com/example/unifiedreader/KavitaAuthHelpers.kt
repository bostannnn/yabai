package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
} catch (exception: KavitaUnauthorizedException) {
    val refreshToken = initialRefreshToken?.takeIf(String::isNotBlank)
        ?: throw exception
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
    private val tokenRefreshMutex = Mutex()

    suspend fun <T> run(
        api: KavitaApi,
        session: KavitaStoredSession,
        block: suspend (accessToken: String, apiKey: String?) -> T,
    ): T = runCatching {
        block(session.accessToken, session.apiKey)
    }.recoverCatching { throwable ->
        if (throwable !is KavitaUnauthorizedException) throw throwable
        val refreshedSession = refreshSessionAfterUnauthorized(
            api = api,
            session = session,
        )
        block(refreshedSession.accessToken, refreshedSession.apiKey)
    }.getOrThrow()

    private suspend fun refreshSessionAfterUnauthorized(
        api: KavitaApi,
        session: KavitaStoredSession,
    ): KavitaStoredSession = tokenRefreshMutex.withLock {
        val currentSession = credentialStore.sessionFlow.value ?: session
        if (currentSession.accessToken != session.accessToken) {
            return@withLock currentSession
        }
        val refreshToken = currentSession.refreshToken?.takeIf(String::isNotBlank)
            ?: throw KavitaUnauthorizedException()
        val refreshedSession = api.refreshSession(
            refreshToken = refreshToken,
            accessToken = currentSession.accessToken,
            apiKey = currentSession.apiKey,
        )
        val storedSession = currentSession.copy(
            accessToken = refreshedSession.accessToken,
            refreshToken = refreshedSession.refreshToken,
            apiKey = refreshedSession.apiKey ?: currentSession.apiKey,
        )
        credentialStore.save(storedSession)
        storedSession
    }
}
