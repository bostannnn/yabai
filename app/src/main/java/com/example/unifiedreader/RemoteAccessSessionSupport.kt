package com.example.unifiedreader

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface AccessTokenStoredSession : StoredSourceSession {
    val accessToken: String
    val refreshToken: String?
}

internal class AccessTokenSessionRequestRunner<Session : AccessTokenStoredSession, Api>(
    private val credentialStore: SourceSessionStore<Session>,
    private val isUnauthorized: (Throwable) -> Boolean,
    private val refreshSession: suspend (api: Api, session: Session) -> Session,
) {
    private val tokenRefreshMutex = Mutex()

    suspend inline fun <T> run(
        api: Api,
        session: Session,
        crossinline block: suspend (session: Session) -> T,
    ): T = runCatching {
        block(session)
    }.recoverCatching { throwable ->
        if (!isUnauthorized(throwable)) throw throwable
        block(
            refreshSessionAfterUnauthorized(
                api = api,
                session = session,
            ),
        )
    }.getOrThrow()

    private suspend fun refreshSessionAfterUnauthorized(
        api: Api,
        session: Session,
    ): Session = tokenRefreshMutex.withLock {
        val currentSession = credentialStore.sessionFlow.value ?: session
        if (currentSession.accessToken != session.accessToken) {
            return@withLock currentSession
        }
        refreshSession(api, currentSession).also { refreshedSession ->
            credentialStore.save(refreshedSession)
        }
    }
}
