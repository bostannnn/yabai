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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface GrimmoryApiFactory {
    fun create(
        baseUrl: String,
    ): GrimmoryApi
}

/** Each sync hands [GrimmorySyncProgress] to its `onProgress` on the caller's thread, in order, before it returns. */
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
    // Decoding the book list (2,000 books, megabytes of JSON), comparing it with the saved library and the
    // uploads run here, not on the caller's (main) thread, which froze the app and its bar for seconds.
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    ): GrimmoryCatalogSyncResult = offCallerThread(onProgress) { progress -> syncCatalog(request, progress, refresh = false) }

    override suspend fun refresh(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit,
    ): GrimmoryCatalogSyncResult = offCallerThread(onProgress) { progress -> syncCatalog(request, progress, refresh = true) }

    /**
     * Runs [work] on [workDispatcher] and relays its progress to [onProgress] in the caller's context, in
     * order and all of it before returning, so the caller's state is only ever touched on its own thread.
     */
    private suspend fun <T> offCallerThread(
        onProgress: (GrimmorySyncProgress) -> Unit,
        work: suspend (onProgress: (GrimmorySyncProgress) -> Unit) -> T,
    ): T = coroutineScope {
        val steps = Channel<GrimmorySyncProgress>(Channel.UNLIMITED)
        launch { for (step in steps) onProgress(step) }
        try {
            withContext(workDispatcher) { work { step -> steps.trySend(step) } }
        } finally {
            steps.close()
        }
    }

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
                onProgress(GrimmorySyncSteps.connecting)
                val libraries = api.getLibraries(accessToken = token)
                onProgress(GrimmorySyncSteps.bookList)
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
            onProgress = { _, fraction -> onProgress(GrimmorySyncSteps.saving(fraction)) },
            savingMessage = GrimmorySyncSteps.saving(0f).message,
            applyingProgressMessage = GrimmorySyncSteps.saving(1f).message,
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
    override val baseUrl: String,
    val backupBaseUrl: String? = null,
    val activeBaseUrl: String? = null,
    override val username: String,
    val password: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
) : SourceSyncRequest

data class GrimmoryCatalogConnectionResult(
    val libraryCount: Int,
    val session: GrimmorySession? = null,
    val activeBaseUrl: String? = null,
)

data class GrimmorySyncProgress(
    val message: String,
    // Null while the step's length can't be known: the bar moves instead of filling (GrimmorySyncSteps).
    val fraction: Float?,
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
