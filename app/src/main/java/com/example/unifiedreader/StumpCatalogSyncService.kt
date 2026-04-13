package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

fun interface StumpApiFactory {
    fun create(
        baseUrl: String,
    ): StumpApi
}

interface StumpCatalogSyncer {
    suspend fun connect(
        request: StumpCatalogSyncRequest,
    ): StumpCatalogConnectionResult

    suspend fun sync(
        request: StumpCatalogSyncRequest,
        onProgress: (StumpSyncProgress) -> Unit = {},
    ): StumpCatalogSyncResult
}

class StumpCatalogSyncService(
    private val stumpApiFactory: StumpApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val progressDiagnostics: StumpProgressSyncDiagnostics = LogcatStumpProgressSyncDiagnostics,
) : StumpCatalogSyncer {
    private val progressSyncStore: ProgressSyncStore? = readerSessionStore?.let(::ReaderSessionProgressStoreAdapter)

    override suspend fun connect(
        request: StumpCatalogSyncRequest,
    ): StumpCatalogConnectionResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = stumpApiFactory.create(selectedBaseUrl)
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Stump connection requires a password or saved access token.")
            val refreshResult = api.runWithTokenRefresh(
                initialAccessToken = accessToken,
                initialRefreshToken = session?.refreshToken ?: request.refreshToken,
            ) { token ->
                api.getLibraries(token)
            }
            StumpCatalogConnectionResult(
                libraryCount = refreshResult.value.size,
                libraries = refreshResult.value,
                session = refreshResult.refreshedSession ?: session,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    override suspend fun sync(
        request: StumpCatalogSyncRequest,
        onProgress: (StumpSyncProgress) -> Unit,
    ): StumpCatalogSyncResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = stumpApiFactory.create(selectedBaseUrl)
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Stump sync requires a password or saved access token.")
            var activeAccessToken = accessToken
            var activeRefreshToken = session?.refreshToken ?: request.refreshToken
            var latestSession: StumpSession? = session
            val fetchResult = api.runWithTokenRefresh(
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
            ) { token ->
                onProgress(StumpSyncProgress(message = "Loading Stump libraries...", fraction = 0.05f))
                val libraries = api.getLibraries(token)
                fetchStumpCatalogWithPartialSeriesRecovery(
                    api = api,
                    accessToken = token,
                    libraries = libraries,
                    remoteCatalogWriter = remoteCatalogWriter,
                    onProgress = { message, fraction ->
                        onProgress(StumpSyncProgress(message = message, fraction = fraction))
                    },
                )
            }
            activeAccessToken = fetchResult.accessToken
            activeRefreshToken = fetchResult.refreshToken
            latestSession = fetchResult.refreshedSession ?: latestSession
            val catalogFetch = fetchResult.value
            val syncOutcome = finalizeRemoteCatalogSync(
                sourceType = SourceType.STUMP,
                fetch = catalogFetch.fetch.supportedStumpCatalog(),
                remoteCatalogWriter = remoteCatalogWriter,
                progressSyncStore = progressSyncStore,
                nowEpochMillis = nowEpochMillis(),
                onProgress = { message, fraction ->
                    onProgress(StumpSyncProgress(message = message, fraction = fraction))
                },
                savingMessage = "Saving Stump catalog...",
                applyingProgressMessage = "Applying Stump progress...",
                itemOf = StumpMediaCatalogEntry::item,
                progressOf = StumpMediaCatalogEntry::progress,
                missingRemoteClearsLocal = StumpMediaCatalogEntry::isExplicitUnreadReset,
                remoteClearOverridesLocal = { false },
            ) { items ->
                val progressSync = pushDirtyProgress(
                    api = api,
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                    items = items,
                )
                activeAccessToken = progressSync.accessToken
                activeRefreshToken = progressSync.refreshToken
                RemoteCatalogProgressPushResult(
                    session = progressSync.refreshedSession,
                    failures = progressSync.value,
                )
            }
            latestSession = syncOutcome.session ?: latestSession
            StumpCatalogSyncResult(
                libraryCount = syncOutcome.libraryCount,
                itemCount = syncOutcome.itemCount,
                supportedFormatCounts = syncOutcome.supportedFormatCounts,
                libraries = catalogFetch.fetch.libraries,
                session = latestSession,
                progressPushFailures = syncOutcome.progressPushFailures,
                partialCatalogWarning = catalogFetch.warning,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private suspend fun pushDirtyProgress(
        api: StumpApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        items: List<ReadableItem>,
    ): StumpTokenRefreshResult<List<Throwable>> {
        val store = readerSessionStore ?: return StumpTokenRefreshResult(
            value = emptyList(),
            accessToken = initialAccessToken,
            refreshToken = initialRefreshToken,
            refreshedSession = null,
        )
        return pushDirtyStumpProgress(
            api = api,
            initialAccessToken = initialAccessToken,
            initialRefreshToken = initialRefreshToken,
            items = items,
            readerSessionStore = store,
            diagnostics = progressDiagnostics,
        )
    }

    private fun RemoteCatalogFetch<StumpMediaCatalogEntry>.supportedStumpCatalog(): RemoteCatalogFetch<StumpMediaCatalogEntry> =
        copy(
            entries = entries.filter { entry ->
                entry.item.format in STUMP_SUPPORTED_SYNC_FORMATS
            },
        )
}

data class StumpCatalogSyncRequest(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String? = null,
    val activeBaseUrl: String? = null,
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

data class StumpCatalogConnectionResult(
    val libraryCount: Int,
    val libraries: List<Library> = emptyList(),
    val session: StumpSession? = null,
    val activeBaseUrl: String? = null,
)

data class StumpSyncProgress(
    val message: String,
    val fraction: Float,
)

data class StumpCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val libraries: List<Library> = emptyList(),
    val session: StumpSession? = null,
    val activeBaseUrl: String? = null,
    val progressPushFailures: List<Throwable> = emptyList(),
    val partialCatalogWarning: StumpPartialCatalogWarning? = null,
)

private val STUMP_SUPPORTED_SYNC_FORMATS = setOf(
    FormatType.CBZ,
    FormatType.CBR,
)
