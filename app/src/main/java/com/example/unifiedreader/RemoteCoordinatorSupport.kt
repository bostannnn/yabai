package com.example.unifiedreader

internal data class RemoteConnectionInput<Session : StoredSourceSession>(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String?,
    val activeBaseUrl: String?,
    val username: String,
    val password: String?,
    val savedSession: Session?,
    val cleartextWarningMessage: String?,
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
        val cleartextWarningMessage: String?,
    ) : StoredSessionRefreshInputPreparation<Connection, Session>
}

internal fun <Connection, Session : StoredSourceSession> refreshedRemoteConnectionState(
    connection: Connection,
    baseUrl: String,
    backupBaseUrl: String,
    username: String,
    copyHasStoredSession: (Connection, Boolean) -> Connection,
    matchingStoredSession: (String, String?, String) -> Session?,
): Connection {
    val normalizedBaseUrl = normalizeServerBaseUrl(baseUrl) ?: return copyHasStoredSession(connection, false)
    val normalizedBackupBaseUrl = normalizeOptionalServerBaseUrl(backupBaseUrl)
    val normalizedUsername = username.trim()
    if (normalizedUsername.isBlank()) {
        return copyHasStoredSession(connection, false)
    }
    return copyHasStoredSession(
        connection,
        matchingStoredSession(normalizedBaseUrl, normalizedBackupBaseUrl, normalizedUsername) != null,
    )
}

internal fun <Connection, Session : StoredSourceSession> refreshedRemoteConnectionState(
    connection: Connection,
    baseUrl: String,
    username: String,
    copyHasStoredSession: (Connection, Boolean) -> Connection,
    matchingStoredSession: (String, String) -> Session?,
): Connection = refreshedRemoteConnectionState(
    connection = connection,
    baseUrl = baseUrl,
    backupBaseUrl = "",
    username = username,
    copyHasStoredSession = copyHasStoredSession,
    matchingStoredSession = { normalizedBaseUrl, _, normalizedUsername ->
        matchingStoredSession(normalizedBaseUrl, normalizedUsername)
    },
)

internal fun <Connection, Session : StoredSourceSession> prepareRemoteConnectInput(
    connection: Connection,
    displayName: String,
    baseUrl: String,
    backupBaseUrl: String,
    username: String,
    password: String,
    invalidConnection: (String) -> Connection,
    matchingStoredSession: (String, String?, String) -> Session?,
    missingRequiredFieldsMessage: String,
    missingCredentialMessage: String,
    validateSavedSession: (savedSession: Session?, password: String?) -> String? = { _, _ -> null },
): RemoteConnectionInputPreparation<Connection, Session> {
    val validatedBaseUrl = when (val validation = validateServerBaseUrl(baseUrl)) {
        is ServerBaseUrlValidation.Valid -> validation
        is ServerBaseUrlValidation.Invalid -> {
            return RemoteConnectionInputPreparation.Invalid(
                connection = invalidConnection(validation.message),
            )
        }
    }
    val validatedBackupBaseUrl = when (val validation = validateOptionalServerBaseUrl(backupBaseUrl)) {
        is ServerBaseUrlValidation.Valid -> validation
        is ServerBaseUrlValidation.Invalid -> {
            return RemoteConnectionInputPreparation.Invalid(
                connection = invalidConnection(validation.message),
            )
        }
        null -> null
    }
    val normalizedDisplayName = displayName.trim()
    val normalizedUsername = username.trim()
    if (normalizedDisplayName.isBlank() || normalizedUsername.isBlank()) {
        return RemoteConnectionInputPreparation.Invalid(
            connection = invalidConnection(missingRequiredFieldsMessage),
        )
    }
    val normalizedPassword = password.takeIf(String::isNotBlank)
    val savedSession = matchingStoredSession(
        validatedBaseUrl.normalizedBaseUrl,
        validatedBackupBaseUrl?.normalizedBaseUrl,
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
            baseUrl = validatedBaseUrl.normalizedBaseUrl,
            backupBaseUrl = validatedBackupBaseUrl?.normalizedBaseUrl,
            activeBaseUrl = savedSession?.activeBaseUrl?.takeIf { activeBaseUrl ->
                activeBaseUrl in buildList {
                    add(validatedBaseUrl.normalizedBaseUrl)
                    validatedBackupBaseUrl?.normalizedBaseUrl?.let(::add)
                }
            },
            username = normalizedUsername,
            password = normalizedPassword,
            savedSession = savedSession,
            cleartextWarningMessage = listOfNotNull(
                validatedBaseUrl.cleartextWarningMessage,
                validatedBackupBaseUrl?.cleartextWarningMessage,
            ).firstOrNull(),
        ),
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
): RemoteConnectionInputPreparation<Connection, Session> = prepareRemoteConnectInput(
    connection = connection,
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = "",
    username = username,
    password = password,
    invalidConnection = invalidConnection,
    matchingStoredSession = { normalizedBaseUrl, _, normalizedUsername ->
        matchingStoredSession(normalizedBaseUrl, normalizedUsername)
    },
    missingRequiredFieldsMessage = missingRequiredFieldsMessage,
    missingCredentialMessage = missingCredentialMessage,
    validateSavedSession = validateSavedSession,
)

