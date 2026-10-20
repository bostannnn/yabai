package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppFloatingPanel
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement
import com.example.unifiedreader.ui.components.AppPanelHeader

/**
 * The book's table of contents as a start-edge panel (a bottom sheet on phones), shaped like
 * the drawer. Hierarchy is shown by indent alone; the current entry is filled, the entries
 * already read are muted; the list opens scrolled to the current entry; the footer repeats
 * the position so the panel also works as a progress view.
 */
@Composable
internal fun EpubTableOfContentsSheet(
    entries: List<EpubTocEntry>,
    currentHref: String?,
    currentProgress: Double,
    currentPage: Int,
    pageCount: Int,
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    onEntrySelected: (EpubTocEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = readerOverlayTokens()
    val activeEntry = remember(entries, currentProgress, currentHref) {
        entries.activeEpubTocEntry(
            currentHref = currentHref,
            currentProgress = currentProgress,
        )
    }
    val chapterPosition = remember(entries, activeEntry) {
        entries.epubTocChapterPosition(activeEntry)
    }
    val chapterCount = remember(entries) { entries.epubTocChapterIndices().size }
    val listState = rememberLazyListState()
    LaunchedEffect(isOpen, activeEntry?.id, entries) {
        if (!isOpen) return@LaunchedEffect
        val index = entries.indexOfFirst { it.id == activeEntry?.id }
        if (index >= 0) {
            listState.scrollToItem((index - EpubTocRowsAboveActive).coerceAtLeast(0))
        }
    }

    AppFloatingPanel(
        visible = isOpen,
        placement = AppFloatingPanelPlacement.Start,
        onDismissRequest = onDismissRequest,
        containerColor = tokens.colors.container,
        testTag = EpubTocSheetTag,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeGestures),
    ) {
        AppPanelHeader(
            title = "Contents",
            subtitle = when (chapterCount) {
                0 -> null
                1 -> "1 chapter"
                else -> "$chapterCount chapters"
            },
            onClose = onDismissRequest,
        )
        if (entries.isEmpty()) {
            Text(
                text = "No table of contents",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.secondaryText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.id },
                ) { _, entry ->
                    EpubTableOfContentsRow(
                        entry = entry,
                        selected = entry.id == activeEntry?.id,
                        finished = entry.isEpubTocEntryFinished(activeEntry),
                        onClick = { onEntrySelected(entry) },
                    )
                }
            }
        }
        if (chapterPosition != null || (currentPage > 0 && pageCount > 0)) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterPosition?.let { "Chapter ${it.index} of ${it.count}" }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildEpubTocFooterProgress(
                        progress = currentProgress,
                        position = currentPage,
                        pageCount = pageCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.primaryText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EpubTableOfContentsRow(
    entry: EpubTocEntry,
    selected: Boolean,
    finished: Boolean,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    val topLevel = entry.depth == 0
    val titleColor = when {
        selected -> tokens.colors.primaryText
        finished -> tokens.colors.mutedText
        topLevel -> tokens.colors.primaryText
        else -> tokens.colors.secondaryText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) tokens.colors.selectedContainer else Color.Transparent,
                shape = tokens.shapes.selectedRow,
            )
            .clickable(onClick = onClick)
            .padding(
                start = 12.dp + tocRowIndent(entry.depth),
                end = 12.dp,
                top = if (topLevel) 11.dp else 8.dp,
                bottom = if (topLevel) 11.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = entry.title,
            style = if (topLevel) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = when {
                selected -> FontWeight.SemiBold
                topLevel -> FontWeight.Medium
                else -> FontWeight.Normal
            },
            color = titleColor,
            maxLines = if (topLevel) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.position?.let { position ->
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) tokens.colors.primaryText else tokens.colors.mutedText,
                maxLines = 1,
            )
        }
    }
}

private fun buildEpubTocFooterProgress(
    progress: Double,
    position: Int,
    pageCount: Int,
): String {
    val pages = buildEpubPageProgressLabel(position = position, pageCount = pageCount)
    val percent = buildEpubPercentLabel(progress)
    return if (pages.isEmpty()) percent else "$percent · $pages"
}

private fun tocRowIndent(depth: Int): Dp = (depth.coerceAtLeast(0) * 16).dp

/** Rows kept above the current entry when the list opens, so it reads in context. */
private const val EpubTocRowsAboveActive = 2

internal const val EpubTocSheetTag = "epub-toc-sheet"
