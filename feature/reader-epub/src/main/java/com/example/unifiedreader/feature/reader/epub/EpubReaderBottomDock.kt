package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPaneAnchor
import com.example.unifiedreader.feature.reader.core.ReaderBottomProgressBar
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import org.readium.r2.shared.publication.Link

@Composable
internal fun EpubReaderBottomDock(
    title: String,
    tocEntries: List<EpubTocEntry>,
    currentHref: String?,
    currentProgress: Double,
    currentPage: Int,
    pageCount: Int,
    chapterMarks: List<Float>,
    isTocOpen: Boolean,
    onDismissToc: () -> Unit,
    onTocClick: () -> Unit,
    onTocEntrySelected: (Link) -> Unit,
    onProgressSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeGestures.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
            ) {
                EpubTableOfContentsSheet(
                    title = title,
                    entries = tocEntries,
                    currentHref = currentHref,
                    currentProgress = currentProgress,
                    isOpen = isTocOpen,
                    maxSheetHeight = maxHeight,
                    onDismissRequest = onDismissToc,
                    anchor = ReaderAttachedPaneAnchor.Bottom,
                    onEntrySelected = { entry ->
                        onTocEntrySelected(entry.link)
                    },
                )
            }
            if (isTocOpen) {
                Spacer(modifier = Modifier.height(EpubDockPaneSeamHeight))
            }
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
                            imageVector = Icons.AutoMirrored.Filled.Toc,
                            contentDescription = "Table of contents",
                            tint = readerOverlayTokens().colors.primaryText,
                        )
                    }
                },
                onProgressSelected = onProgressSelected,
            )
        }
    }
}

private val EpubDockPaneSeamHeight = 2.dp
