package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.ServerEntity
import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import org.json.JSONObject

fun interface GrimmoryApiFactory {
    fun create(
        baseUrl: String,
        serverId: String,
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
        val api = grimmoryApiFactory.create(
            baseUrl = request.baseUrl,
            serverId = request.serverId,
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
            val bookEntries = libraries.flatMap { library ->
                api.getBookEntries(
                    accessToken = token,
                    libraryId = library.id,
                )
            }
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
            incomingServerId = request.serverId,
        )
        remoteCatalogWriter.replaceServerCatalog(
            server = ServerEntity(
                id = request.serverId,
                displayName = request.displayName,
                baseUrl = request.baseUrl.trimEnd('/'),
                sourceType = SourceType.GRIMMORY.name,
            ),
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
        return GrimmoryCatalogSyncResult(
            libraryCount = libraries.size,
            itemCount = items.size,
            supportedFormatCounts = items.groupingBy(ReadableItem::format).eachCount(),
            session = latestSession,
        )
    }

    private fun pullProgress(
        entries: List<GrimmoryBookCatalogEntry>,
    ) {
        val store = readerSessionStore ?: return
        entries.forEach { entry ->
            val existing = store.getProgress(entry.item)
            if (
                existing?.syncState == SyncState.DIRTY ||
                existing?.syncState == SyncState.SYNCING ||
                existing?.syncState == SyncState.FAILED
            ) {
                return@forEach
            }
            val progress = entry.progress
            if (progress == null) {
                if (existing?.syncState == SyncState.CLEAN) {
                    store.clearProgress(entry.item.id)
                }
                return@forEach
            }
            store.saveProgress(
                item = entry.item,
                locator = progress.toReaderLocator(),
                isFinished = progress.isFinished,
                syncState = SyncState.CLEAN,
                pageCount = entry.item.pageCount,
                updatedAtEpochMillis = progress.updatedAtEpochMillis ?: nowEpochMillis(),
            )
        }
    }

    private suspend fun clearStaleGrimmoryProgress(
        incomingServerId: String,
    ) {
        val store = readerSessionStore ?: return
        remoteCatalogWriter.getItemsForSourceType(SourceType.GRIMMORY)
            .asSequence()
            .filter { item -> item.serverId != incomingServerId }
            .map(ReadableItem::id)
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
        val itemsById = items.associateBy(ReadableItem::id)
        var activeAccessToken = initialAccessToken
        var activeRefreshToken = initialRefreshToken
        var latestSession: GrimmorySession? = null
        store.progressByItemIdFlow.value.forEach { (itemId, progress) ->
            val item = itemsById[itemId] ?: return@forEach
            if (progress.syncState != SyncState.DIRTY && progress.syncState != SyncState.FAILED) return@forEach
            val update = progress.toGrimmoryProgressUpdate(item) ?: return@forEach
            store.saveProgress(
                item = item,
                locator = progress.toReaderLocator(),
                isFinished = progress.isFinished,
                syncState = SyncState.SYNCING,
                pageCount = store.getSession(item)?.pageCount,
                updatedAtEpochMillis = progress.updatedAtEpochMillis,
            )
            runCatching {
                api.runWithTokenRefresh(
                    initialAccessToken = activeAccessToken,
                    initialRefreshToken = activeRefreshToken,
                ) { token ->
                    api.updateProgress(
                        accessToken = token,
                        progress = update,
                    )
                }
            }.onSuccess {
                activeAccessToken = it.accessToken
                activeRefreshToken = it.refreshToken
                latestSession = it.refreshedSession ?: latestSession
                store.saveProgress(
                    item = item,
                    locator = progress.toReaderLocator(),
                    isFinished = progress.isFinished,
                    syncState = SyncState.CLEAN,
                    pageCount = store.getSession(item)?.pageCount,
                    updatedAtEpochMillis = progress.updatedAtEpochMillis,
                )
            }.onFailure {
                store.saveProgress(
                    item = item,
                    locator = progress.toReaderLocator(),
                    isFinished = progress.isFinished,
                    syncState = SyncState.FAILED,
                    pageCount = store.getSession(item)?.pageCount,
                    updatedAtEpochMillis = progress.updatedAtEpochMillis,
                )
            }
        }
        return GrimmoryTokenRefreshResult(
            value = Unit,
            accessToken = activeAccessToken,
            refreshToken = activeRefreshToken,
            refreshedSession = latestSession,
        )
    }
}

private data class GrimmorySyncFetch(
    val libraries: List<com.example.unifiedreader.core.model.Library>,
    val entries: List<GrimmoryBookCatalogEntry>,
)

data class GrimmoryCatalogSyncRequest(
    val serverId: String,
    val displayName: String,
    val baseUrl: String,
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
)

private fun ReadingProgress.toReaderLocator(): ReaderLocator = ReaderLocator(
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
)

private fun ReaderProgressSnapshot.toReaderLocator(): ReaderLocator = ReaderLocator(
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
)

private fun ReaderProgressSnapshot.toGrimmoryProgressUpdate(
    item: ReadableItem,
): GrimmoryProgressUpdate? {
    val positionData = when (locatorType) {
        LocatorType.CFI -> cfi?.toGrimmoryEpubPosition()?.cfi
        LocatorType.PAGE -> page?.let { (it + 1).coerceAtLeast(1).toString() }
        LocatorType.PERCENT -> percent?.let { (it.coerceIn(0.0, 1.0) * 100.0).toInt().toString() }
    } ?: return null
    val epubPosition = cfi?.toGrimmoryEpubPosition()
    return GrimmoryProgressUpdate(
        bookId = item.id,
        bookFileId = item.remoteFileId,
        bookType = item.remoteFileType ?: item.format.name,
        positionData = positionData,
        positionHref = epubPosition?.href,
        percent = percent ?: 0.0,
    )
}

private data class GrimmoryEpubPosition(
    val cfi: String,
    val href: String?,
)

private fun String.toGrimmoryEpubPosition(): GrimmoryEpubPosition {
    if (!trimStart().startsWith("{")) {
        return GrimmoryEpubPosition(cfi = this, href = null)
    }
    return runCatching {
        val locator = JSONObject(this)
        val href = locator.optString("href").takeIf(String::isNotBlank)
        val fragments = locator.optJSONObject("locations")?.optJSONArray("fragments")
        val cfi = fragments?.optString(0)?.takeIf(String::isNotBlank)
            ?: return@runCatching GrimmoryEpubPosition(cfi = this, href = href)
        GrimmoryEpubPosition(cfi = cfi, href = href)
    }.getOrElse {
        GrimmoryEpubPosition(cfi = this, href = null)
    }
}
