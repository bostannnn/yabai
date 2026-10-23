package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.feature.reader.core.ReaderChromeDefaults
import com.example.unifiedreader.feature.reader.core.ReaderPanel
import com.example.unifiedreader.feature.reader.core.ReaderPanelSlot
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.appProgressFill
import kotlin.math.roundToInt

/**
 * The book's table of contents as a start-edge panel between the bars (a bottom sheet on
 * phones). Hierarchy is the indent alone; the current entry is filled and says how far into it
 * you are; entries already read are muted with a check; the list opens two rows above the
 * current entry; the footer is the chapter and the book's progress with a bar.
 */
@Composable
internal fun EpubTableOfContentsSheet(
    entries: List<EpubTocEntry>,
    bookTitle: String,
    currentHref: String?,
    currentProgress: Double,
    isOpen: Boolean,
    slot: ReaderPanelSlot,
    onDismissRequest: () -> Unit,
    onEntrySelected: (EpubTocEntry) -> Unit,
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
    val activeFraction = remember(entries, activeEntry, currentProgress) {
        activeEntry?.let { entry -> entries.epubTocEntryFraction(entry, currentProgress) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(isOpen, activeEntry?.id, entries) {
        if (!isOpen) return@LaunchedEffect
        val index = entries.indexOfFirst { it.id == activeEntry?.id }
        if (index >= 0) {
            listState.scrollToItem((index - EpubTocRowsAboveActive).coerceAtLeast(0))
        }
    }

    ReaderPanel(
        visible = isOpen,
        placement = AppFloatingPanelPlacement.Start,
        width = ReaderChromeDefaults.contentsWidth,
        slot = slot,
        onDismissRequest = onDismissRequest,
        testTag = EpubTocSheetTag,
    ) {
        AppPanelHeader(
            title = "Contents",
            subtitle = epubTocSubtitle(bookTitle = bookTitle, chapterCount = chapterCount),
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
            // Fills the panel, so the footer stays at its bottom under a short list.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items = entries, key = EpubTocEntry::id) { entry ->
                    val selected = entry.id == activeEntry?.id
                    EpubTableOfContentsRow(
                        entry = entry,
                        selected = selected,
                        finished = entry.isEpubTocEntryFinished(activeEntry),
                        throughLabel = activeFraction?.takeIf { selected }?.let(::epubTocThroughLabel),
                        onClick = { onEntrySelected(entry) },
                    )
                }
            }
        }
        AppDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chapterPosition?.let { "Chapter ${it.index} of ${it.count}" }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildEpubPercentLabel(currentProgress),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.primaryText,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .appProgressFill(
                        fraction = currentProgress.toFloat().coerceIn(0f, 1f),
                        progressColor = tokens.colors.accent,
                        trackColor = tokens.colors.track,
                    ),
            )
        }
    }
}

@Composable
private fun EpubTableOfContentsRow(
    entry: EpubTocEntry,
    selected: Boolean,
    finished: Boolean,
    throughLabel: String?,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    // The selected role (primaryContainer), which the cover's accent recolours in the reader.
    val onSelected = MaterialTheme.colorScheme.onPrimaryContainer
    val titleColor = when {
        selected -> onSelected
        finished -> tokens.colors.secondaryText
        else -> tokens.colors.primaryText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EpubTocRowHeight)
            .clip(CircleShape)
            .background(if (selected) tokens.colors.selectedContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 14.dp + tocRowIndent(entry.depth), end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            throughLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
        if (finished && !selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = "Read",
                tint = tokens.colors.secondaryText,
                modifier = Modifier.size(16.dp),
            )
        }
        entry.position?.let { position ->
            // A fixed column, so the read checks before it line up.
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontFeatureSettings = "tnum"),
                color = if (selected) titleColor else tokens.colors.secondaryText,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 32.dp),
            )
        }
    }
}

/** "A Short History of Rain · 6 chapters". */
internal fun epubTocSubtitle(bookTitle: String, chapterCount: Int): String = listOfNotNull(
    bookTitle.trim().takeIf(String::isNotEmpty),
    when (chapterCount) {
        0 -> null
        1 -> "1 chapter"
        else -> "$chapterCount chapters"
    },
).joinToString(" · ")

/** "40% through". */
internal fun epubTocThroughLabel(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}% through"

private fun tocRowIndent(depth: Int): Dp = (depth.coerceAtLeast(0) * 22).dp

private val EpubTocRowHeight = 48.dp

/** Rows kept above the current entry when the list opens, so it reads in context. */
private const val EpubTocRowsAboveActive = 2

internal const val EpubTocSheetTag = "epub-toc-sheet"
