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
    private val composites = mutableStateMapOf<ComicPageCurlCompositeKey, ImageBitmap>()

    fun clear() {
        composites.clear()
    }

    fun compositeFor(
        spread: ComicPageSpread?,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
    ): ImageBitmap? = spread
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
        composites.keys
            .filter { it !in keysToKeep }
            .forEach { key -> composites.remove(key) }

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
    ) {
        val key = spread.compositeKey(canvasSize, pageFitMode)
        if (key in composites) return
        val image = loadComposite(
            item = item,
            spread = spread,
            pageSource = pageSource,
            canvasSize = canvasSize,
            pageFitMode = pageFitMode,
            stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
        ) ?: return
        composites[key] = image
    }

    private suspend fun loadComposite(
        item: ReadableItem,
        spread: ComicPageSpread,
        pageSource: ComicPageSource,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int,
    ): ImageBitmap? = try {
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
                ComicSpreadRasterizer.rasterize(
                    spread = spread,
                    pageImagesById = pageImagesById,
                    canvasSize = canvasSize,
                    pageFitMode = pageFitMode,
                    stitchedSpreadOverlapPx = stitchedOverlap,
                )
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }
}

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
