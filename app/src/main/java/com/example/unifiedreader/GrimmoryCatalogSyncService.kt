package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

fun interface GrimmoryApiFactory {
    fun create(
        baseUrl: String,
    ): GrimmoryApi
}

interface GrimmoryCatalogSyncer {
    suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogSyncResult
}

class GrimmoryCatalogSyncService(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val remoteCatalogWriter: RemoteCatalogWriter,
    private val readerSessionStore: ReaderSessionStore? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : GrimmoryCatalogSyncer {
    override suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
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
            val entries = api.runWithTokenRefresh(
                initialRefreshToken = activeRefreshToken,
                initialAccessToken = activeAccessToken,
            ) { token ->
                val libraries = api.getLibraries(accessToken = token)
                val bookEntries = api.getAllBookEntries(accessToken = token)
                GrimmorySyncFetch(
                    libraries = libraries,
                    entries = bookEntries,
                )
            }
            activeAccessToken = entries.accessToken
            activeRefreshToken = entries.refreshToken
            latestSession = entries.refreshedSession ?: latestSession
            val libraries = entries.value.libraries
            val catalogEntries = entries.value.entries
            val items = catalogEntries.map(GrimmoryBookCatalogEntry::item)
            val updatedAtEpochMillis = nowEpochMillis()
            clearStaleGrimmoryProgress(
                incomingItemIds = items.mapTo(mutableSetOf(), ReadableItem::id),
            )
            remoteCatalogWriter.replaceSourceCatalog(
                sourceType = SourceType.GRIMMORY,
                libraries = libraries.map { library -> library.toEntity() },
                items = items.map { item ->
                    item.toEntity(updatedAtEpochMillis = updatedAtEpochMillis)
                },
            )
            pullProgress(catalogEntries)
            val progressSync = pushDirtyProgress(
                api = api,
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                items = items,
            )
            latestSession = progressSync.refreshedSession ?: latestSession
            GrimmoryCatalogSyncResult(
                libraryCount = libraries.size,
                itemCount = items.size,
                supportedFormatCounts = items.groupingBy(ReadableItem::format).eachCount(),
                session = latestSession,
            )
        }
        return result.value.copy(activeBaseUrl = result.activeBaseUrl)
    }

    private fun pullProgress(
        entries: List<GrimmoryBookCatalogEntry>,
    ) {
        val store = readerSessionStore ?: return
        entries.forEach { entry ->
            applyRemoteProgressSyncDecision(
                store = store,
                item = entry.item,
                pageCount = entry.item.pageCount,
                decision = decideRemoteProgressSync(
                    local = store.getProgress(entry.item),
                    remote = entry.progress,
                    nowEpochMillis = nowEpochMillis(),
                    missingRemoteClearsLocal = entry.isExplicitUnreadReset,
                ),
            )
        }
    }

    private suspend fun clearStaleGrimmoryProgress(
        incomingItemIds: Set<String>,
    ) {
        val store = readerSessionStore ?: return
        remoteCatalogWriter.getItemsForSourceType(SourceType.GRIMMORY)
            .asSequence()
            .map(ReadableItem::id)
            .filterNot(incomingItemIds::contains)
            .distinct()
            .forEach(store::clearItem)
    }

    private suspend fun pushDirtyProgress(
        api: GrimmoryApi,
        initialAccessToken: String,
        initialRefreshToken: String?,
        items: List<ReadableItem>,
    ): GrimmoryTokenRefreshResult<Unit> {
        val store = readerSessionStore ?: return GrimmoryTokenRefreshResult(
            value = Unit,
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
}

private data class GrimmorySyncFetch(
    val libraries: List<com.example.unifiedreader.core.model.Library>,
    val entries: List<GrimmoryBookCatalogEntry>,
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

data class GrimmoryCatalogSyncResult(
    val libraryCount: Int,
    val itemCount: Int,
    val supportedFormatCounts: Map<FormatType, Int>,
    val session: GrimmorySession? = null,
    val activeBaseUrl: String? = null,
)
