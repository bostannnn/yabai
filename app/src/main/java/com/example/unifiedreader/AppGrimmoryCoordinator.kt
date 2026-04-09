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
    ): GrimmoryConnectionUiState = refreshedRemoteConnectionState(
        connection = connection,
        baseUrl = connection.baseUrl,
        username = connection.username,
        copyHasStoredSession = { currentConnection, hasStoredSession ->
            currentConnection.copy(hasStoredSession = hasStoredSession)
        },
        matchingStoredSession = ::matchingStoredSession,
    )

    internal fun prepareConnect(
        connection: GrimmoryConnectionUiState,
    ): GrimmorySyncPreparation {
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
                missingRequiredFieldsMessage = "Fill in the Grimmory server name and username.",
                missingCredentialMessage = "Enter the Grimmory password or use the saved server details.",
            )
        ) {
            is RemoteConnectionInputPreparation.Invalid -> GrimmorySyncPreparation.Invalid(
                connection = connectInput.connection,
            )
            is RemoteConnectionInputPreparation.Ready -> {
                val input = connectInput.input
                GrimmorySyncPreparation.Ready(
                    request = GrimmoryCatalogSyncRequest(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        username = input.username,
                        password = input.password,
                        accessToken = input.savedSession?.accessToken,
                        refreshToken = input.savedSession?.refreshToken,
                    ),
                    activeConnection = connection.copy(baseUrl = input.baseUrl),
                    fallbackSession = input.savedSession?.copy(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        username = input.username,
                    ),
                    successConnectionMessage = "Connected to Grimmory.",
                    failurePrefix = "Unable to connect to Grimmory",
                )
            }
        }
    }

    internal fun prepareRefresh(
        connection: GrimmoryConnectionUiState,
    ): GrimmorySyncPreparation {
        return when (
            val refreshInput = prepareRemoteRefreshInput(
                connection = connection,
                storedSession = grimmoryCredentialStore.sessionFlow.value,
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
            )
        ) {
            StoredSessionRefreshInputPreparation.MissingSession -> GrimmorySyncPreparation.MissingSession
            is StoredSessionRefreshInputPreparation.Invalid -> GrimmorySyncPreparation.Invalid(
                connection = refreshInput.connection,
            )
            is StoredSessionRefreshInputPreparation.Ready -> {
                val savedSession = refreshInput.session
                GrimmorySyncPreparation.Ready(
                    request = GrimmoryCatalogSyncRequest(
                        displayName = savedSession.displayName,
                        baseUrl = savedSession.baseUrl,
                        username = savedSession.username,
                        accessToken = savedSession.accessToken,
                        refreshToken = savedSession.refreshToken,
                    ),
                    activeConnection = refreshInput.connection,
                    fallbackSession = savedSession,
                    successConnectionMessage = "Grimmory library synced.",
                    failurePrefix = "Unable to sync Grimmory",
                )
            }
        }
    }

    internal suspend fun executeSync(
        preparation: GrimmorySyncPreparation.Ready,
        clearCoverState: () -> Unit,
    ): GrimmorySyncExecutionResult = runCatching {
        val result = grimmoryCatalogSyncer.sync(preparation.request)
        persistRemoteSession(
            sessionStore = grimmoryCredentialStore,
            session = result.session,
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
