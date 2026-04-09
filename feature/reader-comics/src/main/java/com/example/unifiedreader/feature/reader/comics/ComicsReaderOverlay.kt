package com.example.unifiedreader.feature.reader.comics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayDropdownItem
import com.example.unifiedreader.feature.reader.core.ReaderOverlayMenuAction
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayPanelState

@Composable
internal fun ReaderOverlayBar(
    itemId: String,
    title: String,
    progressLabel: String,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    spreadMode: ComicSpreadMode,
    splitWidePages: Boolean,
    overlayVisible: Boolean,
    onBack: () -> Unit,
    onReadingDirectionChanged: (ComicReadingDirection) -> Unit,
    onPageFitModeChanged: (ComicPageFitMode) -> Unit,
    onSpreadModeChanged: (ComicSpreadMode) -> Unit,
    onSplitWidePagesChanged: (Boolean) -> Unit,
) {
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
                        tint = Color(0xFFF5F7FA),
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
                        contentDescription = "Page fit mode",
                        tint = Color(0xFFF5F7FA),
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
            ReaderOverlayMenuAction(
                expanded = activePanel == ComicsOverlayPanel.Spread,
                onExpandedChange = { expanded ->
                    activePanel = if (expanded) {
                        ComicsOverlayPanel.Spread
                    } else {
                        ComicsOverlayPanel.None
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = "Spread mode",
                        tint = Color(0xFFF5F7FA),
                    )
                },
            ) {
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
                ReaderOverlayDropdownItem(
                    label = "Split wide: Off",
                    selected = !splitWidePages,
                    onClick = {
                        activePanel = ComicsOverlayPanel.None
                        onSplitWidePagesChanged(false)
                    },
                )
                ReaderOverlayDropdownItem(
                    label = "Split wide: On",
                    selected = splitWidePages,
                    onClick = {
                        activePanel = ComicsOverlayPanel.None
                        onSplitWidePagesChanged(true)
                    },
                )
            }
        },
    )
}

private enum class ComicsOverlayPanel {
    None,
    Direction,
    Fit,
    Spread,
}
