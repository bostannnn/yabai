package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicReaderFormats
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaFileDimensionDto
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class KavitaPageSource(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
    previewStore: PersistentComicPagePreviewStore? = null,
    readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
) : RemoteComicPageSource<KavitaStoredSession>(
    sourceType = com.example.unifiedreader.core.model.SourceType.KAVITA,
    supportedFormats = ComicReaderFormats,
    missingSessionMessage = "Connect to Kavita before reading remote comics.",
    ioDispatcher = ioDispatcher,
    scope = scope,
    previewStore = previewStore,
    readerWorkCoordinator = readerWorkCoordinator,
) {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)
    private val metadataByItemKey = mutableMapOf<String, Map<String, ComicPageMetadata>>()
    private val inFlightMetadataByItemKey = mutableMapOf<String, Deferred<KavitaMetadataLoadResult>>()
    private val metadataLock = Any()

    override val pagePreviewStrategy: RemoteComicPagePreviewStrategy =
        RemoteComicPagePreviewStrategy.PageImagePreview

    override fun activeSessionFor(
        item: ReadableItem,
    ): KavitaStoredSession? = kavitaCredentialStore.activeSessionFor(item)

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = loadKavitaPageMetadata(item)[page.id]
        ?: super.loadPageMetadata(item, page)

    override suspend fun downloadPageBytes(
        session: KavitaStoredSession,
        item: ReadableItem,
        page: ComicPageSpec,
    ): ByteArray? {
        val chapterId = item.remoteId ?: return null
        val pageIndex = page.id.toIntOrNull() ?: return null
        return runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            sessionRequestRunner.run(
                api = api,
                session = activeSession,
            ) { accessToken, apiKey ->
                api.downloadPageImage(
                    accessToken = accessToken,
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    apiKey = apiKey,
                )
            }
        }.also {
            remoteSourceConnectivityPolicy.recordReaderContentSuccess(com.example.unifiedreader.core.model.SourceType.KAVITA)
        }
    }

    override fun onReaderContentFailure(
        item: ReadableItem,
        throwable: Throwable,
    ) {
        remoteSourceConnectivityPolicy.recordReaderContentFailure(
            sourceType = com.example.unifiedreader.core.model.SourceType.KAVITA,
            throwable = throwable,
        )
    }

    override fun clear() {
        super.clear()
        synchronized(metadataLock) {
            inFlightMetadataByItemKey.values.forEach { request ->
                request.cancel()
            }
            inFlightMetadataByItemKey.clear()
            metadataByItemKey.clear()
        }
    }

    private suspend fun loadKavitaPageMetadata(
        item: ReadableItem,
    ): Map<String, ComicPageMetadata> {
        val session = activeSessionFor(item) ?: return emptyMap()
        val cacheKey = item.kavitaMetadataCacheKey(
            baseUrl = session.metadataCacheBaseUrl(),
        )
        val request = synchronized(metadataLock) {
            metadataByItemKey[cacheKey]?.let { return it }
            inFlightMetadataByItemKey[cacheKey] ?: scope.async {
                fetchKavitaPageMetadata(
                    item = item,
                    session = session,
                ).also { result ->
                    if (result.cacheable) {
                        synchronized(metadataLock) {
                            metadataByItemKey[cacheKey] = result.metadata
                        }
                    }
                }
            }.also { deferred ->
                inFlightMetadataByItemKey[cacheKey] = deferred
                deferred.invokeOnCompletion {
                    synchronized(metadataLock) {
                        if (inFlightMetadataByItemKey[cacheKey] === deferred) {
                            inFlightMetadataByItemKey.remove(cacheKey)
                        }
                    }
                }
            }
        }
        val result = request.await()
        return result.metadata
    }

    private suspend fun fetchKavitaPageMetadata(
        item: ReadableItem,
        session: KavitaStoredSession,
    ): KavitaMetadataLoadResult {
        val chapterId = item.remoteId ?: return emptyMetadataResult()
        val metadata = try {
            runWithStoredSessionAddressFailover(
                sessionStore = kavitaCredentialStore,
                session = session,
                createApi = kavitaApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                sessionRequestRunner.run(
                    api = api,
                    session = activeSession,
                ) { accessToken, _ ->
                    api.getChapterInfo(
                        accessToken = accessToken,
                        chapterId = chapterId,
                        includeDimensions = true,
                    )
                }
            }.toComicPageMetadataById(
                pageCount = item.pageCount ?: 0,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return emptyMetadataResult(cacheable = false)
        }
        return KavitaMetadataLoadResult(
            metadata = metadata,
            cacheable = true,
        )
    }

    private fun emptyMetadataResult(
        cacheable: Boolean = false,
    ): KavitaMetadataLoadResult = KavitaMetadataLoadResult(
        metadata = emptyMap(),
        cacheable = cacheable,
    )
}

private data class KavitaMetadataLoadResult(
    val metadata: Map<String, ComicPageMetadata>,
    val cacheable: Boolean,
)

private fun KavitaChapterInfoDto.toComicPageMetadataById(
    pageCount: Int,
): Map<String, ComicPageMetadata> {
    val dimensions = pageDimensions.orEmpty()
    if (dimensions.isEmpty()) return emptyMap()
    val pageNumbers = dimensions.mapNotNull(KavitaFileDimensionDto::pageNumber)
    val usesOneBasedPageNumbers = pageNumbers.isNotEmpty() && 0 !in pageNumbers && pageNumbers.minOrNull() == 1
    return dimensions.mapIndexedNotNull { index, dimension ->
        val width = dimension.width?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
        val height = dimension.height?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
        val pageIndex = dimension.pageNumber
            ?.let { pageNumber ->
                if (usesOneBasedPageNumbers) pageNumber - 1 else pageNumber
            }
            ?: index
        if (pageIndex !in 0 until pageCount.coerceAtLeast(0)) return@mapIndexedNotNull null
        pageIndex.toString() to ComicPageMetadata(
            width = width,
            height = height,
        )
    }.toMap()
}

private fun ReadableItem.kavitaMetadataCacheKey(
    baseUrl: String,
): String = listOf(
    baseUrl.trimEnd('/'),
    id,
    remoteId.orEmpty(),
    remoteFileId.orEmpty(),
    remoteFileType.orEmpty(),
    pageCount?.toString().orEmpty(),
).joinToString(separator = "::")

private fun KavitaStoredSession.metadataCacheBaseUrl(): String = activeBaseUrl
    ?.takeIf(String::isNotBlank)
    ?: baseUrl
