package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.kavita.kavitaAccessTokenWantsRenewal
import com.example.unifiedreader.data.kavita.renewSession
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The tokens one Kavita run (a connect, a sync, a progress flush) works with. Each request takes the
 * current pair; a request the server turns down renews it once for the requests after it, and
 * [onRenewed] hears of each new pair at once, so a run that stops halfway still keeps the new sign-in.
 */
internal class KavitaRunTokens(
    private val api: KavitaApi,
    initial: KavitaSession,
    // A sign-in with the password made for this run, which the run's result saves.
    private val isNewSignIn: Boolean = false,
    private val onRenewed: suspend (KavitaSession) -> Unit = {},
) {
    private val renewalLock = Mutex()

    @Volatile
    var current: KavitaSession = initial
        private set

    @Volatile
    private var renewed: KavitaSession? = null

    /** The pair the run's result carries to be saved: a new sign-in or a renewal, else null. */
    val sessionToSave: KavitaSession?
        get() = if (isNewSignIn) current else renewed

    suspend fun <T> run(
        block: suspend (accessToken: String, apiKey: String?) -> T,
    ): T {
        val used = current
        return try {
            block(used.accessToken, used.apiKey)
        } catch (_: KavitaUnauthorizedException) {
            val fresh = renewAfterRejection(used)
            block(fresh.accessToken, fresh.apiKey)
        }
    }

    /** Renews a pair past half its life; one that fails to renew stays as it is and is still good. */
    suspend fun renewIfAging(
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        if (!kavitaAccessTokenWantsRenewal(current.accessToken, nowEpochMillis)) return
        try {
            renewalLock.withLock {
                if (kavitaAccessTokenWantsRenewal(current.accessToken, nowEpochMillis)) renew(current)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Renewed when the server turns the token down instead.
        }
    }

    private suspend fun renewAfterRejection(
        rejected: KavitaSession,
    ): KavitaSession = renewalLock.withLock {
        if (current.accessToken != rejected.accessToken) current else renew(current)
    }

    private suspend fun renew(
        from: KavitaSession,
    ): KavitaSession = api.renewSession(
        accessToken = from.accessToken,
        refreshToken = from.refreshToken,
        apiKey = from.apiKey,
    ).also { fresh ->
        current = fresh
        renewed = fresh
        onRenewed(fresh)
    }
}

internal class KavitaStoredSessionRequestRunner(
    private val credentialStore: SourceSessionStore<KavitaStoredSession>,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val requestRunner = AccessTokenSessionRequestRunner<KavitaStoredSession, KavitaApi>(
        credentialStore = credentialStore,
        isUnauthorized = { throwable -> throwable is KavitaUnauthorizedException },
        refreshSession = { api: KavitaApi, session: KavitaStoredSession ->
            session.withTokens(
                api.renewSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    apiKey = session.apiKey,
                ),
            )
        },
        wantsEarlyRenewal = { session ->
            kavitaAccessTokenWantsRenewal(session.accessToken, nowEpochMillis())
        },
        nowEpochMillis = nowEpochMillis,
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

internal fun KavitaStoredSession.toKavitaSession(): KavitaSession = KavitaSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    apiKey = apiKey,
)

/** This saved session with a renewed pair, keeping what the renewal didn't send. */
internal fun KavitaStoredSession.withTokens(
    session: KavitaSession,
): KavitaStoredSession = copy(
    accessToken = session.accessToken,
    refreshToken = session.refreshToken ?: refreshToken,
    apiKey = session.apiKey ?: apiKey,
)
