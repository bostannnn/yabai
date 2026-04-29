package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.pullGrimmoryJournalCache
import com.example.unifiedreader.data.grimmory.pushDirtyGrimmoryJournalEntries
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlin.coroutines.cancellation.CancellationException

fun interface GrimmoryApiFactory {
    fun create(
        baseUrl: String,
    ): GrimmoryApi
}

interface GrimmoryCatalogSyncer {
    suspend fun connect(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogConnectionResult

    suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit = {},
    ): GrimmoryCatalogSyncResult
}

class GrimmoryCatalogSyncService(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val journalRepository: JournalRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : GrimmoryCatalogSyncer {
    private val progressSyncStore: ProgressSyncStore? = readerSessionStore?.let(::ReaderSessionProgressStoreAdapter)

    override suspend fun connect(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogConnectionResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = grimmoryApiFactory.create(
                baseUrl = selectedBaseUrl,
            )
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Grimmory connection requires a password or saved access token.")
            val refreshResult = api.runWithTokenRefresh(
                initialRefreshToken = session?.refreshToken ?: request.refreshToken,
                initialAccessToken = accessToken,
            ) { token ->
                api.getLibraries(accessToken = token)
            }
            GrimmoryCatalogConnectionResult(
                libraryCount = refreshResult.value.size,
                session = refreshResult.refreshedSession ?: session,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    override suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit,
    ): GrimmoryCatalogSyncResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = grimmoryApiFactory.create(
                baseUrl = selectedBaseUrl,
            )
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Grimmory sync requires a password or saved access token.")
            var activeAccessToken = accessToken
            var activeRefreshToken = session?.refreshToken ?: request.refreshToken
            var latestSession: GrimmorySession? = session
            val fetchResult = api.runWithTokenRefresh(
                initialRefreshToken = activeRefreshToken,
                initialAccessToken = activeAccessToken,
            ) { token ->
                onProgress(GrimmorySyncProgress(message = "Loading Grimmory libraries...", fraction = 0.1f))
                val libraries = api.getLibraries(accessToken = token)
                onProgress(GrimmorySyncProgress(message = "Loading Grimmory books...", fraction = 0.55f))
                val bookEntries = api.getAllBookEntries(accessToken = token)
                RemoteCatalogFetch(
                    libraries = libraries,
                    entries = bookEntries,
                )
            }
            activeAccessToken = fetchResult.accessToken
            activeRefreshToken = fetchResult.refreshToken
            latestSession = fetchResult.refreshedSession ?: latestSession
            val syncOutcome = finalizeRemoteCatalogSync(
                sourceType = SourceType.GRIMMORY,
                fetch = fetchResult.value,
                remoteCatalogWriter = remoteCatalogWriter,
                progressSyncStore = progressSyncStore,
                nowEpochMillis = nowEpochMillis(),
                onProgress = { message, fraction ->
                    onProgress(GrimmorySyncProgress(message = message, fraction = fraction))
                },
                savingMessage = "Saving Grimmory catalog...",
                applyingProgressMessage = "Applying Grimmory progress...",
                itemOf = GrimmoryBookCatalogEntry::item,
                progressOf = GrimmoryBookCatalogEntry::progress,
                missingRemoteClearsLocal = GrimmoryBookCatalogEntry::isExplicitUnreadReset,
                remoteClearOverridesLocal = { false },
            ) { items ->
                onProgress(GrimmorySyncProgress(message = "Uploading Grimmory progress...", fraction = 0.98f))
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
            runCatching {
                onProgress(GrimmorySyncProgress(message = "Loading Grimmory journal...", fraction = 0.99f))
                val journalPull = pullJournalCache(
                    api = api,
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                    entries = fetchResult.value.entries,
                )
                activeAccessToken = journalPull.accessToken
                activeRefreshToken = journalPull.refreshToken
                latestSession = journalPull.refreshedSession ?: latestSession
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
            onProgress(GrimmorySyncProgress(message = "Syncing Grimmory journal...", fraction = 0.995f))
            val journalSync = pushDirtyJournalEntries(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                entries = fetchResult.value.entries.map(GrimmoryBookCatalogEntry::item),
            )
            activeAccessToken = journalSync.accessToken
            activeRefreshToken = journalSync.refreshToken
            latestSession = journalSync.refreshedSession ?: latestSession
            GrimmoryCatalogSyncResult(
                libraryCount = syncOutcome.libraryCount,
                itemCount = syncOutcome.itemCount,
                supportedFormatCounts = syncOutcome.supportedFormatCounts,
                session = latestSession,
                progressPushFailures = syncOutcome.progressPushFailures,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private suspend fun pushDirtyProgress(
        api: GrimmoryApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        items: List<ReadableItem>,
    ): GrimmoryTokenRefreshResult<List<Throwable>> {
        val store = readerSessionStore ?: return GrimmoryTokenRefreshResult(
            value = emptyList(),
            accessToken = initialAccessToken,
            refreshToken = initialRefreshToken,
            refreshedSession = null,
        )
        return pushDirtyGrimmoryProgress(
            api = api,
            initialAccessToken = initialAccessToken,
            initialRefreshToken = initialRefreshToken,
            items = items,
            readerSessionStore = store,
        )
    }

    private suspend fun pushDirtyJournalEntries(
        api: GrimmoryApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        entries: List<ReadableItem>,
    ): GrimmoryTokenRefreshResult<Unit> = api.runWithTokenRefresh(
        initialAccessToken = initialAccessToken,
        initialRefreshToken = initialRefreshToken,
    ) { accessToken ->
        pushDirtyGrimmoryJournalEntries(
            api = api,
            accessToken = accessToken,
            entries = entries,
            journalRepository = journalRepository,
        )
    }

    private suspend fun pullJournalCache(
        api: GrimmoryApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        entries: List<GrimmoryBookCatalogEntry>,
    ): GrimmoryTokenRefreshResult<Unit> = api.runWithTokenRefresh(
        initialAccessToken = initialAccessToken,
        initialRefreshToken = initialRefreshToken,
    ) { accessToken ->
        pullGrimmoryJournalCache(
            api = api,
            accessToken = accessToken,
            entries = entries,
            journalRepository = journalRepository,
        )
    }
}

data class GrimmoryCatalogSyncRequest(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String? = null,
    val activeBaseUrl: String? = null,
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

data class GrimmoryCatalogConnectionResult(
    val libraryCount: Int,
    val session: GrimmorySession? = null,
    val activeBaseUrl: String? = null,
)

data class GrimmorySyncProgress(
    val message: String,
    val fraction: Float,
)

data class GrimmoryCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val session: GrimmorySession? = null,
    val activeBaseUrl: String? = null,
    val progressPushFailures: List<Throwable> = emptyList(),
)
