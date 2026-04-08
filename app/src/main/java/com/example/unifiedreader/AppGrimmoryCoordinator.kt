package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import javax.inject.Inject

class AppGrimmoryCoordinator @Inject constructor(
    private val grimmoryCatalogSyncer: GrimmoryCatalogSyncer,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val grimmoryCoverRepository: GrimmoryCoverDataSource,
) {
    fun refreshedConnectionState(
        connection: GrimmoryConnectionUiState,
    ): GrimmoryConnectionUiState {
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
        connection: GrimmoryConnectionUiState,
    ): GrimmorySyncPreparation {
        val normalizedBaseUrl = normalizeServerBaseUrl(connection.baseUrl)
            ?: return GrimmorySyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Enter a server URL that starts with http:// or https://.",
                    isError = true,
                ),
            )
        val displayName = connection.displayName.trim()
        val username = connection.username.trim()
        if (displayName.isBlank() || username.isBlank()) {
            return GrimmorySyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Fill in the Grimmory server name and username.",
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
            return GrimmorySyncPreparation.Invalid(
                connection = connection.copy(
                    message = "Enter the Grimmory password or use the saved server details.",
                    isError = true,
                ),
            )
        }

        return GrimmorySyncPreparation.Ready(
            request = GrimmoryCatalogSyncRequest(
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
            successConnectionMessage = "Connected to Grimmory.",
            failurePrefix = "Unable to connect to Grimmory",
        )
    }

    internal fun prepareRefresh(
        connection: GrimmoryConnectionUiState,
    ): GrimmorySyncPreparation {
        val savedSession = grimmoryCredentialStore.sessionFlow.value
            ?: return GrimmorySyncPreparation.MissingSession
        val refreshedConnection = refreshedConnectionState(connection.withStoredSession(savedSession))
        return GrimmorySyncPreparation.Ready(
            request = GrimmoryCatalogSyncRequest(
                displayName = savedSession.displayName,
                baseUrl = savedSession.baseUrl,
                username = savedSession.username,
                accessToken = savedSession.accessToken,
                refreshToken = savedSession.refreshToken,
            ),
            activeConnection = refreshedConnection,
            fallbackSession = savedSession,
            successConnectionMessage = "Grimmory library synced.",
            failurePrefix = "Unable to sync Grimmory",
        )
    }

    internal suspend fun executeSync(
        preparation: GrimmorySyncPreparation.Ready,
        clearCoverState: () -> Unit,
    ): GrimmorySyncExecutionResult = runCatching {
        val result = grimmoryCatalogSyncer.sync(preparation.request)
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
            grimmoryCredentialStore.save(storedSession)
        }
        clearCoverState()
        grimmoryCoverRepository.clearCachedCovers()
        GrimmorySyncExecutionResult.Success(
            libraryCount = result.libraryCount,
            itemCount = result.itemCount,
            supportedFormatCounts = result.supportedFormatCounts,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                message = preparation.successConnectionMessage,
                isError = false,
            ),
        )
    }.getOrElse { throwable ->
        GrimmorySyncExecutionResult.Failure(
            message = "${preparation.failurePrefix}: ${throwable.userFacingMessage()}",
            connection = preparation.activeConnection.copy(
                isConnecting = false,
                message = "${preparation.failurePrefix}: ${throwable.userFacingMessage()}",
                isError = true,
            ),
        )
    }

    private fun matchingStoredSession(
        baseUrl: String,
        username: String,
    ): GrimmoryStoredSession? = grimmoryCredentialStore.sessionFlow.value?.takeIf { session ->
        session.baseUrl == baseUrl &&
            session.username == username
    }
}

internal sealed interface GrimmorySyncPreparation {
    data class Invalid(
        val connection: GrimmoryConnectionUiState,
    ) : GrimmorySyncPreparation

    data object MissingSession : GrimmorySyncPreparation

    data class Ready(
        val request: GrimmoryCatalogSyncRequest,
        val activeConnection: GrimmoryConnectionUiState,
        val fallbackSession: GrimmoryStoredSession?,
        val successConnectionMessage: String,
        val failurePrefix: String,
    ) : GrimmorySyncPreparation
}

internal sealed interface GrimmorySyncExecutionResult {
    data class Success(
        val libraryCount: Int,
        val itemCount: Int,
        val supportedFormatCounts: Map<FormatType, Int>,
        val connection: GrimmoryConnectionUiState,
    ) : GrimmorySyncExecutionResult

    data class Failure(
        val message: String,
        val connection: GrimmoryConnectionUiState,
    ) : GrimmorySyncExecutionResult
}
