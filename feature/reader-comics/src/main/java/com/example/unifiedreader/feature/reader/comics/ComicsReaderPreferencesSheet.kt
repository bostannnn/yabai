package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPane
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPaneAnchor
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode

@Composable
internal fun ComicsReaderPreferencesSheet(
    isOpen: Boolean,
    maxSheetHeight: Dp,
    readerMode: ComicReaderMode,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    spreadMode: ComicSpreadMode,
    onDismissRequest: () -> Unit,
    onReaderModeChanged: (ComicReaderMode) -> Unit,
    onReadingDirectionChanged: (ComicReadingDirection) -> Unit,
    onPageFitModeChanged: (ComicPageFitMode) -> Unit,
    onSpreadModeChanged: (ComicSpreadMode) -> Unit,
) {
    val tokens = readerOverlayTokens()
    val sheetMaxHeight = (maxSheetHeight * 0.64f).coerceAtMost(ComicsPreferencesSheetMaxHeight)
    ReaderAttachedPane(
        visible = isOpen,
        anchor = ReaderAttachedPaneAnchor.Top,
        maxPaneHeight = sheetMaxHeight,
        maxPaneWidth = ComicsPreferencesSheetMaxWidth,
        horizontalAlignment = Alignment.End,
        horizontalPadding = 10.dp,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.primaryText,
            )
            ComicsPreferenceSection(label = "Reading style") {
                ComicReaderMode.entries.forEach { entry ->
                    ComicsSelectableRow(
                        label = entry.label,
                        selected = entry == readerMode,
                        onClick = { onReaderModeChanged(entry) },
                    )
                }
            }
            ComicsPreferenceSection(label = "Direction") {
                ComicReadingDirection.entries.forEach { entry ->
                    ComicsSelectableRow(
                        label = entry.label,
                        selected = entry == readingDirection,
                        onClick = { onReadingDirectionChanged(entry) },
                    )
                }
            }
            if (readerMode == ComicReaderMode.Paged) {
                ComicsPreferenceSection(label = "Pages") {
                    ComicSpreadMode.entries.forEach { entry ->
                        ComicsSelectableRow(
                            label = entry.label,
                            selected = entry == spreadMode,
                            onClick = { onSpreadModeChanged(entry) },
                        )
                    }
                }
                ComicsPreferenceSection(label = "Fit") {
                    ComicPageFitMode.entries.forEach { entry ->
                        ComicsSelectableRow(
                            label = entry.label,
                            selected = entry == pageFitMode,
                            onClick = { onPageFitModeChanged(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicsPreferenceSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = readerOverlayTokens().colors.secondaryText,
        )
        ComicsSelectableRowGroup(content = content)
    }
}

@Composable
private fun ComicsSelectableRowGroup(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = readerOverlayTokens().colors.elevatedContainer,
                shape = readerOverlayTokens().shapes.controlBar,
            ),
    ) {
        content()
    }
}

@Composable
private fun ComicsSelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) tokens.colors.selectedContainer else Color.Transparent,
                shape = tokens.shapes.controlBar,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = tokens.colors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ComicsPreferencesSheetMaxHeight = 560.dp
private val ComicsPreferencesSheetMaxWidth = 480.dp
