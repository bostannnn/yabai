package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.pullKavitaJournalCache
import com.example.unifiedreader.data.kavita.pushDirtyKavitaJournalEntries
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlin.coroutines.cancellation.CancellationException

fun interface KavitaApiFactory {
    fun create(
        baseUrl: String,
    ): KavitaApi
}

interface KavitaCatalogSyncer {
    suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult

    suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit = {},
    ): KavitaCatalogSyncResult
}

class KavitaCatalogSyncService(
    private val kavitaApiFactory: KavitaApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val journalRepository: JournalRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : KavitaCatalogSyncer {
    private val progressSyncStore: ProgressSyncStore? = readerSessionStore?.let(::ReaderSessionProgressStoreAdapter)

    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Kavita connection requires a password or saved access token.")
            val refreshResult = api.runWithTokenRefresh(
                initialAccessToken = accessToken,
                initialRefreshToken = session?.refreshToken ?: request.refreshToken,
                initialApiKey = session?.apiKey ?: request.apiKey,
            ) { token, _ ->
                api.getLibraries(token)
            }
            KavitaCatalogConnectionResult(
                libraryCount = refreshResult.value.size,
                libraries = refreshResult.value,
                session = refreshResult.refreshedSession ?: session,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult {
        val result = runWithServerAddressFailover(
            baseUrl = request.baseUrl,
            backupBaseUrl = request.backupBaseUrl,
            activeBaseUrl = request.activeBaseUrl,
        ) { selectedBaseUrl ->
            val api = kavitaApiFactory.create(selectedBaseUrl)
            val session = request.password?.takeIf(String::isNotBlank)?.let { password ->
                api.login(
                    username = request.username,
                    password = password,
                )
            }
            val accessToken = session?.accessToken ?: request.accessToken
                ?: error("A Kavita sync requires a password or saved access token.")
            var activeAccessToken = accessToken
            var activeRefreshToken = session?.refreshToken ?: request.refreshToken
            var activeApiKey = session?.apiKey ?: request.apiKey
            var latestSession: KavitaSession? = session
            var catalogSyncGroups: List<RemoteCatalogSyncGroupEntity> = emptyList()
            val fetchResult = api.runWithTokenRefresh(
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = activeApiKey,
            ) { token, apiKey ->
                onProgress(KavitaSyncProgress(message = "Loading Kavita libraries...", fraction = 0.05f))
                val libraries = api.getLibraries(token)
                onProgress(KavitaSyncProgress(message = "Loading Kavita series...", fraction = 0.15f))
                val series = api.getSeries(
                    accessToken = token,
                    apiKey = apiKey,
                )
                val catalogFetch = fetchKavitaCatalog(
                    api = api,
                    accessToken = token,
                    libraries = libraries,
                    series = series,
                    remoteCatalogWriter = remoteCatalogWriter,
                    forceFullRefresh = request.syncMode == KavitaCatalogSyncMode.FULL,
                    nowEpochMillis = nowEpochMillis(),
                    onProgress = onProgress,
                )
                catalogSyncGroups = catalogFetch.syncGroups
                RemoteCatalogFetch(
                    libraries = libraries,
                    entries = catalogFetch.entries,
                )
            }
            activeAccessToken = fetchResult.accessToken
            activeRefreshToken = fetchResult.refreshToken
            activeApiKey = fetchResult.refreshedSession?.apiKey ?: activeApiKey
            latestSession = fetchResult.refreshedSession ?: latestSession
            val syncOutcome = finalizeRemoteCatalogSync(
                sourceType = SourceType.KAVITA,
                fetch = fetchResult.value,
                remoteCatalogWriter = remoteCatalogWriter,
                progressSyncStore = progressSyncStore,
                nowEpochMillis = nowEpochMillis(),
                onProgress = { message, fraction ->
                    onProgress(KavitaSyncProgress(message = message, fraction = fraction))
                },
                savingMessage = "Saving Kavita catalog...",
                applyingProgressMessage = "Applying Kavita progress...",
                itemOf = KavitaChapterCatalogEntry::item,
                progressOf = KavitaChapterCatalogEntry::progress,
                missingRemoteClearsLocal = KavitaChapterCatalogEntry::isExplicitUnreadReset,
                remoteClearOverridesLocal = { false },
                syncGroups = catalogSyncGroups,
            ) { items ->
                val progressSync = pushDirtyProgress(
                    api = api,
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                    initialApiKey = activeApiKey,
                    items = items,
                )
                activeAccessToken = progressSync.accessToken
                activeRefreshToken = progressSync.refreshToken
                activeApiKey = progressSync.refreshedSession?.apiKey ?: activeApiKey
                RemoteCatalogProgressPushResult(
                    session = progressSync.refreshedSession,
                    failures = progressSync.value,
                )
            }
            latestSession = syncOutcome.session ?: latestSession
            runCatching {
                val journalPull = pullJournalCache(
                    api = api,
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                    initialApiKey = activeApiKey,
                    libraries = fetchResult.value.libraries,
                    entries = fetchResult.value.entries,
                )
                activeAccessToken = journalPull.accessToken
                activeRefreshToken = journalPull.refreshToken
                activeApiKey = journalPull.refreshedSession?.apiKey ?: activeApiKey
                latestSession = journalPull.refreshedSession ?: latestSession
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
            val journalSync = pushDirtyJournalEntries(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = activeApiKey,
                entries = fetchResult.value.entries.map(KavitaChapterCatalogEntry::item),
            )
            activeAccessToken = journalSync.accessToken
            activeRefreshToken = journalSync.refreshToken
            activeApiKey = journalSync.refreshedSession?.apiKey ?: activeApiKey
            latestSession = journalSync.refreshedSession ?: latestSession
            KavitaCatalogSyncResult(
                libraryCount = syncOutcome.libraryCount,
                itemCount = syncOutcome.itemCount,
                supportedFormatCounts = syncOutcome.supportedFormatCounts,
                libraries = fetchResult.value.libraries,
                session = latestSession,
                progressPushFailures = syncOutcome.progressPushFailures,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private suspend fun pushDirtyProgress(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        items: List<ReadableItem>,
    ): KavitaTokenRefreshResult<List<Throwable>> {
        val store = readerSessionStore ?: return KavitaTokenRefreshResult(
            value = emptyList(),
            accessToken = initialAccessToken,
            refreshToken = initialRefreshToken,
            refreshedSession = null,
        )
        return pushDirtyKavitaProgress(
            api = api,
            initialAccessToken = initialAccessToken,
            initialRefreshToken = initialRefreshToken,
            initialApiKey = initialApiKey,
            items = items,
            readerSessionStore = store,
        )
    }

    private suspend fun pushDirtyJournalEntries(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        entries: List<ReadableItem>,
    ): KavitaTokenRefreshResult<Unit> = api.runWithTokenRefresh(
        initialAccessToken = initialAccessToken,
        initialRefreshToken = initialRefreshToken,
        initialApiKey = initialApiKey,
    ) { accessToken, _ ->
        pushDirtyKavitaJournalEntries(
            api = api,
            accessToken = accessToken,
            entries = entries,
            journalRepository = journalRepository,
        )
    }

    private suspend fun pullJournalCache(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        libraries: List<com.example.unifiedreader.core.model.Library>,
        entries: List<KavitaChapterCatalogEntry>,
    ): KavitaTokenRefreshResult<Unit> = api.runWithTokenRefresh(
        initialAccessToken = initialAccessToken,
        initialRefreshToken = initialRefreshToken,
        initialApiKey = initialApiKey,
    ) { accessToken, _ ->
        pullKavitaJournalCache(
            api = api,
            accessToken = accessToken,
            libraries = libraries,
            entries = entries,
            journalRepository = journalRepository,
        )
    }

    internal suspend fun fetchSeriesEntries(
        api: KavitaApi,
        accessToken: String,
        series: List<KavitaSeriesEntry>,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): List<KavitaChapterCatalogEntry> = fetchSeriesEntriesWithProgress(
        sourceLabel = "Kavita",
        series = series,
        onProgress = { message, fraction ->
            onProgress(KavitaSyncProgress(message = message, fraction = fraction))
        },
    ) { seriesEntry ->
        api.getSeriesDetail(
            accessToken = accessToken,
            series = seriesEntry,
        )
    }
}

data class KavitaCatalogSyncRequest(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String? = null,
    val activeBaseUrl: String? = null,
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val syncMode: KavitaCatalogSyncMode = KavitaCatalogSyncMode.FAST,
)

enum class KavitaCatalogSyncMode {
    /**
     * Best-effort incremental sync. It skips series whose stored fingerprint still matches the
     * current series metadata plus Kavita's recent-update marker feed. Full resync remains the
     * authoritative path for exact cleanup of old marker-window misses.
     */
    FAST,

    /**
     * Authoritative catalog refresh. It refetches every series detail and does not depend on
     * Kavita's recent-update marker endpoint.
     */
    FULL,
}

data class KavitaCatalogConnectionResult(
    val libraryCount: Int,
    val libraries: List<Library> = emptyList(),
    val session: KavitaSession? = null,
    val activeBaseUrl: String? = null,
)

data class KavitaSyncProgress(
    val message: String,
    val fraction: Float,
)

data class KavitaCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val libraries: List<Library> = emptyList(),
    val session: KavitaSession? = null,
    val activeBaseUrl: String? = null,
    val progressPushFailures: List<Throwable> = emptyList(),
)
