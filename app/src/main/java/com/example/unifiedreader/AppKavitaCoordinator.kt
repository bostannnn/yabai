package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import javax.inject.Inject

class AppKavitaCoordinator @Inject constructor(
    private val kavitaCatalogSyncer: KavitaCatalogSyncer,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    fun refreshedConnectionState(
        connection: KavitaConnectionUiState,
    ): KavitaConnectionUiState = refreshedRemoteConnectionState(
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
        connection: KavitaConnectionUiState,
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
            is RemoteConnectionInputPreparation.Invalid -> KavitaSyncPreparation.Invalid(
                connection = connectInput.connection,
            )
            is RemoteConnectionInputPreparation.Ready -> {
                val input = connectInput.input
                KavitaSyncPreparation.Ready(
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
        syncMode: KavitaCatalogSyncMode,
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
                        backupBaseUrl = savedSession.backupBaseUrl,
                        activeBaseUrl = savedSession.activeBaseUrl,
                        username = savedSession.username,
                        accessToken = savedSession.accessToken,
                        refreshToken = savedSession.refreshToken,
                        apiKey = savedSession.apiKey,
                        syncMode = syncMode,
                    ),
                    activeConnection = refreshInput.connection,
                    fallbackSession = savedSession,
                    successConnectionMessage = connectionSuccessMessage(
                        message = when (syncMode) {
                            KavitaCatalogSyncMode.FAST -> "Kavita fast sync completed. Full resync is available for exact cleanup."
                            KavitaCatalogSyncMode.FULL -> "Kavita library fully resynced."
                        },
                        cleartextWarningMessage = refreshInput.cleartextWarningMessage,
                    ),
                    failurePrefix = "Unable to sync Kavita",
                )
            }
        }
    }

    internal suspend fun executeSync(
        preparation: KavitaSyncPreparation.Ready,
        clearCoverState: suspend () -> Unit,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): KavitaSyncExecutionResult = runSuspendCatchingPreservingCancellation {
        val result = kavitaCatalogSyncer.sync(
            request = preparation.request,
            onProgress = onProgress,
        )
        persistSession(
            preparation = preparation,
            session = result.session,
            activeBaseUrl = result.activeBaseUrl,
        )
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        if (preparation.request.syncMode == KavitaCatalogSyncMode.FULL) {
            clearCoverState()
        }
        val progressPushWarning = progressPushWarningMessage(
            sourceLabel = "Kavita",
            failures = result.progressPushFailures,
        )
        KavitaSyncExecutionResult.Success(
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
                message = listOfNotNull(preparation.successConnectionMessage, progressPushWarning)
                    .joinToString(" "),
                isError = progressPushWarning != null,
            ),
        )
    }.getOrElse { throwable ->
        remoteSourceConnectivityPolicy.markFromFailure(SourceType.KAVITA, throwable)
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
    ): KavitaConnectExecutionResult = runSuspendCatchingPreservingCancellation {
        val result = kavitaCatalogSyncer.connect(preparation.request)
        persistSession(
            preparation = preparation,
            session = result.session,
            activeBaseUrl = result.activeBaseUrl,
        )
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        KavitaConnectExecutionResult.Success(
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
        backupBaseUrl: String?,
        username: String,
    ): KavitaStoredSession? = kavitaCredentialStore.sessionFlow.value?.takeIf { session ->
        session.matchesServerIdentity(
            baseUrl = baseUrl,
            backupBaseUrl = backupBaseUrl,
            username = username,
        )
    }

    internal suspend fun loadAvailableLibraries(
        session: KavitaStoredSession,
    ): List<Library> = kavitaCatalogSyncer.connect(
        request = KavitaCatalogSyncRequest(
            displayName = session.displayName,
            baseUrl = session.baseUrl,
            backupBaseUrl = session.backupBaseUrl,
            activeBaseUrl = session.activeBaseUrl,
            username = session.username,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            apiKey = session.apiKey,
        ),
    ).libraries

    private suspend fun persistSession(
        preparation: KavitaSyncPreparation.Ready,
        session: com.example.unifiedreader.data.kavita.KavitaSession?,
        activeBaseUrl: String?,
    ) {
        persistRemoteSession(
            sessionStore = kavitaCredentialStore,
            session = session,
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
        val availableLibraries: List<Library>,
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
        val availableLibraries: List<Library>,
        val connection: KavitaConnectionUiState,
    ) : KavitaConnectExecutionResult

    data class Failure(
        val message: String,
        val connection: KavitaConnectionUiState,
    ) : KavitaConnectExecutionResult
}
