package com.example.unifiedreader.feature.reader.epub

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppIcons

@Composable
internal fun EpubReaderOverlay(
    title: String,
    progressLabel: String,
    isPreferencesPaneOpen: Boolean,
    onBack: () -> Unit,
    onPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier,
    applySafeGesturesPadding: Boolean = true,
    bottomPadding: Dp = 8.dp,
) {
    val tokens = readerOverlayTokens()
    CompactReaderOverlayFrame(
        title = title,
        progressLabel = progressLabel,
        modifier = modifier,
        applySafeGesturesPadding = applySafeGesturesPadding,
        bottomPadding = bottomPadding,
        leadingActions = {
            ReaderOverlayBackButton(onClick = onBack)
        },
        trailingActions = {
            ReaderOverlayIconButton(
                isActive = isPreferencesPaneOpen,
                onClick = onPreferencesClick,
            ) {
                Icon(
                    imageVector = AppIcons.FormatSize,
                    contentDescription = "Reader preferences",
                    tint = tokens.colors.primaryText,
                )
            }
        },
    )
}
