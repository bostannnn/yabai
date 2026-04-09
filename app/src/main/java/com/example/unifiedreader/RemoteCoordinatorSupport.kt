package com.example.unifiedreader

internal data class RemoteConnectionInput<Session : StoredSourceSession>(
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val password: String?,
    val savedSession: Session?,
)

internal sealed interface RemoteConnectionInputPreparation<out Connection, out Session : StoredSourceSession> {
    data class Invalid<Connection>(
        val connection: Connection,
    ) : RemoteConnectionInputPreparation<Connection, Nothing>

    data class Ready<Session : StoredSourceSession>(
        val input: RemoteConnectionInput<Session>,
    ) : RemoteConnectionInputPreparation<Nothing, Session>
}

internal sealed interface StoredSessionRefreshInputPreparation<out Connection, out Session : StoredSourceSession> {
    data class Invalid<Connection>(
        val connection: Connection,
    ) : StoredSessionRefreshInputPreparation<Connection, Nothing>

    data object MissingSession : StoredSessionRefreshInputPreparation<Nothing, Nothing>

    data class Ready<Connection, Session : StoredSourceSession>(
        val connection: Connection,
        val session: Session,
    ) : StoredSessionRefreshInputPreparation<Connection, Session>
}

internal fun <Connection, Session : StoredSourceSession> refreshedRemoteConnectionState(
    connection: Connection,
    baseUrl: String,
    username: String,
    copyHasStoredSession: (Connection, Boolean) -> Connection,
    matchingStoredSession: (String, String) -> Session?,
): Connection {
    val normalizedBaseUrl = normalizeServerBaseUrl(baseUrl) ?: return copyHasStoredSession(connection, false)
    val normalizedUsername = username.trim()
    if (normalizedUsername.isBlank()) {
        return copyHasStoredSession(connection, false)
    }
    return copyHasStoredSession(
        connection,
        matchingStoredSession(normalizedBaseUrl, normalizedUsername) != null,
    )
}

internal fun <Connection, Session : StoredSourceSession> prepareRemoteConnectInput(
    connection: Connection,
    displayName: String,
    baseUrl: String,
    username: String,
    password: String,
    invalidConnection: (String) -> Connection,
    matchingStoredSession: (String, String) -> Session?,
    missingRequiredFieldsMessage: String,
    missingCredentialMessage: String,
    validateSavedSession: (savedSession: Session?, password: String?) -> String? = { _, _ -> null },
): RemoteConnectionInputPreparation<Connection, Session> {
    val normalizedBaseUrl = normalizeServerBaseUrl(baseUrl)
        ?: return RemoteConnectionInputPreparation.Invalid(
            connection = invalidConnection("Enter a server URL that starts with http:// or https://."),
        )
    val normalizedDisplayName = displayName.trim()
    val normalizedUsername = username.trim()
    if (normalizedDisplayName.isBlank() || normalizedUsername.isBlank()) {
        return RemoteConnectionInputPreparation.Invalid(
            connection = invalidConnection(missingRequiredFieldsMessage),
        )
    }
    val normalizedPassword = password.takeIf(String::isNotBlank)
    val savedSession = matchingStoredSession(
        normalizedBaseUrl,
        normalizedUsername,
    ).takeIf { normalizedPassword == null }
    if (normalizedPassword == null && savedSession == null) {
        return RemoteConnectionInputPreparation.Invalid(
            connection = invalidConnection(missingCredentialMessage),
        )
    }
    val validationMessage = validateSavedSession(savedSession, normalizedPassword)
    if (validationMessage != null) {
        return RemoteConnectionInputPreparation.Invalid(
            connection = invalidConnection(validationMessage),
        )
    }
    return RemoteConnectionInputPreparation.Ready(
        input = RemoteConnectionInput(
            displayName = normalizedDisplayName,
            baseUrl = normalizedBaseUrl,
            username = normalizedUsername,
            password = normalizedPassword,
            savedSession = savedSession,
        ),
    )
}

internal fun <Connection, Session : StoredSourceSession> prepareRemoteRefreshInput(
    connection: Connection,
    storedSession: Session?,
    restoreConnection: (Connection, Session) -> Connection,
    refreshedConnectionState: (Connection) -> Connection,
    invalidConnection: (Connection, String) -> Connection,
    validateStoredSession: (Session) -> String? = { null },
): StoredSessionRefreshInputPreparation<Connection, Session> {
    val session = storedSession ?: return StoredSessionRefreshInputPreparation.MissingSession
    val restoredConnection = restoreConnection(connection, session)
    val validationMessage = validateStoredSession(session)
    if (validationMessage != null) {
        return StoredSessionRefreshInputPreparation.Invalid(
            connection = invalidConnection(restoredConnection, validationMessage),
        )
    }
    return StoredSessionRefreshInputPreparation.Ready(
        connection = refreshedConnectionState(restoredConnection),
        session = session,
    )
}

internal suspend fun <StoredSession : StoredSourceSession, Session> persistRemoteSession(
    sessionStore: SourceSessionStore<StoredSession>,
    session: Session?,
    fallbackSession: StoredSession?,
    displayName: String,
    baseUrl: String,
    username: String,
    toStoredSession: (Session, String, String, String) -> StoredSession,
    restoreFallbackSession: (StoredSession, String, String, String) -> StoredSession,
) {
    val storedSession = session?.let { activeSession ->
        toStoredSession(
            activeSession,
            displayName,
            baseUrl,
            username,
        )
    } ?: fallbackSession?.let { savedSession ->
        restoreFallbackSession(
            savedSession,
            displayName,
            baseUrl,
            username,
        )
    }
    if (storedSession != null) {
        sessionStore.save(storedSession)
    }
}
