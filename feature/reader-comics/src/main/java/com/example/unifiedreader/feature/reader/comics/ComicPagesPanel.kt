package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChromeDefaults
import com.example.unifiedreader.feature.reader.core.ReaderPanel
import com.example.unifiedreader.feature.reader.core.ReaderPanelSlot
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement
import com.example.unifiedreader.ui.components.AppPanelHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Every page of the issue as a three-column grid of 2:3 thumbnails (a start-edge panel between
 * the bars, a bottom sheet on phones). The page or spread on screen is ringed in the accent; a
 * thumbnail opens its page. The footer is where you are: "Pages 2–3 of 8 · 38%".
 */
@Composable
internal fun ComicPagesPanel(
    item: ReadableItem,
    issueLabel: String,
    pages: List<ComicPageSpec>,
    currentPageIndexes: Set<Int>,
    progress: Float,
    pageSource: ComicPageSource,
    readerWorkCoordinator: ReaderWorkCoordinator?,
    previewLoadingEnabled: Boolean,
    isOpen: Boolean,
    slot: ReaderPanelSlot,
    onDismissRequest: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    val tokens = readerOverlayTokens()
    val gridState = rememberLazyGridState()
    LaunchedEffect(isOpen) {
        if (!isOpen) return@LaunchedEffect
        val first = currentPageIndexes.minOrNull() ?: return@LaunchedEffect
        // A row of context above the current page.
        gridState.scrollToItem((first - PagesGridColumns).coerceAtLeast(0))
    }
    ReaderPanel(
        visible = isOpen,
        placement = AppFloatingPanelPlacement.Start,
        width = ReaderChromeDefaults.pagesWidth,
        slot = slot,
        onDismissRequest = onDismissRequest,
        testTag = ComicPagesPanelTag,
    ) {
        AppPanelHeader(
            title = "Pages",
            subtitle = comicPagesSubtitle(issueLabel = issueLabel, pageCount = pages.size),
            onClose = onDismissRequest,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(PagesGridColumns),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = pages, key = ComicPageSpec::id) { page ->
                ComicPagesGridItem(
                    item = item,
                    page = page,
                    selected = page.pageIndex in currentPageIndexes,
                    pageSource = pageSource,
                    readerWorkCoordinator = readerWorkCoordinator,
                    previewLoadingEnabled = previewLoadingEnabled,
                    onClick = { onPageSelected(page.pageIndex) },
                )
            }
        }
        AppDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comicPagesPositionLabel(currentPageIndexes, pages.size),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = comicPercentLabel(progress),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.primaryText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ComicPagesGridItem(
    item: ReadableItem,
    page: ComicPageSpec,
    selected: Boolean,
    pageSource: ComicPageSource,
    readerWorkCoordinator: ReaderWorkCoordinator?,
    previewLoadingEnabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    val pages = remember(page) { listOf(page) }
    val previewVersion by remember(item.id, page.id, pageSource) {
        pageSource.observePagePreviewVersion(item = item, pages = pages, options = PreviewOptions)
    }.collectAsState(initial = 0L)
    val preview by produceState<ComicPagePreviewResult>(
        initialValue = ComicPagePreviewResult.Unavailable,
        item.id,
        page.id,
        previewVersion,
        previewLoadingEnabled,
    ) {
        if (!previewLoadingEnabled) return@produceState
        readerWorkCoordinator?.awaitReaderBestEffortWorkAllowed()
        value = withContext(Dispatchers.IO) {
            pageSource.loadPagePreview(item = item, page = page, options = PreviewOptions)
        }
    }
    val shape = tokens.shapes.previewItem
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(tokens.colors.fallbackPage)
                .border(
                    width = if (selected) PagesSelectedRing else 0.dp,
                    color = if (selected) tokens.colors.accent else Color.Transparent,
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(if (selected) PagesSelectedRing else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val result = preview) {
                is ComicPagePreviewResult.Success -> Image(
                    bitmap = result.image,
                    contentDescription = page.contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                ComicPagePreviewResult.Unavailable -> Text(
                    text = (page.pageIndex + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.tertiaryText,
                )
            }
        }
        Text(
            text = (page.pageIndex + 1).toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) tokens.colors.primaryText else tokens.colors.secondaryText,
            modifier = if (selected) Modifier.testTag(ComicPagesCurrentTag) else Modifier,
        )
    }
}

/** "Neon Ronin #2 · 8 pages". */
internal fun comicPagesSubtitle(issueLabel: String, pageCount: Int): String = listOfNotNull(
    issueLabel.trim().takeIf(String::isNotEmpty),
    if (pageCount == 1) "1 page" else "$pageCount pages",
).joinToString(" · ")

/** "Pages 2–3 of 8" for a spread, "Page 5 of 8" for one page. */
internal fun comicPagesPositionLabel(pageIndexes: Set<Int>, pageCount: Int): String {
    val sorted = pageIndexes.filter { it in 0 until pageCount }.sorted()
    return when {
        sorted.isEmpty() -> "$pageCount pages"
        sorted.size == 1 -> "Page ${sorted.first() + 1} of $pageCount"
        else -> "Pages ${sorted.first() + 1}–${sorted.last() + 1} of $pageCount"
    }
}

/** The dock's position: "2–3 / 8", "5 / 8". */
internal fun comicDockPositionLabel(pageIndexes: Set<Int>, pageCount: Int): String {
    val sorted = pageIndexes.filter { it in 0 until pageCount }.sorted()
    return when {
        sorted.isEmpty() -> "$pageCount"
        sorted.size == 1 -> "${sorted.first() + 1} / $pageCount"
        else -> "${sorted.first() + 1}–${sorted.last() + 1} / $pageCount"
    }
}

internal fun comicPercentLabel(progress: Float): String = "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"

private const val PagesGridColumns = 3
private val PagesSelectedRing = 2.5.dp

internal const val ComicPagesPanelTag = "comic-pages-panel"
internal const val ComicPagesCurrentTag = "comic-pages-current"
