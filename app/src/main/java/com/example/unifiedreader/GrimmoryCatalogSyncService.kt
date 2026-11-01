package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.enrichGrimmoryJournalCatalogMetadata
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

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

    /**
     * The library's refresh: the same catalog as [sync], saving only the books that changed (nothing
     * when none did), but the journal's reading history only for the books read since the last pull.
     */
    suspend fun refresh(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit = {},
    ): GrimmoryCatalogSyncResult = sync(request, onProgress)
}

class GrimmoryCatalogSyncService(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val journalRepository: JournalRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : GrimmoryCatalogSyncer {
    private val progressSyncStore: ProgressSyncStore? = readerSessionStore?.let(::ReaderSessionProgressStoreAdapter)
    private val syncUploads = GrimmoryCatalogSyncUploads(grimmoryApiFactory, readerSessionStore, journalRepository)

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
    ): GrimmoryCatalogSyncResult = syncCatalog(request, onProgress, refresh = false)

    override suspend fun refresh(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit,
    ): GrimmoryCatalogSyncResult = syncCatalog(request, onProgress, refresh = true)

    private suspend fun syncCatalog(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit,
        refresh: Boolean,
    ): GrimmoryCatalogSyncResult {
        // Only the fetch moves to the other address and starts over: once the catalog is saved, the uploads
        // after it retry on their own, so a server that stops answering then can't fail the sync.
        val fetched = runWithServerAddressFailover(
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
            val fetchResult = api.runWithTokenRefresh(
                initialRefreshToken = session?.refreshToken ?: request.refreshToken,
                initialAccessToken = accessToken,
            ) { token ->
                onProgress(GrimmorySyncProgress(message = "Loading Grimmory libraries...", fraction = 0.1f))
                val libraries = api.getLibraries(accessToken = token)
                onProgress(GrimmorySyncProgress(message = "Loading Grimmory books...", fraction = 0.55f))
                val bookEntries = enrichGrimmoryJournalCatalogMetadata(
                    api = api,
                    accessToken = token,
                    entries = api.getAllBookEntries(accessToken = token),
                )
                RemoteCatalogFetch(
                    libraries = libraries,
                    entries = bookEntries,
                )
            }
            GrimmoryFetchedCatalog(
                api = api,
                fetch = fetchResult.value,
                tokens = GrimmorySyncTokens(
                    accessToken = fetchResult.accessToken,
                    refreshToken = fetchResult.refreshToken,
                    session = fetchResult.refreshedSession ?: session,
                ),
            )
        }
        // A sync and a refresh both write only the books that changed, so covers stay put when none did.
        val syncOutcome = finalizeRemoteCatalogSync<GrimmoryBookCatalogEntry, GrimmorySession>(
            sourceType = SourceType.GRIMMORY,
            fetch = fetched.value.fetch,
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
            writeOnlyChanges = true,
        ) {
            // Uploaded below, after the catalog is saved, where a failure can't fail the sync.
            RemoteCatalogProgressPushResult(session = null, failures = emptyList())
        }
        val uploads = syncUploads.run(
            request = request,
            api = fetched.value.api,
            activeBaseUrl = fetched.activeBaseUrl,
            tokens = fetched.value.tokens,
            entries = fetched.value.fetch.entries,
            refresh = refresh,
            onProgress = onProgress,
        )
        return GrimmoryCatalogSyncResult(
            libraryCount = syncOutcome.libraryCount,
            itemCount = syncOutcome.itemCount,
            supportedFormatCounts = syncOutcome.supportedFormatCounts,
            session = uploads.tokens.session,
            activeBaseUrl = uploads.activeBaseUrl,
            progressPushFailures = uploads.failures,
            hasCatalogChanges = syncOutcome.hasCatalogChanges,
        )
    }
}

private class GrimmoryFetchedCatalog(
    val api: GrimmoryApi,
    val fetch: RemoteCatalogFetch<GrimmoryBookCatalogEntry>,
    val tokens: GrimmorySyncTokens,
)

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
    // False when a refresh found nothing to save: the library and its covers stay as they are.
    val hasCatalogChanges: Boolean = true,
)
