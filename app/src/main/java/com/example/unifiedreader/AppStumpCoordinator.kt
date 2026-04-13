package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import javax.inject.Inject

class AppStumpCoordinator @Inject constructor(
    private val stumpCatalogSyncer: StumpCatalogSyncer,
    private val stumpCredentialStore: StumpCredentialStore,
) {
    fun refreshedConnectionState(
        connection: StumpConnectionUiState,
    ): StumpConnectionUiState = refreshedRemoteConnectionState(
        connection = connection,
        baseUrl = connection.baseUrl,
        backupBaseUrl = connection.backupBaseUrl,
        username = connection.username,
        copyHasStoredSession = { currentConnection, hasStoredSession ->
            currentConnection.copy(hasStoredSession = hasStoredSession)
        },
        matchingStoredSession = ::matchingStoredSession,
    )

    internal fun prepareConnect(
        connection: StumpConnectionUiState,
    ): StumpSyncPreparation {
        return when (
            val connectInput = prepareRemoteConnectInput(
                connection = connection,
                displayName = connection.displayName,
                baseUrl = connection.baseUrl,
                backupBaseUrl = connection.backupBaseUrl,
                username = connection.username,
                password = connection.password,
                invalidConnection = { message ->
                    connection.copy(
                        message = message,
                        isError = true,
                    )
                },
                matchingStoredSession = ::matchingStoredSession,
                missingRequiredFieldsMessage = "Fill in the Stump server name and username.",
                missingCredentialMessage = "Enter the Stump password or use the saved server details.",
            )
        ) {
            is RemoteConnectionInputPreparation.Invalid -> StumpSyncPreparation.Invalid(
                connection = connectInput.connection,
            )
            is RemoteConnectionInputPreparation.Ready -> {
                val input = connectInput.input
                StumpSyncPreparation.Ready(
                    request = StumpCatalogSyncRequest(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        backupBaseUrl = input.backupBaseUrl,
                        activeBaseUrl = input.activeBaseUrl,
                        username = input.username,
                        password = input.password,
                        accessToken = input.savedSession?.accessToken,
                        refreshToken = input.savedSession?.refreshToken,
                    ),
                    activeConnection = connection.copy(
                        baseUrl = input.baseUrl,
                        backupBaseUrl = input.backupBaseUrl.orEmpty(),
                    ),
                    fallbackSession = input.savedSession?.copy(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        backupBaseUrl = input.backupBaseUrl,
                        activeBaseUrl = input.activeBaseUrl,
                        username = input.username,
                    ),
                    successConnectionMessage = connectionSuccessMessage(
                        message = "Connected to Stump. Tap Sync Stump to import the library.",
                        cleartextWarningMessage = input.cleartextWarningMessage,
                    ),
                    failurePrefix = "Unable to connect to Stump",
                )
            }
        }
    }

    internal fun prepareRefresh(
        connection: StumpConnectionUiState,
    ): StumpSyncPreparation {
        return when (
            val refreshInput = prepareRemoteRefreshInput(
                connection = connection,
                storedSession = stumpCredentialStore.sessionFlow.value,
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
            StoredSessionRefreshInputPreparation.MissingSession -> StumpSyncPreparation.MissingSession
            is StoredSessionRefreshInputPreparation.Invalid -> StumpSyncPreparation.Invalid(
                connection = refreshInput.connection,
            )
            is StoredSessionRefreshInputPreparation.Ready -> {
                val savedSession = refreshInput.session
                StumpSyncPreparation.Ready(
                    request = StumpCatalogSyncRequest(
                        displayName = savedSession.displayName,
                        baseUrl = savedSession.baseUrl,
                        backupBaseUrl = savedSession.backupBaseUrl,
                        activeBaseUrl = savedSession.activeBaseUrl,
                        username = savedSession.username,
                        accessToken = savedSession.accessToken,
                        refreshToken = savedSession.refreshToken,
                    ),
                    activeConnection = refreshInput.connection,
                    fallbackSession = savedSession,
                    successConnectionMessage = connectionSuccessMessage(
                        message = "Stump library synced.",
                        cleartextWarningMessage = refreshInput.cleartextWarningMessage,
                    ),
                    failurePrefix = "Unable to sync Stump",
                )
            }
        }
    }

    internal suspend fun executeSync(
        preparation: StumpSyncPreparation.Ready,
        clearCoverState: suspend () -> Unit,
        onProgress: (StumpSyncProgress) -> Unit = {},
    ): StumpSyncExecutionResult = runCatching {
        val result = stumpCatalogSyncer.sync(
            request = preparation.request,
            onProgress = onProgress,
        )
        persistRemoteSession(
            sessionStore = stumpCredentialStore,
            session = result.session,
            fallbackSession = preparation.fallbackSession,
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            backupBaseUrl = preparation.request.backupBaseUrl,
            activeBaseUrl = result.activeBaseUrl ?: preparation.request.activeBaseUrl,
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
        clearCoverState()
        val catalogWarning = result.partialCatalogWarning?.userFacingMessage()
        val progressPushWarning = progressPushWarningMessage(
            sourceLabel = "Stump",
            failures = result.progressPushFailures,
        )
        StumpSyncExecutionResult.Success(
            libraryCount = result.libraryCount,
            itemCount = result.itemCount,
            supportedFormatCounts = result.supportedFormatCounts,
            availableLibraries = result.libraries,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = listOfNotNull(preparation.successConnectionMessage, catalogWarning, progressPushWarning)
                    .joinToString(" "),
                isError = catalogWarning != null || progressPushWarning != null,
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
        persistRemoteSession(
            sessionStore = stumpCredentialStore,
            session = result.session,
            fallbackSession = preparation.fallbackSession,
            displayName = preparation.request.displayName,
            baseUrl = preparation.request.baseUrl,
            backupBaseUrl = preparation.request.backupBaseUrl,
            activeBaseUrl = result.activeBaseUrl ?: preparation.request.activeBaseUrl,
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
        StumpConnectExecutionResult.Success(
            libraryCount = result.libraryCount,
            availableLibraries = result.libraries,
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
        backupBaseUrl: String?,
        username: String,
    ): StumpStoredSession? = stumpCredentialStore.sessionFlow.value?.takeIf { session ->
        session.matchesServerIdentity(
            baseUrl = baseUrl,
            backupBaseUrl = backupBaseUrl,
            username = username,
        )
    }

    internal suspend fun loadAvailableLibraries(
        session: StumpStoredSession,
    ): List<Library> = stumpCatalogSyncer.connect(
        request = StumpCatalogSyncRequest(
            displayName = session.displayName,
            baseUrl = session.baseUrl,
            backupBaseUrl = session.backupBaseUrl,
            activeBaseUrl = session.activeBaseUrl,
            username = session.username,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
        ),
    ).libraries
}

private fun StumpPartialCatalogWarning.userFacingMessage(): String {
    val affectedSeries = affectedSeriesCount.seriesLabel()
    val preservedItems = preservedItemCount.itemLabel()
    return "Stump returned incomplete data for $affectedSeries; preserved $preservedItems from the previous sync."
}

private fun Int.seriesLabel(): String = when (this) {
    1 -> "1 series"
    else -> "$this series"
}

private fun Int.itemLabel(): String = when (this) {
    1 -> "1 cached item"
    else -> "$this cached items"
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
        val availableLibraries: List<Library>,
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
        val availableLibraries: List<Library>,
        val connection: StumpConnectionUiState,
    ) : StumpConnectExecutionResult

    data class Failure(
        val message: String,
        val connection: StumpConnectionUiState,
    ) : StumpConnectExecutionResult
}
