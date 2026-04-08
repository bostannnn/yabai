package com.example.unifiedreader

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StumpPageSource(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ComicPageSource {
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)
    private val pageBitmapCache = LruCache<String, ComicPageLoadResult.Success>(12)
    private val cacheGeneration = AtomicInteger(0)
    private val prefetchInFlight = mutableSetOf<String>()
    private val prefetchLock = Any()

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (item.sourceType != SourceType.STUMP) return emptyList()
        if (item.format != FormatType.CBZ && item.format != FormatType.CBR) return emptyList()
        val pageCount = item.pageCount?.coerceAtLeast(0) ?: return emptyList()
        return List(pageCount) { index ->
            ComicPageSpec(
                id = index.toString(),
                contentDescription = "${item.title} page ${index + 1}",
            )
        }
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult {
        if (item.sourceType != SourceType.STUMP) {
            return ComicPageLoadResult.Failure("Unsupported source.")
        }
        val session = stumpCredentialStore.activeSessionFor(item)
            ?: return ComicPageLoadResult.Failure("Connect to Stump before reading remote comics.")
        val cacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemId = item.id,
            pageId = page.id,
        )
        val requestGeneration = cacheGeneration.get()
        pageBitmapCache[cacheKey]?.let { return it }
        val mediaId = item.remoteId
            ?: return ComicPageLoadResult.Failure("Unable to resolve Stump media id.")
        val pageIndex = page.id.toIntOrNull()
            ?: return ComicPageLoadResult.Failure("Unable to resolve Stump page index.")
        val api = stumpApiFactory.create(session.baseUrl)
        val result = runCatching {
            sessionRequestRunner.run(
                api = api,
                session = session,
            ) { accessToken ->
                api.downloadPageImage(
                    accessToken = accessToken,
                    mediaId = mediaId,
                    pageIndex = pageIndex,
                )
            }
        }.mapCatching { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Unable to decode this page.")
            ComicPageLoadResult.Success(bitmap.asImageBitmap())
        }.getOrElse { throwable ->
            ComicPageLoadResult.Failure(throwable.message ?: "Unable to load this page.")
        }
        if (result is ComicPageLoadResult.Success && cacheGeneration.get() == requestGeneration) {
            pageBitmapCache.put(cacheKey, result)
        }
        return result
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        if (item.sourceType != SourceType.STUMP || pageCount <= 1) return
        val session = stumpCredentialStore.activeSessionFor(item) ?: return
        val pages = getPages(item)
        val candidateIndexes = buildList {
            for (offset in 1..4) {
                add(settledPageIndex + offset)
            }
            for (offset in 1..4) {
                add(settledPageIndex - offset)
            }
        }.filter { it in 0 until minOf(pageCount, pages.size) }

        candidateIndexes.forEach { pageIndex ->
            val page = pages[pageIndex]
            val cacheKey = pageCacheKey(
                baseUrl = session.baseUrl,
                itemId = item.id,
                pageId = page.id,
            )
            if (pageBitmapCache.get(cacheKey) != null) {
                return@forEach
            }
            val shouldLaunch = synchronized(prefetchLock) {
                prefetchInFlight.add(cacheKey)
            }
            if (!shouldLaunch) {
                return@forEach
            }
            scope.launch {
                try {
                    loadPageImage(item, page)
                } finally {
                    synchronized(prefetchLock) {
                        prefetchInFlight.remove(cacheKey)
                    }
                }
            }
        }
    }

    fun clear() {
        cacheGeneration.incrementAndGet()
        pageBitmapCache.evictAll()
        synchronized(prefetchLock) {
            prefetchInFlight.clear()
        }
    }

    private fun pageCacheKey(
        baseUrl: String,
        itemId: String,
        pageId: String,
    ): String = "${baseUrl.trimEnd('/')}::$itemId::$pageId"
}
