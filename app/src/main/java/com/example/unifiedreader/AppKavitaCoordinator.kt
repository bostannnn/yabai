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
    ): KavitaConnectionUiState {
        val normalizedBaseUrl = normalizeServerBaseUrl(connection.baseUrl) ?: return connection.copy(
            hasStoredSession = false,
        )
        val username = connection.username.trim()
        if (username.isBlank()) {
            return connection.copy(hasStoredSession = false)
        }
        val matchingSession = matchingStoredSession(
            baseUrl = normalizedBaseUrl,
            username = username,
        )
        return connection.copy(hasStoredSession = matchingSession != null)
    }

    internal fun prepareConnect(
        connection: KavitaConnectionUiState,
    ): KavitaSyncPreparation {
        val normalizedBaseUrl = normalizeServerBaseUrl(connection.baseUrl)
            ?: return KavitaSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Enter a server URL that starts with http:// or https://.",
                    isError = true,
                ),
            )
        val displayName = connection.displayName.trim()
        val username = connection.username.trim()
        if (displayName.isBlank() || username.isBlank()) {
            return KavitaSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Fill in the Kavita server name and username.",
                    isError = true,
                ),
            )
        }
        val savedSession = matchingStoredSession(
            baseUrl = normalizedBaseUrl,
            username = username,
        ).takeIf { connection.password.isBlank() }
        val password = connection.password.takeIf(String::isNotBlank)
        if (password == null && savedSession == null) {
            return KavitaSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Enter the Kavita password or use the saved server details.",
                    isError = true,
                ),
            )
        }
        if (password == null && savedSession?.apiKey.isNullOrBlank()) {
            return KavitaSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Re-enter the Kavita password once to refresh the API key for covers and reading.",
                    isError = true,
                ),
            )
        }

        return KavitaSyncPreparation.Ready(
            request = KavitaCatalogSyncRequest(
                displayName = displayName,
                baseUrl = normalizedBaseUrl,
                username = username,
                password = password,
                accessToken = savedSession?.accessToken,
                refreshToken = savedSession?.refreshToken,
                apiKey = savedSession?.apiKey,
            ),
            activeConnection = connection.copy(baseUrl = normalizedBaseUrl),
            fallbackSession = savedSession?.copy(
                displayName = displayName,
                baseUrl = normalizedBaseUrl,
                username = username,
            ),
            successConnectionMessage = "Connected to Kavita.",
            failurePrefix = "Unable to connect to Kavita",
        )
    }

    internal fun prepareRefresh(
        connection: KavitaConnectionUiState,
    ): KavitaSyncPreparation {
        val savedSession = kavitaCredentialStore.sessionFlow.value
            ?: return KavitaSyncPreparation.MissingSession
        if (savedSession.apiKey.isNullOrBlank()) {
            return KavitaSyncPreparation.Invalid(
                connection = connection.withStoredSession(savedSession).copy(
                    message = "Re-enter the Kavita password once to refresh the API key for covers and reading.",
                    isError = true,
                ),
            )
        }
        val refreshedConnection = refreshedConnectionState(connection.withStoredSession(savedSession))
        return KavitaSyncPreparation.Ready(
            request = KavitaCatalogSyncRequest(
                displayName = savedSession.displayName,
                baseUrl = savedSession.baseUrl,
                username = savedSession.username,
                accessToken = savedSession.accessToken,
                refreshToken = savedSession.refreshToken,
                apiKey = savedSession.apiKey,
            ),
            activeConnection = refreshedConnection,
            fallbackSession = savedSession,
            successConnectionMessage = "Kavita library synced.",
            failurePrefix = "Unable to sync Kavita",
        )
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
                message = "Connected to Kavita. Tap Sync Kavita to import the library.",
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
        val storedSession = session?.toStoredSession(
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
        ) ?: preparation.fallbackSession?.copy(
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
        )
        if (storedSession != null) {
            kavitaCredentialStore.save(storedSession)
        }
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