internal fun connectionSuccessMessage(
    message: String,
    cleartextWarningMessage: String?,
): String = listOfNotNull(message, cleartextWarningMessage).joinToString(" ")

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
    val baseUrlValidation = validateServerBaseUrl(session.baseUrl)
    if (baseUrlValidation is ServerBaseUrlValidation.Invalid) {
        return StoredSessionRefreshInputPreparation.Invalid(
            connection = invalidConnection(restoredConnection, baseUrlValidation.message),
        )
    }
    val backupBaseUrlValidation = validateOptionalServerBaseUrl(session.backupBaseUrl.orEmpty())
    if (backupBaseUrlValidation is ServerBaseUrlValidation.Invalid) {
        return StoredSessionRefreshInputPreparation.Invalid(
            connection = invalidConnection(restoredConnection, backupBaseUrlValidation.message),
        )
    }
    val validationMessage = validateStoredSession(session)
    if (validationMessage != null) {
        return StoredSessionRefreshInputPreparation.Invalid(
            connection = invalidConnection(restoredConnection, validationMessage),
        )
    }
    return StoredSessionRefreshInputPreparation.Ready(
        connection = refreshedConnectionState(restoredConnection),
        session = session,
        cleartextWarningMessage = listOfNotNull(
            (baseUrlValidation as? ServerBaseUrlValidation.Valid)?.cleartextWarningMessage,
            (backupBaseUrlValidation as? ServerBaseUrlValidation.Valid)?.cleartextWarningMessage,
        ).firstOrNull(),
    )
}

internal suspend fun <StoredSession : StoredSourceSession, Session> persistRemoteSession(
    sessionStore: SourceSessionStore<StoredSession>,
    session: Session?,
    fallbackSession: StoredSession?,
    displayName: String,
    baseUrl: String,
    backupBaseUrl: String?,
    activeBaseUrl: String?,
    username: String,
    toStoredSession: (Session, String, String, String?, String?, String) -> StoredSession,
    restoreFallbackSession: (StoredSession, String, String, String?, String?, String) -> StoredSession,
) {
    val storedSession = session?.let { activeSession ->
        toStoredSession(
            activeSession,
            displayName,
            baseUrl,
            backupBaseUrl,
            activeBaseUrl,
            username,
        )
    } ?: fallbackSession?.let { savedSession ->
        restoreFallbackSession(
            savedSession,
            displayName,
            baseUrl,
            backupBaseUrl,
            activeBaseUrl,
            username,
        )
    }
    if (storedSession != null) {
        sessionStore.save(storedSession)
    }
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
    persistRemoteSession(
        sessionStore = sessionStore,
        session = session,
        fallbackSession = fallbackSession,
        displayName = displayName,
        baseUrl = baseUrl,
        backupBaseUrl = fallbackSession?.backupBaseUrl,
        activeBaseUrl = fallbackSession?.activeBaseUrl,
        username = username,
        toStoredSession = { activeSession, name, resolvedBaseUrl, _, _, resolvedUsername ->
            toStoredSession(activeSession, name, resolvedBaseUrl, resolvedUsername)
        },
        restoreFallbackSession = { savedSession, name, resolvedBaseUrl, _, _, resolvedUsername ->
            restoreFallbackSession(savedSession, name, resolvedBaseUrl, resolvedUsername)
        },
    )
}

private fun normalizeOptionalServerBaseUrl(
    baseUrl: String,
): String? = when (val validation = validateOptionalServerBaseUrl(baseUrl)) {
    is ServerBaseUrlValidation.Valid -> validation.normalizedBaseUrl
    else -> null
}

private fun validateOptionalServerBaseUrl(
    baseUrl: String,
): ServerBaseUrlValidation? {
    if (baseUrl.isBlank()) return null
    return validateServerBaseUrl(baseUrl)
}
