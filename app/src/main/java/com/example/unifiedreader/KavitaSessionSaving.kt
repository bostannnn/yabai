package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KavitaSession

/**
 * Writes a pair of tokens renewed during a run onto the saved session as it is now, when that is still
 * the account the run used ([runSession]), so a pair the reader or the covers saved meanwhile only
 * changes the tokens, not the rest. Nothing is saved once the server was signed out or swapped.
 */
internal suspend fun SourceSessionStore<KavitaStoredSession>.saveRenewedTokens(
    renewed: KavitaSession,
    runSession: KavitaStoredSession,
) {
    val currentSession = sessionFlow.value?.takeIf { session ->
        session.matchesServerIdentity(
            baseUrl = runSession.baseUrl,
            backupBaseUrl = runSession.backupBaseUrl,
            username = runSession.username,
        )
    } ?: return
    save(currentSession.withTokens(renewed))
}

/**
 * What a connect or sync on the saved sign-in does with each pair it renews: saves it at once. A run
 * that signs in with the password saves its pair when it is done, as a new account it may be.
 */
internal fun KavitaCredentialStore.renewedTokensSaver(
    preparation: KavitaSyncReady,
): suspend (KavitaSession) -> Unit {
    val runSession = preparation.fallbackSession?.takeIf { preparation.request.password == null }
        ?: return {}
    return { renewed -> saveRenewedTokens(renewed, runSession) }
}

/**
 * Saves the sign-in once a connect or sync is done. A new sign-in with the password is saved as it
 * came. A run on the saved sign-in already saved each pair it renewed, so it only brings the saved
 * session's name and addresses up to date: writing back the pair it started with would undo a
 * renewal the reader or the covers made while it ran.
 */
internal suspend fun KavitaCredentialStore.saveAfterRun(
    preparation: KavitaSyncReady,
    session: KavitaSession?,
    activeBaseUrl: String?,
) {
    persistRemoteSession(
        sessionStore = this,
        session = session?.takeIf { preparation.request.password != null },
        fallbackSession = preparation.fallbackSession,
        displayName = preparation.request.displayName,
        baseUrl = preparation.request.baseUrl,
        backupBaseUrl = preparation.request.backupBaseUrl,
        activeBaseUrl = activeBaseUrl ?: preparation.request.activeBaseUrl,
        username = preparation.request.username,
        toStoredSession = { activeSession, displayName, baseUrl, backupBaseUrl, activeBaseUrl, username ->
            activeSession.toStoredSession(
                displayName = displayName,
                baseUrl = baseUrl,
                backupBaseUrl = backupBaseUrl,
                activeBaseUrl = activeBaseUrl,
                username = username,
            )
        },
        restoreFallbackSession = { savedSession, displayName, baseUrl, backupBaseUrl, activeBaseUrl, username ->
            savedSession.copy(
                displayName = displayName,
                baseUrl = baseUrl,
                backupBaseUrl = backupBaseUrl,
                activeBaseUrl = activeBaseUrl,
                username = username,
            )
        },
    )
}
