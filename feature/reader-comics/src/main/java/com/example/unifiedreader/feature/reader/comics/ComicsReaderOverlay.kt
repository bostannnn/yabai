package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
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
    var isPreferencesSheetOpen by rememberSaveable(itemId) { mutableStateOf(false) }
    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) {
            isPreferencesSheetOpen = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
    ) {
        CompactReaderOverlayFrame(
            title = title,
            progressLabel = progressLabel,
            leadingActions = {
                ReaderOverlayBackButton(onClick = onBack)
            },
            trailingActions = {
                ReaderOverlayIconButton(
                    isActive = isPreferencesSheetOpen,
                    onClick = { isPreferencesSheetOpen = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "Reader preferences",
                        tint = tokens.colors.primaryText,
                    )
                }
            },
        )
        if (isPreferencesSheetOpen) {
            Spacer(modifier = Modifier.height(ComicsTopPaneSeamHeight))
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            ComicsReaderPreferencesSheet(
                isOpen = isPreferencesSheetOpen,
                maxSheetHeight = maxHeight,
                readerMode = readerMode,
                readingDirection = readingDirection,
                pageFitMode = pageFitMode,
                spreadMode = spreadMode,
                onDismissRequest = { isPreferencesSheetOpen = false },
                onReaderModeChanged = onReaderModeChanged,
                onReadingDirectionChanged = onReadingDirectionChanged,
                onPageFitModeChanged = onPageFitModeChanged,
                onSpreadModeChanged = onSpreadModeChanged,
            )
        }
    }
}

private val ComicsTopPaneSeamHeight = 2.dp
