package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The top bar. The reading settings panel is a sibling of the docks, not hosted here. */
@Composable
internal fun EpubReaderTopDock(
    title: String,
    progressLabel: String,
    isPreferencesOpen: Boolean,
    onBack: () -> Unit,
    onPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeGestures.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        EpubReaderOverlay(
            title = title,
            progressLabel = progressLabel,
            isPreferencesPaneOpen = isPreferencesOpen,
            onBack = onBack,
            onPreferencesClick = onPreferencesClick,
            applySafeGesturesPadding = false,
            bottomPadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
