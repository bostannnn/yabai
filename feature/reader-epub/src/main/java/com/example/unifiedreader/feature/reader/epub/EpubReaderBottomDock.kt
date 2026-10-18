package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.unifiedreader.feature.reader.core.ReaderBottomProgressBar
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppIcons

/** The bottom bar: the contents button and the page slider. The contents panel itself is a sibling of the docks. */
@Composable
internal fun EpubReaderBottomDock(
    currentProgress: Double,
    currentPage: Int,
    pageCount: Int,
    chapterMarks: List<Float>,
    isTocOpen: Boolean,
    onTocClick: () -> Unit,
    onProgressSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = readerOverlayTokens()
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeGestures.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ReaderBottomProgressBar(
            progress = currentProgress.toFloat(),
            percentLabel = buildEpubPercentLabel(currentProgress),
            pageLabel = buildEpubPageProgressLabel(
                position = currentPage,
                pageCount = pageCount,
            ),
            chapterMarks = chapterMarks,
            applySafeGesturesPadding = false,
            leadingAction = {
                ReaderOverlayIconButton(
                    isActive = isTocOpen,
                    onClick = onTocClick,
                ) {
                    Icon(
                        imageVector = AppIcons.Toc,
                        contentDescription = "Table of contents",
                        tint = tokens.colors.primaryText,
                    )
                }
            },
            onProgressSelected = onProgressSelected,
        )
    }
}
