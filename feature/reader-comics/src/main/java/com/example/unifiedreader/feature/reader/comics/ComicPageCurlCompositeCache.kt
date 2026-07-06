package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

@Stable
internal class ComicPageCurlCompositeCache {
    private val composites = mutableStateMapOf<ComicPageCurlCompositeKey, ComicPageCurlComposite>()

    internal val cachedCompositeCount: Int
        get() = composites.size

    fun clear() {
        composites.clear()
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
        val targetIndexes = listOf(
            settledSpreadIndex,
            settledSpreadIndex - 1,
            settledSpreadIndex + 1,
        ).filter { it in spreads.indices }
        val keysToKeep = targetIndexes
            .map { spreads[it].compositeKey(canvasSize, pageFitMode) }
            .toSet()
        retainOnly(keysToKeep)

        coroutineScope {
            targetIndexes.map { spreadIndex ->
                async {
                    ensureComposite(
                        item = item,
                        spread = spreads[spreadIndex],
                        pageSource = pageSource,
                        canvasSize = canvasSize,
                        pageFitMode = pageFitMode,
                        stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
                        keysToKeep = keysToKeep,
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun ensureComposite(
        item: ReadableItem,
        spread: ComicPageSpread,
        pageSource: ComicPageSource,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int,
        keysToKeep: Set<ComicPageCurlCompositeKey>,
    ) {
        val key = spread.compositeKey(canvasSize, pageFitMode)
        if (key in composites) return
        val composite = loadComposite(
            item = item,
            spread = spread,
            pageSource = pageSource,
            canvasSize = canvasSize,
            pageFitMode = pageFitMode,
            stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
        ) ?: return
        composites[key] = composite
        if (composites.size > MaximumCachedCurlComposites) {
            retainOnly(keysToKeep)
        }
    }

    private suspend fun loadComposite(
        item: ReadableItem,
        spread: ComicPageSpread,
        pageSource: ComicPageSource,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int,
    ): ComicPageCurlComposite? = try {
        coroutineScope {
            val loadedImagesByPageId = spread.pages
                .map(ComicSpreadPage::spec)
                .distinctBy(ComicPageSpec::id)
                .map { page ->
                    async(Dispatchers.IO) {
                        val image = try {
                            when (
                                val result = pageSource.loadPageImage(
                                    item = item,
                                    page = page,
                                    priority = ComicPageLoadPriority.Prefetch,
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
                val stitchedOverlap = if (spread.isLikelyStitchedSpread(loadedImagesByPageId)) {
                    stitchedSpreadOverlapPx
                } else {
                    0
                }
                val layout = ComicSpreadRasterizer.layout(
                    spread = spread,
                    imageSizesByPageId = pageImagesById.mapValues { (_, image) ->
                        IntSize(image.width, image.height)
                    },
                    canvasSize = canvasSize,
                    pageFitMode = pageFitMode,
                    stitchedSpreadOverlapPx = stitchedOverlap,
                )
                val image = ComicSpreadRasterizer.rasterize(
                    spread = spread,
                    pageImagesById = pageImagesById,
                    canvasSize = canvasSize,
                    pageFitMode = pageFitMode,
                    stitchedSpreadOverlapPx = stitchedOverlap,
                )
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
    }

    private companion object {
        const val MaximumCachedCurlComposites = 3
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
