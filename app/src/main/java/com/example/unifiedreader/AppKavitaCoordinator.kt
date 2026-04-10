package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import javax.inject.Inject

class AppKavitaCoordinator @Inject constructor(
    private val kavitaCatalogSyncer: KavitaCatalogSyncer,
    private val kavitaCredentialStore: KavitaCredentialStore,
) {
    fun refreshedConnectionState(
        connection: KavitaConnectionUiState,
    ): KavitaConnectionUiState = refreshedRemoteConnectionState(
        connection = connection,
        baseUrl = connection.baseUrl,
        username = connection.username,
        copyHasStoredSession = { currentConnection, hasStoredSession ->
            currentConnection.copy(hasStoredSession = hasStoredSession)
        },
        matchingStoredSession = ::matchingStoredSession,
    )

    internal fun prepareConnect(
        connection: KavitaConnectionUiState,
    ): KavitaSyncPreparation {
        return when (
            val connectInput = prepareRemoteConnectInput(
                connection = connection,
                displayName = connection.displayName,
                baseUrl = connection.baseUrl,
                username = connection.username,
                password = connection.password,
                invalidConnection = { message ->
                    connection.copy(
                        message = message,
                        isError = true,
                    )
                },
                matchingStoredSession = ::matchingStoredSession,
                missingRequiredFieldsMessage = "Fill in the Kavita server name and username.",
                missingCredentialMessage = "Enter the Kavita password or use the saved server details.",
                validateSavedSession = { savedSession, password ->
                    if (password == null && savedSession?.apiKey.isNullOrBlank()) {
                        "Re-enter the Kavita password once to refresh the API key for covers and reading."
                    } else {
                        null
                    }
                },
            )
        ) {
            is RemoteConnectionInputPreparation.Invalid -> KavitaSyncPreparation.Invalid(
                connection = connectInput.connection,
            )
            is RemoteConnectionInputPreparation.Ready -> {
                val input = connectInput.input
                KavitaSyncPreparation.Ready(
                    request = KavitaCatalogSyncRequest(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        username = input.username,
                        password = input.password,
                        accessToken = input.savedSession?.accessToken,
                        refreshToken = input.savedSession?.refreshToken,
                        apiKey = input.savedSession?.apiKey,
                    ),
                    activeConnection = connection.copy(baseUrl = input.baseUrl),
                    fallbackSession = input.savedSession?.copy(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        username = input.username,
                    ),
                    successConnectionMessage = connectionSuccessMessage(
                        message = "Connected to Kavita. Tap Sync Kavita to import the library.",
                        cleartextWarningMessage = input.cleartextWarningMessage,
                    ),
                    failurePrefix = "Unable to connect to Kavita",
                )
            }
        }
    }

    internal fun prepareRefresh(
        connection: KavitaConnectionUiState,
    ): KavitaSyncPreparation {
        return when (
            val refreshInput = prepareRemoteRefreshInput(
                connection = connection,
                storedSession = kavitaCredentialStore.sessionFlow.value,
                restoreConnection = { currentConnection, session ->
                    currentConnection.withStoredSession(session)
                },
                refreshedConnectionState = ::refreshedConnectionState,
                invalidConnection = { currentConnection, message ->
                    currentConnection.copy(
                        message = message,
                        isError = true,
                    )
                },
                validateStoredSession = { session ->
                    if (session.apiKey.isNullOrBlank()) {
                        "Re-enter the Kavita password once to refresh the API key for covers and reading."
                    } else {
                        null
                    }
                },
            )
        ) {
            StoredSessionRefreshInputPreparation.MissingSession -> KavitaSyncPreparation.MissingSession
            is StoredSessionRefreshInputPreparation.Invalid -> KavitaSyncPreparation.Invalid(
                connection = refreshInput.connection,
            )
            is StoredSessionRefreshInputPreparation.Ready -> {
                val savedSession = refreshInput.session
                KavitaSyncPreparation.Ready(
                    request = KavitaCatalogSyncRequest(
                        displayName = savedSession.displayName,
                        baseUrl = savedSession.baseUrl,
                        username = savedSession.username,
                        accessToken = savedSession.accessToken,
                        refreshToken = savedSession.refreshToken,
                        apiKey = savedSession.apiKey,
                    ),
                    activeConnection = refreshInput.connection,
                    fallbackSession = savedSession,
                    successConnectionMessage = "Kavita library synced.",
                    failurePrefix = "Unable to sync Kavita",
                )
            }
        }
    }

    internal suspend fun executeSync(
        preparation: KavitaSyncPreparation.Ready,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): KavitaSyncExecutionResult = runCatching {
        val result = kavitaCatalogSyncer.sync(
            request = preparation.request,
            onProgress = onProgress,
        )
        persistSession(
            preparation = preparation,
            session = result.session,
        )
        KavitaSyncExecutionResult.Success(
            libraryCount = result.libraryCount,
            itemCount = result.itemCount,
            supportedFormatCounts = result.supportedFormatCounts,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = preparation.successConnectionMessage,
                isError = false,
            ),
        )
    }.getOrElse { throwable ->
        KavitaSyncExecutionResult.Failure(
            message = "${preparation.failurePrefix}: ${throwable.userFacingMessage()}",
            connection = preparation.activeConnection.copy(
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = "${preparation.failurePrefix}: ${throwable.userFacingMessage()}",
                isError = true,
            ),
        )
    }

    internal suspend fun executeConnect(
        preparation: KavitaSyncPreparation.Ready,
    ): KavitaConnectExecutionResult = runCatching {
        val result = kavitaCatalogSyncer.connect(preparation.request)
        persistSession(
            preparation = preparation,
            session = result.session,
        )
        KavitaConnectExecutionResult.Success(
            libraryCount = result.libraryCount,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = preparation.successConnectionMessage,
                isError = false,
            ),
        )
    }.getOrElse { throwable ->
        KavitaConnectExecutionResult.Failure(
            message = "${preparation.failurePrefix}: ${throwable.userFacingMessage()}",
            connection = preparation.activeConnection.copy(
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = "${preparation.failurePrefix}: ${throwable.userFacingMessage()}",
                isError = true,
            ),
        )
    }

    private fun matchingStoredSession(
        baseUrl: String,
        username: String,
    ): KavitaStoredSession? = kavitaCredentialStore.sessionFlow.value?.takeIf { session ->
        session.baseUrl == baseUrl &&
            session.username == username
    }

    private suspend fun persistSession(
        preparation: KavitaSyncPreparation.Ready,
        session: com.example.unifiedreader.data.kavita.KavitaSession?,
    ) {
        persistRemoteSession(
            sessionStore = kavitaCredentialStore,
            session = session,
            fallbackSession = preparation.fallbackSession,
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
            toStoredSession = { activeSession, displayName, baseUrl, username ->
                activeSession.toStoredSession(
                    displayName = displayName,
                    baseUrl = baseUrl,
                    username = username,
                )
            },
            restoreFallbackSession = { savedSession, displayName, baseUrl, username ->
                savedSession.copy(
                    displayName = displayName,
                    baseUrl = baseUrl,
                    username = username,
                )
            },
        )
    }
}

internal sealed interface KavitaSyncPreparation {
    data class Invalid(
        val connection: KavitaConnectionUiState,
    ) : KavitaSyncPreparation

    data object MissingSession : KavitaSyncPreparation

    data class Ready(
        val request: KavitaCatalogSyncRequest,
        val activeConnection: KavitaConnectionUiState,
        val fallbackSession: KavitaStoredSession?,
        val successConnectionMessage: String,
        val failurePrefix: String,
    ) : KavitaSyncPreparation
}

internal sealed interface KavitaSyncExecutionResult {
    data class Success(
        val libraryCount: Int,
        val itemCount: Int,
        val supportedFormatCounts: Map<FormatType, Int>,
        val connection: KavitaConnectionUiState,
    ) : KavitaSyncExecutionResult

    data class Failure(
        val message: String,
        val connection: KavitaConnectionUiState,
    ) : KavitaSyncExecutionResult
}

internal sealed interface KavitaConnectExecutionResult {
    data class Success(
        val libraryCount: Int,
        val connection: KavitaConnectionUiState,
    ) : KavitaConnectExecutionResult

    data class Failure(
        val message: String,
        val connection: KavitaConnectionUiState,
    ) : KavitaConnectExecutionResult
}
