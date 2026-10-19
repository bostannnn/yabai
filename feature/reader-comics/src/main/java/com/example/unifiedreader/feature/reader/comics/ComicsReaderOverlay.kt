package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.ui.components.AppIcons

@Composable
internal fun ReaderOverlayBar(
    itemId: String,
    title: String,
    progressLabel: String,
    readerMode: ComicReaderMode,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    spreadMode: ComicSpreadMode,
    pageTurnStyle: PageTurnStyle,
    overlayVisible: Boolean,
    onBack: () -> Unit,
    onReaderModeChanged: (ComicReaderMode) -> Unit,
    onReadingDirectionChanged: (ComicReadingDirection) -> Unit,
    onPageFitModeChanged: (ComicPageFitMode) -> Unit,
    onSpreadModeChanged: (ComicSpreadMode) -> Unit,
    onPageTurnStyleChanged: (PageTurnStyle) -> Unit,
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
            .windowInsetsPadding(
                WindowInsets.safeGestures.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            )
            .zIndex(1f),
    ) {
        CompactReaderOverlayFrame(
            title = title,
            progressLabel = progressLabel,
            applySafeGesturesPadding = false,
            bottomPadding = 0.dp,
            leadingActions = {
                ReaderOverlayBackButton(onClick = onBack)
            },
            trailingActions = {
                ReaderOverlayIconButton(
                    isActive = isPreferencesSheetOpen,
                    onClick = { isPreferencesSheetOpen = !isPreferencesSheetOpen },
                ) {
                    Icon(
                        imageVector = AppIcons.Tune,
                        contentDescription = "Reading settings",
                        tint = tokens.colors.primaryText,
                    )
                }
            },
        )
        // The panel's slot: everything under the bar, clear of the gesture bar so the phone
        // sheet sits above it. The card anchors to the slot's top end, under the button.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
        ) {
            ComicsReaderPreferencesSheet(
                isOpen = isPreferencesSheetOpen,
                maxSheetHeight = maxHeight,
                title = title,
                readerMode = readerMode,
                readingDirection = readingDirection,
                pageFitMode = pageFitMode,
                spreadMode = spreadMode,
                pageTurnStyle = pageTurnStyle,
                onDismissRequest = { isPreferencesSheetOpen = false },
                onReaderModeChanged = onReaderModeChanged,
                onReadingDirectionChanged = onReadingDirectionChanged,
                onPageFitModeChanged = onPageFitModeChanged,
                onSpreadModeChanged = onSpreadModeChanged,
                onPageTurnStyleChanged = onPageTurnStyleChanged,
            )
        }
    }
}
