package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPane
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPaneAnchor
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens

@androidx.compose.runtime.Composable
internal fun EpubTableOfContentsSheet(
    title: String,
    entries: List<EpubTocEntry>,
    currentHref: String?,
    currentProgress: Double,
    isOpen: Boolean,
    maxSheetHeight: Dp,
    anchor: ReaderAttachedPaneAnchor,
    onDismissRequest: () -> Unit,
    onEntrySelected: (EpubTocEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = readerOverlayTokens()
    val activeEntry = androidx.compose.runtime.remember(entries, currentProgress, currentHref) {
        entries.activeEpubTocEntry(
            currentHref = currentHref,
            currentProgress = currentProgress,
        )
    }
    val sheetMaxHeight = when {
        maxSheetHeight < 540.dp -> maxSheetHeight * 0.74f
        maxSheetHeight < 900.dp -> maxSheetHeight * 0.58f
        else -> maxSheetHeight * 0.5f
    }
    ReaderAttachedPane(
        visible = isOpen,
        anchor = anchor,
        maxPaneHeight = sheetMaxHeight,
        maxPaneWidth = EpubTocSheetMaxWidth,
        horizontalAlignment = Alignment.Start,
        horizontalPadding = 10.dp,
        onDismissRequest = onDismissRequest,
        testTag = EpubTocSheetTag,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Contents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.primaryText,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.tertiaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 12.dp,
                ),
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
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = entries,
                        key = { entry -> entry.id },
                    ) { entry ->
                        EpubTableOfContentsSheetRow(
                            entry = entry,
                            selected = entry.id == activeEntry?.id,
                            onClick = {
                                onEntrySelected(entry)
                            },
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EpubTableOfContentsSheetRow(
    entry: EpubTocEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    val titleColor = when {
        selected -> tokens.colors.primaryText
        entry.depth == 0 -> tokens.colors.progress
        else -> tokens.colors.secondaryText
    }
    val titleStyle = when {
        selected -> MaterialTheme.typography.labelLarge
        entry.depth == 0 -> MaterialTheme.typography.bodyLarge
        else -> MaterialTheme.typography.bodyMedium
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
                start = 16.dp + tocRowIndent(entry.depth),
                end = 14.dp,
                top = if (entry.depth == 0) 11.dp else 9.dp,
                bottom = if (entry.depth == 0) 11.dp else 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entry.depth > 0) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .background(
                        color = if (selected) tokens.colors.primaryText else tokens.colors.tertiaryText,
                        shape = CircleShape,
                    ),

            )
        }
        Text(
            text = entry.title,
            style = titleStyle,
            fontWeight = if (selected || entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = titleColor,
            maxLines = if (entry.depth == 0) 3 else 2,
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

private fun tocRowIndent(depth: Int): Dp = (depth.coerceAtLeast(0) * 12).dp

internal const val EpubTocSheetTag = "epub-toc-sheet"
private val EpubTocSheetMaxWidth = 312.dp
