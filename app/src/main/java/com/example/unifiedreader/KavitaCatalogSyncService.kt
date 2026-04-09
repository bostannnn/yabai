package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

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
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : KavitaCatalogSyncer {
    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult {
        val api = kavitaApiFactory.create(request.baseUrl)
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
        return KavitaCatalogConnectionResult(
            libraryCount = refreshResult.value.size,
            session = refreshResult.refreshedSession ?: session,
        )
    }

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult {
        val api = kavitaApiFactory.create(request.baseUrl)
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
            val entries = fetchSeriesEntries(
                api = api,
                accessToken = token,
                series = series,
                onProgress = onProgress,
            )
            RemoteCatalogFetch(
                libraries = libraries,
                entries = entries,
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
            readerSessionStore = readerSessionStore,
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
            progressSync.refreshedSession
        }
        latestSession = syncOutcome.session ?: latestSession
        return KavitaCatalogSyncResult(
            libraryCount = syncOutcome.libraryCount,
            itemCount = syncOutcome.itemCount,
            supportedFormatCounts = syncOutcome.supportedFormatCounts,
            session = latestSession,
        )
    }

    private suspend fun pushDirtyProgress(
        api: KavitaApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        initialApiKey: String?,
        items: List<ReadableItem>,
    ): KavitaTokenRefreshResult<Unit> {
        val store = readerSessionStore ?: return KavitaTokenRefreshResult(
            value = Unit,
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

    private suspend fun fetchSeriesEntries(
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
    val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
)

data class KavitaCatalogConnectionResult(
    val libraryCount: Int,
    val session: KavitaSession? = null,
)

data class KavitaSyncProgress(
    val message: String,
    val fraction: Float,
)

data class KavitaCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val session: KavitaSession? = null,
)
