package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.pullGrimmoryJournalCache
import com.example.unifiedreader.data.grimmory.pushDirtyGrimmoryJournalEntries
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.journal.JournalRepository
import kotlin.coroutines.cancellation.CancellationException

/** A sync's tokens: a step that refreshes them hands the new ones to the next, and the last session is saved. */
internal data class GrimmorySyncTokens(
    val accessToken: String,
    val refreshToken: String?,
    // Set once a login or a token refresh made a session to save.
    val session: GrimmorySession?,
) {
    fun after(result: GrimmoryTokenRefreshResult<*>): GrimmorySyncTokens = GrimmorySyncTokens(
        accessToken = result.accessToken,
        refreshToken = result.refreshToken,
        session = result.refreshedSession ?: session,
    )
}

internal data class GrimmorySyncUploadsResult(
    val tokens: GrimmorySyncTokens,
    val activeBaseUrl: String,
    val failures: List<Throwable>,
)

/**
 * What a Grimmory sync does once the catalog is saved: uploads the progress waiting to go, loads the journal's
 * reading history and uploads journal changes. The library is saved by then, so none of this fails the sync or
 * redoes the catalog: an address that stops answering is tried again on the other one, and whatever still
 * fails comes back as not uploaded, and waits for the next sync or flush.
 */
internal class GrimmoryCatalogSyncUploads(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val readerSessionStore: ReaderSessionStore?,
    private val journalRepository: JournalRepository?,
) {
    suspend fun run(
        request: GrimmoryCatalogSyncRequest,
        api: GrimmoryApi,
        activeBaseUrl: String,
        tokens: GrimmorySyncTokens,
        entries: List<GrimmoryBookCatalogEntry>,
        // A library refresh: reading history for only the books read since the last pull.
        refresh: Boolean,
        onProgress: (GrimmorySyncProgress) -> Unit,
    ): GrimmorySyncUploadsResult {
        var latestTokens = tokens
        val items = entries.map(GrimmoryBookCatalogEntry::item)
        val uploads = try {
            runWithServerAddressFailover(
                baseUrl = request.baseUrl,
                backupBaseUrl = request.backupBaseUrl,
                activeBaseUrl = activeBaseUrl,
            ) { selectedBaseUrl ->
                val selectedApi = if (selectedBaseUrl == activeBaseUrl) api else grimmoryApiFactory.create(selectedBaseUrl)
                onProgress(GrimmorySyncSteps.uploadingProgress)
                val progressPush = pushDirtyProgress(selectedApi, latestTokens, items)
                latestTokens = latestTokens.after(progressPush)
                onProgress(GrimmorySyncSteps.readingHistory(loaded = 0, total = 0))
                pullJournal(selectedApi, latestTokens, entries, changedBooksOnly = refresh) { loaded, total ->
                    onProgress(GrimmorySyncSteps.readingHistory(loaded = loaded, total = total))
                }?.let { journalPull -> latestTokens = latestTokens.after(journalPull) }
                onProgress(GrimmorySyncSteps.uploadingJournal)
                val journalPush = selectedApi.runWithTokenRefresh(
                    initialAccessToken = latestTokens.accessToken,
                    initialRefreshToken = latestTokens.refreshToken,
                ) { accessToken ->
                    pushDirtyGrimmoryJournalEntries(
                        api = selectedApi,
                        accessToken = accessToken,
                        entries = items,
                        journalRepository = journalRepository,
                        itemsAreWholeCatalog = true,
                    )
                }
                latestTokens = latestTokens.after(journalPush)
                progressPush.value + journalPush.value
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return GrimmorySyncUploadsResult(tokens = latestTokens, activeBaseUrl = activeBaseUrl, failures = listOf(error))
        }
        return GrimmorySyncUploadsResult(
            tokens = latestTokens,
            activeBaseUrl = uploads.activeBaseUrl,
            failures = uploads.value,
        )
    }

    private suspend fun pushDirtyProgress(
        api: GrimmoryApi,
        tokens: GrimmorySyncTokens,
        items: List<ReadableItem>,
    ): GrimmoryTokenRefreshResult<List<Throwable>> {
        val store = readerSessionStore ?: return GrimmoryTokenRefreshResult(
            value = emptyList(),
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            refreshedSession = null,
        )
        return pushDirtyGrimmoryProgress(
            api = api,
            initialAccessToken = tokens.accessToken,
            initialRefreshToken = tokens.refreshToken,
            items = items,
            readerSessionStore = store,
        )
    }

    // The journal's history is a cache: when it doesn't load, the journal keeps what it had.
    private suspend fun pullJournal(
        api: GrimmoryApi,
        tokens: GrimmorySyncTokens,
        entries: List<GrimmoryBookCatalogEntry>,
        changedBooksOnly: Boolean,
        onBookHistoryLoaded: (loaded: Int, total: Int) -> Unit,
    ): GrimmoryTokenRefreshResult<Unit>? = try {
        api.runWithTokenRefresh(
            initialAccessToken = tokens.accessToken,
            initialRefreshToken = tokens.refreshToken,
        ) { accessToken ->
            pullGrimmoryJournalCache(
                api = api,
                accessToken = accessToken,
                entries = entries,
                journalRepository = journalRepository,
                changedBooksOnly = changedBooksOnly,
                onBookHistoryLoaded = onBookHistoryLoaded,
            )
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        null
    }
}
