package com.example.unifiedreader

import kotlin.coroutines.cancellation.CancellationException
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
    // A session worth renewing before its access token runs out. A renewal that fails keeps it as it
    // is, and another isn't tried for an hour, so a device clock ahead of the server can't renew on
    // every request.
    private val wantsEarlyRenewal: (Session) -> Boolean = { false },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val tokenRefreshMutex = Mutex()

    @Volatile
    private var lastEarlyRenewalAtEpochMillis: Long? = null

    suspend inline fun <T> run(
        api: Api,
        session: Session,
        crossinline block: suspend (session: Session) -> T,
    ): T {
        val activeSession = renewEarlyIfDue(
            api = api,
            session = session,
        )
        return runCatching {
            block(activeSession)
        }.recoverCatching { throwable ->
            if (!isUnauthorized(throwable)) throw throwable
            block(
                refreshSessionAfterUnauthorized(
                    api = api,
                    session = activeSession,
                ),
            )
        }.getOrThrow()
    }

    private suspend fun renewEarlyIfDue(
        api: Api,
        session: Session,
    ): Session {
        if (!wantsEarlyRenewal(session)) return session
        val now = nowEpochMillis()
        val lastTriedAt = lastEarlyRenewalAtEpochMillis
        if (lastTriedAt != null && now - lastTriedAt < EARLY_RENEWAL_RETRY_AFTER_MILLIS) return session
        return tokenRefreshMutex.withLock {
            val currentSession = credentialStore.sessionFlow.value ?: session
            if (currentSession.accessToken != session.accessToken || !wantsEarlyRenewal(currentSession)) {
                return@withLock currentSession
            }
            lastEarlyRenewalAtEpochMillis = now
            try {
                refreshSession(api, currentSession).also { refreshedSession ->
                    credentialStore.save(refreshedSession)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Still good until it runs out; then the server turns it down and it renews below.
                currentSession
            }
        }
    }

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

private const val EARLY_RENEWAL_RETRY_AFTER_MILLIS = 60 * 60 * 1000L
