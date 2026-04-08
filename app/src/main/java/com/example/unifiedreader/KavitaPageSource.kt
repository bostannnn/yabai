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

class KavitaPageSource(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ComicPageSource {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)
    private val pageBitmapCache = LruCache<String, ComicPageLoadResult.Success>(12)
    private val cacheGeneration = AtomicInteger(0)
    private val prefetchInFlight = mutableSetOf<String>()
    private val prefetchLock = Any()

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (item.sourceType != SourceType.KAVITA || item.format != FormatType.CBZ) return emptyList()
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
        if (item.sourceType != SourceType.KAVITA) {
            return ComicPageLoadResult.Failure("Unsupported source.")
        }
        val session = kavitaCredentialStore.activeSessionFor(item)
            ?: return ComicPageLoadResult.Failure("Connect to Kavita before reading remote comics.")
        val cacheKey = pageCacheKey(
            baseUrl = session.baseUrl,
            itemId = item.id,
            pageId = page.id,
        )
        val requestGeneration = cacheGeneration.get()
        pageBitmapCache[cacheKey]?.let { return it }
        val chapterId = item.remoteId
            ?: return ComicPageLoadResult.Failure("Unable to resolve Kavita chapter id.")
        val pageIndex = page.id.toIntOrNull()
            ?: return ComicPageLoadResult.Failure("Unable to resolve Kavita page index.")
        val api = kavitaApiFactory.create(session.baseUrl)
        val result = runCatching {
            sessionRequestRunner.run(
                api = api,
                session = session,
            ) { accessToken, apiKey ->
                api.downloadPageImage(
                    accessToken = accessToken,
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    apiKey = apiKey,
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
        if (item.sourceType != SourceType.KAVITA || pageCount <= 1) return
        val session = kavitaCredentialStore.activeSessionFor(item) ?: return
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

class CompositeComicPageSource(
    private val localComicPageSource: LocalComicPageSource,
    private val kavitaPageSource: KavitaPageSource,
    private val stumpPageSource: StumpPageSource,
) : ComicPageSource {
    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.getPages(item)
        SourceType.KAVITA -> kavitaPageSource.getPages(item)
        SourceType.STUMP -> stumpPageSource.getPages(item)
        SourceType.GRIMMORY -> emptyList()
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.loadPageImage(item, page)
        SourceType.KAVITA -> kavitaPageSource.loadPageImage(item, page)
        SourceType.STUMP -> stumpPageSource.loadPageImage(item, page)
        SourceType.GRIMMORY -> ComicPageLoadResult.Failure("Unsupported source.")
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        when (item.sourceType) {
            SourceType.LOCAL -> localComicPageSource.prefetchAround(item, settledPageIndex, pageCount)
            SourceType.KAVITA -> kavitaPageSource.prefetchAround(item, settledPageIndex, pageCount)
            SourceType.STUMP -> stumpPageSource.prefetchAround(item, settledPageIndex, pageCount)
            SourceType.GRIMMORY -> Unit
        }
    }

    fun clearKavitaCache() {
        kavitaPageSource.clear()
    }

    fun clearStumpCache() {
        stumpPageSource.clear()
    }
}
