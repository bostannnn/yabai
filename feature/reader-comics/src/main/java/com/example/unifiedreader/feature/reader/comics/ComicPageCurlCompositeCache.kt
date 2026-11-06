package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Spreads rasterized for the curl, on [canvasColor] (the reader's `readerCanvas`). */
@Stable
internal class ComicPageCurlCompositeCache(
    private val canvasColor: Color,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val composites = mutableStateMapOf<ComicPageCurlCompositeKey, ComicPageCurlComposite>()
    private val loads = mutableMapOf<ComicPageCurlCompositeKey, Mutex>()
    private var generation = 0
    private var retainedKeys = emptySet<ComicPageCurlCompositeKey>()
    private var cacheCapacity = MaximumCachedCurlComposites

    internal val cachedCompositeCount: Int
        get() = composites.size

    fun clear() {
        traceComicReaderSection("ComicPageCurl.clearCompositeCache") {
            composites.clear()
            generation++
            retainedKeys = emptySet()
            loads.entries.removeAll { !it.value.isLocked }
        }
    }

    fun compositeFor(
        spread: ComicPageSpread?,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
    ): ComicPageCurlComposite? = spread
        ?.takeIf { canvasSize.width > 0 && canvasSize.height > 0 }
        ?.let { composites[it.compositeKey(canvasSize, pageFitMode)] }

    suspend fun prewarm(
        item: ReadableItem,
        spreads: List<ComicPageSpread>,
        settledSpreadIndex: Int,
        pageSource: ComicPageSource,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int,
    ) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || spreads.isEmpty()) {
            clear()
            return
        }
        val targetIndexes = traceComicReaderSection("ComicPageCurl.preparePrewarmTargets") {
            val bytesPerComposite = canvasSize.width.toLong() * canvasSize.height * 4L
            val capacity = (MaximumCacheBytes / bytesPerComposite).toInt().coerceIn(3, MaximumCachedCurlComposites)
            cacheCapacity = capacity
            val targets = listOf(
                settledSpreadIndex,
                settledSpreadIndex + 1,
                settledSpreadIndex - 1,
                settledSpreadIndex + 2,
                settledSpreadIndex - 2,
            ).filter { it in spreads.indices }.take(capacity)
            val keys = targets
                .map { spreads[it].compositeKey(canvasSize, pageFitMode) }
                .toSet()
            retainOnly(keys)
            retainedKeys = keys
            targets
        }

        coroutineScope {
            targetIndexes.map { spreadIndex ->
                async {
                    getOrLoad(
                        item = item,
                        spread = spreads[spreadIndex],
                        pageSource = pageSource,
                        canvasSize = canvasSize,
                        pageFitMode = pageFitMode,
                        stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
                    )
                }
            }.awaitAll()
        }
    }

    /** An intentional turn waits for its images instead of switching to the sliding pager. */
    suspend fun getOrLoad(
        item: ReadableItem,
        spread: ComicPageSpread,
        pageSource: ComicPageSource,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int,
        priority: ComicPageLoadPriority = ComicPageLoadPriority.Prefetch,
    ): ComicPageCurlComposite? {
        val key = spread.compositeKey(canvasSize, pageFitMode)
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
        composites[key]?.let { return it }
        val requestedGeneration = generation
        return loads.getOrPut(key) { Mutex() }.withLock {
            composites[key]?.let { return@withLock it }
            val composite = loadComposite(
                item, spread, pageSource, canvasSize, pageFitMode, stitchedSpreadOverlapPx, priority,
            ) ?: return@withLock null
            if (generation != requestedGeneration) return@withLock null
            composites[key] = composite
            if (composites.size > cacheCapacity) {
                retainOnly(retainedKeys + key)
                while (composites.size > cacheCapacity) {
                    composites.remove(composites.keys.first { it != key })
                }
            }
            composite
        }
    }

    private suspend fun loadComposite(
        item: ReadableItem,
        spread: ComicPageSpread,
        pageSource: ComicPageSource,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int,
        priority: ComicPageLoadPriority,
    ): ComicPageCurlComposite? = try {
        coroutineScope {
            val loadedImagesByPageId = spread.pages
                .map(ComicSpreadPage::spec)
                .distinctBy(ComicPageSpec::id)
                .map { page ->
                    async(ioDispatcher) {
                        val image = try {
                            when (
                                val result = pageSource.loadPageImage(
                                    item = item,
                                    page = page,
                                    priority = priority,
                                )
                            ) {
                                is ComicPageLoadResult.Success -> result.image
                                is ComicPageLoadResult.Failure -> null
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            null
                        }
                        page.id to image
                    }
                }
                .awaitAll()
                .toMap()

            if (spread.pages.any { page -> loadedImagesByPageId[page.spec.id] == null }) {
                return@coroutineScope null
            }
            val pageImagesById = loadedImagesByPageId
                .mapNotNull { (pageId, image) ->
                    image?.let { pageId to it }
                }
                .toMap()
            if (pageImagesById.size != loadedImagesByPageId.size) {
                return@coroutineScope null
            }

            withContext(Dispatchers.Default) {
                ensureActive()
                val stitchedOverlap = if (spread.isLikelyStitchedSpread(loadedImagesByPageId)) {
                    stitchedSpreadOverlapPx
                } else {
                    0
                }
                val layout = traceComicReaderSection("ComicPageCurl.layoutComposite") {
                    ComicSpreadRasterizer.layout(
                        spread = spread,
                        imageSizesByPageId = pageImagesById.mapValues { (_, image) ->
                            IntSize(image.width, image.height)
                        },
                        canvasSize = canvasSize,
                        pageFitMode = pageFitMode,
                        stitchedSpreadOverlapPx = stitchedOverlap,
                    )
                }
                ensureActive()
                val image = traceComicReaderSection("ComicPageCurl.rasterizeComposite") {
                    ComicSpreadRasterizer.rasterize(
                        spread = spread,
                        pageImagesById = pageImagesById,
                        canvasSize = canvasSize,
                        pageFitMode = pageFitMode,
                        backgroundColor = canvasColor,
                        stitchedSpreadOverlapPx = stitchedOverlap,
                    )
                }
                ensureActive()
                ComicPageCurlComposite(
                    image = image,
                    layout = layout,
                )
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private fun retainOnly(
        keysToKeep: Set<ComicPageCurlCompositeKey>,
    ) {
        composites.keys
            .filter { it !in keysToKeep }
            .forEach { key -> composites.remove(key) }
        loads.entries.removeAll { it.key !in keysToKeep && !it.value.isLocked }
    }

    private companion object {
        const val MaximumCachedCurlComposites = 5
        const val MaximumCacheBytes = 96L * 1024 * 1024
    }
}

internal data class ComicPageCurlComposite(
    val image: ImageBitmap,
    val layout: ComicSpreadRasterLayout,
)

private data class ComicPageCurlCompositeKey(
    val spreadId: String,
    val pageIds: List<String>,
    val canvasSize: IntSize,
    val pageFitMode: ComicPageFitMode,
)

private fun ComicPageSpread.compositeKey(
    canvasSize: IntSize,
    pageFitMode: ComicPageFitMode,
): ComicPageCurlCompositeKey = ComicPageCurlCompositeKey(
    spreadId = id,
    pageIds = pages.map { page -> "${page.spec.id}:${page.segment.name}" },
    canvasSize = canvasSize,
    pageFitMode = pageFitMode,
)
