package com.example.unifiedreader.feature.reader.comics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        ComicsReaderPreferencesSheet(
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
