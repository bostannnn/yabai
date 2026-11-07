package com.example.unifiedreader

import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import javax.inject.Inject

class AppKavitaCoordinator @Inject constructor(
    private val kavitaCatalogSyncer: KavitaCatalogSyncer,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    fun refreshedConnectionState(
        connection: RemoteSourceConnectionUiState,
    ): RemoteSourceConnectionUiState = refreshedRemoteConnectionState(
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
        connection: RemoteSourceConnectionUiState,
    ): KavitaSyncPreparation {
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
            is RemoteConnectionInputPreparation.Invalid -> SourceSyncPreparation.Invalid(
                connection = connectInput.connection,
            )
            is RemoteConnectionInputPreparation.Ready -> {
                val input = connectInput.input
                SourceSyncPreparation.Ready(
                    request = KavitaCatalogSyncRequest(
                        displayName = input.displayName,
                        baseUrl = input.baseUrl,
                        backupBaseUrl = input.backupBaseUrl,
                        activeBaseUrl = input.activeBaseUrl,
                        username = input.username,
                        password = input.password,
                        accessToken = input.savedSession?.accessToken,
                        refreshToken = input.savedSession?.refreshToken,
                        apiKey = input.savedSession?.apiKey,
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
                        message = "Connected to Kavita. Tap Sync now to get your library.",
                        cleartextWarningMessage = input.cleartextWarningMessage,
                    ),
                    failurePrefix = "Unable to connect to Kavita",
                )
            }
        }
    }

    internal fun prepareRefresh(
        connection: RemoteSourceConnectionUiState,
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
            StoredSessionRefreshInputPreparation.MissingSession -> SourceSyncPreparation.MissingSession
            is StoredSessionRefreshInputPreparation.Invalid -> SourceSyncPreparation.Invalid(
                connection = refreshInput.connection,
            )
            is StoredSessionRefreshInputPreparation.Ready -> {
                val savedSession = refreshInput.session
                SourceSyncPreparation.Ready(
                    request = KavitaCatalogSyncRequest(
                        displayName = savedSession.displayName,
                        baseUrl = savedSession.baseUrl,
                        backupBaseUrl = savedSession.backupBaseUrl,
                        activeBaseUrl = savedSession.activeBaseUrl,
                        username = savedSession.username,
                        accessToken = savedSession.accessToken,
                        refreshToken = savedSession.refreshToken,
                        apiKey = savedSession.apiKey,
                    ),
                    activeConnection = refreshInput.connection,
                    fallbackSession = savedSession,
                    successConnectionMessage = connectionSuccessMessage(
                        message = "Kavita library synced.",
                        cleartextWarningMessage = refreshInput.cleartextWarningMessage,
                    ),
                    failurePrefix = "Unable to sync Kavita",
                )
            }
        }
    }

    internal suspend fun executeSync(
        preparation: KavitaSyncReady,
        clearCoverState: suspend () -> Unit,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): SourceSyncExecutionResult = runSuspendCatching {
        val result = kavitaCatalogSyncer.sync(
            request = preparation.request,
            onProgress = onProgress,
            onSessionRenewed = kavitaCredentialStore.renewedTokensSaver(preparation),
        )
        kavitaCredentialStore.saveAfterRun(
            preparation = preparation,
            session = result.session,
            activeBaseUrl = result.activeBaseUrl,
        )
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        clearCoverState()
        val progressPushWarning = progressPushWarningMessage(
            sourceLabel = "Kavita",
            failures = result.progressPushFailures,
        )
        SourceSyncExecutionResult.Success(
            libraryCount = result.libraryCount,
            itemCount = result.itemCount,
            supportedFormatCounts = result.supportedFormatCounts,
            availableLibraries = result.libraries,
            hasCatalogChanges = true,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = listOfNotNull(preparation.successConnectionMessage, progressPushWarning)
                    .joinToString(" "),
                isError = progressPushWarning != null,
            ),
        )
    }.getOrElse { throwable ->
        remoteSourceConnectivityPolicy.markFromFailure(SourceType.KAVITA, throwable)
        SourceSyncExecutionResult.Failure(
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

    internal suspend fun executeFastSync(
        preparation: KavitaSyncReady,
        clearCoverState: suspend () -> Unit,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): SourceSyncExecutionResult = runSuspendCatching {
        val result = kavitaCatalogSyncer.fastSync(
            request = preparation.request,
            onProgress = onProgress,
            onSessionRenewed = kavitaCredentialStore.renewedTokensSaver(preparation),
        )
        kavitaCredentialStore.saveAfterRun(
            preparation = preparation,
            session = result.session,
            activeBaseUrl = result.activeBaseUrl,
        )
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        if (result.hasCatalogChanges) {
            clearCoverState()
        }
        val progressPushWarning = progressPushWarningMessage(
            sourceLabel = "Kavita",
            failures = result.progressPushFailures,
        )
        SourceSyncExecutionResult.Success(
            libraryCount = result.libraryCount,
            itemCount = result.itemCount,
            supportedFormatCounts = result.supportedFormatCounts,
            availableLibraries = result.libraries,
            hasCatalogChanges = result.hasCatalogChanges,
            connection = preparation.activeConnection.copy(
                password = "",
                hasStoredSession = true,
                isConnecting = false,
                syncProgressFraction = null,
                syncProgressLabel = null,
                message = listOfNotNull(
                    preparation.successConnectionMessage.replaceFirst(
                        oldValue = "Kavita library synced.",
                        newValue = "Kavita fast sync complete.",
                    ),
                    progressPushWarning,
                ).joinToString(" "),
                isError = progressPushWarning != null,
            ),
        )
    }.getOrElse { throwable ->
        remoteSourceConnectivityPolicy.markFromFailure(SourceType.KAVITA, throwable)
        SourceSyncExecutionResult.Failure(
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
        preparation: KavitaSyncReady,
    ): SourceConnectExecutionResult = runSuspendCatching {
        val result = kavitaCatalogSyncer.connect(
            request = preparation.request,
            onSessionRenewed = kavitaCredentialStore.renewedTokensSaver(preparation),
        )
        kavitaCredentialStore.saveAfterRun(
            preparation = preparation,
            session = result.session,
            activeBaseUrl = result.activeBaseUrl,
        )
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        SourceConnectExecutionResult.Success(
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
        remoteSourceConnectivityPolicy.markFromFailure(SourceType.KAVITA, throwable)
        SourceConnectExecutionResult.Failure(
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
    ): KavitaStoredSession? = kavitaCredentialStore.sessionFlow.value?.takeIf { session ->
        session.matchesServerIdentity(
            baseUrl = baseUrl,
            backupBaseUrl = backupBaseUrl,
            username = username,
        )
    }
}

internal typealias KavitaSyncPreparation = SourceSyncPreparation<KavitaCatalogSyncRequest, KavitaStoredSession>
internal typealias KavitaSyncReady = SourceSyncPreparation.Ready<KavitaCatalogSyncRequest, KavitaStoredSession>
