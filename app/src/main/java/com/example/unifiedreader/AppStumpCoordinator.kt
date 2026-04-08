package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import javax.inject.Inject

class AppStumpCoordinator @Inject constructor(
    private val stumpCatalogSyncer: StumpCatalogSyncer,
    private val stumpCredentialStore: StumpCredentialStore,
    private val stumpCoverRepository: StumpCoverDataSource,
) {
    fun refreshedConnectionState(
        connection: StumpConnectionUiState,
    ): StumpConnectionUiState {
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
        connection: StumpConnectionUiState,
    ): StumpSyncPreparation {
        val normalizedBaseUrl = normalizeServerBaseUrl(connection.baseUrl)
            ?: return StumpSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Enter a server URL that starts with http:// or https://.",
                    isError = true,
                ),
            )
        val displayName = connection.displayName.trim()
        val username = connection.username.trim()
        if (displayName.isBlank() || username.isBlank()) {
            return StumpSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Fill in the Stump server name and username.",
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
            return StumpSyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Enter the Stump password or use the saved server details.",
                    isError = true,
                ),
            )
        }

        return StumpSyncPreparation.Ready(
            request = StumpCatalogSyncRequest(
                displayName = displayName,
                baseUrl = normalizedBaseUrl,
                username = username,
                password = password,
                accessToken = savedSession?.accessToken,
                refreshToken = savedSession?.refreshToken,
            ),
            activeConnection = connection.copy(baseUrl = normalizedBaseUrl),
            fallbackSession = savedSession?.copy(
                displayName = displayName,
                baseUrl = normalizedBaseUrl,
                username = username,
            ),
            successConnectionMessage = "Connected to Stump.",
            failurePrefix = "Unable to connect to Stump",
        )
    }

    internal fun prepareRefresh(
        connection: StumpConnectionUiState,
    ): StumpSyncPreparation {
        val savedSession = stumpCredentialStore.sessionFlow.value
            ?: return StumpSyncPreparation.MissingSession
        val refreshedConnection = refreshedConnectionState(connection.withStoredSession(savedSession))
        return StumpSyncPreparation.Ready(
            request = StumpCatalogSyncRequest(
                displayName = savedSession.displayName,
                baseUrl = savedSession.baseUrl,
                username = savedSession.username,
                accessToken = savedSession.accessToken,
                refreshToken = savedSession.refreshToken,
            ),
            activeConnection = refreshedConnection,
            fallbackSession = savedSession,
            successConnectionMessage = "Stump library synced.",
            failurePrefix = "Unable to sync Stump",
        )
    }

    internal suspend fun executeSync(
        preparation: StumpSyncPreparation.Ready,
        clearCoverState: () -> Unit,
        onProgress: (StumpSyncProgress) -> Unit = {},
    ): StumpSyncExecutionResult = runCatching {
        val result = stumpCatalogSyncer.sync(
            request = preparation.request,
            onProgress = onProgress,
        )
        val storedSession = result.session?.toStoredSession(
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
        ) ?: preparation.fallbackSession?.copy(
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
        )
        if (storedSession != null) {
            stumpCredentialStore.save(storedSession)
        }
        clearCoverState()
        stumpCoverRepository.clearCachedCovers()
        StumpSyncExecutionResult.Success(
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
        StumpSyncExecutionResult.Failure(
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
        preparation: StumpSyncPreparation.Ready,
    ): StumpConnectExecutionResult = runCatching {
        val result = stumpCatalogSyncer.connect(preparation.request)
        val storedSession = result.session?.toStoredSession(
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
        ) ?: preparation.fallbackSession?.copy(
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            username = preparation.request.username,
        )
        if (storedSession != null) {
            stumpCredentialStore.save(storedSession)
        }
        StumpConnectExecutionResult.Success(
            libraryCount = result.libraryCount,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = "Connected to Stump. Tap Sync Stump to import the library.",
                isError = false,
            ),
        )
    }.getOrElse { throwable ->
        StumpConnectExecutionResult.Failure(
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
    ): StumpStoredSession? = stumpCredentialStore.sessionFlow.value?.takeIf { session ->
        session.baseUrl == baseUrl &&
            session.username == username
    }
}

internal sealed interface StumpSyncPreparation {
    data class Invalid(
        val connection: StumpConnectionUiState,
    ) : StumpSyncPreparation

    data object MissingSession : StumpSyncPreparation

    data class Ready(
        val request: StumpCatalogSyncRequest,
        val activeConnection: StumpConnectionUiState,
        val fallbackSession: StumpStoredSession?,
        val successConnectionMessage: String,
        val failurePrefix: String,
    ) : StumpSyncPreparation
}

internal sealed interface StumpSyncExecutionResult {
    data class Success(
        val libraryCount: Int,
        val itemCount: Int,
        val supportedFormatCounts: Map<FormatType, Int>,
        val connection: StumpConnectionUiState,
    ) : StumpSyncExecutionResult

    data class Failure(
        val message: String,
        val connection: StumpConnectionUiState,
    ) : StumpSyncExecutionResult
}

internal sealed interface StumpConnectExecutionResult {
    data class Success(
        val libraryCount: Int,
        val connection: StumpConnectionUiState,
    ) : StumpConnectExecutionResult

    data class Failure(
        val message: String,
        val connection: StumpConnectionUiState,
    ) : StumpConnectExecutionResult
}
