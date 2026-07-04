package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
internal fun ContinuousComicPageItem(
    item: ReadableItem,
    page: ComicPageSpec,
    pageSource: ComicPageSource,
    shouldLoadImage: Boolean,
    metadataLoadingEnabled: Boolean,
    contentWidth: Dp,
    fallbackPlaceholderHeight: Dp,
    onVisiblePageLoaded: (Int) -> Unit,
    onToggleOverlay: () -> Unit,
) {
    val pageMetadata by produceState<ComicPageMetadata?>(
        initialValue = null,
        key1 = item.id,
        key2 = page.id,
        key3 = metadataLoadingEnabled,
    ) {
        if (!metadataLoadingEnabled) {
            value = null
            return@produceState
        }
        value = pageSource.loadPageMetadata(item, page)
    }
    val pageResult by produceState<ComicPageLoadResult?>(
        initialValue = null,
        key1 = item.id,
        key2 = page.id,
        key3 = shouldLoadImage,
    ) {
        if (!shouldLoadImage) {
            value = null
            return@produceState
        }
        value = pageSource.loadPageImage(
            item = item,
            page = page,
            priority = ComicPageLoadPriority.Visible,
        )
    }
    LaunchedEffect(shouldLoadImage, page.pageIndex, pageResult) {
        if (shouldLoadImage && pageResult != null) {
            onVisiblePageLoaded(page.pageIndex)
        }
    }
    val placeholderHeight = remember(contentWidth, fallbackPlaceholderHeight, pageMetadata) {
        continuousReaderPlaceholderHeight(
            contentWidth = contentWidth,
            metadata = pageMetadata,
        ) ?: fallbackPlaceholderHeight
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (val result = pageResult) {
            is ComicPageLoadResult.Success -> {
                val aspectRatio = remember(result.image) {
                    if (result.image.height > 0) {
                        result.image.width / result.image.height.toFloat()
                    } else {
                        1f
                    }
                }
                Box(modifier = Modifier.width(contentWidth)) {
                    Image(
                        bitmap = result.image,
                        contentDescription = page.contentDescription,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(page.id, onToggleOverlay) {
                                detectTapGestures(onTap = { onToggleOverlay() })
                            }
                            .height(contentWidth / aspectRatio),
                    )
                }
            }

            is ComicPageLoadResult.Failure -> {
                Box(
                    modifier = Modifier
                        .width(contentWidth)
                        .height(placeholderHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    ReaderPageMessage(message = result.message)
                }
            }

            null -> {
                Box(
                    modifier = Modifier
                        .width(contentWidth)
                        .height(placeholderHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    ReaderPageMessage(message = "Loading page...")
                }
            }
        }
    }
}
