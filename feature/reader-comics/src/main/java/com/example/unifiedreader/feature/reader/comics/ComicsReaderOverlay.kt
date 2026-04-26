package com.example.unifiedreader.feature.reader.comics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayDropdownItem
import com.example.unifiedreader.feature.reader.core.ReaderOverlayMenuAction
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayPanelState
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode

@Composable
internal fun ReaderOverlayBar(
    itemId: String,
    title: String,
    progressLabel: String,
    readerMode: ComicReaderMode,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    spreadMode: ComicSpreadMode,
    overlayVisible: Boolean,
    onBack: () -> Unit,
    onReaderModeChanged: (ComicReaderMode) -> Unit,
    onReadingDirectionChanged: (ComicReadingDirection) -> Unit,
    onPageFitModeChanged: (ComicPageFitMode) -> Unit,
    onSpreadModeChanged: (ComicSpreadMode) -> Unit,
) {
    val tokens = readerOverlayTokens()
    var activePanel by rememberReaderOverlayPanelState(
        key = itemId,
        overlayVisible = overlayVisible,
        hiddenPanel = ComicsOverlayPanel.None,
    )
    CompactReaderOverlayFrame(
        title = title,
        progressLabel = progressLabel,
        leadingActions = {
            ReaderOverlayBackButton(onClick = onBack)
        },
        trailingActions = {
            ReaderOverlayMenuAction(
                expanded = activePanel == ComicsOverlayPanel.Layout,
                onExpandedChange = { expanded ->
                    activePanel = if (expanded) {
                        ComicsOverlayPanel.Layout
                    } else {
                        ComicsOverlayPanel.None
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "Layout",
                        tint = tokens.colors.primaryText,
                    )
                },
            ) {
                ComicsReaderOverlaySectionLabel(label = "Reading style")
                ComicReaderMode.entries.forEach { entry ->
                    ReaderOverlayDropdownItem(
                        label = entry.label,
                        selected = entry == readerMode,
                        onClick = {
                            activePanel = ComicsOverlayPanel.None
                            onReaderModeChanged(entry)
                        },
                    )
                }
                if (readerMode == ComicReaderMode.Paged) {
                    HorizontalDivider(color = tokens.colors.divider)
                    ComicsReaderOverlaySectionLabel(label = "Pages")
                    ComicSpreadMode.entries.forEach { entry ->
                        ReaderOverlayDropdownItem(
                            label = entry.label,
                            selected = entry == spreadMode,
                            onClick = {
                                activePanel = ComicsOverlayPanel.None
                                onSpreadModeChanged(entry)
                            },
                        )
                    }
                }
            }
            ReaderOverlayMenuAction(
                expanded = activePanel == ComicsOverlayPanel.Direction,
                onExpandedChange = { expanded ->
                    activePanel = if (expanded) {
                        ComicsOverlayPanel.Direction
                    } else {
                        ComicsOverlayPanel.None
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Reading direction",
                        tint = tokens.colors.primaryText,
                    )
                },
            ) {
                ComicReadingDirection.entries.forEach { entry ->
                    ReaderOverlayDropdownItem(
                        label = entry.label,
                        selected = entry == readingDirection,
                        onClick = {
                            activePanel = ComicsOverlayPanel.None
                            onReadingDirectionChanged(entry)
                        },
                    )
                }
            }
            if (readerMode == ComicReaderMode.Paged) {
                ReaderOverlayMenuAction(
                    expanded = activePanel == ComicsOverlayPanel.Fit,
                    onExpandedChange = { expanded ->
                        activePanel = if (expanded) {
                            ComicsOverlayPanel.Fit
                        } else {
                            ComicsOverlayPanel.None
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Fit",
                            tint = tokens.colors.primaryText,
                        )
                    },
                ) {
                    ComicPageFitMode.entries.forEach { entry ->
                        ReaderOverlayDropdownItem(
                            label = entry.label,
                            selected = entry == pageFitMode,
                            onClick = {
                                activePanel = ComicsOverlayPanel.None
                                onPageFitModeChanged(entry)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ComicsReaderOverlaySectionLabel(
    label: String,
) {
    val tokens = readerOverlayTokens()
    Text(
        text = label,
        color = tokens.colors.tertiaryText,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private enum class ComicsOverlayPanel {
    None,
    Layout,
    Direction,
    Fit,
}
